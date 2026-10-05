package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

/**
 * Launches an Eclipse launch configuration — so an external tool or agent can start an
 * application without the developer doing it by hand.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code config} — a launch config name, or a project name (required to launch).
 *       Omit it to just <em>list</em> the available configs.</li>
 *   <li>{@code mode} — {@code debug} (default) or {@code run}. Debug is the default
 *       because the dev loop (hot-code-replace, HotswapAgent) needs a debug JVM.</li>
 *   <li>{@code open} — {@code true} to open the config's project if it is closed in the
 *       workspace (and build it) instead of refusing to launch.</li>
 *   <li>{@code ignoreErrors} — {@code true} to launch even when the project or one of the
 *       projects it depends on has compile errors (by default the launch is refused and
 *       the broken projects and their errors are reported).</li>
 *   <li>{@code allowMultiple} — {@code true} to launch even when a launch of the same
 *       config is already running (by default that is refused: the second instance
 *       usually just loses a port-bind fight).</li>
 *   <li>{@code waitForPort} — a TCP port; when given, the response is delayed until
 *       something listens on that port (success), the launched process terminates
 *       (failure, with its last output and a pointer at {@code /console}), a modal dialog
 *       appears (failure, with the dialog and a pointer at {@code /dialogs}), the app stalls
 *       — no port and no output for {@link LaunchLiveness#STALL_MILLIS} (failure, with its
 *       last output and thread state) — or {@code timeout} elapses.</li>
 *   <li>{@code timeout} — seconds to wait for {@code waitForPort} (default 60).</li>
 *   <li>{@code port} — launch on this port instead of the configuration's own (a
 *       {@code -WOPort} argument injected into an unsaved working copy; see
 *       {@link LaunchPorts}). Also the default for {@code waitForPort}.</li>
 *   <li>{@code args} — extra program arguments appended the same way.</li>
 *   <li>{@code stopOthers} — {@code true} to stop the apps holding the target port and take
 *       it (see Ports). Not the default: ask the developer first.</li>
 * </ul>
 *
 * <h2>Ports</h2>
 * Development apps share a port by convention (1200 unless the config says otherwise),
 * so launching one while another runs is a clash the frameworks resolve silently — the
 * earlier instance is stopped or evicted, and an external caller only sees an app die.
 * Preflight decides it without waiting. An app on the port is never stopped unasked: it may
 * be in use by another agent or the developer. The launch runs on the next free port and
 * says so ({@code movedFrom}, {@code port}), listing the holders with how long ago each was
 * last worked on through the dev server. {@code stopOthers=true} takes the port — stopping
 * the holders, hard-killing one that won't stop — for when the developer has said to;
 * {@code port=N} runs on a port of the caller's choosing.
 *
 * <h2>Truthfulness</h2>
 * The contract an external caller actually needs is not "Eclipse was asked to launch"
 * but "a JVM is (about to be) running" — so this handler checks the reasons a launch
 * silently produces nothing <em>before</em> launching: unknown config, closed project,
 * compile errors, already-running instance. Each refusal names its reason and, where
 * one exists, the parameter that overrides it.
 *
 * <p>Resolution is handled by {@link LaunchConfigs}: an exact config name wins;
 * otherwise the query is treated as a project name, preferring a "local"/"dev" config
 * when a project has several so we never accidentally fire e.g. "… - Production". When
 * the choice is still ambiguous, nothing is launched — the candidates are returned for
 * the caller to choose from, because guessing wrong could start the wrong environment.
 *
 * <h2>No dialogs, ever</h2>
 * Eclipse's own launch path asks the user things: "Errors exist in required project(s)
 * — proceed?", "Save modified resources?", "Switch to debug mode?". Those are modal
 * dialogs on the UI thread — invisible to an external caller, who only sees a launch
 * that never becomes ready and then starts "fixing" a problem that is really a dialog
 * waiting for a click. So this handler takes over every decision Eclipse would have
 * prompted for, and launches with Eclipse's prompting disabled:
 * <ol>
 *   <li>It checks the same project set Eclipse checks — the launched project plus its
 *       transitive references ({@link LaunchClosure}) — after the same incremental
 *       pre-launch build, and refuses with the broken projects' names and errors (and
 *       the recovery command: a clean rebuild of the dependency usually fixes it).</li>
 *   <li>It launches synchronously on the request thread via
 *       {@link ILaunchConfiguration#launch(String, org.eclipse.core.runtime.IProgressMonitor, boolean, boolean)}
 *       with the debug framework's status handlers switched off for the duration
 *       ({@link #ENABLE_STATUS_HANDLERS} — the platform's own headless-launching switch).
 *       Every prompt point in the launch delegate then continues silently, and a launch
 *       failure comes back as a {@link CoreException} — reported as JSON — instead of an
 *       error dialog. (A launch the developer fires by hand during those few seconds also
 *       goes unprompted; that's the accepted cost.) Unsaved editor buffers are NOT saved
 *       — the launch reflects what's on disk, which is what an external editor writes.</li>
 * </ol>
 * Dialogs from <em>other</em> sources (hot-code-replace failures, other tooling) can still
 * appear; the wait loop reports one that shows up mid-wait so the caller can answer it
 * through {@code /dialogs}.
 */
class LaunchHandler implements DevServerHandler {

	/** The debug framework's preference node. */
	static final String DEBUG_CORE_PREFS = "org.eclipse.debug.core";

	/**
	 * The debug framework's master switch for status handlers — the mechanism behind all
	 * of its launch-time prompts. With it off, {@code DebugPlugin.getStatusHandler} returns
	 * null and the launch delegate's prompt points continue without asking. This is the
	 * platform's documented way to launch headlessly; we flip it only around our own
	 * synchronous launch call and restore it in a {@code finally}.
	 */
	static final String ENABLE_STATUS_HANDLERS = "org.eclipse.debug.core.PREF_ENABLE_STATUS_HANDLERS";

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String query = params.get("config") != null ? params.get("config") : params.get("app");
		final String modeParam = params.get("mode");
		final String mode = "run".equalsIgnoreCase(modeParam) ? ILaunchManager.RUN_MODE : ILaunchManager.DEBUG_MODE;

		// No target → list available configs so the caller can choose.
		if (query == null || query.isEmpty()) {
			return listJson(LaunchConfigs.all());
		}

		final LaunchConfigs.Resolution resolution = LaunchConfigs.resolve(query);

		if (resolution.chosen == null) {
			// Ambiguous or not found — don't guess. Report the candidates.
			if (resolution.candidates.isEmpty()) {
				return "{\"launched\":false,\"reason\":\"no launch config or project matches \\\""
						+ DevServerJson.escape(query) + "\\\"\"}";
			}
			return "{\"launched\":false,\"reason\":\"ambiguous — specify an exact config name\",\"candidates\":"
					+ configArray(resolution.candidates) + "}";
		}

		final ILaunchConfiguration config = resolution.chosen;

		// ---- Preflight: catch the ways a launch silently produces no JVM. ----

		final String projectName = LaunchConfigs.projectNameOf(config);
		final IProject project = projectName.isEmpty() ? null : ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

		String openedNote = "";
		if (project != null && project.exists() && !project.isOpen()) {
			if ("true".equalsIgnoreCase(params.get("open"))) {
				// Opening just the target is not enough: dependency resolution is
				// Maven-workspace-level, which only sees OPEN projects — so open the
				// project's workspace dependencies transitively too (pom-walk).
				final ProjectOpener.Result openResult = ProjectOpener.openWithRelated(project);
				openedNote = ",\"opened\":" + DevServerJson.stringArray(openResult.opened);
			}
			else {
				return "{\"launched\":false,\"reason\":\"project \\\"" + DevServerJson.escape(projectName)
						+ "\\\" is closed in the workspace\",\"hint\":\"pass open=true to open it and its workspace dependencies\"}";
			}
		}

		if (project != null && project.isOpen()) {
			// The same set Eclipse's launch delegate checks (project + transitive references),
			// after the same incremental pre-launch build — so the error check sees the
			// build's outcome, and a broken DEPENDENCY is refused here, as data, instead of
			// surfacing as Eclipse's "Errors exist in required project(s)" dialog.
			final List<IProject> closure = LaunchClosure.of(project);
			LaunchClosure.build(closure);
			if (!"true".equalsIgnoreCase(params.get("ignoreErrors"))) {
				// Java compile / build-path errors only: template-validation markers don't
				// prevent a launch and must not block one.
				List<IProject> broken = LaunchClosure.withErrors(closure);
				if (!broken.isEmpty()) {
					// Errors right after a build are sometimes transient - a classpath job
					// (m2e) finishing late re-triggers a build that clears them. Settle once
					// more and re-check before refusing, so we don't refuse on a snapshot the
					// caller can't reproduce a few seconds later.
					LaunchClosure.awaitClasspathJobs();
					RefreshProjectHandler.waitForBuildToSettle();
					broken = LaunchClosure.withErrors(closure);
				}
				if (!broken.isEmpty()) {
					return refusalForErrors(projectName, broken, openedNote);
				}
			}
		}

		// ---- Ports: decide the clash explicitly instead of letting the runtimes fight. ----

		final Integer requestedPort = parsePort(params.get("port"));
		int targetPort = requestedPort != null ? requestedPort.intValue() : LaunchPorts.portOf(config);
		// Another app on the port is never stopped unasked: it may be in use (another agent's, or the developer's). The
		// launch moves to a free port instead; stopOthers=true takes the port, for when the developer said so.
		final boolean stopOthers = "true".equalsIgnoreCase(params.get("stopOthers"));

		// A second instance of the same config is only refused when it would land on the
		// same port; with port=N the caller explicitly wants one alongside.
		if (requestedPort == null && !"true".equalsIgnoreCase(params.get("allowMultiple")) && findRunningLaunch(config.getName()) != null) {
			return "{\"launched\":false,\"reason\":\"a launch of \\\"" + DevServerJson.escape(config.getName())
					+ "\\\" is already running\",\"hint\":\"/restart?app=" + DevServerJson.escape(config.getName()) + " to relaunch it, or pass port=N to run a second instance alongside\"}";
		}

		// Port decisions apply to launches of THE application (explicit -WOPort, or the
		// project's principalClass as main type). A utility main in the same project neither
		// wants the dev port nor holds it - it must not be refused because the app is up.
		final boolean applicationLaunch = requestedPort != null || LaunchPorts.isApplicationLaunch(config);

		String stoppedNote = "";
		// The port the app moves to when its own can't be had; set, the launch gets -WOPort.
		Integer movedToPort = null;
		if (applicationLaunch && LaunchPorts.isHeld(targetPort)) {
			final List<String> holders = LaunchPorts.holdersOf(targetPort);
			stoppedNote = ",\"holders\":" + holdersJson(holders);
			if (stopOthers && !holders.isEmpty()) {
				stopHolders(holders, targetPort);
				stoppedNote += ",\"stopped\":" + DevServerJson.stringArray(holders);
			}
			if (LaunchPorts.isHeld(targetPort)) {
				// Held by an app we don't stop unasked, or by one that couldn't be stopped: don't wait
				// on it and don't refuse - run on the next free port, and say so.
				final int free = LaunchLiveness.nextFreePort(targetPort, LaunchPorts::isHeld);
				if (free < 0) {
					return "{\"launched\":false,\"reason\":\"port " + targetPort + " is held and no free port follows it\",\"hint\":\"lsof -nP -iTCP:" + targetPort + " -sTCP:LISTEN\"}";
				}
				stoppedNote += ",\"movedFrom\":" + targetPort + ",\"portNote\":\"" + DevServerJson.escape(movedNote(targetPort, free, holders, stopOthers)) + "\"";
				targetPort = free;
				movedToPort = Integer.valueOf(free);
			}
		}

		// Extra program arguments go into an unsaved working copy; the saved config is untouched.
		ILaunchConfiguration toLaunch = config;
		final StringBuilder extraArguments = new StringBuilder();
		final Integer launchPort = movedToPort != null ? movedToPort : requestedPort;
		if (launchPort != null) {
			extraArguments.append("-WOPort ").append(launchPort);
		}
		if (params.get("args") != null && !params.get("args").isBlank()) {
			extraArguments.append(extraArguments.length() > 0 ? " " : "").append(params.get("args").trim());
		}
		if (extraArguments.length() > 0) {
			toLaunch = LaunchPorts.withExtraArguments(config, extraArguments.toString());
		}

		// ---- Launch. ----

		// Dialogs already open before we launch are the developer's business (a
		// Preferences window, say) — the wait loop only reports ones that appear after.
		final Set<String> dialogsBefore = dialogTitles(ModalDialogs.list());

		final ILaunch launch;
		try {
			launch = launchWithoutPrompts(toLaunch, mode);
		}
		catch (CoreException e) {
			// The failure Eclipse would have shown in an error dialog, as data.
			return "{\"launched\":false,\"config\":\"" + DevServerJson.escape(config.getName())
					+ "\",\"reason\":\"" + DevServerJson.escape(e.getStatus().getMessage()) + "\"}";
		}
		if (launch == null) {
			return "{\"launched\":false,\"config\":\"" + DevServerJson.escape(config.getName())
					+ "\",\"reason\":\"a pre-launch check cancelled the launch\"}";
		}

		// ---- Optionally wait until the app is actually ready (or provably dead). ----

		final String portNote = (applicationLaunch ? ",\"port\":" + targetPort : "") + stoppedNote + openedNote;
		final String waitForPort = params.get("waitForPort");
		if (waitForPort != null && !waitForPort.isEmpty()) {
			// A moved launch is waited for where it went: the asked-for port answers, but for its holder.
			final int waitPort = movedToPort != null ? movedToPort.intValue() : Integer.parseInt(waitForPort);
			return waitJson(config, mode, portNote, launch, dialogsBefore, waitPort, timeoutSeconds(params));
		}
		if (launchPort != null) {
			// The port is chosen (by the caller, or moved), so readiness on it is knowable - wait by default.
			return waitJson(config, mode, portNote, launch, dialogsBefore, launchPort.intValue(), timeoutSeconds(params));
		}

		return "{\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName())
				+ "\",\"mode\":\"" + mode + "\"" + portNote + "}";
	}

	/**
	 * Why the launch moved to another port, for the response: who holds the port, and how to take it.
	 */
	static String movedNote(int port, int free, List<String> holders, boolean stopOthers) {
		final String heldBy = holders.isEmpty() ? "a process the dev server doesn't know"
				: stopOthers ? "\"" + String.join("\", \"", holders) + "\", which wouldn't stop"
				: "\"" + String.join("\", \"", holders) + "\"";
		final String take = holders.isEmpty() || stopOthers ? ""
				: ". It may be in use (see holders: lastTouchedSeconds); to take the port, ask the developer, then relaunch with stopOthers=true";
		return "port " + port + " is held by " + heldBy + ", so the app runs on " + free + " - use that port in log/eval/problems URLs" + take;
	}

	/**
	 * The apps holding a port, each with how long ago it was last worked on through the dev server (null: not recently).
	 */
	static String holdersJson(List<String> holders) {
		final StringBuilder b = new StringBuilder("[");
		for (final String holder : holders) {
			if (b.length() > 1) {
				b.append(',');
			}
			final Long touched = ActivityLog.lastTouchedMillis(namesOf(holder));
			b.append("{\"name\":\"").append(DevServerJson.escape(holder)).append("\",\"lastTouchedSeconds\":")
					.append(touched == null ? "null" : String.valueOf((System.currentTimeMillis() - touched) / 1000)).append('}');
		}
		return b.append(']').toString();
	}

	/** A holder's names as requests name it: itself, and its launch config's project. */
	static List<String> namesOf(String holder) {
		final List<String> names = new ArrayList<>();
		names.add(holder);
		final LaunchConfigs.Resolution resolution = LaunchConfigs.resolve(holder);
		if (resolution.chosen != null) {
			names.add(resolution.chosen.getName());
			names.add(LaunchConfigs.projectNameOf(resolution.chosen));
		}
		return names;
	}

	/**
	 * Stops the apps holding the port, escalating rather than waiting: a clean stop, a few
	 * seconds for the port to come free, then a hard kill of any holder that registered a pid
	 * (a wedged JVM ignores the clean stop), and a few more seconds. Whatever still holds the
	 * port after that is left alone — the caller moves to another port instead of waiting.
	 */
	private static void stopHolders(List<String> holders, int port) throws InterruptedException {
		final StopHandler stopper = new StopHandler();
		for (final String holder : holders) {
			final Map<String, String> stopParams = new java.util.HashMap<>();
			stopParams.put("app", holder);
			stopper.handle(stopParams);
		}
		if (portReleasedWithin(port, 5_000)) {
			return;
		}
		for (final String holder : holders) {
			final Map<String, String> stopParams = new java.util.HashMap<>();
			stopParams.put("app", holder);
			stopParams.put("force", "true");
			stopper.handle(stopParams);
		}
		portReleasedWithin(port, 3_000);
	}

	/** Polls until nothing listens on the port, up to the given time; whether it came free. */
	private static boolean portReleasedWithin(int port, long millis) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + millis;
		while (LaunchPorts.isHeld(port)) {
			if (System.currentTimeMillis() >= deadline) {
				return false;
			}
			Thread.sleep(250);
		}
		return true;
	}

	/**
	 * Launches synchronously with the debug framework's prompting disabled (see the class
	 * doc). {@code build=false} because the closure was already built in preflight —
	 * building again here would only re-run the pre-launch checks we've already made.
	 */
	private static ILaunch launchWithoutPrompts(ILaunchConfiguration config, String mode) throws CoreException {
		ensureDebugUiStarted();
		final IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(DEBUG_CORE_PREFS);
		// Remember the raw prior value (null = "not set, platform default applies") so the
		// restore leaves the node exactly as found. Deliberately not flushed to disk: the
		// override is transient and must never outlive this call.
		final String previous = prefs.get(ENABLE_STATUS_HANDLERS, null);
		prefs.putBoolean(ENABLE_STATUS_HANDLERS, false);
		try {
			final ILaunch launch = config.launch(mode, new NullProgressMonitor(), false, true);
			// A compile error or uncaught exception in this app fails fast instead of suspending it.
			AutoResume.register(launch);
			return launch;
		}
		finally {
			if (previous == null) {
				prefs.remove(ENABLE_STATUS_HANDLERS);
			}
			else {
				prefs.put(ENABLE_STATUS_HANDLERS, previous);
			}
		}
	}

	private static volatile boolean _debugUiStarted;

	/**
	 * Makes sure the debug UI bundle is started before we launch, so the launch gets a console
	 * in Eclipse's Console view (and shows up properly in the Debug view).
	 *
	 * <p>Process consoles are created by the debug UI's {@code ProcessConsoleManager}, which only
	 * exists once {@code org.eclipse.debug.ui} has started - and that bundle activates lazily, the
	 * first time one of its classes is loaded. A launch through {@code DebugUITools.launch} does
	 * that as a side effect; our dialog-free launch uses the core API only, so in a fresh Eclipse
	 * session where nobody has touched Run/Debug yet, the app ran with no console for the
	 * developer to look at. Touching a public debug-UI class activates the bundle; its start-up
	 * also adopts launches that are already running. Done on the UI thread (the bundle creates
	 * UI-affine services) and once per session.
	 */
	private static void ensureDebugUiStarted() {
		if (_debugUiStarted) {
			return;
		}
		try {
			org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> org.eclipse.debug.ui.DebugUITools.getPreferenceStore());
			_debugUiStarted = true;
		}
		catch (Throwable t) {
			// No console is a cosmetic loss; never let it stop a launch.
			org.objectstyle.wolips.componenteditor.ComponenteditorPlugin.getDefault().log(t);
		}
	}

	/** The refusal for compile errors in the launch closure: the broken projects, their errors, and the way out. */
	private static String refusalForErrors(String launchedProject, List<IProject> broken, String openedNote) {
		final List<String> names = new ArrayList<>();
		final StringBuilder projects = new StringBuilder("[");
		for (final IProject p : broken) {
			names.add(p.getName());
			if (projects.length() > 1) {
				projects.append(',');
			}
			projects.append("{\"project\":\"").append(DevServerJson.escape(p.getName()))
					.append("\",\"problems\":").append(WorkspaceProblems.toJsonArray(WorkspaceProblems.javaErrors(p, 10)))
					.append('}');
		}
		projects.append(']');

		final boolean onlyItself = names.size() == 1 && names.get(0).equals(launchedProject);
		final String reason = onlyItself
				? "project \"" + launchedProject + "\" has compile errors"
				: "compile errors in required project(s): " + String.join(", ", names);
		return "{\"launched\":false,\"reason\":\"" + DevServerJson.escape(reason)
				+ "\",\"errorProjects\":" + projects + openedNote
				+ ",\"hint\":\"" + DevServerJson.escape(onlyItself ? ownErrorsHint(launchedProject) : brokenProjectsHint(names)) + "\"}";
	}

	/**
	 * The recovery advice for broken projects in the closure. Stale build state is the
	 * usual cause (a dependency edited on disk, half-built, or built against an older
	 * neighbour), and a clean rebuild of the dependency is the usual cure — so the hint
	 * names the exact call per project, then the override.
	 */
	static String brokenProjectsHint(List<String> brokenProjects) {
		final List<String> calls = new ArrayList<>();
		for (final String name : brokenProjects) {
			calls.add("/refreshProject?project=" + name + "&clean=true");
		}
		return "usually stale build state — clean-rebuild the broken project(s): " + String.join(" ; ", calls)
				+ " — then retry; or pass ignoreErrors=true to launch anyway";
	}

	/**
	 * The hint when the only broken project is the one being launched. There the errors are
	 * almost always real — in code just edited — so the first advice is to fix them; a stale
	 * build (errors that don't match the source) is the second possibility, not the first.
	 */
	static String ownErrorsHint(String project) {
		return "fix the listed compile errors in " + project + "'s code, refresh, then retry; if they don't match the source (a stale build), /refreshProject?project="
				+ project + "&clean=true; or pass ignoreErrors=true to launch anyway";
	}

	private static Integer parsePort(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			final int port = Integer.parseInt(value.trim());
			return port > 0 && port < 65536 ? Integer.valueOf(port) : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static Set<String> dialogTitles(ModalDialogs.Snapshot snapshot) {
		final Set<String> titles = new HashSet<>();
		for (final ModalDialogs.Dialog dialog : snapshot.dialogs) {
			titles.add(dialog.title);
		}
		return titles;
	}

	private static int timeoutSeconds(Map<String, String> params) {
		try {
			if (params.get("timeout") != null) {
				return Math.max(1, Integer.parseInt(params.get("timeout")));
			}
		}
		catch (NumberFormatException e) {
			// keep the default
		}
		return 60;
	}

	/**
	 * Polls until the port answers, the launched process dies, a modal dialog appears, or
	 * the timeout elapses — so the caller's next request can't race a still-booting (or
	 * already-dead, or blocked) app.
	 */
	private static String waitJson(ILaunchConfiguration config, String mode, String openedNote, ILaunch launch, Set<String> dialogsBefore, int port, int timeoutSeconds) throws InterruptedException {
		final long start = System.currentTimeMillis();
		final long deadline = start + timeoutSeconds * 1000L;
		final String base = "\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName()) + "\",\"mode\":\"" + mode + "\"" + openedNote;

		while (System.currentTimeMillis() < deadline) {
			if (portAnswers(port)) {
				final long answeredAfter = System.currentTimeMillis() - start;
				// The port answering is not the app being up: WOApplication's constructor binds
				// the HTTP port BEFORE the subclass's constructor body runs, so an app that
				// fails in its Application constructor answers for a moment, then exits.
				// Reporting that as ready sent callers off to exercise an app that was gone.
				// Watch the process through a short grace period first.
				if (diesWithin(launch, READY_GRACE_MILLIS)) {
					return "{" + base + ",\"ready\":false,\"reason\":\"process terminated right after opening port " + port + " - it failed during startup\""
							+ ",\"hint\":\"see /console?app=" + DevServerJson.escape(config.getName()) + " for the exception\"}";
				}
				return "{" + base + ",\"ready\":true,\"readyPort\":" + port
						+ ",\"startupMillis\":" + answeredAfter + "}";
			}
			if (LaunchLiveness.isOver(launch)) {
				// Over when the process is, even with its debug target lingering: settle the
				// launch so /status and the Debug view agree, and answer now.
				LaunchLiveness.settle(launch);
				return "{" + base + ",\"ready\":false,\"reason\":\"process terminated during startup\"" + consoleTail(launch)
						+ ",\"hint\":\"see /console?app=" + DevServerJson.escape(config.getName()) + " for the output\"}";
			}
			// Neither answering nor saying anything: not starting. Report it with what the app
			// last said and its threads, instead of waiting out the timeout.
			final ConsoleBuffer.Buffer console = ConsoleBuffer.forLaunch(launch);
			final long lastOutput = console != null ? console.lastOutputEpochMillis : 0;
			if (LaunchLiveness.isStalled(start, lastOutput, System.currentTimeMillis())) {
				final int[] threads = LaunchLiveness.threadCounts(launch);
				return "{" + base + ",\"ready\":false,\"reason\":\"stalled: port " + port + " not answering and no output for "
						+ (LaunchLiveness.STALL_MILLIS / 1000) + "s\",\"suspendedThreads\":" + threads[0] + ",\"threads\":" + threads[1] + consoleTail(launch)
						+ ",\"hint\":\"" + (threads[0] > 0 ? "threads are suspended - see /threads; " : "")
						+ "/stop?app=" + DevServerJson.escape(config.getName()) + "&force=true, then launch once more; if it stalls again, report it with /console\"}";
			}
			// A dialog that appeared since we launched is, in all likelihood, about this
			// launch — and nothing will progress until it's answered. Say so, with the
			// dialog itself, rather than letting the caller time out in the dark.
			for (final ModalDialogs.Dialog dialog : ModalDialogs.list().dialogs) {
				if (!dialogsBefore.contains(dialog.title)) {
					return "{" + base + ",\"ready\":false,\"reason\":\"blocked by a modal dialog\",\"dialog\":" + dialog.toJson()
							+ ",\"hint\":\"answer it with /dialogs?press=BUTTON (one of the dialog's buttons) or /dialogs?close=true, then check /status\"}";
				}
			}
			Thread.sleep(500);
		}
		return "{" + base + ",\"ready\":false,\"reason\":\"port " + port + " not answering after "
				+ timeoutSeconds + "s\",\"hint\":\"see /console?app=" + DevServerJson.escape(config.getName()) + "\"}";
	}

	/**
	 * How long a launch must stay alive after its port first answers to count as ready. Long
	 * enough to see an app that bound its port in WOApplication's constructor and then failed
	 * in its own (it exits within a few hundred milliseconds); short enough not to slow every
	 * launch noticeably.
	 */
	private static final long READY_GRACE_MILLIS = 1500;

	/** Whether the launch terminates within the given time (polled). */
	private static boolean diesWithin(ILaunch launch, long millis) throws InterruptedException {
		final long until = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < until) {
			if (LaunchLiveness.isOver(launch)) {
				return true;
			}
			Thread.sleep(100);
		}
		return LaunchLiveness.isOver(launch);
	}

	/** The last few lines the launch wrote, as a {@code ,"lastOutput":[…]} member; empty when it wrote nothing. */
	private static String consoleTail(ILaunch launch) {
		final ConsoleBuffer.Buffer console = ConsoleBuffer.forLaunch(launch);
		if (console == null) {
			return "";
		}
		final List<String> lines = lastLines(console.text(), 5);
		return lines.isEmpty() ? "" : ",\"lastOutput\":" + DevServerJson.stringArray(lines);
	}

	/** The last {@code count} non-blank lines of the text, oldest first. */
	static List<String> lastLines(String text, int count) {
		final List<String> lines = new ArrayList<>();
		final String[] all = text.split("\\R");
		for (int i = all.length - 1; i >= 0 && lines.size() < count; i--) {
			if (!all[i].isBlank()) {
				lines.add(0, all[i].length() > 300 ? all[i].substring(0, 300) + "…" : all[i]);
			}
		}
		return lines;
	}

	private static boolean portAnswers(int port) {
		try (java.net.Socket socket = new java.net.Socket()) {
			socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 250);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	/** The most recent launch of the given config, running or not; null when none exists. */
	static ILaunch findLaunch(String configName) {
		ILaunch found = null;
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			final ILaunchConfiguration c = launch.getLaunchConfiguration();
			if (c != null && configName.equalsIgnoreCase(c.getName())) {
				found = launch;
			}
		}
		return found;
	}

	/** A non-terminated launch of the given config, or null. */
	static ILaunch findRunningLaunch(String configName) {
		final ILaunch launch = findLaunch(configName);
		if (launch == null) {
			return null;
		}
		if (LaunchLiveness.isOver(launch)) {
			LaunchLiveness.settle(launch); // a dead JVM's lingering debug target isn't "running"
			return null;
		}
		return launch;
	}

	private static String listJson(List<ILaunchConfiguration> configs) {
		return "{\"configs\":" + configArray(configs) + "}";
	}

	private static String configArray(List<ILaunchConfiguration> configs) {
		final StringBuilder b = new StringBuilder(configs.size() * 80 + 2);
		b.append('[');
		for (int i = 0; i < configs.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			final ILaunchConfiguration c = configs.get(i);
			b.append("{\"name\":\"").append(DevServerJson.escape(c.getName()))
					.append("\",\"project\":\"").append(DevServerJson.escape(LaunchConfigs.projectNameOf(c)))
					.append("\"}");
		}
		b.append(']');
		return b.toString();
	}
}

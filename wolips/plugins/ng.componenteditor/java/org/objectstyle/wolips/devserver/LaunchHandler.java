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
 *       (failure, with a pointer at {@code /console}), a modal dialog appears (failure,
 *       with the dialog and a pointer at {@code /dialogs}), or {@code timeout} elapses.</li>
 *   <li>{@code timeout} — seconds to wait for {@code waitForPort} (default 60).</li>
 * </ul>
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
				final List<IProject> broken = LaunchClosure.withErrors(closure);
				if (!broken.isEmpty()) {
					return refusalForErrors(projectName, broken, openedNote);
				}
			}
		}

		if (!"true".equalsIgnoreCase(params.get("allowMultiple")) && findRunningLaunch(config.getName()) != null) {
			return "{\"launched\":false,\"reason\":\"a launch of \\\"" + DevServerJson.escape(config.getName())
					+ "\\\" is already running\",\"hint\":\"use /stop or /restart, or pass allowMultiple=true\"}";
		}

		// ---- Launch. ----

		// Dialogs already open before we launch are the developer's business (a
		// Preferences window, say) — the wait loop only reports ones that appear after.
		final Set<String> dialogsBefore = dialogTitles(ModalDialogs.list());

		final ILaunch launch;
		try {
			launch = launchWithoutPrompts(config, mode);
		}
		catch (CoreException e) {
			// The failure Eclipse would have shown in an error dialog, as data.
			return "{\"launched\":false,\"config\":\"" + DevServerJson.escape(config.getName())
					+ "\",\"error\":\"" + DevServerJson.escape(e.getStatus().getMessage()) + "\"}";
		}
		if (launch == null) {
			return "{\"launched\":false,\"config\":\"" + DevServerJson.escape(config.getName())
					+ "\",\"reason\":\"a pre-launch check cancelled the launch\"}";
		}

		// ---- Optionally wait until the app is actually ready (or provably dead). ----

		final String waitForPort = params.get("waitForPort");
		if (waitForPort != null && !waitForPort.isEmpty()) {
			return waitJson(config, mode, openedNote, launch, dialogsBefore, Integer.parseInt(waitForPort), timeoutSeconds(params));
		}

		return "{\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName())
				+ "\",\"mode\":\"" + mode + "\"" + openedNote + "}";
	}

	/**
	 * Launches synchronously with the debug framework's prompting disabled (see the class
	 * doc). {@code build=false} because the closure was already built in preflight —
	 * building again here would only re-run the pre-launch checks we've already made.
	 */
	private static ILaunch launchWithoutPrompts(ILaunchConfiguration config, String mode) throws CoreException {
		final IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(DEBUG_CORE_PREFS);
		// Remember the raw prior value (null = "not set, platform default applies") so the
		// restore leaves the node exactly as found. Deliberately not flushed to disk: the
		// override is transient and must never outlive this call.
		final String previous = prefs.get(ENABLE_STATUS_HANDLERS, null);
		prefs.putBoolean(ENABLE_STATUS_HANDLERS, false);
		try {
			return config.launch(mode, new NullProgressMonitor(), false, true);
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
				+ ",\"hint\":\"" + DevServerJson.escape(brokenProjectsHint(names)) + "\"}";
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
				return "{" + base + ",\"ready\":true,\"port\":" + port
						+ ",\"startupMillis\":" + (System.currentTimeMillis() - start) + "}";
			}
			if (launch.isTerminated()) {
				return "{" + base + ",\"ready\":false,\"reason\":\"process terminated during startup\""
						+ ",\"hint\":\"see /console?app=" + DevServerJson.escape(config.getName()) + " for the output\"}";
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
		return launch != null && !launch.isTerminated() ? launch : null;
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

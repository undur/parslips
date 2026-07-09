package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.swt.widgets.Display;

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
 *   <li>{@code ignoreErrors} — {@code true} to launch even when the project has compile
 *       errors (by default the launch is refused and the errors are reported).</li>
 *   <li>{@code allowMultiple} — {@code true} to launch even when a launch of the same
 *       config is already running (by default that is refused: the second instance
 *       usually just loses a port-bind fight).</li>
 *   <li>{@code waitForPort} — a TCP port; when given, the response is delayed until
 *       something listens on that port (success), the launched process terminates
 *       (failure, with a pointer at {@code /console}), or {@code timeout} elapses.</li>
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
 * <p>Launching runs on the UI thread (the debug UI requires it) via {@code syncExec},
 * and uses {@link DebugUITools#launch} so it behaves exactly like the Run/Debug button
 * (save, build, open console).
 */
class LaunchHandler implements DevServerHandler {

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

		if (project != null && project.isOpen() && !"true".equalsIgnoreCase(params.get("ignoreErrors"))) {
			// Java compile / build-path errors only: template-validation markers don't
			// prevent a launch and must not block one.
			final List<WorkspaceProblems.Problem> errors = WorkspaceProblems.javaErrors(project, 10);
			if (!errors.isEmpty()) {
				return "{\"launched\":false,\"reason\":\"project \\\"" + DevServerJson.escape(projectName)
						+ "\\\" has compile errors\",\"problems\":" + WorkspaceProblems.toJsonArray(errors)
						+ openedNote + ",\"hint\":\"fix them, or pass ignoreErrors=true\"}";
			}
		}

		if (!"true".equalsIgnoreCase(params.get("allowMultiple")) && findRunningLaunch(config.getName()) != null) {
			return "{\"launched\":false,\"reason\":\"a launch of \\\"" + DevServerJson.escape(config.getName())
					+ "\\\" is already running\",\"hint\":\"use /stop or /restart, or pass allowMultiple=true\"}";
		}

		// ---- Launch. ----

		// Snapshot the launches that exist BEFORE we fire, so the wait loop can tell the
		// launch we're creating apart from an older (terminated) launch of the same config
		// still listed by the launch manager — matching by config name alone made a restart
		// report "terminated during startup" while the new process was starting fine.
		final java.util.Set<ILaunch> preexisting = new java.util.HashSet<>(java.util.Arrays.asList(DebugPlugin.getDefault().getLaunchManager().getLaunches()));

		final AtomicReference<String> error = new AtomicReference<>();
		Display.getDefault().syncExec(() -> {
			try {
				// build=true so classes are fresh; behaves like pressing Run/Debug.
				DebugUITools.launch(config, mode);
			}
			catch (Throwable t) {
				error.set(String.valueOf(t));
			}
		});

		if (error.get() != null) {
			return "{\"launched\":false,\"config\":\"" + DevServerJson.escape(config.getName())
					+ "\",\"error\":\"" + DevServerJson.escape(error.get()) + "\"}";
		}

		// ---- Optionally wait until the app is actually ready (or provably dead). ----

		final String waitForPort = params.get("waitForPort");
		if (waitForPort != null && !waitForPort.isEmpty()) {
			return waitJson(config, mode, openedNote, preexisting, Integer.parseInt(waitForPort), timeoutSeconds(params));
		}

		return "{\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName())
				+ "\",\"mode\":\"" + mode + "\"" + openedNote + "}";
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
	 * Polls until the port answers, the launched process dies, or the timeout elapses —
	 * so the caller's next request can't race a still-booting (or already-dead) app.
	 */
	private static String waitJson(ILaunchConfiguration config, String mode, String openedNote, java.util.Set<ILaunch> preexisting, int port, int timeoutSeconds) throws InterruptedException {
		final long start = System.currentTimeMillis();
		final long deadline = start + timeoutSeconds * 1000L;
		final String base = "\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName()) + "\",\"mode\":\"" + mode + "\"" + openedNote;

		while (System.currentTimeMillis() < deadline) {
			if (portAnswers(port)) {
				return "{" + base + ",\"ready\":true,\"port\":" + port
						+ ",\"startupMillis\":" + (System.currentTimeMillis() - start) + "}";
			}
			// Only the launch created by THIS request counts — older launches of the same
			// config linger in the manager (terminated) and must not be mistaken for ours.
			final ILaunch launch = findLaunch(config.getName(), preexisting);
			if (launch != null && launch.isTerminated()) {
				return "{" + base + ",\"ready\":false,\"reason\":\"process terminated during startup\""
						+ ",\"hint\":\"see /console?app=" + DevServerJson.escape(config.getName()) + " for the output\"}";
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
		return findLaunch(configName, java.util.Set.of());
	}

	/** Like {@link #findLaunch(String)}, but ignoring the given (pre-existing) launches. */
	static ILaunch findLaunch(String configName, java.util.Set<ILaunch> ignored) {
		ILaunch found = null;
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			if (ignored.contains(launch)) {
				continue;
			}
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
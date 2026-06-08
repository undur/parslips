package org.objectstyle.wolips.devserver;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Stops a running application — so an external tool or agent can shut one down without
 * the developer doing it by hand.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code app} (or {@code config}) — the app/config/project name to stop (required).</li>
 *   <li>{@code force} — {@code true} to hard-kill the process ({@code kill -9}) instead
 *       of terminating it through Eclipse. Use this when a hot-reload wedged the JVM
 *       (DCEVM can choke on large changes) and a clean terminate won't take.</li>
 * </ul>
 *
 * <h2>How it stops</h2>
 * Default: terminate the matching Eclipse launch (clean — flushes the console, runs
 * shutdown). If no Eclipse launch matches (e.g. the app was started outside Eclipse)
 * but the app registered a pid (see {@link AppRegistry}), fall back to a graceful
 * {@code kill} (SIGTERM) of that pid. With {@code force=true}, skip straight to
 * {@code kill -9} of the registered pid.
 */
class StopHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String name = params.get("app") != null ? params.get("app") : params.get("config");
		if (name == null || name.isEmpty()) {
			return "{\"stopped\":false,\"reason\":\"missing required parameter 'app'\"}";
		}
		final boolean force = "true".equalsIgnoreCase(params.get("force"));

		final AppRegistry.Entry registered = AppRegistry.get(name);
		final String pid = registered != null ? registered.pid : null;

		// Forced hard-kill: go straight for the registered pid. This is the wedged-JVM
		// escape hatch, so we don't bother trying a clean Eclipse terminate first.
		if (force) {
			if (pid == null || pid.isEmpty()) {
				return "{\"stopped\":false,\"reason\":\"force kill requested but no registered pid for \\\""
						+ DevServerJson.escape(name) + "\\\" (is it running and registered?)\"}";
			}
			return killJson(name, pid, true);
		}

		// Default: terminate the matching Eclipse launch (clean shutdown).
		final AtomicReference<Boolean> terminated = new AtomicReference<>(Boolean.FALSE);
		Display.getDefault().syncExec(() -> terminated.set(terminateMatchingLaunch(name)));
		if (Boolean.TRUE.equals(terminated.get())) {
			return "{\"stopped\":true,\"app\":\"" + DevServerJson.escape(name) + "\",\"via\":\"eclipse\"}";
		}

		// No Eclipse launch matched — fall back to a graceful kill of the registered pid.
		if (pid != null && !pid.isEmpty()) {
			return killJson(name, pid, false);
		}

		return "{\"stopped\":false,\"reason\":\"no running Eclipse launch and no registered pid for \\\""
				+ DevServerJson.escape(name) + "\\\"\"}";
	}

	/**
	 * Terminates the running Eclipse launch whose config name or project matches
	 * {@code name}. Runs on the UI thread (caller wraps in syncExec).
	 *
	 * @return true if a matching, terminable launch was found and terminate() was called
	 */
	private static boolean terminateMatchingLaunch(String name) {
		boolean any = false;
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			if (launch.isTerminated() || !launch.canTerminate()) {
				continue;
			}
			final ILaunchConfiguration config = launch.getLaunchConfiguration();
			if (config == null) {
				continue;
			}
			final boolean matches = name.equalsIgnoreCase(config.getName())
					|| name.equalsIgnoreCase(LaunchConfigs.projectNameOf(config));
			if (matches) {
				try {
					launch.terminate();
					any = true;
				}
				catch (Exception e) {
					ComponenteditorPlugin.getDefault().log(e);
				}
			}
		}
		return any;
	}

	/** Sends a kill to the pid: SIGKILL when {@code hard}, otherwise SIGTERM. */
	private static String killJson(String name, String pid, boolean hard) {
		try {
			final ProcessBuilder pb = new ProcessBuilder("kill", hard ? "-9" : "-15", pid);
			pb.redirectErrorStream(true);
			final Process p = pb.start();
			final int exit = p.waitFor();
			final boolean ok = exit == 0;
			return "{\"stopped\":" + ok + ",\"app\":\"" + DevServerJson.escape(name)
					+ "\",\"via\":\"" + (hard ? "kill -9" : "kill") + "\",\"pid\":\"" + DevServerJson.escape(pid)
					+ "\",\"exit\":" + exit + "}";
		}
		catch (Exception e) {
			return "{\"stopped\":false,\"app\":\"" + DevServerJson.escape(name)
					+ "\",\"error\":\"" + DevServerJson.escape(String.valueOf(e)) + "\"}";
		}
	}
}

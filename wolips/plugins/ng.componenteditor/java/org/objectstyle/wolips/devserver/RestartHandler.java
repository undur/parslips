package org.objectstyle.wolips.devserver;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.ILaunch;

/**
 * The edit→see-it cycle as one call: stop the app, optionally refresh+rebuild projects,
 * launch again — replacing the stop/poll/refresh/launch/poll dance every external script
 * otherwise reimplements (with sleeps in all the wrong places).
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code app} (or {@code config}) — the app/config/project to restart (required).</li>
 *   <li>{@code refresh} — comma-separated project names to refresh+rebuild between stop
 *       and launch (e.g. the framework project just edited). Optional.</li>
 *   <li>{@code waitForPort}/{@code timeout}/{@code mode}/{@code open}/{@code ignoreErrors}
 *       — passed through to {@code /launch}.</li>
 * </ul>
 *
 * <p>Delegates to the stop / refreshProject / launch handlers rather than reimplementing
 * them, and reports each stage's result so a failure names the stage it happened in.
 * Between stop and launch it waits for the old launch to actually terminate — the race
 * that makes hand-rolled restart scripts flaky.
 */
class RestartHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String name = params.get("app") != null ? params.get("app") : params.get("config");
		if (name == null || name.isEmpty()) {
			return "{\"error\":\"missing required parameter 'app'\"}";
		}

		// ---- Stage 1: stop (skipped when nothing is running — that's not an error). ----
		String stopResult = "{\"stopped\":false,\"reason\":\"nothing was running\"}";
		if (LaunchHandler.findRunningLaunch(resolveConfigName(name)) != null || AppRegistry.get(name) != null) {
			stopResult = new StopHandler().handle(params);
			waitForTermination(resolveConfigName(name), 15_000);
		}

		// ---- Stage 2: refresh+rebuild the named projects (optional). ----
		String refreshResult = null;
		final String refresh = params.get("refresh");
		if (refresh != null && !refresh.isEmpty()) {
			final RefreshProjectHandler refresher = new RefreshProjectHandler();
			final StringBuilder combined = new StringBuilder("[");
			boolean first = true;
			for (final String projectName : refresh.split(",")) {
				final Map<String, String> refreshParams = new HashMap<>();
				refreshParams.put("project", projectName.trim());
				final String result = refresher.handle(refreshParams);
				if (!first) {
					combined.append(',');
				}
				first = false;
				combined.append(result != null ? result : "{\"project\":\"" + DevServerJson.escape(projectName.trim()) + "\",\"refreshed\":true}");
			}
			refreshResult = combined.append(']').toString();
		}

		// ---- Stage 3: launch (with whatever launch options the caller passed along). ----
		final String launchResult = new LaunchHandler().handle(params);

		final StringBuilder b = new StringBuilder();
		b.append("{\"stop\":").append(stopResult);
		if (refreshResult != null) {
			b.append(",\"refresh\":").append(refreshResult);
		}
		b.append(",\"launch\":").append(launchResult).append('}');
		return b.toString();
	}

	private static String resolveConfigName(String query) {
		final LaunchConfigs.Resolution resolution = LaunchConfigs.resolve(query);
		return resolution.chosen != null ? resolution.chosen.getName() : query;
	}

	/** Waits (bounded) for the config's launch to report terminated, so the relaunch can't race it. */
	private static void waitForTermination(String configName, long timeoutMillis) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			final ILaunch launch = LaunchHandler.findLaunch(configName);
			if (launch == null || launch.isTerminated()) {
				return;
			}
			Thread.sleep(250);
		}
	}
}
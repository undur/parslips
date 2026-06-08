package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
 * </ul>
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
	public String handle(Map<String, String> params) {
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
		return "{\"launched\":true,\"config\":\"" + DevServerJson.escape(config.getName())
				+ "\",\"mode\":\"" + mode + "\"}";
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

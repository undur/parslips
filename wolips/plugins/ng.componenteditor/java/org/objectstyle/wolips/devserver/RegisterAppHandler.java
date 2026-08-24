package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * Records a running application's port in the {@link AppRegistry}, so external tools
 * and AI agents can later discover where an app is running by name (see
 * {@link AppsHandler}) instead of being told the port or guessing a convention.
 *
 * <p>Called by the app itself at startup (best-effort, development-only on the app
 * side). It's the app→dev-server "here I am" direction that complements the
 * dev-server→app direction (open component, refresh, etc.).
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code name} — the application name (required)</li>
 *   <li>{@code port} — the port it's listening on (required, positive integer)</li>
 *   <li>{@code pid} — the process id (optional, informational)</li>
 *   <li>{@code runtime} — the app's framework, {@code "ng"} or {@code "wo"} (optional; absent
 *       from older app builds). Lets a tool pick the right runtime endpoint URL form
 *       ({@code /ng/dev/…} vs {@code …woa/…}) instead of probing or inferring it.</li>
 * </ul>
 *
 * <p>Returns a small JSON acknowledgement. Malformed registrations (missing name or
 * unparseable/!positive port) are reported rather than silently dropped, so a
 * misconfigured app-side ping is debuggable.
 */
class RegisterAppHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String name = params.get("name");
		if (name == null || name.isEmpty()) {
			return "{\"ok\":false,\"error\":\"missing required parameter 'name'\"}";
		}

		final int port = parseInt(params.get("port"), -1);
		if (port <= 0) {
			return "{\"ok\":false,\"error\":\"missing or invalid 'port'\"}";
		}

		final String pid = params.get("pid"); // optional
		final String runtime = params.get("runtime"); // optional: "ng" or "wo"; null from older app builds
		AppRegistry.register(name, port, pid, runtime, System.currentTimeMillis());

		return "{\"ok\":true,\"name\":\"" + DevServerJson.escape(name) + "\",\"port\":" + port + "}";
	}

	private static int parseInt(String value, int fallback) {
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return fallback;
		}
	}
}

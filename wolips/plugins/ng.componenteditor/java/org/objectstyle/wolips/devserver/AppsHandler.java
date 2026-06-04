package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;

/**
 * Reports applications that have announced themselves to the dev server (see
 * {@link RegisterAppHandler}), so an external tool or AI agent can discover an app's
 * port by name instead of being told it or guessing a per-developer convention.
 *
 * <p>Serves two shapes from one handler:
 * <ul>
 *   <li>{@code /apps} — a JSON array of every known app.</li>
 *   <li>{@code /apps?name=Foo} — the single matching app, or {@code found:false} if
 *       nothing has registered under that name.</li>
 * </ul>
 *
 * <p>Each entry includes {@code lastSeen} (ISO-ish epoch millis) so callers can judge
 * staleness: the registry records when an app last <em>announced</em> itself, which
 * is not a guarantee it's still running. Treat an entry as a strong hint, freshest
 * first.
 */
class AppsHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String name = params.get("name");

		// Single-app lookup when a name is given.
		if (name != null && !name.isEmpty()) {
			final AppRegistry.Entry entry = AppRegistry.get(name);
			if (entry == null) {
				return "{\"found\":false,\"name\":\"" + DevServerJson.escape(name) + "\"}";
			}
			return "{\"found\":true,\"app\":" + toJson(entry) + "}";
		}

		// Otherwise, list all known apps.
		final List<AppRegistry.Entry> all = AppRegistry.all();
		final StringBuilder b = new StringBuilder(64 + all.size() * 96);
		b.append("{\"apps\":[");
		for (int i = 0; i < all.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append(toJson(all.get(i)));
		}
		b.append("]}");
		return b.toString();
	}

	private static String toJson(AppRegistry.Entry e) {
		final StringBuilder b = new StringBuilder(96);
		b.append('{')
				.append("\"name\":\"").append(DevServerJson.escape(e.name)).append('"')
				.append(",\"port\":").append(e.port)
				.append(",\"lastSeen\":").append(e.lastSeenEpochMillis);
		if (e.pid != null && !e.pid.isEmpty()) {
			b.append(",\"pid\":\"").append(DevServerJson.escape(e.pid)).append('"');
		}
		b.append('}');
		return b.toString();
	}
}

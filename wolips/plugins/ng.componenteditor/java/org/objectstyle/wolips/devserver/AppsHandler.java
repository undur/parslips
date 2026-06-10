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
 * <p>Liveness is checked at query time: each entry's port is probed, every entry carries
 * a {@code running} flag, and entries found dead are evicted (apps don't deregister on
 * shutdown — they're often killed abruptly or crash). So the list reflects what's actually
 * up <em>now</em>, regardless of how an app died. A single lookup of a just-died app still
 * returns it once with {@code running:false} (then evicts it), so the caller learns "it was
 * here, it's gone" rather than a bare not-found.
 *
 * <p>{@code lastSeen} (epoch millis) records when the app last announced itself — useful as
 * a recency hint alongside {@code running}.
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
			final boolean running = AppRegistry.isReachable(entry);
			if (!running) {
				// Report it once as not-running so the caller sees "it was registered, it's
				// gone now", then evict so later lookups cleanly return not-found.
				AppRegistry.remove(entry.name);
			}
			return "{\"found\":true,\"app\":" + toJson(entry, running) + "}";
		}

		// Otherwise, list all known apps — probing each and evicting the dead, so the list
		// only ever shows what's actually reachable.
		final List<AppRegistry.Entry> all = AppRegistry.all();
		final StringBuilder b = new StringBuilder(64 + all.size() * 96);
		b.append("{\"apps\":[");
		boolean first = true;
		for (final AppRegistry.Entry e : all) {
			if (!AppRegistry.isReachable(e)) {
				AppRegistry.remove(e.name);
				continue; // dead — drop from the list entirely
			}
			if (!first) {
				b.append(',');
			}
			first = false;
			b.append(toJson(e, true));
		}
		b.append("]}");
		return b.toString();
	}

	private static String toJson(AppRegistry.Entry e, boolean running) {
		final StringBuilder b = new StringBuilder(192);
		b.append('{')
				.append("\"name\":\"").append(DevServerJson.escape(e.name)).append('"')
				.append(",\"port\":").append(e.port)
				.append(",\"running\":").append(running)
				.append(",\"lastSeen\":").append(e.lastSeenEpochMillis);
		if (e.pid != null && !e.pid.isEmpty()) {
			b.append(",\"pid\":\"").append(DevServerJson.escape(e.pid)).append('"');
		}

		// The app's dependencies that are open in the workspace with source — i.e. the
		// ones an agent can actually read and edit (jar-only deps are omitted). Computed
		// live from the workspace, since which projects are open can change between
		// queries.
		b.append(",\"dependencies\":[");
		final List<WorkspaceDependencies.Dependency> deps = WorkspaceDependencies.forApp(e.name);
		for (int i = 0; i < deps.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append(depToJson(deps.get(i)));
		}
		b.append(']');

		b.append('}');
		return b.toString();
	}

	private static String depToJson(WorkspaceDependencies.Dependency d) {
		final StringBuilder b = new StringBuilder(160);
		b.append('{')
				.append("\"name\":\"").append(DevServerJson.escape(d.name)).append('"')
				.append(",\"path\":\"").append(DevServerJson.escape(d.path)).append('"')
				.append(",\"sourceFolders\":[");
		for (int i = 0; i < d.sourceFolders.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append('"').append(DevServerJson.escape(d.sourceFolders.get(i))).append('"');
		}
		b.append("]}");
		return b.toString();
	}
}

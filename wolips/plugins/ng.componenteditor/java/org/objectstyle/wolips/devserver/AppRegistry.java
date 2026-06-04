package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of running applications that have announced themselves to the
 * dev server (see {@link RegisterAppHandler}). It exists so an external tool or AI
 * agent can discover which port an app is running on by name, instead of being told
 * the port or guessing a per-developer convention.
 *
 * <p>The model is deliberately simple: <b>one entry per app name, latest write
 * wins</b>, each carrying the port, an optional pid, and a {@code lastSeen}
 * timestamp. That timestamp is the honesty in the design — an entry records when an
 * app <em>last announced itself</em>, not that it's still alive (a crashed app
 * lingers until something overwrites it). Callers use {@code lastSeen} to judge
 * staleness rather than trusting the entry blindly. No persistence and no
 * expiry: the registry is per-Eclipse-session scratch state, and an app re-announces
 * every time it starts.
 *
 * <p>Backed by a {@link ConcurrentHashMap} because registrations arrive on dev-server
 * request threads while queries may read concurrently.
 */
final class AppRegistry {

	/** A single app's last-known location. Immutable; replaced wholesale on re-register. */
	static final class Entry {
		final String name;
		final int port;
		final String pid; // may be null — apps aren't required to send it
		final long lastSeenEpochMillis;

		Entry(String name, int port, String pid, long lastSeenEpochMillis) {
			this.name = name;
			this.port = port;
			this.pid = pid;
			this.lastSeenEpochMillis = lastSeenEpochMillis;
		}
	}

	private static final Map<String, Entry> _byName = new ConcurrentHashMap<>();

	private AppRegistry() {
	}

	/**
	 * Records (or overwrites) an app's location. The caller supplies the timestamp so
	 * registration time is the moment the announcement was received.
	 */
	static void register(String name, int port, String pid, long nowEpochMillis) {
		_byName.put(name, new Entry(name, port, pid, nowEpochMillis));
	}

	/** @return the entry for an app name, or null if none has registered under it. */
	static Entry get(String name) {
		return name == null ? null : _byName.get(name);
	}

	/** @return a snapshot of all known entries (order unspecified). */
	static List<Entry> all() {
		return new ArrayList<>(_byName.values());
	}
}

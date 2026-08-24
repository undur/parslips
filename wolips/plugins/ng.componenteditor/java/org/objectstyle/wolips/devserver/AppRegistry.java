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
 * timestamp. An entry records when an app <em>last announced itself</em>, which is
 * not a guarantee it's still alive. Apps don't deregister on shutdown — they're often
 * killed abruptly, crash, or end up unreachable, so a shutdown hook would miss exactly
 * the messy cases. Instead, liveness is checked at query time: {@link #isReachable}
 * probes the registered port, and the {@code /apps} handler evicts entries it finds
 * dead. No persistence and no time-based expiry; the registry is per-Eclipse-session
 * scratch state, and an app re-announces every time it starts.
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
		final String runtime; // "ng" or "wo", or null if the app didn't say (older builds)
		final long lastSeenEpochMillis;

		Entry(String name, int port, String pid, String runtime, long lastSeenEpochMillis) {
			this.name = name;
			this.port = port;
			this.pid = pid;
			this.runtime = runtime;
			this.lastSeenEpochMillis = lastSeenEpochMillis;
		}
	}

	private static final Map<String, Entry> _byName = new ConcurrentHashMap<>();

	private AppRegistry() {
	}

	/**
	 * Records (or overwrites) an app's location. The caller supplies the timestamp so
	 * registration time is the moment the announcement was received.
	 *
	 * <p>Any <em>other</em> app previously registered on the same port is evicted first.
	 * Only one process can bind a port at a time, so if a new app is announcing this port
	 * the prior occupant has necessarily exited — its entry is stale. This keeps the
	 * common "always launch dev apps on :1200" workflow tidy: re-launching a different app
	 * on 1200 drops the dead one instead of leaving two entries fighting over the port.
	 */
	static void register(String name, int port, String pid, String runtime, long nowEpochMillis) {
		_byName.entrySet().removeIf(e -> e.getValue().port == port && !e.getKey().equals(name));
		_byName.put(name, new Entry(name, port, pid, runtime, nowEpochMillis));
	}

	/** @return the entry for an app name, or null if none has registered under it. */
	static Entry get(String name) {
		return name == null ? null : _byName.get(name);
	}

	/** @return a snapshot of all known entries (order unspecified). */
	static List<Entry> all() {
		return new ArrayList<>(_byName.values());
	}

	/** Removes an entry (e.g. once it's been found unreachable). No-op if absent. */
	static void remove(String name) {
		if (name != null) {
			_byName.remove(name);
		}
	}

	/**
	 * @return true if something is listening on the entry's port — a TCP connect on the
	 *         loopback interface. Protocol-agnostic on purpose: it answers "is the app
	 *         still up?" without assuming any particular endpoint, so it works for both
	 *         WO and ng apps and for an app mid-restart. A short timeout keeps {@code /apps}
	 *         responsive even when several entries are dead.
	 */
	static boolean isReachable(Entry entry) {
		try (java.net.Socket socket = new java.net.Socket()) {
			socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), entry.port), 250);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}
}

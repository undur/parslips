package org.objectstyle.wolips.devserver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A bounded in-memory record of every request the dev server has handled — path, query,
 * status, duration and (capped) response body. This is the data behind {@code /activity}
 * (the feed as JSON) and {@code /watch} (the live spectator page): it makes the agent's
 * work on the workspace observable, and doubles as an after-the-fact audit trail of what
 * an external tool actually did.
 *
 * <p>Same philosophy as {@link ConsoleBuffer}: a static ring buffer, bounded in both entry
 * count and per-entry payload, so it can run unconditionally without ever growing into a
 * memory problem. Recording happens after the response has been sent, so it never adds
 * latency to a request.
 *
 * <p>The feed's own consumers are excluded ({@code /activity}, {@code /watch}, favicon
 * noise) — otherwise the watcher would pollute the very feed it is watching, one entry
 * per poll.
 */
final class ActivityLog {

	/** One handled request. Immutable; snapshots hand these out directly. */
	static final class Entry {
		final long seq; // monotonically increasing, never reused — the poll cursor
		final long time; // epoch millis at completion
		final String path;
		final String query; // decoded query string as received ("" when none)
		final int status; // HTTP status answered
		final long millis; // handling duration
		final String response; // response body, capped at MAX_RESPONSE_CHARS
		final int responseLength; // the UNCAPPED length, so truncation is visible
		final boolean truncated;

		Entry(long seq, long time, String path, String query, int status, long millis, String response, int responseLength, boolean truncated) {
			this.seq = seq;
			this.time = time;
			this.path = path;
			this.query = query;
			this.status = status;
			this.millis = millis;
			this.response = response;
			this.responseLength = responseLength;
			this.truncated = truncated;
		}
	}

	/** Entries kept. Old ones fall off the front; seq numbers keep increasing regardless. */
	static final int MAX_ENTRIES = 500;

	/**
	 * Per-entry response cap. Big payloads (/console tails, uncapped /problems) would
	 * otherwise dominate the buffer; 16k keeps every normal response whole while bounding
	 * the pathological ones.
	 */
	static final int MAX_RESPONSE_CHARS = 16_000;

	private static final ArrayDeque<Entry> _entries = new ArrayDeque<>();
	private static long _nextSeq = 1;

	private ActivityLog() {
	}

	/**
	 * Whether requests to this path belong in the feed. The feed's own consumers don't —
	 * a 1s poller would bury the real activity under its own heartbeat.
	 */
	static boolean isRecordable(String path) {
		return !"/activity".equals(path) && !"/watch".equals(path) && !"/favicon.ico".equals(path);
	}

	/** Records one handled request (no-op for the excluded feed-consumer paths). */
	static synchronized void record(String path, String query, int status, long millis, String response) {
		if (!isRecordable(path)) {
			return;
		}
		final String fullResponse = response == null ? "" : response;
		final boolean truncated = fullResponse.length() > MAX_RESPONSE_CHARS;
		final String kept = truncated ? fullResponse.substring(0, MAX_RESPONSE_CHARS) : fullResponse;
		_entries.addLast(new Entry(_nextSeq++, System.currentTimeMillis(), path, query == null ? "" : query, status, millis, kept, fullResponse.length(), truncated));
		if (_entries.size() > MAX_ENTRIES) {
			_entries.removeFirst();
		}
	}

	/**
	 * A snapshot of the entries with {@code seq > sinceSeq}, oldest first. Pass 0 for
	 * everything currently buffered. This is the poll contract: a client remembers the
	 * last seq it saw and asks for what came after.
	 */
	static synchronized List<Entry> entriesSince(long sinceSeq) {
		final List<Entry> result = new ArrayList<>();
		for (Entry entry : _entries) {
			if (entry.seq > sinceSeq) {
				result.add(entry);
			}
		}
		return result;
	}

	/** The seq of the newest entry, 0 when empty — lets a client start tailing "from now". */
	static synchronized long lastSeq() {
		return _entries.isEmpty() ? 0 : _entries.getLast().seq;
	}

	/** Empties the buffer (seq numbering continues — cursors held by pollers stay valid). */
	static synchronized void clear() {
		_entries.clear();
	}
}

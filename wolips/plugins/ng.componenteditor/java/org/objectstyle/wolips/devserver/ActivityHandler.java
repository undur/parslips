package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;

/**
 * {@code /activity} — the dev server's request feed as JSON, from {@link ActivityLog}.
 *
 * <p>Parameters:
 * <ul>
 * <li>{@code since=SEQ} — only entries newer than this sequence number, the poll cursor
 * (a client remembers the {@code lastSeq} it saw and asks for what came after; omit for
 * everything buffered).</li>
 * <li>{@code clear=true} — empty the buffer first and answer with the now-empty feed
 * (for resetting between demo takes).</li>
 * </ul>
 *
 * <p>Requests to {@code /activity} itself (and {@code /watch}) are never in the feed —
 * the watcher must not pollute what it watches.
 */
class ActivityHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		if ("true".equals(params.get("clear"))) {
			ActivityLog.clear();
		}

		long since = 0;
		final String sinceParam = params.get("since");
		if (sinceParam != null && !sinceParam.isEmpty()) {
			try {
				since = Long.parseLong(sinceParam);
			}
			catch (NumberFormatException e) {
				return "{\"error\":\"since must be a number\"}";
			}
		}

		final List<ActivityLog.Entry> entries = ActivityLog.entriesSince(since);

		final StringBuilder b = new StringBuilder(entries.size() * 256 + 64);
		b.append("{\"lastSeq\":").append(ActivityLog.lastSeq());
		b.append(",\"entries\":[");
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			final ActivityLog.Entry e = entries.get(i);
			b.append("{\"seq\":").append(e.seq)
					.append(",\"time\":").append(e.time)
					.append(",\"path\":\"").append(DevServerJson.escape(e.path))
					.append("\",\"query\":\"").append(DevServerJson.escape(e.query))
					.append("\",\"status\":").append(e.status)
					.append(",\"millis\":").append(e.millis)
					.append(",\"responseLength\":").append(e.responseLength)
					.append(",\"truncated\":").append(e.truncated)
					.append(",\"response\":\"").append(DevServerJson.escape(e.response))
					.append("\"}");
		}
		b.append("]}");
		return b.toString();
	}
}

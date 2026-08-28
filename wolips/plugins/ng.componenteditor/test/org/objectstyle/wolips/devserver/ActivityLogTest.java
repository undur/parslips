package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Guards the {@link ActivityLog} ring buffer and the {@code /activity} feed built on it:
 * the seq-cursor poll contract, the entry and payload caps, the feed-consumer exclusions,
 * and the handler's JSON shape (structurally, same approach as {@link IndexHandlerTest} —
 * no JSON parser on the classpath).
 */
public class ActivityLogTest {

	@Before
	public void clearLog() {
		// The log is static (deliberately — it must survive across requests), so each
		// test starts from an empty buffer. Seq numbering carries over, which is fine:
		// the tests assert relative order, not absolute seq values.
		ActivityLog.clear();
	}

	@Test
	public void recordsAndReturnsEntriesInOrder() {
		ActivityLog.record("/validate", "component=Main", 200, 12, "{\"problems\":[]}");
		ActivityLog.record("/refreshProject", "project=MyApp", 200, 340, "ok");

		final List<ActivityLog.Entry> entries = ActivityLog.entriesSince(0);
		assertEquals(2, entries.size());
		assertEquals("/validate", entries.get(0).path);
		assertEquals("/refreshProject", entries.get(1).path);
		assertTrue("seq must increase", entries.get(1).seq > entries.get(0).seq);
		assertEquals("component=Main", entries.get(0).query);
		assertEquals(200, entries.get(0).status);
		assertEquals(12, entries.get(0).millis);
	}

	@Test
	public void sinceCursorReturnsOnlyNewerEntries() {
		ActivityLog.record("/a", "", 200, 1, "ok");
		final long cursor = ActivityLog.lastSeq();
		ActivityLog.record("/b", "", 200, 1, "ok");
		ActivityLog.record("/c", "", 200, 1, "ok");

		final List<ActivityLog.Entry> newer = ActivityLog.entriesSince(cursor);
		assertEquals(2, newer.size());
		assertEquals("/b", newer.get(0).path);
		assertEquals("/c", newer.get(1).path);
	}

	@Test
	public void feedConsumersAreExcluded() {
		ActivityLog.record("/activity", "since=5", 200, 1, "{}");
		ActivityLog.record("/watch", "", 200, 1, "<html>");
		ActivityLog.record("/favicon.ico", "", 200, 1, "");
		ActivityLog.record("/validate", "component=Main", 200, 1, "ok");

		final List<ActivityLog.Entry> entries = ActivityLog.entriesSince(0);
		assertEquals(1, entries.size());
		assertEquals("/validate", entries.get(0).path);
	}

	@Test
	public void bufferIsCappedAndOldEntriesFallOff() {
		for (int i = 0; i < ActivityLog.MAX_ENTRIES + 10; i++) {
			ActivityLog.record("/n" + i, "", 200, 1, "ok");
		}
		final List<ActivityLog.Entry> entries = ActivityLog.entriesSince(0);
		assertEquals(ActivityLog.MAX_ENTRIES, entries.size());
		// The oldest surviving entry is the 11th recorded one — the first 10 fell off.
		assertEquals("/n10", entries.get(0).path);
	}

	@Test
	public void oversizedResponsesAreTruncatedButFullLengthReported() {
		final String big = "x".repeat(ActivityLog.MAX_RESPONSE_CHARS + 500);
		ActivityLog.record("/console", "app=MyApp", 200, 5, big);

		final ActivityLog.Entry entry = ActivityLog.entriesSince(0).get(0);
		assertTrue(entry.truncated);
		assertEquals(ActivityLog.MAX_RESPONSE_CHARS, entry.response.length());
		assertEquals(big.length(), entry.responseLength);
	}

	@Test
	public void smallResponsesAreKeptWhole() {
		ActivityLog.record("/validate", "", 200, 5, "{\"problems\":[]}");
		final ActivityLog.Entry entry = ActivityLog.entriesSince(0).get(0);
		assertFalse(entry.truncated);
		assertEquals("{\"problems\":[]}", entry.response);
		assertEquals(entry.response.length(), entry.responseLength);
	}

	@Test
	public void clearEmptiesTheBufferButSeqNumberingContinues() {
		ActivityLog.record("/a", "", 200, 1, "ok");
		final long seqBefore = ActivityLog.lastSeq();
		ActivityLog.clear();
		assertEquals(0, ActivityLog.lastSeq());
		assertTrue(ActivityLog.entriesSince(0).isEmpty());

		ActivityLog.record("/b", "", 200, 1, "ok");
		assertTrue("seq must not restart after clear — pollers hold cursors", ActivityLog.lastSeq() > seqBefore);
	}

	@Test
	public void activityHandlerEmitsWellFormedJson() throws Exception {
		ActivityLog.record("/validate", "component=Main", 200, 12, "{\"problems\":[]}");
		ActivityLog.record("/refreshProject", "project=My \"quoted\" App", 500, 3, "error: boom\nsecond line");

		final String feed = new ActivityHandler().handle(new HashMap<>());
		IndexHandlerTest.assertWellFormed(feed);
		assertTrue(feed.contains("\"path\":\"/validate\""));
		assertTrue("embedded quotes must be escaped", feed.contains("\\\"quoted\\\""));
		assertTrue("newlines must be escaped", feed.contains("\\n"));
	}

	@Test
	public void activityHandlerHonorsSinceParameter() throws Exception {
		ActivityLog.record("/a", "", 200, 1, "ok");
		final long cursor = ActivityLog.lastSeq();
		ActivityLog.record("/b", "", 200, 1, "ok");

		final Map<String, String> params = new HashMap<>();
		params.put("since", String.valueOf(cursor));
		final String feed = new ActivityHandler().handle(params);
		assertFalse(feed.contains("\"path\":\"/a\""));
		assertTrue(feed.contains("\"path\":\"/b\""));
	}

	@Test
	public void activityHandlerClearParameterEmptiesTheBuffer() throws Exception {
		ActivityLog.record("/a", "", 200, 1, "ok");
		final Map<String, String> params = new HashMap<>();
		params.put("clear", "true");
		final String feed = new ActivityHandler().handle(params);
		assertTrue(feed.contains("\"entries\":[]"));
		assertTrue(ActivityLog.entriesSince(0).isEmpty());
	}

	@Test
	public void watchPageResourceIsPresentAndServed() throws Exception {
		final String page = new WatchHandler().handle(new HashMap<>());
		assertTrue("watch.html must ship in the bundle", page.startsWith("<!DOCTYPE html>"));
		assertTrue("the page must poll /activity", page.contains("/activity?since="));
	}
}

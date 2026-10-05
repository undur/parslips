package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * The rules that keep a launch from being waited out: when a starting app counts as stalled,
 * which port a launch moves to when its own is held, and what of its output a failure carries.
 */
public class LaunchLivenessTest {

	private static final long START = 1_000_000;

	@Test
	public void silentFromTheStartStallsAfterTheThreshold() {
		assertFalse(LaunchLiveness.isStalled(START, 0, START + LaunchLiveness.STALL_MILLIS - 1));
		assertTrue(LaunchLiveness.isStalled(START, 0, START + LaunchLiveness.STALL_MILLIS));
	}

	@Test
	public void outputResetsTheClock() {
		final long lastOutput = START + 10_000;
		assertFalse(LaunchLiveness.isStalled(START, lastOutput, START + LaunchLiveness.STALL_MILLIS + 1_000));
		assertTrue(LaunchLiveness.isStalled(START, lastOutput, lastOutput + LaunchLiveness.STALL_MILLIS));
	}

	@Test
	public void outputBeforeTheWaitDoesNotCountAgainstIt() {
		// A console line from before the wait started (an earlier launch's buffer, say) mustn't
		// make a fresh launch look stalled at once.
		assertFalse(LaunchLiveness.isStalled(START, START - 60_000, START + 1_000));
	}

	@Test
	public void nextFreePortSkipsHeldOnes() {
		final Set<Integer> held = Set.of(1200, 1201, 1202);
		assertEquals(1203, LaunchLiveness.nextFreePort(1200, held::contains));
		assertEquals(1201, LaunchLiveness.nextFreePort(1200, port -> false));
	}

	@Test
	public void noFreePortWithinRange() {
		assertEquals(-1, LaunchLiveness.nextFreePort(1200, port -> true));
	}

	@Test
	public void lastLinesSkipBlankAndKeepOrder() {
		assertEquals(List.of("b", "c"), LaunchHandler.lastLines("a\nb\n\n  \nc\n", 2));
		assertEquals(List.of("only"), LaunchHandler.lastLines("only", 5));
		assertEquals(List.of(), LaunchHandler.lastLines("", 5));
	}
}

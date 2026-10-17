package org.objectstyle.wolips.devserver;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * The refusal the component endpoints give when a name's files can't be told apart in a project
 * (two classes sharing the simple name): it names the component, the project and what clashed,
 * and says why, so the caller knows a different spelling of the name won't help.
 */
public class LocateFailureReasonTest {

	@Test
	public void namesComponentProjectAndClash() {
		final String reason = DevServerComponents.locateFailureReason("Duplicate", "core", "Duplicate located: L/core/a/Duplicate.java L/core/b/Duplicate.java");
		assertTrue(reason, reason.contains("'Duplicate'"));
		assertTrue(reason, reason.contains("project 'core'"));
		assertTrue(reason, reason.contains("L/core/a/Duplicate.java L/core/b/Duplicate.java"));
		assertTrue(reason, reason.contains("simple name"));
	}
}

package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The WO ↔ NG name bridge, both directions, including the one spelling that isn't a prefix swap. */
public class NGElementNamesTest {

	@Test
	public void prefixSwapInBothDirections() {
		assertEquals("NGString", NGElementNames.toNG("WOString"));
		assertEquals("NGConditional", NGElementNames.toNG("WOConditional"));
		assertEquals("WOString", NGElementNames.toWO("NGString"));
		assertEquals("WOConditional", NGElementNames.toWO("NGConditional"));
	}

	@Test
	public void checkboxSpellingDivergesAndRoundTrips() {
		assertEquals("NGCheckbox", NGElementNames.toNG("WOCheckBox"));
		assertEquals("WOCheckBox", NGElementNames.toWO("NGCheckbox"));
		assertEquals("WOCheckBox", NGElementNames.toWO(NGElementNames.toNG("WOCheckBox")));
	}

	@Test
	public void otherNamesPassThrough() {
		assertEquals("ERXWOString", NGElementNames.toNG("ERXWOString"));
		assertEquals("ERXWOString", NGElementNames.toWO("ERXWOString"));
		assertEquals("", NGElementNames.toNG(""));
		assertNull(NGElementNames.toNG(null));
		assertNull(NGElementNames.toWO(null));
	}
}

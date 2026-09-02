package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The WO → NG name bridge used to expand legacy tag shortcuts in ng projects. */
public class NGElementNamesTest {

	@Test
	public void prefixSwap() {
		assertEquals("NGString", NGElementNames.toNG("WOString"));
		assertEquals("NGConditional", NGElementNames.toNG("WOConditional"));
	}

	@Test
	public void checkboxSpellingDiverges() {
		assertEquals("NGCheckbox", NGElementNames.toNG("WOCheckBox"));
	}

	@Test
	public void otherNamesPassThrough() {
		assertEquals("ERXWOString", NGElementNames.toNG("ERXWOString"));
		assertEquals("", NGElementNames.toNG(""));
		assertNull(NGElementNames.toNG(null));
	}
}

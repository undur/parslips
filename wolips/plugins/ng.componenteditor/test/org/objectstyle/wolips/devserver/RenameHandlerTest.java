package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link RenameHandler#renamedMemberName}: renaming a key renames every member that implements
 * it, each keeping its own naming pattern.
 */
public class RenameHandlerTest {

	@Test
	public void plainAccessorAndField() {
		assertEquals("title", RenameHandler.renamedMemberName("name", "name", "title"));
	}

	@Test
	public void underscoreField() {
		assertEquals("_title", RenameHandler.renamedMemberName("_name", "name", "title"));
	}

	@Test
	public void getterSetterAndIs() {
		assertEquals("getTitle", RenameHandler.renamedMemberName("getName", "name", "title"));
		assertEquals("setTitle", RenameHandler.renamedMemberName("setName", "name", "title"));
		assertEquals("isActive", RenameHandler.renamedMemberName("isEnabled", "enabled", "active"));
	}

	@Test
	public void camelCaseKeys() {
		assertEquals("setHomeSide", RenameHandler.renamedMemberName("setHomeTeam", "homeTeam", "homeSide"));
		assertEquals("homeSide", RenameHandler.renamedMemberName("homeTeam", "homeTeam", "homeSide"));
	}

	@Test
	public void aMemberThatDoesNotEndInTheKeyIsLeftAlone() {
		assertNull(RenameHandler.renamedMemberName("nameForDisplay", "name", "title"));
	}

	@Test
	public void identifiers() {
		assertTrue(RenameHandler.isIdentifier("homeTeam"));
		assertTrue(RenameHandler.isIdentifier("_x1"));
		assertFalse(RenameHandler.isIdentifier("1st"));
		assertFalse(RenameHandler.isIdentifier("home.team"));
		assertFalse(RenameHandler.isIdentifier(""));
	}
}

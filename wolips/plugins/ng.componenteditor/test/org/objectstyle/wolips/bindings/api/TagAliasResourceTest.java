package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Each runtime reads only its own tag registry, and so does the editor: ng projects the ng file,
 * WebObjects projects Parsley's. A classpath carrying both frameworks must never merge them.
 */
public class TagAliasResourceTest {

	@Test
	public void eachProjectKindReadsItsRuntimesRegistry() {
		assertEquals("ng-tag-aliases.properties", ParsleyTagAliasResolver.aliasResourceFor(true));
		assertEquals("parsley-tag-aliases.properties", ParsleyTagAliasResolver.aliasResourceFor(false));
	}

	@Test
	public void bothRegistriesInvalidateTheCache() {
		assertTrue(ParsleyTagAliasResolver.isAliasResource("ng-tag-aliases.properties"));
		assertTrue(ParsleyTagAliasResolver.isAliasResource("parsley-tag-aliases.properties"));
		assertFalse(ParsleyTagAliasResolver.isAliasResource("build.properties"));
	}
}

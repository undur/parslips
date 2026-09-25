package org.objectstyle.wolips.variables;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which templates are rendered by ng-objects: those under ng/&lt;namespace&gt;/components/. */
public class TemplateRuntimeTest {

	@Test
	public void ngComponentLocationsAreNG() {
		assertTrue(TemplateRuntime.isNGTemplatePath("src/main/resources/ng/app/components/NGPage.wo"));
		assertTrue(TemplateRuntime.isNGTemplatePath("src/main/resources/ng/app/components/Main.html"));
		assertTrue(TemplateRuntime.isNGTemplatePath("src/main/resources/ng/myframework/components/sub/Deep.html"));
		assertTrue(TemplateRuntime.isNGTemplatePath("target/classes/ng/app/components/Main.html"));
	}

	@Test
	public void woLocationsAreNot() {
		assertFalse(TemplateRuntime.isNGTemplatePath("src/main/components/WOPage.wo"));
		assertFalse(TemplateRuntime.isNGTemplatePath("src/main/resources/ng/app/app-resources/x.html"));
		assertFalse(TemplateRuntime.isNGTemplatePath("ng/components/Main.html"));
		assertFalse(TemplateRuntime.isNGTemplatePath(null));
	}

	@Test
	public void eachRuntimeNamesItsElementRoot() {
		assertEquals("ng.appserver.templating.NGElement", TemplateRuntime.NG.elementClass());
		assertEquals("com.webobjects.appserver.WOElement", TemplateRuntime.WO.elementClass());
	}
}

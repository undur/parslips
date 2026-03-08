package org.objectstyle.wolips.locate.result;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link ElementDescriptor} and {@link TemplateFormat}.
 *
 * <p>Since {@code ElementDescriptor} depends on Eclipse {@code IFile} and
 * {@code IProject} types, these tests use the package-private constructor
 * with null file references to test the format detection and convenience
 * methods without requiring an Eclipse runtime.
 */
public class ElementDescriptorTest {

	// ---- TemplateFormat enum ----

	@Test
	public void templateFormat_hasThreeValues() {
		assertEquals(3, TemplateFormat.values().length);
	}

	@Test
	public void templateFormat_valuesAreCorrect() {
		assertNotNull(TemplateFormat.valueOf("BUNDLE"));
		assertNotNull(TemplateFormat.valueOf("STANDALONE"));
		assertNotNull(TemplateFormat.valueOf("NONE"));
	}

	// ---- Convenience methods ----

	@Test
	public void hasTemplate_trueForBundle() {
		ElementDescriptor desc = bundle("MyComponent");
		assertTrue(desc.hasTemplate());
	}

	@Test
	public void hasTemplate_trueForStandalone() {
		ElementDescriptor desc = standalone("MyComponent");
		assertTrue(desc.hasTemplate());
	}

	@Test
	public void hasTemplate_falseForNone() {
		ElementDescriptor desc = noTemplate("WODynamicElement");
		assertFalse(desc.hasTemplate());
	}

	@Test
	public void isBundle_trueOnlyForBundle() {
		assertTrue(bundle("Comp").isBundle());
		assertFalse(standalone("Comp").isBundle());
		assertFalse(noTemplate("Comp").isBundle());
	}

	@Test
	public void isStandalone_trueOnlyForStandalone() {
		assertFalse(bundle("Comp").isStandalone());
		assertTrue(standalone("Comp").isStandalone());
		assertFalse(noTemplate("Comp").isStandalone());
	}

	// ---- Accessors ----

	@Test
	public void getName_returnsName() {
		assertEquals("Main", bundle("Main").getName());
		assertEquals("PageWrapper", standalone("PageWrapper").getName());
	}

	@Test
	public void getTemplateFormat_returnsFormat() {
		assertEquals(TemplateFormat.BUNDLE, bundle("X").getTemplateFormat());
		assertEquals(TemplateFormat.STANDALONE, standalone("X").getTemplateFormat());
		assertEquals(TemplateFormat.NONE, noTemplate("X").getTemplateFormat());
	}

	@Test
	public void nullFiles_returnNull() {
		ElementDescriptor desc = noTemplate("Elem");
		assertNull(desc.getHtmlFile());
		assertNull(desc.getWodFile());
		assertNull(desc.getWooFile());
		assertNull(desc.getApiFile());
		assertNull(desc.getJavaFile());
	}

	@Test
	public void getJavaType_returnsNullForNullFile() {
		// No Java file → getJavaType() should return null without throwing
		ElementDescriptor desc = noTemplate("Elem");
		assertNull(desc.getJavaType());
	}

	// ---- Constructor validation ----

	@Test(expected = IllegalArgumentException.class)
	public void constructor_rejectsNullName() {
		new ElementDescriptor(null, StubProject.INSTANCE, TemplateFormat.NONE,
				null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_rejectsNullProject() {
		new ElementDescriptor("Name", null, TemplateFormat.NONE,
				null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_rejectsNullFormat() {
		new ElementDescriptor("Name", StubProject.INSTANCE, null,
				null, null, null, null, null);
	}

	// ---- toString ----

	@Test
	public void toString_containsNameAndFormat() {
		String s = bundle("PageWrapper").toString();
		assertTrue(s.contains("PageWrapper"));
		assertTrue(s.contains("BUNDLE"));
	}

	// ---- Helpers ----

	/**
	 * Creates a BUNDLE descriptor with null files (sufficient for testing
	 * format logic and convenience methods).
	 */
	private static ElementDescriptor bundle(String name) {
		return new ElementDescriptor(name, StubProject.INSTANCE, TemplateFormat.BUNDLE,
				null, null, null, null, null);
	}

	/**
	 * Creates a STANDALONE descriptor with null files.
	 */
	private static ElementDescriptor standalone(String name) {
		return new ElementDescriptor(name, StubProject.INSTANCE, TemplateFormat.STANDALONE,
				null, null, null, null, null);
	}

	/**
	 * Creates a NONE (no template) descriptor with null files.
	 */
	private static ElementDescriptor noTemplate(String name) {
		return new ElementDescriptor(name, StubProject.INSTANCE, TemplateFormat.NONE,
				null, null, null, null, null);
	}
}

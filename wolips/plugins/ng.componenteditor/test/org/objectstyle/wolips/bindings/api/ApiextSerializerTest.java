package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;
import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;
import org.objectstyle.wolips.bindings.api.ApiextModel.TypeRef;

/**
 * Round-trip tests for {@link ApiextSerializer}: {@code parse(serialize(m))} must reproduce the model
 * {@code m}. This is the fidelity guarantee the form editor depends on — every save serializes the
 * model, and re-opening re-parses it, so the two must agree at the model level. Rather than assert on
 * exact bytes (brittle), each test parses a source, serializes, re-parses, and compares the models
 * structurally.
 */
public class ApiextSerializerTest {

	private static ApiextModel parse(String xml) {
		return ApiextModel.parse(xml.getBytes(StandardCharsets.UTF_8));
	}

	/** Parse → serialize → re-parse, and assert the two models are structurally equal. */
	private static void assertRoundTrips(String xml) {
		final ApiextModel a = parse(xml);
		assertNotNull("fixture should parse", a);
		final ApiextModel b = parse(ApiextSerializer.serialize(a));
		assertNotNull("serialized output should re-parse", b);
		assertModelsEqual(a, b);
	}

	// ---- representative shapes (mirroring the real fixtures) ----------------

	@Test
	public void roundTrip_checkBox() {
		assertRoundTrips("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"WOCheckBox\" wrapsContent=\"false\" unknownAttributes=\"passthrough\">"
				+ "<doc><![CDATA[A checkbox (`<input>`). Use **either** `checked` **or** `value`.]]></doc>"
				+ "<binding name=\"checked\"><pull><type interpretation=\"truthy\">java.lang.Object</type></pull>"
				+ "<push><type>java.lang.Boolean</type></push><doc>The checked state.</doc></binding>"
				+ "<binding name=\"value\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"selection\"><pull><type>java.lang.Object</type></pull><push><type>java.lang.Object</type></push></binding>"
				+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>"
				+ "<requires binding=\"selection\" when=\"value\"/>"
				+ "<requires binding=\"value\" when=\"selection\"/>"
				+ "</wo></wodefinitions>");
	}

	@Test
	public void roundTrip_anyOfInBothChooseAndRequires() {
		assertRoundTrips("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" wrapsContent=\"true\">"
				+ "<binding name=\"action\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"actionClass\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"directActionName\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"item\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"displayString\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"value\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<choose min=\"1\" max=\"1\"><binding name=\"action\"/>"
				+ "<any-of><binding name=\"actionClass\"/><binding name=\"directActionName\"/></any-of></choose>"
				+ "<requires binding=\"item\" must=\"settable\">"
				+ "<any-of><binding name=\"displayString\"/><binding name=\"value\"/></any-of></requires>"
				+ "</wo></wodefinitions>");
	}

	@Test
	public void roundTrip_defaultsAndDeprecation() {
		assertRoundTrips("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\">"
				+ "<deprecated>Whole element is legacy.</deprecated>"
				+ "<binding name=\"morph\"><pull><type>java.lang.Boolean</type></pull><default>true</default></binding>"
				+ "<binding name=\"old\"><pull><type>java.lang.String</type></pull><deprecated>Use `new` instead.</deprecated></binding>"
				+ "<binding name=\"list\" required=\"true\"><pull><type>java.util.List</type></pull></binding>"
				+ "</wo></wodefinitions>");
	}

	@Test
	public void roundTrip_unconditionalRequiresAndMessageOverride() {
		assertRoundTrips("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" unknownAttributes=\"forbidden\">"
				+ "<binding name=\"a\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"b\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "<binding name=\"list\"><pull><type>java.util.List</type></pull></binding>"
				+ "<requires binding=\"list\" must=\"gettable\"/>"
				+ "<choose max=\"1\" message=\"pick at most one\"><binding name=\"a\"/><binding name=\"b\"/></choose>"
				+ "</wo></wodefinitions>");
	}

	@Test
	public void roundTrip_absentPolicyStaysAbsent() {
		final ApiextModel a = parse("<?xml version=\"1.0\"?><wodefinitions><wo class=\"X\">"
				+ "<binding name=\"a\"><pull><type>java.lang.Object</type></pull></binding></wo></wodefinitions>");
		final ApiextModel b = parse(ApiextSerializer.serialize(a));
		// The nullable policy must not be synthesized on the way out.
		org.junit.Assert.assertNull(b.getUnknownAttributes());
	}

	// ---- the bundled .apiext files (the ones we ship) -----------------------

	/**
	 * Round-trips every {@code .apiext} the plugin bundles and asserts the serializer preserves the
	 * model — the permanent regression guard that serialization is meaning-preserving on real files, and
	 * that the shipped files stay in the serializer's canonical form (so editor saves diff cleanly). The
	 * surefire CWD is the plugin base dir, so the {@code apiext/} folder resolves directly.
	 */
	@Test
	public void bundledFiles_roundTripAndAreCanonical() throws Exception {
		final java.io.File dir = new java.io.File("apiext");
		final java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".apiext"));
		assertNotNull("bundled apiext/ directory should exist", files);
		org.junit.Assert.assertTrue("expected bundled .apiext files", files.length > 0);

		for (final java.io.File f : files) {
			final byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
			final ApiextModel a = ApiextModel.parse(bytes);
			assertNotNull(f.getName() + " should parse", a);

			// (1) meaning-preserving: parse → serialize → re-parse yields an equal model.
			final String serialized = ApiextSerializer.serialize(a);
			final ApiextModel b = ApiextModel.parse(serialized.getBytes(StandardCharsets.UTF_8));
			assertNotNull(f.getName() + " serialized output should re-parse", b);
			assertModelsEqual(a, b);

			// (2) already canonical: the shipped bytes equal the serializer's output, so opening and
			// saving in the editor changes nothing but the user's actual edit.
			assertEquals(f.getName() + " is not in canonical serializer form (re-normalize it)",
					new String(bytes, StandardCharsets.UTF_8), serialized);

			// (3) idempotent: serializing the re-parsed model gives identical bytes.
			assertEquals(f.getName() + " serialization is not idempotent",
					serialized, ApiextSerializer.serialize(b));
		}
	}

	// ---- structural model equality ------------------------------------------

	private static void assertModelsEqual(ApiextModel a, ApiextModel b) {
		assertEquals("class", a.getClassName(), b.getClassName());
		assertEquals("wrapsContent", a.isComponentContent(), b.isComponentContent());
		assertEquals("unknownAttributes", a.getUnknownAttributes(), b.getUnknownAttributes());
		assertEquals("element doc", a.getDoc(), b.getDoc());
		assertEquals("element deprecated", a.isDeprecated(), b.isDeprecated());
		assertEquals("element deprecation note", a.getDeprecationNote(), b.getDeprecationNote());

		assertEquals("binding count", a.getBindings().size(), b.getBindings().size());
		for (int i = 0; i < a.getBindings().size(); i++) {
			assertBindingsEqual(a.getBindings().get(i), b.getBindings().get(i));
		}

		assertEquals("constraint count", a.getConstraints().size(), b.getConstraints().size());
		for (int i = 0; i < a.getConstraints().size(); i++) {
			assertConstraintsEqual(a.getConstraints().get(i), b.getConstraints().get(i));
		}
	}

	private static void assertBindingsEqual(Binding a, Binding b) {
		assertEquals("binding name", a.getName(), b.getName());
		assertEquals("required", a.isRequired(), b.isRequired());
		assertEquals("default", a.getDefaultValue(), b.getDefaultValue());
		assertEquals("deprecated", a.isDeprecated(), b.isDeprecated());
		assertEquals("deprecation note", a.getDeprecationNote(), b.getDeprecationNote());
		assertEquals("doc", a.getDoc(), b.getDoc());
		assertTypesEqual("pull", a.getPullTypes(), b.getPullTypes());
		assertTypesEqual("push", a.getPushTypes(), b.getPushTypes());
	}

	private static void assertTypesEqual(String where, List<TypeRef> a, List<TypeRef> b) {
		assertEquals(where + " type count", a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(where + " type name", a.get(i).getName(), b.get(i).getName());
			assertEquals(where + " interpretation", a.get(i).getInterpretation(), b.get(i).getInterpretation());
		}
	}

	private static void assertConstraintsEqual(Constraint a, Constraint b) {
		assertEquals("constraint kind", a.getClass(), b.getClass());
		assertEquals("message", a.getMessage(), b.getMessage());
		if (a instanceof Choose) {
			final Choose ca = (Choose) a;
			final Choose cb = (Choose) b;
			assertEquals("min", ca.getMin(), cb.getMin());
			assertEquals("max", ca.getMax(), cb.getMax());
			assertAlternativesEqual(ca.getAlternatives(), cb.getAlternatives());
		}
		else if (a instanceof Requires) {
			final Requires ra = (Requires) a;
			final Requires rb = (Requires) b;
			assertEquals("consequent", ra.getBinding(), rb.getBinding());
			assertEquals("must", ra.getMust(), rb.getMust());
			assertEquals("antecedent presence", ra.getAntecedent() == null, rb.getAntecedent() == null);
			if (ra.getAntecedent() != null) {
				assertEquals("antecedent names", ra.getAntecedent().getBindingNames(), rb.getAntecedent().getBindingNames());
			}
		}
	}

	private static void assertAlternativesEqual(List<Alternative> a, List<Alternative> b) {
		assertEquals("alternative count", a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals("alt isAnyOf", a.get(i).isAnyOf(), b.get(i).isAnyOf());
			assertEquals("alt names", a.get(i).getBindingNames(), b.get(i).getBindingNames());
		}
	}
}

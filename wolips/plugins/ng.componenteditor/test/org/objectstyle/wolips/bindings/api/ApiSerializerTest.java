package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.junit.Test;

/**
 * Tests for {@link ApiSerializer} — the write path from {@link ApiSnapshot}
 * POJOs back to {@code .api} XML format.
 *
 * <p>Most tests verify round-trip fidelity: parse XML → serialize → re-parse
 * → compare. This ensures that the parser and serializer agree on the format
 * and that no information is lost during serialization.
 */
public class ApiSerializerTest {

	// ---- Helpers ------------------------------------------------------------

	/**
	 * Parses XML, serializes the result, and returns the serialized string.
	 */
	private static String roundTrip(String xml) throws Exception {
		ApiSnapshot snapshot = ApiParser.parseReader(new StringReader(xml));
		StringWriter writer = new StringWriter();
		ApiSerializer.serialize(snapshot, writer);
		return writer.toString();
	}

	/**
	 * Parses XML, serializes, re-parses, and returns the re-parsed snapshot.
	 * This is the strongest round-trip test: the data must survive parse → serialize → parse.
	 */
	private static ApiSnapshot roundTripSnapshot(String xml) throws Exception {
		ApiSnapshot original = ApiParser.parseReader(new StringReader(xml));
		StringWriter writer = new StringWriter();
		ApiSerializer.serialize(original, writer);
		return ApiParser.parseReader(new StringReader(writer.toString()));
	}

	// ---- Basic serialization ------------------------------------------------

	@Test
	public void serialize_minimalComponent() throws Exception {
		String xml = "<wodefinitions><wo class=\"MyComponent\" wocomponentcontent=\"false\"></wo></wodefinitions>";
		String output = roundTrip(xml);
		assertTrue(output.contains("<?xml version=\"1.0\""));
		assertTrue(output.contains("<wodefinitions>"));
		assertTrue(output.contains("class=\"MyComponent\""));
		assertTrue(output.contains("wocomponentcontent=\"false\""));
		assertTrue(output.contains("</wodefinitions>"));
	}

	@Test
	public void serialize_componentContentTrue() throws Exception {
		String xml = "<wodefinitions><wo class=\"X\" wocomponentcontent=\"true\"></wo></wodefinitions>";
		String output = roundTrip(xml);
		assertTrue(output.contains("wocomponentcontent=\"true\""));
	}

	// ---- Binding round-trips ------------------------------------------------

	@Test
	public void roundTrip_simpleBinding() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals(1, result.getBindings().size());
		assertEquals("item", result.getBindings().get(0).getName());
		assertNull(result.getBindings().get(0).getDefaults());
	}

	@Test
	public void roundTrip_bindingWithDefaults() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"action\" defaults=\"Actions\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		IApiBinding binding = result.getBinding("action");
		assertEquals("Actions", binding.getDefaults());
	}

	@Test
	public void roundTrip_explicitlyRequiredBinding() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"value\" required=\"YES\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		IApiBinding binding = result.getBinding("value");
		assertTrue(binding.isRequired());
		assertTrue(binding.isExplicitlyRequired());
	}

	@Test
	public void roundTrip_explicitlySettableBinding() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"selection\" settable=\"YES\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		IApiBinding binding = result.getBinding("selection");
		assertTrue(binding.isWillSet());
		assertTrue(binding.isExplicitlySettable());
	}

	@Test
	public void roundTrip_multipleBindings_preserveOrder() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"alpha\"/>" +
			"  <binding name=\"beta\" defaults=\"Boolean\"/>" +
			"  <binding name=\"gamma\" required=\"YES\" settable=\"YES\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		List<IApiBinding> bindings = result.getBindings();
		assertEquals(3, bindings.size());
		assertEquals("alpha", bindings.get(0).getName());
		assertEquals("beta", bindings.get(1).getName());
		assertEquals("Boolean", bindings.get(1).getDefaults());
		assertEquals("gamma", bindings.get(2).getName());
		assertTrue(bindings.get(2).isExplicitlyRequired());
		assertTrue(bindings.get(2).isExplicitlySettable());
	}

	@Test
	public void roundTrip_bindingNotRequired_noRequiredAttribute() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"optional\"/>" +
			"</wo></wodefinitions>";
		String output = roundTrip(xml);
		assertFalse("Non-required binding should not have required attribute",
			output.contains("required="));
	}

	// ---- Validation round-trips ---------------------------------------------

	@Test
	public void roundTrip_simpleValidation() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\"/>" +
			"  <validation message=\"'item' is required.\">" +
			"    <unbound name=\"item\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals(1, result.getValidations().size());
		ApiValidation validation = result.getValidations().get(0);
		assertEquals("'item' is required.", validation.getMessage());
		assertEquals(1, validation.getChildren().size());
		assertEquals(ApiValidation.Kind.UNBOUND, validation.getChildren().get(0).getKind());
		assertEquals("item", validation.getChildren().get(0).getBindingName());
	}

	@Test
	public void roundTrip_complexValidationTree() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"a\"/>" +
			"  <binding name=\"b\"/>" +
			"  <validation message=\"complex rule\">" +
			"    <or>" +
			"      <and>" +
			"        <bound name=\"a\"/>" +
			"        <unbound name=\"b\"/>" +
			"      </and>" +
			"      <not>" +
			"        <settable name=\"a\"/>" +
			"      </not>" +
			"    </or>" +
			"  </validation>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals(1, result.getValidations().size());
		ApiValidation validation = result.getValidations().get(0);
		assertEquals("complex rule", validation.getMessage());

		ApiValidation or = validation.getChildren().get(0);
		assertEquals(ApiValidation.Kind.OR, or.getKind());

		ApiValidation and = or.getChildren().get(0);
		assertEquals(ApiValidation.Kind.AND, and.getKind());
		assertEquals(ApiValidation.Kind.BOUND, and.getChildren().get(0).getKind());
		assertEquals("a", and.getChildren().get(0).getBindingName());
		assertEquals(ApiValidation.Kind.UNBOUND, and.getChildren().get(1).getKind());
		assertEquals("b", and.getChildren().get(1).getBindingName());

		ApiValidation not = or.getChildren().get(1);
		assertEquals(ApiValidation.Kind.NOT, not.getKind());
		assertEquals(ApiValidation.Kind.SETTABLE, not.getChildren().get(0).getKind());
	}

	@Test
	public void roundTrip_countValidation() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"a\"/>" +
			"  <binding name=\"b\"/>" +
			"  <validation message=\"Need exactly one.\">" +
			"    <count test=\"!=1\">" +
			"      <bound name=\"a\"/>" +
			"      <bound name=\"b\"/>" +
			"    </count>" +
			"  </validation>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		ApiValidation count = result.getValidations().get(0).getChildren().get(0);
		assertEquals(ApiValidation.Kind.COUNT, count.getKind());
		assertEquals("!=1", count.getCountTest());
		assertEquals(2, count.getChildren().size());
	}

	@Test
	public void roundTrip_multipleValidations() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"a\"/>" +
			"  <binding name=\"b\"/>" +
			"  <validation message=\"msg1\">" +
			"    <unbound name=\"a\"/>" +
			"  </validation>" +
			"  <validation message=\"msg2\">" +
			"    <unsettable name=\"b\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals(2, result.getValidations().size());
		assertEquals("msg1", result.getValidations().get(0).getMessage());
		assertEquals("msg2", result.getValidations().get(1).getMessage());
	}

	// ---- XML escaping -------------------------------------------------------

	@Test
	public void roundTrip_xmlSpecialCharsInBindingName() throws Exception {
		// Binding names with special chars should survive round-trip via escaping
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"it&amp;em\"/>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals("it&em", result.getBindings().get(0).getName());
	}

	@Test
	public void roundTrip_xmlSpecialCharsInClassName() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"com.example.My&amp;Component\">" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertEquals("com.example.My&Component", result.getClassName());
	}

	@Test
	public void serialize_escapesSpecialChars() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <validation message=\"&apos;item&apos; must be &lt; 10.\">" +
			"    <unbound name=\"item\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>";
		String output = roundTrip(xml);
		// The serialized output should escape the special characters
		assertTrue("Message with special chars should be properly escaped",
			output.contains("&apos;item&apos; must be &lt; 10."));
	}

	// ---- Snapshot mutation + serialization -----------------------------------

	@Test
	public void serialize_afterAddingBinding() throws Exception {
		ApiSnapshot snapshot = ApiParser.parseReader(new StringReader(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"existing\"/>" +
			"</wo></wodefinitions>"
		));
		snapshot.addBinding("newBinding");

		StringWriter writer = new StringWriter();
		ApiSerializer.serialize(snapshot, writer);
		ApiSnapshot result = ApiParser.parseReader(new StringReader(writer.toString()));

		assertEquals(2, result.getBindings().size());
		assertNotNull(result.getBinding("existing"));
		assertNotNull(result.getBinding("newBinding"));
	}

	@Test
	public void serialize_afterRemovingBinding() throws Exception {
		ApiSnapshot snapshot = ApiParser.parseReader(new StringReader(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"keep\"/>" +
			"  <binding name=\"remove\"/>" +
			"</wo></wodefinitions>"
		));
		snapshot.removeBinding("remove");

		StringWriter writer = new StringWriter();
		ApiSerializer.serialize(snapshot, writer);
		ApiSnapshot result = ApiParser.parseReader(new StringReader(writer.toString()));

		assertEquals(1, result.getBindings().size());
		assertEquals("keep", result.getBindings().get(0).getName());
	}

	@Test
	public void serialize_afterTogglingComponentContent() throws Exception {
		ApiSnapshot snapshot = ApiParser.parseReader(new StringReader(
			"<wodefinitions><wo class=\"X\" wocomponentcontent=\"false\"></wo></wodefinitions>"
		));
		snapshot.setComponentContent(true);

		StringWriter writer = new StringWriter();
		ApiSerializer.serialize(snapshot, writer);
		ApiSnapshot result = ApiParser.parseReader(new StringReader(writer.toString()));

		assertTrue(result.isComponentContent());
	}

	// ---- Preview round-trip -------------------------------------------------

	@Test
	public void roundTrip_previewContent() throws Exception {
		String xml =
			"<wodefinitions><wo class=\"X\">" +
			"  <preview>some preview content</preview>" +
			"</wo></wodefinitions>";
		ApiSnapshot result = roundTripSnapshot(xml);
		assertNotNull(result.getPreview());
		assertTrue(result.getPreview().contains("some preview content"));
	}
}

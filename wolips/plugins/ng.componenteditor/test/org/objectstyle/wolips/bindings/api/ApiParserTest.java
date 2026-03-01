package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Tests for {@link ApiParser} — the read path from {@code .api} XML to
 * immutable {@link ApiSnapshot} POJOs.
 *
 * <p>These tests exercise the parser in isolation using in-memory XML strings,
 * with no file I/O or Eclipse dependencies. They verify that bindings,
 * validations, component content, and preview content are correctly extracted.
 */
public class ApiParserTest {

	// ---- Helper to parse XML strings ----------------------------------------

	private static ApiSnapshot parse(String xml) throws Exception {
		return ApiParser.parseReader(new StringReader(xml));
	}

	private static Document toDocument(String xml) throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return builder.parse(new InputSource(new StringReader(xml)));
	}

	// ---- Basic parsing ------------------------------------------------------

	@Test
	public void parse_minimalComponent() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"MyComponent\" wocomponentcontent=\"false\">" +
			"</wo></wodefinitions>"
		);
		assertNotNull(snapshot);
		assertEquals("MyComponent", snapshot.getClassName());
		assertFalse(snapshot.isComponentContent());
		assertTrue(snapshot.getBindings().isEmpty());
		assertTrue(snapshot.getValidations().isEmpty());
		assertNull(snapshot.getPreview());
	}

	@Test
	public void parse_componentContent_true() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\" wocomponentcontent=\"true\"></wo></wodefinitions>"
		);
		assertTrue(snapshot.isComponentContent());
	}

	@Test
	public void parse_componentContent_yes() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\" wocomponentcontent=\"YES\"></wo></wodefinitions>"
		);
		assertTrue("'YES' should be accepted as true", snapshot.isComponentContent());
	}

	@Test
	public void parse_componentContent_absent() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\"></wo></wodefinitions>"
		);
		assertFalse("Missing wocomponentcontent should default to false", snapshot.isComponentContent());
	}

	@Test
	public void parse_noWoElement_returnsNull() throws Exception {
		ApiSnapshot snapshot = parse("<wodefinitions></wodefinitions>");
		assertNull(snapshot);
	}

	// ---- Binding parsing ----------------------------------------------------

	@Test
	public void parse_simpleBinding() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\"/>" +
			"</wo></wodefinitions>"
		);
		assertEquals(1, snapshot.getBindings().size());
		IApiBinding binding = snapshot.getBindings().get(0);
		assertEquals("item", binding.getName());
		assertNull(binding.getDefaults());
		assertFalse(binding.isRequired());
		assertFalse(binding.isWillSet());
	}

	@Test
	public void parse_bindingWithDefaults() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"action\" defaults=\"Actions\"/>" +
			"</wo></wodefinitions>"
		);
		IApiBinding binding = snapshot.getBindings().get(0);
		assertEquals("Actions", binding.getDefaults());
		assertTrue("Binding with Actions defaults should be an action", binding.isAction());
	}

	@Test
	public void parse_explicitlyRequiredBinding() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"value\" required=\"YES\"/>" +
			"</wo></wodefinitions>"
		);
		IApiBinding binding = snapshot.getBindings().get(0);
		assertTrue(binding.isRequired());
		assertTrue(binding.isExplicitlyRequired());
	}

	@Test
	public void parse_explicitlySettableBinding() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"selection\" settable=\"YES\"/>" +
			"</wo></wodefinitions>"
		);
		IApiBinding binding = snapshot.getBindings().get(0);
		assertTrue(binding.isWillSet());
		assertTrue(binding.isExplicitlySettable());
	}

	@Test
	public void parse_multipleBindings_preservesOrder() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"alpha\"/>" +
			"  <binding name=\"beta\"/>" +
			"  <binding name=\"gamma\"/>" +
			"</wo></wodefinitions>"
		);
		List<IApiBinding> bindings = snapshot.getBindings();
		assertEquals(3, bindings.size());
		assertEquals("alpha", bindings.get(0).getName());
		assertEquals("beta", bindings.get(1).getName());
		assertEquals("gamma", bindings.get(2).getName());
	}

	@Test
	public void parse_bindingLookupByName() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\"/>" +
			"  <binding name=\"value\" defaults=\"Boolean\"/>" +
			"</wo></wodefinitions>"
		);
		assertNotNull(snapshot.getBinding("item"));
		assertNotNull(snapshot.getBinding("value"));
		assertEquals("Boolean", snapshot.getBinding("value").getDefaults());
		assertNull(snapshot.getBinding("nonexistent"));
	}

	// ---- Implicit required via validation -----------------------------------

	@Test
	public void parse_implicitRequired_singleUnbound() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\"/>" +
			"  <validation message=\"'item' is required.\">" +
			"    <unbound name=\"item\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		IApiBinding binding = snapshot.getBinding("item");
		assertTrue("Single <unbound> validation should make binding implicitly required",
			binding.isRequired());
		assertFalse("Should NOT be explicitly required (no required=YES attribute)",
			binding.isExplicitlyRequired());
	}

	@Test
	public void parse_implicitWillSet_singleUnsettable() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"selection\"/>" +
			"  <validation message=\"'selection' must be settable.\">" +
			"    <unsettable name=\"selection\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		IApiBinding binding = snapshot.getBinding("selection");
		assertTrue("Single <unsettable> validation should make binding implicitly willSet",
			binding.isWillSet());
		assertFalse("Should NOT be explicitly settable (no settable=YES attribute)",
			binding.isExplicitlySettable());
	}

	@Test
	public void parse_complexValidation_doesNotImplyRequired() throws Exception {
		// A validation with multiple children is NOT an implicit required pattern
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"list\"/>" +
			"  <binding name=\"item\"/>" +
			"  <validation message=\"Need list or item.\">" +
			"    <and>" +
			"      <unbound name=\"list\"/>" +
			"      <unbound name=\"item\"/>" +
			"    </and>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		assertFalse("Complex validation should not make 'list' implicitly required",
			snapshot.getBinding("list").isRequired());
		assertFalse("Complex validation should not make 'item' implicitly required",
			snapshot.getBinding("item").isRequired());
	}

	// ---- Validation tree parsing --------------------------------------------

	@Test
	public void parse_validationTree_andOrNot() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"a\"/>" +
			"  <binding name=\"b\"/>" +
			"  <validation message=\"test\">" +
			"    <or>" +
			"      <and>" +
			"        <bound name=\"a\"/>" +
			"        <bound name=\"b\"/>" +
			"      </and>" +
			"      <not>" +
			"        <unbound name=\"a\"/>" +
			"      </not>" +
			"    </or>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		List<ApiValidation> validations = snapshot.getValidations();
		assertEquals(1, validations.size());

		ApiValidation validation = validations.get(0);
		assertEquals(ApiValidation.Kind.VALIDATION, validation.getKind());
		assertEquals("test", validation.getMessage());
		assertEquals(1, validation.getChildren().size());

		ApiValidation or = validation.getChildren().get(0);
		assertEquals(ApiValidation.Kind.OR, or.getKind());
		assertEquals(2, or.getChildren().size());

		ApiValidation and = or.getChildren().get(0);
		assertEquals(ApiValidation.Kind.AND, and.getKind());
		assertEquals(2, and.getChildren().size());
		assertEquals(ApiValidation.Kind.BOUND, and.getChildren().get(0).getKind());
		assertEquals("a", and.getChildren().get(0).getBindingName());

		ApiValidation not = or.getChildren().get(1);
		assertEquals(ApiValidation.Kind.NOT, not.getKind());
		assertEquals(1, not.getChildren().size());
		assertEquals(ApiValidation.Kind.UNBOUND, not.getChildren().get(0).getKind());
	}

	@Test
	public void parse_countValidation() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"a\"/>" +
			"  <binding name=\"b\"/>" +
			"  <validation message=\"Need exactly one.\">" +
			"    <count test=\"!=1\">" +
			"      <bound name=\"a\"/>" +
			"      <bound name=\"b\"/>" +
			"    </count>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		ApiValidation count = snapshot.getValidations().get(0).getChildren().get(0);
		assertEquals(ApiValidation.Kind.COUNT, count.getKind());
		assertEquals("!=1", count.getCountTest());
		assertEquals(2, count.getChildren().size());
	}

	@Test
	public void parse_allLeafPredicateKinds() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"x\"/>" +
			"  <validation message=\"test\">" +
			"    <or>" +
			"      <bound name=\"x\"/>" +
			"      <unbound name=\"x\"/>" +
			"      <settable name=\"x\"/>" +
			"      <unsettable name=\"x\"/>" +
			"      <gettable name=\"x\"/>" +
			"      <ungettable name=\"x\"/>" +
			"    </or>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		List<ApiValidation> leaves = snapshot.getValidations().get(0).getChildren().get(0).getChildren();
		assertEquals(6, leaves.size());
		assertEquals(ApiValidation.Kind.BOUND, leaves.get(0).getKind());
		assertEquals(ApiValidation.Kind.UNBOUND, leaves.get(1).getKind());
		assertEquals(ApiValidation.Kind.SETTABLE, leaves.get(2).getKind());
		assertEquals(ApiValidation.Kind.UNSETTABLE, leaves.get(3).getKind());
		assertEquals(ApiValidation.Kind.GETTABLE, leaves.get(4).getKind());
		assertEquals(ApiValidation.Kind.UNGETTABLE, leaves.get(5).getKind());
		for (ApiValidation leaf : leaves) {
			assertEquals("x", leaf.getBindingName());
		}
	}

	// ---- Preview content ----------------------------------------------------

	@Test
	public void parse_previewContent() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <preview><div>Hello</div></preview>" +
			"</wo></wodefinitions>"
		);
		assertNotNull(snapshot.getPreview());
		assertTrue("Preview should contain the div element",
			snapshot.getPreview().contains("Hello"));
	}

	@Test
	public void parse_noPreview() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\"></wo></wodefinitions>"
		);
		assertNull(snapshot.getPreview());
	}

	// ---- parseAll (multi-definition files) ----------------------------------

	@Test
	public void parseAll_multipleWoElements() throws Exception {
		String xml =
			"<wodefinitions>" +
			"  <wo class=\"com.example.Foo\">" +
			"    <binding name=\"value\"/>" +
			"  </wo>" +
			"  <wo class=\"com.example.Bar\">" +
			"    <binding name=\"item\"/>" +
			"    <binding name=\"index\"/>" +
			"  </wo>" +
			"</wodefinitions>";
		Document document = toDocument(xml);
		Map<String, ApiSnapshot> snapshots = ApiParser.parseAll(document);
		assertEquals(2, snapshots.size());

		ApiSnapshot foo = snapshots.get("com.example.Foo");
		assertNotNull(foo);
		assertEquals(1, foo.getBindings().size());
		assertEquals("value", foo.getBindings().get(0).getName());

		ApiSnapshot bar = snapshots.get("com.example.Bar");
		assertNotNull(bar);
		assertEquals(2, bar.getBindings().size());
	}

	// ---- Action detection ---------------------------------------------------

	@Test
	public void parse_actionBinding_byDefaults() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"submit\" defaults=\"Actions\"/>" +
			"</wo></wodefinitions>"
		);
		assertTrue(snapshot.getBinding("submit").isAction());
	}

	@Test
	public void parse_actionBinding_byNameConvention() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"submitAction\"/>" +
			"</wo></wodefinitions>"
		);
		assertTrue("Binding name ending in 'Action' should be detected as action",
			snapshot.getBinding("submitAction").isAction());
	}

	@Test
	public void parse_nonActionBinding() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"value\"/>" +
			"</wo></wodefinitions>"
		);
		assertFalse(snapshot.getBinding("value").isAction());
	}

	// ---- Validation evaluation ----------------------------------------------

	@Test
	public void validation_unboundFails_whenBindingMissing() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\" required=\"YES\"/>" +
			"  <validation message=\"'item' is required.\">" +
			"    <unbound name=\"item\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		Map<String, String> bindings = Map.of();
		List<ApiValidation> failed = snapshot.getFailedValidations(bindings);
		assertEquals("Validation should fail when required binding is missing", 1, failed.size());
		assertEquals("'item' is required.", failed.get(0).getMessage());
	}

	@Test
	public void validation_unboundPasses_whenBindingPresent() throws Exception {
		ApiSnapshot snapshot = parse(
			"<wodefinitions><wo class=\"X\">" +
			"  <binding name=\"item\" required=\"YES\"/>" +
			"  <validation message=\"'item' is required.\">" +
			"    <unbound name=\"item\"/>" +
			"  </validation>" +
			"</wo></wodefinitions>"
		);
		Map<String, String> bindings = Map.of("item", "myItem");
		List<ApiValidation> failed = snapshot.getFailedValidations(bindings);
		assertTrue("Validation should pass when required binding is present", failed.isEmpty());
	}
}

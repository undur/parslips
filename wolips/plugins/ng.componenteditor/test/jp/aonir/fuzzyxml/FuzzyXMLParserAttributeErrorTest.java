package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import jp.aonir.fuzzyxml.event.FuzzyXMLErrorEvent;
import jp.aonir.fuzzyxml.event.FuzzyXMLErrorListener;

/**
 * Tests that the parser detects and reports missing '=' between attribute
 * names and values — e.g. {@code negate"true"} instead of
 * {@code negate="true"}.
 *
 * <p>Also verifies that the parser <em>recovers</em> from this error by
 * treating the quote as an implicit '=', so the attribute name and value
 * are still correctly parsed and available in the DOM.
 */
public class FuzzyXMLParserAttributeErrorTest {

	// ---- Error detection tests ----------------------------------------------

	@Test
	public void missingEquals_doubleQuote() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors("<wo:if negate\"true\">");
		assertEquals("Should report one error for missing '='", 1, errors.size());
		assertTrue("Error message should mention the attribute name",
				errors.get(0).getMessage().contains("negate"));
	}

	@Test
	public void missingEquals_singleQuote() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors("<wo:if negate'true'>");
		assertEquals("Should report one error for missing '='", 1, errors.size());
		assertTrue("Error message should mention the attribute name",
				errors.get(0).getMessage().contains("negate"));
	}

	@Test
	public void missingEquals_afterValidAttribute() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:if condition=\"$showGraphs\" negate\"true\">");
		assertEquals("Should report one error (only for negate, not condition)", 1, errors.size());
		assertTrue(errors.get(0).getMessage().contains("negate"));
	}

	@Test
	public void missingEquals_multipleErrors() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:if a\"1\" b\"2\">");
		assertEquals("Should report two errors", 2, errors.size());
		assertTrue(errors.get(0).getMessage().contains("a"));
		assertTrue(errors.get(1).getMessage().contains("b"));
	}

	@Test
	public void missingEquals_selfClosingTag() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:str negate\"true\" />");
		assertEquals("Should report error in self-closing tag", 1, errors.size());
		assertTrue(errors.get(0).getMessage().contains("negate"));
	}

	@Test
	public void missingEquals_regularHtmlTag() {
		// The detection works for any tag, not just wo: tags
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<div class\"myClass\">");
		assertEquals("Should report error in regular HTML tag", 1, errors.size());
		assertTrue(errors.get(0).getMessage().contains("class"));
	}

	// ---- No false positives -------------------------------------------------

	@Test
	public void validAttributes_noError() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:if condition=\"$showGraphs\" negate=\"true\">");
		assertEquals("Should report no errors for valid attributes", 0, errors.size());
	}

	@Test
	public void valuelessAttribute_noError() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors("<div hidden>");
		assertEquals("Should not flag valueless attributes", 0, errors.size());
	}

	@Test
	public void singleQuoteValue_noError() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:str value='$title' />");
		assertEquals("Should not flag single-quoted valid attributes", 0, errors.size());
	}

	@Test
	public void emptyValue_noError() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:str value=\"\" />");
		assertEquals("Should not flag empty attribute values", 0, errors.size());
	}

	// ---- Recovery tests (DOM correctness after error) -----------------------

	@Test
	public void missingEquals_recoversAttributeName() {
		FuzzyXMLDocument doc = parseDocument("<wo:if negate\"true\"></wo:if>");
		FuzzyXMLElement element = findElement(doc, "wo:if");
		assertNotNull("Element should be parsed", element);

		FuzzyXMLAttribute[] attrs = element.getAttributes();
		boolean foundNegate = false;
		for (FuzzyXMLAttribute attr : attrs) {
			if ("negate".equals(attr.getName())) {
				foundNegate = true;
			}
		}
		assertTrue("Recovered attribute should have name 'negate'", foundNegate);
	}

	@Test
	public void missingEquals_recoversAttributeValue() {
		FuzzyXMLDocument doc = parseDocument("<wo:if negate\"true\"></wo:if>");
		FuzzyXMLElement element = findElement(doc, "wo:if");
		assertNotNull(element);

		FuzzyXMLAttribute attr = findAttribute(element, "negate");
		assertNotNull("Should find attribute 'negate'", attr);
		assertEquals("Recovered attribute value should be 'true'", "true", attr.getValue());
	}

	@Test
	public void missingEquals_recoversWithValidAttributes() {
		// Both the valid attribute and the recovered attribute should be present
		FuzzyXMLDocument doc = parseDocument(
				"<wo:if condition=\"$showGraphs\" negate\"true\"></wo:if>");
		FuzzyXMLElement element = findElement(doc, "wo:if");
		assertNotNull(element);

		FuzzyXMLAttribute condition = findAttribute(element, "condition");
		assertNotNull("Valid attribute 'condition' should be present", condition);
		assertEquals("$showGraphs", condition.getValue());

		FuzzyXMLAttribute negate = findAttribute(element, "negate");
		assertNotNull("Recovered attribute 'negate' should be present", negate);
		assertEquals("true", negate.getValue());
	}

	@Test
	public void missingEquals_selfClosing_recoversAttribute() {
		FuzzyXMLDocument doc = parseDocument("<wo:str negate\"true\" />");
		FuzzyXMLElement element = findElement(doc, "wo:str");
		assertNotNull(element);

		FuzzyXMLAttribute attr = findAttribute(element, "negate");
		assertNotNull("Should find attribute 'negate' in self-closing tag", attr);
		assertEquals("true", attr.getValue());
	}

	// ---- Escaped quote tests ------------------------------------------------

	@Test
	public void escapedQuotes_parsedCorrectly() {
		// value="\"Hello\"" — literal string with embedded quotes
		FuzzyXMLDocument doc = parseDocument("<wo:str value=\"\\\"Hello\\\"\" />");
		FuzzyXMLElement element = findElement(doc, "wo:str");
		assertNotNull("Element should be parsed", element);

		FuzzyXMLAttribute attr = findAttribute(element, "value");
		assertNotNull("Should find attribute 'value'", attr);
		assertEquals("Escaped quotes should be preserved in value",
				"\"Hello\"", attr.getValue());
	}

	@Test
	public void escapedQuotes_noError() {
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(
				"<wo:str value=\"\\\"Hello\\\"\" />");
		assertEquals("Should not report errors for escaped quotes", 0, errors.size());
	}

	@Test
	public void escapedQuotes_otherAttributesPreserved() {
		// Escaped quotes in one attribute shouldn't corrupt subsequent attributes
		FuzzyXMLDocument doc = parseDocument(
				"<wo:WOGenericElement elementName=\"\\\"div\\\"\" class=\"$cssClass\" />");
		FuzzyXMLElement element = findElement(doc, "wo:WOGenericElement");
		assertNotNull(element);

		FuzzyXMLAttribute nameAttr = findAttribute(element, "elementName");
		assertNotNull("Should find 'elementName'", nameAttr);
		assertEquals("\"div\"", nameAttr.getValue());

		FuzzyXMLAttribute classAttr = findAttribute(element, "class");
		assertNotNull("Should find 'class' after escaped-quote attribute", classAttr);
		assertEquals("$cssClass", classAttr.getValue());
	}

	@Test
	public void escapedQuotes_attributeCount() {
		// Verify the parser doesn't split the tag at an embedded quote
		FuzzyXMLDocument doc = parseDocument(
				"<wo:str value=\"\\\"Hello\\\"\" />");
		FuzzyXMLElement element = findElement(doc, "wo:str");
		assertNotNull(element);
		assertEquals("Should have exactly 1 attribute", 1, element.getAttributes().length);
	}

	// ---- Error offset tests -------------------------------------------------

	@Test
	public void missingEquals_errorOffsetPointsToAttributeName() {
		//               0123456789...
		String source = "<wo:if negate\"true\">";
		// 'negate' starts at index 7 in the source
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals(1, errors.size());

		FuzzyXMLErrorEvent error = errors.get(0);
		// The error offset should point to the attribute name 'negate'
		// which starts at position 7 in the source string.
		// tagContentOffset = 1 (after '<'), attribute starts at position 6
		// in the tag content text, so global offset = 1 + 6 = 7.
		assertEquals("Error offset should point to start of 'negate'",
				7, error.getOffset());
		assertEquals("Error length should cover 'negate'",
				6, error.getLength());
	}

	// ---- Helper methods -----------------------------------------------------

	/**
	 * Parses the given source and collects error events for missing '='
	 * errors. Filters out unrelated parser errors (e.g. unclosed tags)
	 * that are not relevant to these tests.
	 */
	private List<FuzzyXMLErrorEvent> parseAndCollectErrors(String source) {
		List<FuzzyXMLErrorEvent> errors = new ArrayList<FuzzyXMLErrorEvent>();
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		parser.addErrorListener(new FuzzyXMLErrorListener() {
			@Override
			public void error(FuzzyXMLErrorEvent event) {
				if (event.getMessage().contains("Missing '='")) {
					errors.add(event);
				}
			}
		});
		parser.parse(source);
		return errors;
	}

	/**
	 * Parses the given source and returns the resulting document.
	 */
	private FuzzyXMLDocument parseDocument(String source) {
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		return parser.parse(source);
	}

	/**
	 * Finds the first element with the given name in the document (searches
	 * children of the document element only).
	 */
	private FuzzyXMLElement findElement(FuzzyXMLDocument doc, String name) {
		for (FuzzyXMLNode node : doc.getDocumentElement().getChildren()) {
			if (node instanceof FuzzyXMLElement) {
				FuzzyXMLElement el = (FuzzyXMLElement) node;
				if (el.getName().equalsIgnoreCase(name)) {
					return el;
				}
			}
		}
		return null;
	}

	/**
	 * Finds an attribute by name on the given element.
	 */
	private FuzzyXMLAttribute findAttribute(FuzzyXMLElement element, String name) {
		for (FuzzyXMLAttribute attr : element.getAttributes()) {
			if (name.equals(attr.getName())) {
				return attr;
			}
		}
		return null;
	}
}

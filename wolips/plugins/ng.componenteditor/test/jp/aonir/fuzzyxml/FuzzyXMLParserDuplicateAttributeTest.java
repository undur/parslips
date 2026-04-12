package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import jp.aonir.fuzzyxml.event.FuzzyXMLErrorEvent;
import jp.aonir.fuzzyxml.event.FuzzyXMLErrorListener;

/**
 * Tests that the parser detects and reports duplicate attributes on tags.
 *
 * <p>The HTML spec says duplicate attributes are a parse error — the first
 * occurrence wins and subsequent ones must be ignored. We keep that behavior
 * but now also report the duplicate to the user.
 *
 * <p>Duplicate detection is case-insensitive: HTML attributes are
 * case-insensitive, so {@code class} and {@code Class} are the same.
 */
public class FuzzyXMLParserDuplicateAttributeTest {

	// =========================================================================
	// Error detection tests
	// =========================================================================

	@Test
	public void duplicateAttribute_sameCase() {
		String source = "<div class=\"a\" class=\"b\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("Should report one duplicate-attribute error", 1, errors.size());
		assertTrue("Error message should mention the attribute name",
				errors.get(0).getMessage().contains("class"));
	}

	@Test
	public void duplicateAttribute_differentCase() {
		// HTML attributes are case-insensitive — Class and class are the same.
		String source = "<div class=\"a\" Class=\"b\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("Should report one duplicate-attribute error", 1, errors.size());
		String msg = errors.get(0).getMessage();
		assertTrue("Error message should mention the duplicate name 'Class'",
				msg.contains("Class"));
		assertTrue("Error message should mention the original name 'class'",
				msg.contains("class"));
	}

	@Test
	public void duplicateAttribute_woTag() {
		String source = "<wo:str value=\"$x\" value=\"$y\" />";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("Should report one duplicate-attribute error", 1, errors.size());
		assertTrue("Error message should mention 'value'",
				errors.get(0).getMessage().contains("value"));
	}

	@Test
	public void duplicateAttribute_threeOccurrences() {
		// Three identical attributes → two duplicate errors (the 2nd and 3rd).
		String source = "<div class=\"a\" class=\"b\" class=\"c\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("Should report two duplicate-attribute errors", 2, errors.size());
	}

	// =========================================================================
	// Value preservation — the first attribute wins per the HTML spec.
	// =========================================================================

	@Test
	public void duplicateAttribute_firstValuePreserved() {
		String source = "<div class=\"first\" class=\"second\"></div>";
		FuzzyXMLDocument doc = parseDocument(source);
		FuzzyXMLElement div = findElement(doc, "div");
		assertNotNull("div element should be parsed", div);
		assertEquals("First attribute value should be preserved",
				"first", div.getAttributeValue("class"));
	}

	// =========================================================================
	// Regression guards — no false positives
	// =========================================================================

	@Test
	public void noDuplicates_noErrors() {
		String source = "<div class=\"a\" id=\"b\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("No duplicates should produce no errors", 0, errors.size());
	}

	@Test
	public void noDuplicates_woTag_noErrors() {
		String source = "<wo:str value=\"$x\" escapeHTML=\"false\" />";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("No duplicates should produce no errors", 0, errors.size());
	}

	@Test
	public void sameValueDifferentNames_noErrors() {
		// Same value on different attributes — not a duplicate.
		String source = "<input name=\"x\" value=\"x\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectDuplicateErrors(source);
		assertEquals("Different attribute names should produce no errors", 0, errors.size());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	/**
	 * Parses the given source and collects all error events whose message
	 * matches the duplicate-attribute error format.
	 */
	private List<FuzzyXMLErrorEvent> parseAndCollectDuplicateErrors(String source) {
		List<FuzzyXMLErrorEvent> errors = new ArrayList<FuzzyXMLErrorEvent>();
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		parser.addErrorListener(new FuzzyXMLErrorListener() {
			@Override
			public void error(FuzzyXMLErrorEvent event) {
				if (event.getMessage().contains("Duplicate attribute")) {
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
	 * Finds the first element with the given name in the document.
	 */
	private FuzzyXMLElement findElement(FuzzyXMLDocument doc, String name) {
		return findElementIn(doc.getDocumentElement().getChildren(), name);
	}

	/**
	 * Recursively searches for an element by name.
	 */
	private FuzzyXMLElement findElementIn(FuzzyXMLNode[] nodes, String name) {
		for (FuzzyXMLNode node : nodes) {
			if (node instanceof FuzzyXMLElement && !(node instanceof FuzzyXMLAttribute)) {
				FuzzyXMLElement el = (FuzzyXMLElement) node;
				if (el.getName().equalsIgnoreCase(name)) {
					return el;
				}
				FuzzyXMLElement found = findElementIn(el.getChildren(), name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}

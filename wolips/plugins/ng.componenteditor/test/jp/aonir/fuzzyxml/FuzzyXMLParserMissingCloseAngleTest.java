package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import jp.aonir.fuzzyxml.event.FuzzyXMLErrorEvent;
import jp.aonir.fuzzyxml.event.FuzzyXMLErrorListener;

/**
 * Tests that the parser detects and reports a missing closing {@code >} on
 * tags — e.g. {@code <wo:if condition="$x"} followed by a newline and the
 * next tag, instead of a proper {@code >}.
 *
 * <p>The FuzzyXML regex allows tags to match without a closing {@code >}
 * (the "fuzzy" part), but this is almost always a typo. These tests verify
 * that an error is fired while the parser still recovers and processes the
 * tag.
 */
public class FuzzyXMLParserMissingCloseAngleTest {

	// ---- Error detection tests ----------------------------------------------

	@Test
	public void missingCloseAngle_woStartTag() {
		String source = "<wo:if condition=\"$showGraphs\"\n\t<div class=\"row\">";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should report one missing '>' error", 1, errors.size());
		assertTrue("Error message should mention the tag name",
				errors.get(0).getMessage().contains("wo:if"));
	}

	@Test
	public void missingCloseAngle_htmlStartTag() {
		String source = "<div class=\"row\"\n\t<span>hello</span>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should report one missing '>' error", 1, errors.size());
		assertTrue(errors.get(0).getMessage().contains("div"));
	}

	@Test
	public void missingCloseAngle_closeTag() {
		// Missing > on a closing tag
		String source = "<wo:if condition=\"$x\">content</wo:if\n<div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should report one missing '>' error", 1, errors.size());
		assertTrue("Error message should mention the tag name",
				errors.get(0).getMessage().contains("wo:if"));
	}

	@Test
	public void missingCloseAngle_selfClosingTag() {
		// Self-closing tag missing the final >: <wo:str value="$x" /\n
		String source = "<wo:str value=\"$x\" /\n<div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should report one missing '>' error", 1, errors.size());
	}

	@Test
	public void missingCloseAngle_errorMessageFormat() {
		String source = "<wo:if condition=\"$showGraphs\"\n\t<div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals(1, errors.size());
		String msg = errors.get(0).getMessage();
		assertTrue("Message should say 'Missing closing'",
				msg.contains("Missing closing '>'"));
		assertTrue("Message should include the tag name in angle brackets",
				msg.contains("<wo:if>"));
	}

	// ---- No false positives -------------------------------------------------

	@Test
	public void properTag_noError() {
		String source = "<wo:if condition=\"$showGraphs\">\n\t<div class=\"row\"></div>\n</wo:if>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should report no missing '>' errors for well-formed tags", 0, errors.size());
	}

	@Test
	public void selfClosingTag_noError() {
		String source = "<wo:str value=\"$x\" />";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should not flag proper self-closing tags", 0, errors.size());
	}

	@Test
	public void htmlComment_noError() {
		// HTML comments have different closing syntax (-->), should not be flagged
		String source = "<!-- this is a comment -->";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should not flag HTML comments", 0, errors.size());
	}

	@Test
	public void multipleProperTags_noError() {
		String source = "<div class=\"row\">\n\t<wo:if condition=\"$x\">\n\t\t<span>text</span>\n\t</wo:if>\n</div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectErrors(source);
		assertEquals("Should not flag any well-formed tags", 0, errors.size());
	}

	// ---- Recovery tests (parser still processes the tag) --------------------

	@Test
	public void missingCloseAngle_tagStillParsed() {
		// Even with missing >, the parser should still create the element
		String source = "<wo:if condition=\"$showGraphs\"\n\t<div class=\"row\"></div>";
		FuzzyXMLDocument doc = parseDocument(source);
		FuzzyXMLElement woIf = findElement(doc, "wo:if");
		assertNotNull("wo:if element should still be created despite missing '>'", woIf);
	}

	@Test
	public void missingCloseAngle_attributeStillParsed() {
		String source = "<wo:if condition=\"$showGraphs\"\n\t<div>";
		FuzzyXMLDocument doc = parseDocument(source);
		FuzzyXMLElement woIf = findElement(doc, "wo:if");
		assertNotNull(woIf);

		FuzzyXMLAttribute[] attrs = woIf.getAttributes();
		boolean foundCondition = false;
		for (FuzzyXMLAttribute attr : attrs) {
			if ("condition".equals(attr.getName())) {
				foundCondition = true;
				assertEquals("$showGraphs", attr.getValue());
			}
		}
		assertTrue("Attribute 'condition' should still be parsed", foundCondition);
	}

	// ---- Helper methods -----------------------------------------------------

	/**
	 * Parses the given source and collects error events for missing closing
	 * '>' errors. Filters out unrelated parser errors (e.g. unclosed tags)
	 * that are not relevant to these tests.
	 */
	private List<FuzzyXMLErrorEvent> parseAndCollectErrors(String source) {
		List<FuzzyXMLErrorEvent> errors = new ArrayList<FuzzyXMLErrorEvent>();
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		parser.addErrorListener(new FuzzyXMLErrorListener() {
			@Override
			public void error(FuzzyXMLErrorEvent event) {
				if (event.getMessage().contains("Missing closing '>'")) {
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

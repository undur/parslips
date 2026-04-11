package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import jp.aonir.fuzzyxml.event.FuzzyXMLErrorEvent;
import jp.aonir.fuzzyxml.event.FuzzyXMLErrorListener;

/**
 * Tests that the parser detects and reports extraneous close tags — close
 * tags that have no matching open tag in the document, e.g.
 *
 * <pre>
 * &lt;div&gt;
 * &lt;/div&gt;
 * &lt;/div&gt;    ← extraneous, should be reported
 * &lt;/div&gt;    ← extraneous, should be reported
 * </pre>
 *
 * <p>A pre-existing bug in {@code handleCloseTag} silently returned when the
 * open-tag stack was empty, swallowing all extraneous close-tag errors.
 * The fix reports each empty-stack close tag as an {@code error.noStartTag}.
 */
public class FuzzyXMLParserExtraneousCloseTagTest {

	// =========================================================================
	// Error detection tests
	// =========================================================================

	@Test
	public void extraneousCloseTag_afterAllTagsClosed() {
		// A div that has been properly closed, followed by an extraneous
		// </div>. The second close tag has no matching open and must be
		// reported as an error.
		String source = "<div>\n</div>\n</div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Should report one extraneous close-tag error", 1, errors.size());
		assertTrue("Error message should mention the tag name",
				errors.get(0).getMessage().contains("div"));
	}

	@Test
	public void extraneousCloseTag_multipleStackedExtras() {
		// The original report from the field: a single div with multiple
		// dangling close tags. Each extraneous close must be flagged.
		String source = "<div class=\"smu\">\n</div>\n</div>\n</div>\n</div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Should report three extraneous close-tag errors", 3, errors.size());
	}

	@Test
	public void extraneousCloseTag_atStartOfDocument() {
		// A close tag before any open tag has been seen. The stack is
		// empty when we hit </div>, and the error must still be reported.
		String source = "</div><div>content</div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Should report one extraneous close-tag error", 1, errors.size());
	}

	@Test
	public void extraneousCloseTag_woTag() {
		// Extraneous close tag for a wo: element. The namespace prefix
		// should be preserved in the error message.
		String source = "<wo:if condition=\"$x\">content</wo:if>\n</wo:if>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Should report one extraneous close-tag error", 1, errors.size());
		assertTrue("Error message should mention wo:if",
				errors.get(0).getMessage().contains("wo:if"));
	}

	// =========================================================================
	// Error-message format
	// =========================================================================

	@Test
	public void extraneousCloseTag_errorMessageFormat() {
		String source = "<div></div></div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals(1, errors.size());
		String msg = errors.get(0).getMessage();
		// The stock message format is "<div> start tag is not found."
		assertTrue("Message should look like '<div> start tag is not found.' but was: " + msg,
				msg.contains("<div>") && msg.toLowerCase().contains("start tag"));
	}

	// =========================================================================
	// Regression guards — existing valid templates should still produce
	// no extraneous-close-tag errors.
	// =========================================================================

	@Test
	public void validWellFormedTemplate_noErrors() {
		String source = "<div><span>hello</span></div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Well-formed template should produce no errors", 0, errors.size());
	}

	@Test
	public void validWoTemplate_noErrors() {
		String source = "<wo:if condition=\"$x\"><wo:str value=\"$y\"/></wo:if>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Well-formed wo template should produce no errors", 0, errors.size());
	}

	@Test
	public void unclosedOpenTag_notReportedAsExtraneousClose() {
		// A missing close tag produces a different error (error.noCloseTag),
		// which should NOT be counted as an extraneous-close-tag error.
		String source = "<div><span>hello</div>";
		List<FuzzyXMLErrorEvent> errors = parseAndCollectNoStartTagErrors(source);
		assertEquals("Missing close tag is not an extraneous close-tag error",
				0, errors.size());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	/**
	 * Parses the given source and collects all error events whose message
	 * matches the "no start tag" error format — i.e. the message we fire
	 * for an extraneous close tag.
	 */
	private List<FuzzyXMLErrorEvent> parseAndCollectNoStartTagErrors(String source) {
		List<FuzzyXMLErrorEvent> errors = new ArrayList<FuzzyXMLErrorEvent>();
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		parser.addErrorListener(new FuzzyXMLErrorListener() {
			@Override
			public void error(FuzzyXMLErrorEvent event) {
				// The message format is "<tag> start tag is not found."
				// from error.noStartTag in message.properties.
				if (event.getMessage().contains("start tag is not found")) {
					errors.add(event);
				}
			}
		});
		parser.parse(source);
		return errors;
	}
}

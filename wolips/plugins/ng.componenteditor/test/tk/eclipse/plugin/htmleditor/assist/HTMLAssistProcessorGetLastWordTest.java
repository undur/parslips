package tk.eclipse.plugin.htmleditor.assist;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link TagStackAnalyzer#getLastWord(String)}, the method that
 * builds a stack of unclosed tags to determine which tag should be closed by
 * auto-completion (Ctrl+Space).
 *
 * <p>The return array is: {@code [word, prevTag, lastTag, attr]} where
 * {@code lastTag} (index 2) is the tag name that close-tag completion uses.
 *
 * <p>A known bug existed where attribute values ending with "/" (e.g.
 * {@code href="/"}) would cause a spurious stack pop, making the auto-close
 * suggest the wrong tag.
 */
public class HTMLAssistProcessorGetLastWordTest {

	/**
	 * Returns the "lastTag" (close-tag target, index 2) from getLastWord.
	 */
	private String lastTag(String html) {
		return TagStackAnalyzer.getLastWord(html)[2];
	}

	// =========================================================================
	// Basic close-tag detection
	// =========================================================================

	@Test
	public void simpleUnclosedTag() {
		assertEquals("div", lastTag("<div>"));
	}

	@Test
	public void nestedTags_returnsInnermost() {
		assertEquals("span", lastTag("<div><span>"));
	}

	@Test
	public void closedTag_removedFromStack() {
		assertEquals("div", lastTag("<div><span></span>"));
	}

	@Test
	public void selfClosingTag_notOnStack() {
		assertEquals("div", lastTag("<div><img />"));
	}

	@Test
	public void emptyDocument_noTag() {
		assertEquals("", lastTag(""));
	}

	@Test
	public void allTagsClosed_noTag() {
		assertEquals("", lastTag("<div></div>"));
	}

	// =========================================================================
	// The href="/" bug — attribute values must not corrupt the tag stack
	// =========================================================================

	@Test
	public void hrefSlash_doesNotPopTag() {
		// This was the original bug: href="/" caused <a> to be popped
		assertEquals("a", lastTag("<a href=\"/\">"));
	}

	@Test
	public void hrefSlash_withNestedContext() {
		// The user's exact reproduction case (simplified)
		String html = "<div><a href=\"/\"></a></div><wo:bork>";
		assertEquals("wo:bork", lastTag(html));
	}

	@Test
	public void hrefSlashPath_doesNotPopTag() {
		assertEquals("a", lastTag("<a href=\"/some/path\">"));
	}

	@Test
	public void singleQuotedSlash_doesNotPopTag() {
		assertEquals("a", lastTag("<a href='/'>"));
	}

	@Test
	public void attributeValueEndingWithSlash_doesNotPopTag() {
		assertEquals("a", lastTag("<a data-url=\"http://example.com/\">"));
	}

	// =========================================================================
	// wo: tags
	// =========================================================================

	@Test
	public void woTag_onStack() {
		assertEquals("wo:string", lastTag("<wo:string value=\"$name\">"));
	}

	@Test
	public void woTag_afterRegularTag() {
		assertEquals("wo:bork", lastTag("<div><wo:bork>"));
	}

	@Test
	public void woTag_closedProperly() {
		assertEquals("div", lastTag("<div><wo:string></wo:string>"));
	}

	// =========================================================================
	// Apostrophe in body text — must not break tag tracking
	// =========================================================================

	/**
	 * Returns the "word" (index 0) from getLastWord — the partial token at the
	 * cursor that determines what kind of completion to offer.
	 */
	private String word(String html) {
		return TagStackAnalyzer.getLastWord(html)[0];
	}

	@Test
	public void apostropheInText_doesNotBreakTagCompletion() {
		// An apostrophe in body text must not start quote tracking.
		// If it does, everything after it gets swallowed — including the '<'
		// that should trigger tag completion.
		assertEquals("<", word("<p>what's up</p><"));
	}

	@Test
	public void apostropheInText_lastTagStillCorrect() {
		// The tag stack must survive an apostrophe in body text.
		assertEquals("div", lastTag("<div><p>what's up</p><"));
	}

	@Test
	public void doubleQuoteInText_doesNotBreakTagCompletion() {
		// Same issue with a stray double-quote in body text.
		assertEquals("<", word("<p>she said \"hello</p><"));
	}

	@Test
	public void apostropheInText_tagAfter() {
		// Tag completion after body text with an apostrophe.
		assertEquals("<wo", word("<p>it's fine</p><wo"));
	}

	@Test
	public void singleQuotedAttributeStillWorks() {
		// Single-quoted attribute values must still work correctly.
		assertEquals("a", lastTag("<a href='/path'>"));
	}

	@Test
	public void doubleQuotedAttributeWithSpacesStillWorks() {
		// Spaces inside double-quoted attribute values must still be preserved.
		assertEquals("input", lastTag("<input value=\"hello world\">"));
	}

	// =========================================================================
	// Slash in body text — must not corrupt the tag stack
	// =========================================================================

	@Test
	public void slashInBodyText_doesNotPopTag() {
		// A "/" in body text like "Price / amount" must not be treated as a
		// self-closing tag marker. Previously this popped <td> off the stack.
		assertEquals("tr", lastTag("<table><tr><td>Price / amount</td>"));
	}

	@Test
	public void slashInBodyText_urlInText() {
		// URLs in body text contain multiple slashes — none should corrupt the
		// tag stack. Leave <p> unclosed so we can verify it's still on the stack.
		assertEquals("p", lastTag("<div><p>Visit http://example.com/path/to/page for details"));
	}

	@Test
	public void slashInBodyText_mathExpression() {
		assertEquals("td", lastTag("<table><tr><td>10 / 5 = 2"));
	}

	@Test
	public void slashInBodyText_afterClosedTag() {
		// Slash in body text after a properly closed tag.
		assertEquals("div", lastTag("<div><p>hello</p>a / b"));
	}
}

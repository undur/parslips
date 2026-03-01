package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import org.junit.Test;

import jp.aonir.fuzzyxml.internal.RenderContext;
import jp.aonir.fuzzyxml.internal.WOHTMLRenderDelegate;

/**
 * Tests for the HTML formatter, verifying that formatting preserves the
 * author's intent: line structure, blank lines, entities, comment content,
 * and element structure.
 */
public class FuzzyXMLFormatterTest {

	/**
	 * Parses the given HTML and formats it using the standard formatter
	 * settings (tabs, trim, newlines, HTML mode).
	 */
	private String format(String html) {
		FuzzyXMLDocument doc = new FuzzyXMLParser(false, true).parse(html);
		RenderContext ctx = new RenderContext(true);
		ctx.setIndentTabs(true);
		ctx.setTrim(true);
		ctx.setShowNewlines(true);
		ctx.setSpaceInEmptyTags(true);
		ctx.setDelegate(new WOHTMLRenderDelegate());
		StringBuffer buf = new StringBuffer();
		doc.getDocumentElement().toXMLString(ctx, buf);
		return buf.toString();
	}

	// --- Blank line preservation ---

	@Test
	public void blankLinesBetweenSiblingElements_preserved() {
		String input = "<div>\n\t<p>one</p>\n\n\t<p>two</p>\n</div>";
		String result = format(input);
		assertTrue("Blank line between <p> elements should be preserved",
			result.contains("</p>\n\n"));
	}

	@Test
	public void singleNewlineBetweenElements_noExtraBlankLine() {
		String input = "<div>\n\t<p>one</p>\n\t<p>two</p>\n</div>";
		String result = format(input);
		assertFalse("No blank line should be inserted when original had none",
			result.contains("</p>\n\n"));
	}

	@Test
	public void multipleBlankLinesBetweenElements_preserved() {
		String input = "<div>\n\t<p>one</p>\n\n\n\t<p>two</p>\n</div>";
		String result = format(input);
		assertTrue("Multiple blank lines should be preserved",
			result.contains("</p>\n\n\n"));
	}

	// --- Entity preservation ---

	@Test
	public void nbspEntity_preserved() {
		String input = "<p>hello&nbsp;world</p>";
		String result = format(input);
		assertTrue("&nbsp; should be preserved as-is",
			result.contains("hello&nbsp;world"));
	}

	@Test
	public void nonAsciiCharacters_notConvertedToEntities() {
		String input = "<p>ó ð ú</p>";
		String result = format(input);
		assertTrue("Non-ASCII characters should stay as UTF-8",
			result.contains("ó ð ú"));
		assertFalse("Should not be converted to &oacute;",
			result.contains("&oacute;"));
	}

	@Test
	public void singleQuotesInScript_notEscaped() {
		String input = "<script>\nvar x = 'hello';\n</script>";
		String result = format(input);
		assertTrue("Single quotes in script should stay as single quotes",
			result.contains("'hello'"));
		assertFalse("Single quotes should not become &apos;",
			result.contains("&apos;"));
	}

	// --- Comment preservation ---

	@Test
	public void commentContent_preservedExactly() {
		String input = "<div><!-- comment text --></div>";
		String result = format(input);
		assertTrue("Comment content should be preserved exactly",
			result.contains("<!-- comment text -->"));
	}

	@Test
	public void commentWithNoTrailingSpace_preserved() {
		String input = "<div><!--no spaces--></div>";
		String result = format(input);
		assertTrue("Comment with no spaces should not get spaces added",
			result.contains("<!--no spaces-->"));
	}

	// --- Self-closing and void elements ---

	@Test
	public void voidElement_notSelfClosed() {
		String input = "<p><br></p>";
		String result = format(input);
		assertTrue("br should render as <br> not <br />",
			result.contains("<br>"));
		assertFalse("br should not be self-closed",
			result.contains("<br />") || result.contains("<br/>"));
	}

	@Test
	public void emptyNonVoidElement_notSelfClosed() {
		String input = "<table><tr><th></th></tr></table>";
		String result = format(input);
		assertTrue("Empty <th> should use <th></th>, not <th />",
			result.contains("<th></th>"));
	}

	@Test
	public void emptyScriptTag_staysOnOneLine() {
		String input = "<script src=\"app.js\"></script>";
		String result = format(input);
		assertTrue("Empty script should stay on one line",
			result.contains("<script src=\"app.js\"></script>"));
	}

	@Test
	public void woTag_selfCloses() {
		String input = "<div><wo:str value=\"$name\" /></div>";
		String result = format(input);
		assertTrue("wo: tags should self-close",
			result.contains("<wo:str value=\"$name\" />"));
	}

	// --- Line structure preservation ---

	@Test
	public void inlineContent_staysOnOneLine() {
		String input = "<p><a href=\"#\"><wo:str value=\"$text\" /> link</a></p>";
		String result = format(input);
		// The content between <a> and </a> should stay inline
		assertFalse("Inline content should not be split across lines",
			result.contains("<wo:str value=\"$text\" />\n"));
	}

	@Test
	public void multiLineContent_staysMultiLine() {
		String input = "<div>\n\t<p>text</p>\n</div>";
		String result = format(input);
		assertTrue("Multi-line content should stay multi-line",
			result.contains("<div>\n"));
		assertTrue("Close tag should be on its own line",
			result.contains("\n\t</div>"));
	}

	// --- Trailing space after text ---

	@Test
	public void textWithTrailingNewline_noTrailingSpace() {
		String input = "<wo:if condition=\"$c\">\n\tsome text\n</wo:if>";
		String result = format(input);
		assertFalse("Text with trailing newline should not get a trailing space",
			result.contains("text </wo:if>") || result.contains("text \n"));
	}
}

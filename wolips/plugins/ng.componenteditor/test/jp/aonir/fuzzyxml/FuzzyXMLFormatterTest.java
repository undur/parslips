package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import org.junit.Test;

import jp.aonir.fuzzyxml.internal.RenderContext;
import jp.aonir.fuzzyxml.internal.RenderDelegate;
import jp.aonir.fuzzyxml.internal.WOHTMLRenderDelegate;

/**
 * Tests for the HTML formatter, verifying that formatting preserves the
 * author's intent: line structure, blank lines, entities, comment content,
 * and element structure.
 */
public class FuzzyXMLFormatterTest {

	/**
	 * Parses the given HTML and formats it using the standard formatter
	 * settings (tabs, trim, newlines, HTML mode). Renders using the same
	 * approach as FormatRefactoring — iterating children of the document
	 * element — which is the actual code path used in the editor.
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
		// Match FormatRefactoring's rendering path: iterate children of the
		// document element, calling delegate.renderNode() for each child.
		// This is how the actual editor formats — it skips the <document>
		// wrapper tag and renders children directly.
		RenderDelegate delegate = ctx.getDelegate();
		for (FuzzyXMLNode node : doc.getDocumentElement().getChildren()) {
			if (delegate == null || delegate.renderNode(node, ctx, buf)) {
				node.toXMLString(ctx, buf);
			}
		}
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
		assertTrue("Close tag should be on its own line: " + result,
			result.contains("\n</div>"));
	}

	// --- Space between inline tags ---

	@Test
	public void spaceBetweenInlineTags_preserved() {
		// A space between </strong> and <wo:str> inside a non-breaking
		// parent must be preserved so the rendered text doesn't merge.
		String input = "<wo:if condition=\"$c\"><strong>bt</strong> <wo:str value=\"$v\" /><br></wo:if>";
		String result = format(input);
		assertTrue("Space between </strong> and <wo:str> should be preserved: " + result,
			result.contains("</strong> <wo:str"));
	}

	@Test
	public void spaceBetweenInlineElements_preserved() {
		// Same pattern with plain HTML elements.
		String input = "<p><strong>bold</strong> <em>italic</em></p>";
		String result = format(input);
		assertTrue("Space between </strong> and <em> should be preserved: " + result,
			result.contains("</strong> <em>"));
	}

	// --- Blank lines in real-world template patterns ---

	@Test
	public void blankLineBetweenTopLevelElements_preserved() {
		// Mimics the real template: </style>\n\n<div>
		String input = "<style>\n\t.a { color: red; }\n</style>\n\n<div>\n\t<p>content</p>\n</div>";
		String result = format(input);
		assertTrue("Blank line between </style> and <div> should be preserved: " + result,
			result.contains("</style>\n\n"));
	}

	@Test
	public void doubleBlankLineBetweenTopLevelElements_preserved() {
		// Mimics the real template: </div>\n\n\n<div>
		String input = "<div>\n\t<p>one</p>\n</div>\n\n\n<div>\n\t<p>two</p>\n</div>";
		String result = format(input);
		assertTrue("Double blank line between top-level elements should be preserved: " + result,
			result.contains("</div>\n\n\n"));
	}

	@Test
	public void blankLineBetweenDivAndScript_preserved() {
		// Mimics the real template: </div>\n\n<script>
		String input = "<div>\n\t<p>content</p>\n</div>\n\n<script src=\"app.js\"></script>";
		String result = format(input);
		assertTrue("Blank line between </div> and <script> should be preserved: " + result,
			result.contains("</div>\n\n"));
	}

	@Test
	public void blankLineBetweenScripts_preserved() {
		// Mimics the real template: </script>\n\n<script>
		String input = "<script src=\"a.js\"></script>\n\n<script src=\"b.js\"></script>";
		String result = format(input);
		assertTrue("Blank line between scripts should be preserved: " + result,
			result.contains("</script>\n\n"));
	}

	@Test
	public void realTemplatePattern_blankLinesPreserved() {
		// This mimics the actual template structure where top-level elements
		// (children of the document root) are separated by blank lines.
		String input =
			"<style>\n" +
			"\t.a { color: red; }\n" +
			"</style>\n" +
			"\n" +
			"<div class=\"row\">\n" +
			"\t<div class=\"col\">\n" +
			"\t\t<h1>title</h1>\n" +
			"\t</div>\n" +
			"</div>\n" +
			"\n" +
			"\n" +
			"<div class=\"row\">\n" +
			"\t<div class=\"col\">\n" +
			"\t\t<p>content</p>\n" +
			"\t</div>\n" +
			"</div>\n" +
			"\n" +
			"<script src=\"lib.js\"></script>\n" +
			"\n" +
			"<script>\n" +
			"\tvar x = 'hello';\n" +
			"</script>";
		String result = format(input);
		// With FormatRefactoring-style rendering, top-level elements have
		// no extra indent from a <document> wrapper.
		assertTrue("Blank line between </style> and first <div> should be preserved: " + result,
			result.contains("</style>\n\n"));
		assertTrue("Double blank line between divs should be preserved: " + result,
			result.contains("</div>\n\n\n"));
		assertTrue("Blank line between </div> and <script> should be preserved: " + result,
			result.contains("</div>\n\n<script src"));
		assertTrue("Blank line between </script> and <script> should be preserved: " + result,
			result.contains("</script>\n\n<script"));
	}

	@Test
	public void realTemplate_blankLinesCountPreserved() {
		// The actual template file has blank lines at specific locations.
		// Read the template and verify they survive formatting.
		String input =
			"<style>\n" +
			"\t.price-up { background-image: url(up.png); }\n" +
			"</style>\n" +
			"\n" +
			"<div class=\"row\">\n" +
			"\t<div class=\"col-md-12\">\n" +
			"\t\t<h1>title</h1>\n" +
			"\t</div>\n" +
			"</div>\n" +
			"\n" +
			"\n" +
			"<div class=\"row\">\n" +
			"\t<div class=\"col-md-8\">\n" +
			"\t\t<div class=\"row\">\n" +
			"\t\t\t<div class=\"col mb-3\">\n" +
			"\t\t\t\t<p>content</p>\n" +
			"\t\t\t</div>\n" +
			"\t\t</div>\n" +
			"\t</div>\n" +
			"\t<div class=\"col-md-4\">\n" +
			"\t\t<div class=\"row\">\n" +
			"\t\t\t<div class=\"col mb-3\">\n" +
			"\t\t\t\t<p>sidebar</p>\n" +
			"\t\t\t</div>\n" +
			"\t\t</div>\n" +
			"\t</div>\n" +
			"</div>\n" +
			"\n" +
			"<script src=\"https://cdn.example.com/lib.js\"></script>\n" +
			"\n" +
			"<script>\n" +
			"\tvar x = 'hello';\n" +
			"</script>";
		String result = format(input);

		// Count blank lines (consecutive \n\n) in both input and output.
		// The formatter should preserve all blank lines from the original.
		int inputBlankLines = countOccurrences(input, "\n\n");
		int resultBlankLines = countOccurrences(result, "\n\n");
		assertEquals("Number of blank line groups should be preserved",
			inputBlankLines, resultBlankLines);
	}

	/** Counts non-overlapping occurrences of substring in string. */
	private int countOccurrences(String str, String sub) {
		int count = 0;
		int idx = 0;
		while ((idx = str.indexOf(sub, idx)) != -1) {
			count++;
			idx += sub.length();
		}
		return count;
	}

	@Test
	public void blankLineInsideNestedStructure_preserved() {
		// Blank line between sibling div.row blocks that are INSIDE a
		// parent div — this is a common pattern in Bootstrap layouts.
		String input =
			"<div class=\"col-md-8\">\n" +
			"\t<div class=\"row\">\n" +
			"\t\t<div class=\"col\">\n" +
			"\t\t\t<p>first</p>\n" +
			"\t\t</div>\n" +
			"\t</div>\n" +
			"\n" +
			"\t<div class=\"row\">\n" +
			"\t\t<div class=\"col\">\n" +
			"\t\t\t<p>second</p>\n" +
			"\t\t</div>\n" +
			"\t</div>\n" +
			"</div>";
		String result = format(input);
		assertTrue("Blank line between sibling .row divs should be preserved: " + result,
			result.contains("</div>\n\n"));
	}

	@Test
	public void blankLineWithIndentBetweenElements_preserved() {
		// A blank line that contains only whitespace (tabs) — this is common
		// when authors leave a blank line between blocks with indentation.
		// The whitespace text node is "\n\t\t\t\n\t\t\t" (newline, indent,
		// blank line, indent) — it has 2 newlines.
		String input =
			"<div>\n" +
			"\t<div class=\"row\">\n" +
			"\t\t<p>first</p>\n" +
			"\t</div>\n" +
			"\t\n" +
			"\t<div class=\"row\">\n" +
			"\t\t<p>second</p>\n" +
			"\t</div>\n" +
			"</div>";
		String result = format(input);
		assertTrue("Blank line with indent between elements should be preserved: " + result,
			result.contains("</div>\n\n"));
	}

	@Test
	public void blankLineBeforeWoIf_preserved() {
		// Blank line before a wo:if block inside a parent element.
		String input =
			"<div>\n" +
			"\t<div class=\"row\">\n" +
			"\t\t<p>content</p>\n" +
			"\t</div>\n" +
			"\n" +
			"\t<wo:if condition=\"$show\">\n" +
			"\t\t<div class=\"row\">\n" +
			"\t\t\t<p>conditional</p>\n" +
			"\t\t</div>\n" +
			"\t</wo:if>\n" +
			"</div>";
		String result = format(input);
		assertTrue("Blank line before wo:if should be preserved: " + result,
			result.contains("</div>\n\n"));
	}

	// --- Doctype preservation ---

	@Test
	public void lowercaseDoctype_namePreserved() {
		// <!doctype html> is the standard HTML5 doctype — the parser must
		// capture "html" as the name even though "doctype" is lowercase.
		String input = "<!doctype html>\n<div>content</div>";
		FuzzyXMLDocument doc = new FuzzyXMLParser(false, true).parse(input);
		assertNotNull("Doctype should be parsed", doc.getDocumentType());
		assertEquals("Doctype name should be 'html'", "html", doc.getDocumentType().getName());
	}

	@Test
	public void lowercaseDoctype_rendersCorrectly() {
		String input = "<!doctype html>\n<div>content</div>";
		FuzzyXMLDocument doc = new FuzzyXMLParser(false, true).parse(input);
		RenderContext ctx = new RenderContext(true);
		ctx.setDelegate(new WOHTMLRenderDelegate());
		StringBuffer buf = new StringBuffer();
		doc.getDocumentType().toXMLString(ctx, buf);
		assertTrue("Rendered doctype should contain 'html': " + buf,
			buf.toString().contains("DOCTYPE html"));
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

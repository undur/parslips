package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import org.junit.Test;

import jp.aonir.fuzzyxml.internal.FuzzyXMLUtil;

/**
 * Tests for {@link FuzzyXMLUtil}, focusing on the {@code pBlock2space()}
 * preprocessing step that blanks out content inside {@code <p:raw>} and
 * {@code <p:comment>} blocks before the parser's other preprocessing passes.
 */
public class FuzzyXMLUtilTest {

	// --- pBlock2space: basic behavior ---

	@Test
	public void pBlock2space_blanksRawContent() {
		String input = "<p:raw>some content</p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("<p:raw>            </p:raw>", result);
	}

	@Test
	public void pBlock2space_blanksCommentContent() {
		String input = "<p:comment>some content</p:comment>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("<p:comment>            </p:comment>", result);
	}

	@Test
	public void pBlock2space_preservesLengths() {
		String input = "<p:raw>hello world</p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("Length must be preserved", input.length(), result.length());
	}

	// --- pBlock2space: dangerous content that would corrupt escapeString ---

	@Test
	public void pBlock2space_neutralizesUnclosedQuotes() {
		// This is the actual bug that motivated pBlock2space: an unclosed quote
		// inside a p:comment would cause escapeString to treat the rest of the
		// document as inside a quoted attribute, corrupting all subsequent parsing.
		String input = "<p:comment>broken \" quote</p:comment><body></body>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertTrue("Body tag must survive intact", result.contains("<body></body>"));
		assertFalse("Unclosed quote must be blanked", result.contains("\""));
	}

	@Test
	public void pBlock2space_neutralizesWoTags() {
		// <wo:> tags inside p:raw should not be processed
		String input = "<p:raw><wo:string value=\"$test\" /></p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertFalse("wo:string inside p:raw must be blanked", result.contains("wo:string"));
	}

	@Test
	public void pBlock2space_neutralizesBrokenHTML() {
		String input = "<p:comment><div><span></p:comment><div>real content</div>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertTrue("Content after block must survive", result.contains("<div>real content</div>"));
	}

	// --- pBlock2space: edge cases ---

	@Test
	public void pBlock2space_emptyBlock() {
		String input = "<p:raw></p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("<p:raw></p:raw>", result);
	}

	@Test
	public void pBlock2space_noBlocks() {
		String input = "<div>hello</div>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("No p: blocks means no changes", input, result);
	}

	@Test
	public void pBlock2space_noCloseTag() {
		// When there's no close tag, content should pass through unchanged
		String input = "<p:raw>unclosed content";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals(input, result);
	}

	@Test
	public void pBlock2space_multipleBlocks() {
		String input = "<p:raw>aaa</p:raw> middle <p:comment>bbb</p:comment>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertEquals("Middle text must survive", true, result.contains(" middle "));
		assertFalse("First block content must be blanked", result.contains("aaa"));
		assertFalse("Second block content must be blanked", result.contains("bbb"));
		assertEquals("Length must be preserved", input.length(), result.length());
	}

	@Test
	public void pBlock2space_blockAtDocumentEnd() {
		String input = "<div>hello</div><p:raw>tail content</p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertTrue("Leading content must survive", result.contains("<div>hello</div>"));
		assertFalse("Tail content must be blanked", result.contains("tail content"));
	}

	@Test
	public void pBlock2space_blockAtDocumentStart() {
		String input = "<p:comment>head</p:comment><div>hello</div>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertTrue("Trailing content must survive", result.contains("<div>hello</div>"));
	}

	// --- pBlock2space: case insensitivity ---

	@Test
	public void pBlock2space_caseInsensitiveOpenTag() {
		String input = "<P:RAW>content</P:RAW>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertFalse("Content must be blanked regardless of case", result.contains("content"));
		assertEquals("Length must be preserved", input.length(), result.length());
	}

	@Test
	public void pBlock2space_mixedCaseCloseTag() {
		String input = "<p:raw>content</P:Raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertFalse("Mixed case close tag must still match", result.contains("content"));
	}

	// --- pBlock2space: attributes on open tag ---

	@Test
	public void pBlock2space_openTagWithAttributes() {
		String input = "<p:raw class=\"example\">content</p:raw>";
		String result = FuzzyXMLUtil.pBlock2space(input);
		assertFalse("Content after attributed open tag must be blanked", result.contains("content"));
		assertEquals("Length must be preserved", input.length(), result.length());
	}
}

package org.objectstyle.wolips.wodclipse.core.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for the pure-functional methods in {@link WodHtmlUtils}: tag detection,
 * inline binding prefix checks, parser directive recognition, line number
 * calculation, and binding value parsing.
 *
 * <p>Methods that require Eclipse resources (IFile, WodParserCache, FuzzyXMLElement)
 * are not tested here.
 */
public class WodHtmlUtilsTest {

	// ---- isInline(String) ---------------------------------------------------

	@Test
	public void isInline_woColon_true() {
		assertTrue(WodHtmlUtils.isInline("wo:WOString"));
	}

	@Test
	public void isInline_woCaseInsensitive() {
		assertTrue(WodHtmlUtils.isInline("WO:WOString"));
		assertTrue(WodHtmlUtils.isInline("Wo:WOString"));
	}

	@Test
	public void isInline_woColonOnly_true() {
		assertTrue(WodHtmlUtils.isInline("wo:"));
	}

	@Test
	public void isInline_plainTag_false() {
		assertFalse(WodHtmlUtils.isInline("div"));
	}

	@Test
	public void isInline_webobjectTag_false() {
		assertFalse(WodHtmlUtils.isInline("webobject"));
	}

	@Test
	public void isInline_null_false() {
		assertFalse(WodHtmlUtils.isInline((String) null));
	}

	@Test
	public void isInline_empty_false() {
		assertFalse(WodHtmlUtils.isInline(""));
	}

	// ---- isWOTag(String) ----------------------------------------------------

	@Test
	public void isWOTag_webobject_true() {
		assertTrue(WodHtmlUtils.isWOTag("webobject"));
	}

	@Test
	public void isWOTag_webobjects_true() {
		assertTrue(WodHtmlUtils.isWOTag("webobjects"));
	}

	@Test
	public void isWOTag_webobject_caseInsensitive() {
		assertTrue(WodHtmlUtils.isWOTag("WebObject"));
		assertTrue(WodHtmlUtils.isWOTag("WEBOBJECT"));
	}

	@Test
	public void isWOTag_woExact_true() {
		assertTrue(WodHtmlUtils.isWOTag("wo"));
	}

	@Test
	public void isWOTag_woWithSpace_true() {
		assertTrue(WodHtmlUtils.isWOTag("wo name=\"Foo\""));
	}

	@Test
	public void isWOTag_woColon_true() {
		assertTrue(WodHtmlUtils.isWOTag("wo:WOString"));
	}

	@Test
	public void isWOTag_withWhitespacePadding() {
		assertTrue(WodHtmlUtils.isWOTag("  wo  "));
	}

	@Test
	public void isWOTag_plainTag_false() {
		assertFalse(WodHtmlUtils.isWOTag("div"));
	}

	@Test
	public void isWOTag_word_starting_with_wo_false() {
		// "word" starts with "wo" but is not "wo " or "wo:" — should be false
		assertFalse(WodHtmlUtils.isWOTag("word"));
	}

	@Test
	public void isWOTag_null_false() {
		assertFalse(WodHtmlUtils.isWOTag((String) null));
	}

	@Test
	public void isWOTag_empty_false() {
		assertFalse(WodHtmlUtils.isWOTag(""));
	}

	// ---- isParserDirective(String) ------------------------------------------

	@Test
	public void isParserDirective_pRaw_true() {
		assertTrue(WodHtmlUtils.isParserDirective("p:raw"));
	}

	@Test
	public void isParserDirective_pComment_true() {
		assertTrue(WodHtmlUtils.isParserDirective("p:comment"));
	}

	@Test
	public void isParserDirective_caseInsensitive() {
		assertTrue(WodHtmlUtils.isParserDirective("P:RAW"));
		assertTrue(WodHtmlUtils.isParserDirective("P:Comment"));
	}

	@Test
	public void isParserDirective_withTrailingSpace_true() {
		assertTrue(WodHtmlUtils.isParserDirective("p:raw something"));
		assertTrue(WodHtmlUtils.isParserDirective("p:comment something"));
	}

	@Test
	public void isParserDirective_withLeadingWhitespace_true() {
		assertTrue(WodHtmlUtils.isParserDirective("  p:raw"));
		assertTrue(WodHtmlUtils.isParserDirective("\tp:comment"));
	}

	@Test
	public void isParserDirective_otherTag_false() {
		assertFalse(WodHtmlUtils.isParserDirective("div"));
		assertFalse(WodHtmlUtils.isParserDirective("wo:WOString"));
	}

	@Test
	public void isParserDirective_null_false() {
		assertFalse(WodHtmlUtils.isParserDirective(null));
	}

	@Test
	public void isParserDirective_pOther_false() {
		assertFalse(WodHtmlUtils.isParserDirective("p:other"));
	}

	// ---- getLineAtOffset ----------------------------------------------------

	@Test
	public void getLineAtOffset_firstLine() {
		assertEquals(1, WodHtmlUtils.getLineAtOffset("hello world", 0));
	}

	@Test
	public void getLineAtOffset_endOfFirstLine() {
		assertEquals(1, WodHtmlUtils.getLineAtOffset("hello\nworld", 4));
	}

	@Test
	public void getLineAtOffset_afterFirstNewline() {
		// offset 5 is the '\n', offset 6 is 'w' on line 2
		assertEquals(2, WodHtmlUtils.getLineAtOffset("hello\nworld", 6));
	}

	@Test
	public void getLineAtOffset_multipleLines() {
		String text = "line1\nline2\nline3";
		// 'l' of line3 is at offset 12
		assertEquals(3, WodHtmlUtils.getLineAtOffset(text, 12));
	}

	@Test
	public void getLineAtOffset_atNewline() {
		// The newline char itself: offset 5 is '\n'
		// The method iterates to offset+1=6, counting '\n' at 5 → line 2
		assertEquals(2, WodHtmlUtils.getLineAtOffset("hello\nworld", 5));
	}

	// ---- toBindingValue -----------------------------------------------------

	@Test
	public void toBindingValue_dynamicBinding() {
		WodHtmlUtils.BindingValue bv = WodHtmlUtils.toBindingValue("$session.user", "$", "");
		assertEquals("session.user", bv.getValue());
		assertFalse(bv.isLiteral());
		assertNull(bv.getValueNamespace());
	}

	@Test
	public void toBindingValue_literalValue() {
		WodHtmlUtils.BindingValue bv = WodHtmlUtils.toBindingValue("hello world", "$", "");
		assertEquals("\"hello world\"", bv.getValue());
		assertTrue(bv.isLiteral());
	}

	@Test
	public void toBindingValue_withNamespace() {
		WodHtmlUtils.BindingValue bv = WodHtmlUtils.toBindingValue("$ognl:expression", "$", "");
		assertEquals("expression", bv.getValue());
		assertEquals("ognl", bv.getValueNamespace());
		assertFalse(bv.isLiteral());
	}

	@Test
	public void toBindingValue_withPrefixAndSuffix() {
		WodHtmlUtils.BindingValue bv = WodHtmlUtils.toBindingValue("${session.user}", "${", "}");
		assertEquals("session.user", bv.getValue());
		assertFalse(bv.isLiteral());
	}

	@Test
	public void toBindingValue_prefixNotMatching_treatedAsLiteral() {
		WodHtmlUtils.BindingValue bv = WodHtmlUtils.toBindingValue("plainValue", "${", "}");
		assertEquals("\"plainValue\"", bv.getValue());
		assertTrue(bv.isLiteral());
	}

	// ---- WEBOBJECTS_PATTERN -------------------------------------------------

	@Test
	public void webobjectsPattern_matchesWebobjectTag() {
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<webobject name=\"Foo\">").find());
	}

	@Test
	public void webobjectsPattern_matchesWOTag() {
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<wo name=\"Bar\">").find());
	}

	@Test
	public void webobjectsPattern_caseInsensitive() {
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<WebObject name=\"Foo\">").find());
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<WO name=\"Bar\">").find());
	}

	@Test
	public void webobjectsPattern_selfClosing() {
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<webobject name=\"Foo\" />").find());
	}

	@Test
	public void webobjectsPattern_noQuotes() {
		assertTrue(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<webobject name=Foo>").find());
	}

	@Test
	public void webobjectsPattern_doesNotMatchRegularTag() {
		assertFalse(WodHtmlUtils.WEBOBJECTS_PATTERN.matcher("<div name=\"Foo\">").find());
	}

	// ---- segmentIndexAt(String, int) ---------------------------------------
	// Keypath "activeUser.address.name": positions 0-9 activeUser, 10='.',
	// 11-17 address, 18='.', 19-22 name.

	@Test
	public void segmentIndexAt_firstSegment() {
		assertEquals(0, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 0));
		assertEquals(0, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 5));
		assertEquals(0, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 9));
	}

	@Test
	public void segmentIndexAt_middleSegment() {
		assertEquals(1, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 11));
		assertEquals(1, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 17));
	}

	@Test
	public void segmentIndexAt_lastSegment() {
		assertEquals(2, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 19));
		assertEquals(2, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 22));
	}

	@Test
	public void segmentIndexAt_dotBelongsToPrecedingSegment() {
		// The caret sitting on the '.' after activeUser resolves to activeUser (index 0),
		// not address — the separator counts toward the segment it follows.
		assertEquals(0, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 10));
	}

	@Test
	public void segmentIndexAt_offsetPastEnd_clampsToLast() {
		assertEquals(2, WodHtmlUtils.segmentIndexAt("activeUser.address.name", 999));
	}

	@Test
	public void segmentIndexAt_negativeOffset_firstSegment() {
		assertEquals(0, WodHtmlUtils.segmentIndexAt("activeUser.address.name", -5));
	}

	@Test
	public void segmentIndexAt_singleSegment() {
		assertEquals(0, WodHtmlUtils.segmentIndexAt("name", 0));
		assertEquals(0, WodHtmlUtils.segmentIndexAt("name", 4));
	}

	@Test
	public void segmentIndexAt_nullOrEmpty() {
		assertEquals(0, WodHtmlUtils.segmentIndexAt(null, 5));
		assertEquals(0, WodHtmlUtils.segmentIndexAt("", 5));
	}

	@Test
	public void segmentIndexAt_operatorKeyPath() {
		// "@count.foo" — the leading '@' is part of segment 0's text and doesn't shift indexing.
		assertEquals(0, WodHtmlUtils.segmentIndexAt("@count.foo", 3));
		assertEquals(1, WodHtmlUtils.segmentIndexAt("@count.foo", 7));
	}

	// ---- segmentSpan(String, int) ------------------------------------------
	// "activeUser.address.name": activeUser [0,10], address [11,7], name [19,4].

	@Test
	public void segmentSpan_firstSegment() {
		assertArrayEquals(new int[] { 0, 10 }, WodHtmlUtils.segmentSpan("activeUser.address.name", 0));
	}

	@Test
	public void segmentSpan_middleSegment() {
		assertArrayEquals(new int[] { 11, 7 }, WodHtmlUtils.segmentSpan("activeUser.address.name", 1));
	}

	@Test
	public void segmentSpan_lastSegment() {
		assertArrayEquals(new int[] { 19, 4 }, WodHtmlUtils.segmentSpan("activeUser.address.name", 2));
	}

	@Test
	public void segmentSpan_negativeIndex_lastSegment() {
		// -1 (the whole-path default) resolves to the terminal segment's span.
		assertArrayEquals(new int[] { 19, 4 }, WodHtmlUtils.segmentSpan("activeUser.address.name", -1));
	}

	@Test
	public void segmentSpan_pastEnd_lastSegment() {
		assertArrayEquals(new int[] { 19, 4 }, WodHtmlUtils.segmentSpan("activeUser.address.name", 99));
	}

	@Test
	public void segmentSpan_singleSegment() {
		assertArrayEquals(new int[] { 0, 4 }, WodHtmlUtils.segmentSpan("name", 0));
		assertArrayEquals(new int[] { 0, 4 }, WodHtmlUtils.segmentSpan("name", -1));
	}

	@Test
	public void segmentSpan_roundTripsWithSegmentIndexAt() {
		// The span of the segment segmentIndexAt reports for an offset must contain that offset.
		String kp = "activeUser.address.name";
		for (int off = 0; off < kp.length(); off++) {
			int idx = WodHtmlUtils.segmentIndexAt(kp, off);
			int[] span = WodHtmlUtils.segmentSpan(kp, idx);
			assertTrue("offset " + off + " should fall within its segment span",
					off >= span[0] && off <= span[0] + span[1]);
		}
	}
}

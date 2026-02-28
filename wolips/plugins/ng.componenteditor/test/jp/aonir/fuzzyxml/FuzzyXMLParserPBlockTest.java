package jp.aonir.fuzzyxml;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link FuzzyXMLParser} handling of {@code <p:raw>} and
 * {@code <p:comment>} blocks. Verifies DOM structure, child handling,
 * and close-tag offset tracking used by linked rename (Cmd+2, R).
 *
 * <p>These tests exercise the full parse pipeline — preprocessing
 * ({@code pBlock2space}) through to DOM construction — ensuring the
 * blocks are correctly isolated from the rest of the document.
 */
public class FuzzyXMLParserPBlockTest {

	/**
	 * Helper: parse source as non-well-formed HTML (matching production
	 * usage) and return the document element's children.
	 */
	private FuzzyXMLNode[] parse(String source) {
		FuzzyXMLParser parser = new FuzzyXMLParser(false, true);
		FuzzyXMLDocument doc = parser.parse(source);
		return doc.getDocumentElement().getChildren();
	}

	/**
	 * Helper: find the first element with the given name among the
	 * document's children (non-recursive, top-level only).
	 */
	private FuzzyXMLElement findElement(FuzzyXMLNode[] nodes, String name) {
		for (FuzzyXMLNode node : nodes) {
			if (node instanceof FuzzyXMLElement) {
				FuzzyXMLElement el = (FuzzyXMLElement) node;
				if (el.getName().equalsIgnoreCase(name)) {
					return el;
				}
			}
		}
		return null;
	}

	// =========================================================================
	// p:raw — DOM structure
	// =========================================================================

	@Test
	public void pRaw_producesElementNode() {
		FuzzyXMLNode[] nodes = parse("<p:raw>content</p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertNotNull("p:raw must produce an element in the DOM", raw);
	}

	@Test
	public void pRaw_hasTextChild() {
		FuzzyXMLNode[] nodes = parse("<p:raw>content</p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		FuzzyXMLNode[] children = raw.getChildren();
		// Children include attributes + content nodes. Filter to non-attribute children.
		int nonAttrChildren = 0;
		for (FuzzyXMLNode child : children) {
			if (!(child instanceof FuzzyXMLAttribute)) {
				nonAttrChildren++;
			}
		}
		assertEquals("p:raw must have exactly one text child", 1, nonAttrChildren);
	}

	@Test
	public void pRaw_woTagsNotParsed() {
		// wo: tags inside p:raw must NOT be parsed as elements
		FuzzyXMLNode[] nodes = parse("<p:raw><wo:string value=\"$test\" /></p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		for (FuzzyXMLNode child : raw.getChildren()) {
			if (child instanceof FuzzyXMLElement && !(child instanceof FuzzyXMLAttribute)) {
				fail("wo: tags inside p:raw must not be parsed as elements, found: " + ((FuzzyXMLElement) child).getName());
			}
		}
	}

	@Test
	public void pRaw_emptyBlock() {
		FuzzyXMLNode[] nodes = parse("<p:raw></p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertNotNull("Empty p:raw must still produce an element", raw);
	}

	// =========================================================================
	// p:comment — DOM structure
	// =========================================================================

	@Test
	public void pComment_producesElementNode() {
		FuzzyXMLNode[] nodes = parse("<p:comment>content</p:comment>");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertNotNull("p:comment must produce an element in the DOM", comment);
	}

	@Test
	public void pComment_hasNoContentChildren() {
		FuzzyXMLNode[] nodes = parse("<p:comment>content</p:comment>");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		int nonAttrChildren = 0;
		for (FuzzyXMLNode child : comment.getChildren()) {
			if (!(child instanceof FuzzyXMLAttribute)) {
				nonAttrChildren++;
			}
		}
		assertEquals("p:comment must have no content children", 0, nonAttrChildren);
	}

	@Test
	public void pComment_emptyBlock() {
		FuzzyXMLNode[] nodes = parse("<p:comment></p:comment>");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertNotNull("Empty p:comment must still produce an element", comment);
	}

	// =========================================================================
	// Close tag offsets (linked rename correctness)
	// =========================================================================

	@Test
	public void pRaw_hasCloseTag() {
		FuzzyXMLNode[] nodes = parse("<p:raw>content</p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertTrue("p:raw must have a close tag", raw.hasCloseTag());
	}

	@Test
	public void pComment_hasCloseTag() {
		FuzzyXMLNode[] nodes = parse("<p:comment>content</p:comment>");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertTrue("p:comment must have a close tag", comment.hasCloseTag());
	}

	@Test
	public void pRaw_closeTagOffset_pointsToClosingBracket() {
		String source = "<p:raw>content</p:raw>";
		//                0123456789012345678901
		//                          1111111111222
		// Close tag starts at position 14: </p:raw>
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertEquals("Close tag offset must point to '<' of </p:raw>", 14, raw.getCloseTagOffset());
	}

	@Test
	public void pComment_closeTagOffset() {
		String source = "<p:comment>content</p:comment>";
		//                0123456789012345678901234567890
		//                          1111111111222222222
		// Close tag starts at position 18: </p:comment>
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertEquals("Close tag offset must point to '<' of </p:comment>", 18, comment.getCloseTagOffset());
	}

	/**
	 * Verify the close name offset follows the "after the &lt;" convention
	 * used by {@code handleCloseTag}. This is critical for linked rename:
	 * {@code QuickRenameRefactoring.renameHtmlTag} computes the close tag
	 * name position as {@code closeTagOffset + closeNameOffset + 1}.
	 *
	 * <p>For {@code </p:raw>}: text after '{@code <}' is '{@code /p:raw>}',
	 * so '{@code p:raw}' starts at index 1 — closeNameOffset must be 1.
	 */
	@Test
	public void pRaw_closeNameOffset_matchesHandleCloseTagConvention() {
		FuzzyXMLNode[] nodes = parse("<p:raw>content</p:raw>");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		// In "</p:raw>", after removing '<': "/p:raw>"
		// "p:raw" starts at index 1
		assertEquals("closeNameOffset must be 1 (index of name after '<')", 1, raw.getCloseNameOffset());
	}

	@Test
	public void pComment_closeNameOffset_matchesHandleCloseTagConvention() {
		FuzzyXMLNode[] nodes = parse("<p:comment>content</p:comment>");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertEquals("closeNameOffset must be 1 (index of name after '<')", 1, comment.getCloseNameOffset());
	}

	/**
	 * End-to-end test of the linked rename offset arithmetic. This replicates
	 * what {@code QuickRenameRefactoring.renameHtmlTag} does:
	 * {@code closeTagOffset + closeNameOffset + 1} gives the absolute
	 * position of the tag name in the close tag.
	 */
	@Test
	public void pRaw_linkedRenamePosition_extractsCorrectName() {
		String source = "<p:raw>content</p:raw>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement raw = findElement(nodes, "p:raw");

		// The rename refactoring computes: closeTagOffset + closeNameOffset + 1
		int nameStart = raw.getCloseTagOffset() + raw.getCloseNameOffset() + 1;
		int nameEnd = nameStart + raw.getCloseNameLength();
		String extracted = source.substring(nameStart, nameEnd);
		assertEquals("Linked rename must extract the correct tag name", "p:raw", extracted);
	}

	@Test
	public void pComment_linkedRenamePosition_extractsCorrectName() {
		String source = "<p:comment>content</p:comment>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement comment = findElement(nodes, "p:comment");

		int nameStart = comment.getCloseTagOffset() + comment.getCloseNameOffset() + 1;
		int nameEnd = nameStart + comment.getCloseNameLength();
		String extracted = source.substring(nameStart, nameEnd);
		assertEquals("Linked rename must extract the correct tag name", "p:comment", extracted);
	}

	// =========================================================================
	// Document isolation — broken content in blocks must not affect siblings
	// =========================================================================

	@Test
	public void pComment_brokenContentDoesNotAffectSiblings() {
		// Unclosed quotes inside p:comment must not corrupt surrounding elements
		String source = "<p:comment>broken \" quote</p:comment><body></body>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement body = findElement(nodes, "body");
		assertNotNull("body element must survive broken content in p:comment", body);
	}

	@Test
	public void pComment_unclosedTagsDoNotAffectSiblings() {
		String source = "<p:comment><div><span></p:comment><div>ok</div>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement div = findElement(nodes, "div");
		assertNotNull("div after p:comment must be parsed correctly", div);
	}

	@Test
	public void pRaw_contentDoesNotCreateSiblingErrors() {
		String source = "<p:raw><wo:if condition=\"$bad\"></p:raw><div></div>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement div = findElement(nodes, "div");
		assertNotNull("div after p:raw must be parsed correctly", div);
	}

	// =========================================================================
	// Element span — offset + length covers the entire block
	// =========================================================================

	@Test
	public void pRaw_elementSpansEntireBlock() {
		String source = "<p:raw>content</p:raw>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertEquals("Element must start at 0", 0, raw.getOffset());
		assertEquals("Element must span entire source", source.length(), raw.getLength());
	}

	@Test
	public void pComment_elementSpansEntireBlock() {
		String source = "<p:comment>content</p:comment>";
		FuzzyXMLNode[] nodes = parse(source);
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertEquals("Element must start at 0", 0, comment.getOffset());
		assertEquals("Element must span entire source", source.length(), comment.getLength());
	}

	// =========================================================================
	// Missing close tags
	// =========================================================================

	@Test
	public void pRaw_noCloseTag_stillProducesElement() {
		FuzzyXMLNode[] nodes = parse("<p:raw>unclosed");
		FuzzyXMLElement raw = findElement(nodes, "p:raw");
		assertNotNull("p:raw without close tag must still produce an element", raw);
	}

	@Test
	public void pComment_noCloseTag_stillProducesElement() {
		FuzzyXMLNode[] nodes = parse("<p:comment>unclosed");
		FuzzyXMLElement comment = findElement(nodes, "p:comment");
		assertNotNull("p:comment without close tag must still produce an element", comment);
	}
}

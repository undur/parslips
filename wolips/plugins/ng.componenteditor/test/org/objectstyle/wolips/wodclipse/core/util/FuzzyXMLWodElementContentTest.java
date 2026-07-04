package org.objectstyle.wolips.wodclipse.core.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jp.aonir.fuzzyxml.FuzzyXMLDocument;
import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.FuzzyXMLParser;

/**
 * Tests {@link FuzzyXMLWodElement#hasContent()} — the "does this inline tag have content" signal used
 * to enforce an {@code .apiext} {@code content="forbidden"} policy. Content means an explicit close tag
 * ({@code <wo:x></wo:x>}, even empty), not a self-closed {@code <wo:x/>}. Built with a null project,
 * which the constructor tolerates (it only affects the binding-prefix, not content detection).
 */
public class FuzzyXMLWodElementContentTest {

	/** Parses a single {@code <wo:…>} element out of a fragment and wraps it. */
	private static FuzzyXMLWodElement woElement(String fragment) {
		final FuzzyXMLDocument doc = new FuzzyXMLParser(false).parse(fragment);
		final FuzzyXMLElement el = firstWo(doc.getDocumentElement());
		return new FuzzyXMLWodElement(el, null);
	}

	private static FuzzyXMLElement firstWo(FuzzyXMLElement el) {
		if (el != null && el.getName() != null && el.getName().startsWith("wo:")) {
			return el;
		}
		if (el != null) {
			for (final jp.aonir.fuzzyxml.FuzzyXMLNode child : el.getChildren()) {
				if (child instanceof FuzzyXMLElement) {
					final FuzzyXMLElement found = firstWo((FuzzyXMLElement) child);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null;
	}

	@Test
	public void selfClosed_hasNoContent() {
		assertFalse(woElement("<div><wo:str value=\"$x\"/></div>").hasContent());
	}

	@Test
	public void emptyExplicitTag_hasContent() {
		// The spec is strict: <wo:x></wo:x> counts as content even though it's empty.
		assertTrue(woElement("<div><wo:str value=\"$x\"></wo:str></div>").hasContent());
	}

	@Test
	public void nonEmptyTag_hasContent() {
		assertTrue(woElement("<div><wo:if condition=\"$c\">hello</wo:if></div>").hasContent());
	}
}

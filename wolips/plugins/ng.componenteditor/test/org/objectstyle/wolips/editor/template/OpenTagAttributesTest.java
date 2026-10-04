package org.objectstyle.wolips.editor.template;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

/**
 * Reading the attributes of the tag being typed: unclosed, the caret anywhere in it, quoted
 * values that contain tag characters.
 */
public class OpenTagAttributesTest {

	private static Map<String, String> at(String textWithCaret) {
		final int offset = textWithCaret.indexOf('|');
		return OpenTagAttributes.at(textWithCaret.replace("|", ""), offset);
	}

	@Test
	public void unclosedTag() {
		assertEquals(Map.of("to", "$routes.book"), at("<p><wo:route to=\"$routes.book\" :|"));
	}

	@Test
	public void caretBeforeTheTo() {
		final Map<String, String> attributes = at("<wo:route :| to=\"$routes.book\" :club=\"$club\">Book</wo:route>");
		assertEquals("$routes.book", attributes.get("to"));
		assertEquals("$club", attributes.get(":club"));
	}

	@Test
	public void quotedValuesMayHoldTagCharacters() {
		assertEquals("a > b", at("<wo:route to=\"$routes.x\" title=\"a > b\" |").get("title"));
	}

	@Test
	public void outsideATag() {
		assertTrue(at("<wo:route to=\"$routes.x\">text |").isEmpty());
		assertNull(OpenTagAttributes.tagAt("no tags here", 3));
	}
}

package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/** The patterns {@code /context} finds a template's elements with. */
public class ContextHandlerTest {

	private static List<String> matches(Pattern pattern, String text) {
		final List<String> found = new ArrayList<>();
		final Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void inlineTags() {
		assertEquals(List.of("str", "AjaxUpdateContainer", "if"),
				matches(ContextHandler.INLINE_TAG, "<div><wo:str value=\"$x\"/><wo:AjaxUpdateContainer id=\"a\"><wo:if condition=\"$y\"></wo:if></wo:AjaxUpdateContainer></div>"));
	}

	@Test
	public void closingTagsAreNotUses() {
		assertEquals(List.of("if"), matches(ContextHandler.INLINE_TAG, "<wo:if condition=\"$y\">x</wo:if>"));
	}

	@Test
	public void wodDeclarations() {
		assertEquals(List.of("WOString", "er.extensions.ERXHyperlink"),
				matches(ContextHandler.WOD_DECLARATION, "Name : WOString {\n\tvalue = name;\n}\n\nLink: er.extensions.ERXHyperlink{ action = go; }\n"));
	}
}

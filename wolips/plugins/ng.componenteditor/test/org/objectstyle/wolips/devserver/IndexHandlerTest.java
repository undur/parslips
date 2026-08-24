package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

/**
 * Guards the {@code /} index — the dev server's self-describing endpoint list, which a cold-landing
 * tool parses to discover the API. The index is a hand-maintained JSON text block in
 * {@link IndexHandler}, so it's easy to break its JSON by hand (e.g. an unescaped quote in a
 * description closing a string literal early — which happened once and this test now prevents).
 *
 * <p>There's no JSON parser on the plugin's classpath and we won't add one for a test, so this
 * uses a minimal structural well-formedness check: it tracks string-literal state and
 * brace/bracket nesting, which catches exactly the failure modes hand-editing introduces
 * (unbalanced braces/brackets, and quotes that aren't inside a string — the unescaped-quote bug).
 */
public class IndexHandlerTest {

	@Test
	public void indexIsStructurallyWellFormedJson() {
		final String index = new IndexHandler().handle(new HashMap<>());
		assertWellFormed(index);
	}

	@Test
	public void indexIsAJsonObjectOrArray() {
		final String index = new IndexHandler().handle(new HashMap<>()).strip();
		final char first = index.charAt(0);
		assertTrue("index should start with { or [", first == '{' || first == '[');
	}

	/**
	 * A tiny JSON structural validator — not a full parser, but enough to catch the hand-editing
	 * damage that actually happens to this text block. It checks:
	 * <ul>
	 *   <li>every {@code {}}/{@code []} is balanced and closed in order;</li>
	 *   <li>every string is terminated;</li>
	 *   <li>a string may only <em>open</em> right after a structural character
	 *       ({@code {} {@code [} {@code ,} {@code :}) — this is what catches the unescaped-quote bug:
	 *       an unescaped {@code "} inside a description closes the string, and the text that follows
	 *       tries to open a new string not preceded by a separator (e.g. {@code ..."ng"...} — after
	 *       {@code "ng"} closes, {@code " or "} opens a bare string mid-value).</li>
	 * </ul>
	 * That last rule is the one a naive brace-counter misses, because an even number of stray quotes
	 * keeps the braces balanced by luck.
	 */
	private static void assertWellFormed(final String json) {
		final java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
		boolean inString = false;
		boolean escaped = false;
		char lastStructural = '['; // pretend we just opened a container, so a leading string is fine

		for (int i = 0; i < json.length(); i++) {
			final char c = json.charAt(i);

			if (inString) {
				if (escaped) {
					escaped = false;
				}
				else if (c == '\\') {
					escaped = true;
				}
				else if (c == '"') {
					inString = false;
				}
				continue;
			}

			switch (c) {
			case '"':
				// A string may only begin right after a structural char. If the previous meaningful
				// token was itself the end of a string (or a value), this quote is stray/unescaped.
				assertTrue("string opens without a preceding separator at index " + i
						+ " (likely an unescaped quote inside a value)",
						lastStructural == '{' || lastStructural == '[' || lastStructural == ',' || lastStructural == ':');
				inString = true;
				lastStructural = 'S'; // a completed string value follows once it closes
				break;
			case '{':
			case '[':
				stack.push(c);
				lastStructural = c;
				break;
			case '}':
				assertTrue("unbalanced '}' at index " + i, !stack.isEmpty() && stack.pop() == '{');
				lastStructural = 'V';
				break;
			case ']':
				assertTrue("unbalanced ']' at index " + i, !stack.isEmpty() && stack.pop() == '[');
				lastStructural = 'V';
				break;
			case ',':
			case ':':
				lastStructural = c;
				break;
			case ' ':
			case '\t':
			case '\n':
			case '\r':
				break; // whitespace doesn't change the "last meaningful token"
			default:
				lastStructural = 'V'; // an unquoted value token (number/true/false/null)
				break;
			}
		}

		assertTrue("unterminated string in index JSON", !inString);
		assertEquals("unclosed { or [ in index JSON", 0, stack.size());
	}
}

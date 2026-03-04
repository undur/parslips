package org.objectstyle.wolips.wodclipse.core.refactoring;

import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.text.edits.ReplaceEdit;
import org.junit.Test;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;

/**
 * Tests for the regex-based binding key scanning in
 * {@link RenameBindingKeyProcessor}.
 *
 * <p>Since the processor's public methods require Eclipse workspace objects
 * (IFile, IProject), these tests exercise the package-visible scanning methods
 * that operate on raw strings with the same regex patterns.
 *
 * <p>Also tests the key derivation logic that strips KVC prefixes from method
 * and field names to produce binding keys.
 */
public class RenameBindingKeyProcessorTest {

	// ---- WOD binding key scanning -------------------------------------------

	@Test
	public void wod_findsSimpleKey() {
		String wod = "Title : WOString { value = title; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should find exactly one key reference", 1, edits.size());
		assertEditCovers(wod, edits.get(0), "title");
		assertEquals("heading", edits.get(0).getText());
	}

	@Test
	public void wod_findsKeyPathFirstSegment() {
		String wod = "Title : WOString { value = title.length; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should find key as first segment of key path", 1, edits.size());
		assertEditCovers(wod, edits.get(0), "title");
	}

	@Test
	public void wod_findsKeyWithHelperFunction() {
		String wod = "Title : WOString { value = title|uppercase; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should find key before helper function pipe", 1, edits.size());
		assertEditCovers(wod, edits.get(0), "title");
	}

	@Test
	public void wod_skipsStringLiteral() {
		String wod = "Title : WOString { value = \"title\"; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should not match inside string literal", 0, edits.size());
	}

	@Test
	public void wod_skipsCaretReference() {
		String wod = "Title : WOString { value = ^parent.title; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should not match caret reference", 0, edits.size());
	}

	@Test
	public void wod_doesNotMatchSubstring() {
		String wod = "Title : WOString { value = titleCase; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should not match substring 'title' in 'titleCase'", 0, edits.size());
	}

	@Test
	public void wod_doesNotRenameBindingName() {
		// "title" appears as a binding name (LHS), not as a key (RHS)
		String wod = "Title : WOString { title = something; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should not match binding name (LHS of =)", 0, edits.size());
	}

	@Test
	public void wod_findsMultipleBindingsInOneDeclaration() {
		String wod = "Comp : MyComponent { first = title; second = title.length; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should find key in both bindings", 2, edits.size());
	}

	@Test
	public void wod_findsKeyAcrossMultipleDeclarations() {
		String wod = "A : WOString { value = title; }\nB : WOString { value = title; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should find key in both declarations", 2, edits.size());
	}

	@Test
	public void wod_multilineDeclaration() {
		String wod = "Comp : WOString {\n\tvalue = title;\n\tother = name;\n}";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals(1, edits.size());
		assertEditCovers(wod, edits.get(0), "title");
	}

	@Test
	public void wod_keyFollowedBySemicolonNoSpace() {
		String wod = "Comp : WOString { value=title; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals("Should match key even without spaces around =", 1, edits.size());
	}

	@Test
	public void wod_keyFollowedByWhitespace() {
		String wod = "Comp : WOString { value = title ; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		assertEquals(1, edits.size());
		assertEditCovers(wod, edits.get(0), "title");
	}

	// ---- HTML inline binding key scanning -----------------------------------

	@Test
	public void html_findsSimpleKey() {
		String html = "<wo:str value=\"$title\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should find exactly one key reference", 1, edits.size());
		assertEditCovers(html, edits.get(0), "title");
		assertEquals("heading", edits.get(0).getText());
	}

	@Test
	public void html_findsKeyPath() {
		String html = "<wo:str value=\"$title.length\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should find key as first segment of key path", 1, edits.size());
		assertEditCovers(html, edits.get(0), "title");
	}

	@Test
	public void html_findsKeyWithSingleQuotes() {
		String html = "<wo:str value='$title' />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals(1, edits.size());
		assertEditCovers(html, edits.get(0), "title");
	}

	@Test
	public void html_skipsLiteralValue() {
		String html = "<wo:str value=\"just text\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should not match literal attribute value (no $ prefix)", 0, edits.size());
	}

	@Test
	public void html_doesNotMatchSubstring() {
		String html = "<wo:str value=\"$titleCase\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should not match substring 'title' in 'titleCase'", 0, edits.size());
	}

	@Test
	public void html_findsKeyInMultipleAttributes() {
		String html = "<wo:comp first=\"$title\" second=\"$title.length\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should find key in both attributes", 2, edits.size());
	}

	@Test
	public void html_findsKeyInMultipleTags() {
		String html = "<wo:str value=\"$title\" />\n<wo:str value=\"$title\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should find key in both tags", 2, edits.size());
	}

	@Test
	public void html_keyWithHelperFunction() {
		String html = "<wo:str value=\"$title|uppercase\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should find key before helper function pipe", 1, edits.size());
		assertEditCovers(html, edits.get(0), "title");
	}

	@Test
	public void html_customPrefix() {
		// Some projects might use a different inline binding prefix
		String html = "<wo:str value=\"#title\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "#");
		assertEquals("Should work with custom prefix", 1, edits.size());
		assertEditCovers(html, edits.get(0), "title");
	}

	@Test
	public void html_doesNotMatchWithWrongPrefix() {
		String html = "<wo:str value=\"#title\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		assertEquals("Should not match when prefix doesn't match", 0, edits.size());
	}

	// ---- Apply edits test (verifies offset arithmetic) ----------------------

	@Test
	public void wod_applyEdits_producesCorrectResult() {
		String wod = "Comp : WOString {\n\tvalue = title;\n\tother = title.length;\n}";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "title", "heading");
		String result = applyEdits(wod, edits);
		assertEquals("Comp : WOString {\n\tvalue = heading;\n\tother = heading.length;\n}", result);
	}

	@Test
	public void html_applyEdits_producesCorrectResult() {
		String html = "<wo:str value=\"$title\" /> <wo:str value=\"$title.length\" />";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findHtmlKeyEdits(html, "title", "heading", "$");
		String result = applyEdits(html, edits);
		assertEquals("<wo:str value=\"$heading\" /> <wo:str value=\"$heading.length\" />", result);
	}

	@Test
	public void wod_applyEdits_longerReplacement() {
		String wod = "C : WOString { value = a; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "a", "longKeyName");
		String result = applyEdits(wod, edits);
		assertEquals("C : WOString { value = longKeyName; }", result);
	}

	@Test
	public void wod_applyEdits_shorterReplacement() {
		String wod = "C : WOString { value = longKeyName; }";
		List<ReplaceEdit> edits = RenameBindingKeyProcessor.findWodKeyEdits(wod, "longKeyName", "a");
		String result = applyEdits(wod, edits);
		assertEquals("C : WOString { value = a; }", result);
	}

	// ---- Key derivation tests -----------------------------------------------
	// These test the logic that will be used in RenameBindingKeyParticipant
	// to derive binding keys from method/field names using the same prefix
	// constants as BindingReflectionUtils.

	@Test
	public void deriveKey_plainMethod() {
		assertEquals("title", deriveBindingKeyFromMethodName("title"));
	}

	@Test
	public void deriveKey_getter() {
		assertEquals("title", deriveBindingKeyFromMethodName("getTitle"));
	}

	@Test
	public void deriveKey_booleanGetter() {
		assertEquals("enabled", deriveBindingKeyFromMethodName("isEnabled"));
	}

	@Test
	public void deriveKey_setter() {
		assertEquals("title", deriveBindingKeyFromMethodName("setTitle"));
	}

	@Test
	public void deriveKey_underscoreGetter() {
		assertEquals("title", deriveBindingKeyFromMethodName("_getTitle"));
	}

	@Test
	public void deriveKey_underscoreBooleanGetter() {
		assertEquals("enabled", deriveBindingKeyFromMethodName("_isEnabled"));
	}

	@Test
	public void deriveKey_underscoreSetter() {
		assertEquals("title", deriveBindingKeyFromMethodName("_setTitle"));
	}

	@Test
	public void deriveKey_underscoreMethod() {
		assertEquals("title", deriveBindingKeyFromMethodName("_title"));
	}

	@Test
	public void deriveKey_field_plain() {
		assertEquals("title", deriveBindingKeyFromFieldName("title"));
	}

	@Test
	public void deriveKey_field_underscored() {
		assertEquals("title", deriveBindingKeyFromFieldName("_title"));
	}

	// ---- Helper methods -----------------------------------------------------

	/**
	 * Applies a list of ReplaceEdit objects to a string, working back-to-front
	 * so offsets remain valid.
	 */
	private static String applyEdits(String content, List<ReplaceEdit> edits) {
		// Sort by offset descending so we can apply without offset corruption
		edits.sort((a, b) -> Integer.compare(b.getOffset(), a.getOffset()));
		StringBuilder sb = new StringBuilder(content);
		for (ReplaceEdit edit : edits) {
			sb.replace(edit.getOffset(), edit.getOffset() + edit.getLength(), edit.getText());
		}
		return sb.toString();
	}

	/**
	 * Asserts that a ReplaceEdit covers exactly the expected string at the
	 * expected position in the content.
	 */
	private static void assertEditCovers(String content, ReplaceEdit edit, String expected) {
		String actual = content.substring(edit.getOffset(), edit.getOffset() + edit.getLength());
		assertEquals("Edit should cover '" + expected + "'", expected, actual);
	}

	/**
	 * Derives a binding key from a method name by stripping KVC getter/setter
	 * prefixes and lowercasing the first letter.
	 *
	 * <p>This mirrors the logic that {@code RenameBindingKeyParticipant} will use.
	 * The prefix lists come from {@link BindingReflectionUtils}.
	 *
	 * <p>Most prefixes (get, is, set, _get, _is, _set) require the character
	 * after the prefix to be uppercase — this distinguishes {@code getTitle()}
	 * (key "title") from {@code getter()} (key "getter"). The lone {@code _}
	 * prefix is an exception: {@code _title()} maps to key "title" regardless
	 * of case, matching KVC conventions for private accessor methods.
	 */
	static String deriveBindingKeyFromMethodName(String methodName) {
		// Try all explicit prefixes first (get, _get, is, _is, set, _set),
		// then fall back to the bare "_" prefix last. This ensures that
		// _setTitle matches "_set" (from SET_METHOD_PREFIXES) rather than
		// "_" (from GET_METHOD_PREFIXES).
		//
		// Explicit prefixes require an uppercase letter after stripping
		// (getTitle -> Title, not getter -> ter). The bare "_" prefix is
		// a fallback that doesn't require uppercase (_title -> title).

		// 1. Try getter prefixes (except the bare "_" and empty "")
		// GET_METHOD_PREFIXES = { "get", "", "_get", "is", "_is", "_" }
		for (String prefix : BindingReflectionUtils.GET_METHOD_PREFIXES) {
			if (prefix.length() > 1 && methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 2. Try setter prefixes: { "set", "_set" }
		for (String prefix : BindingReflectionUtils.SET_METHOD_PREFIXES) {
			if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 3. Try the bare "_" prefix last — doesn't require uppercase
		if (methodName.startsWith("_") && methodName.length() > 1) {
			return methodName.substring(1);
		}

		// No recognized prefix — the method name IS the key (e.g. title())
		return methodName;
	}

	/**
	 * Derives a binding key from a field name by stripping the underscore
	 * prefix if present.
	 *
	 * <p>This mirrors the logic that {@code RenameBindingKeyParticipant} will use.
	 * The prefix list comes from {@link BindingReflectionUtils#FIELD_PREFIXES}.
	 */
	static String deriveBindingKeyFromFieldName(String fieldName) {
		// FIELD_PREFIXES = { "", "_" }
		// Try the non-empty prefix "_"
		if (fieldName.startsWith("_") && fieldName.length() > 1) {
			return fieldName.substring(1);
		}
		// No prefix — the field name IS the key
		return fieldName;
	}
}

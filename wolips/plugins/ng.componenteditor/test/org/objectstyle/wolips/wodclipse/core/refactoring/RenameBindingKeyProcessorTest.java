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

	// ---- Case preservation for unprefixed and acronym methods ---------------
	// At runtime, valueForKey(K) looks for getK_capitalized, then K literal,
	// then isK_capitalized. So the "binding key" for a bare method
	// HTTPServerUpdateClicked() is "HTTPServerUpdateClicked" — preserving the
	// case exactly. Same for HttpServerUpdateClicked() — its key is
	// "HttpServerUpdateClicked", NOT "httpServerUpdateClicked", because the
	// runtime literal-method lookup is case-sensitive.

	@Test
	public void deriveKey_bareMethod_preservesAcronym() {
		assertEquals("HTTPServerUpdateClicked",
				deriveBindingKeyFromMethodName("HTTPServerUpdateClicked"));
	}

	@Test
	public void deriveKey_bareMethod_preservesCamelCase() {
		// The exact case from the user's second report — Camel-cased name
		// starting with an uppercase letter must be preserved.
		assertEquals("HttpServerUpdateClicked",
				deriveBindingKeyFromMethodName("HttpServerUpdateClicked"));
	}

	@Test
	public void deriveKey_bareMethod_preservesSimpleCapitalized() {
		// Even a simple "Title()" method preserves its uppercase initial.
		assertEquals("Title", deriveBindingKeyFromMethodName("Title"));
	}

	@Test
	public void deriveKey_underscoreBareMethod_preservesCase() {
		// _HttpServer() — strip the leading underscore but preserve case.
		assertEquals("HttpServer", deriveBindingKeyFromMethodName("_HttpServer"));
	}

	@Test
	public void deriveKey_getterAcronym_preserved() {
		// getURL() → "URL" (acronym preserved by toLowercaseFirstLetter).
		assertEquals("URL", deriveBindingKeyFromMethodName("getURL"));
	}

	@Test
	public void deriveKey_getterCamelCase_lowercased() {
		// getHttpServer() → "httpServer" (only first char lowercase, since
		// 'H' is uppercase and 't' is lowercase — no acronym to preserve).
		assertEquals("httpServer", deriveBindingKeyFromMethodName("getHttpServer"));
	}

	@Test
	public void deriveKey_setterAcronym_preserved() {
		assertEquals("URL", deriveBindingKeyFromMethodName("setURL"));
	}

	@Test
	public void deriveKey_isAcronym_preserved() {
		// Probably nonsense in practice, but exercises the rule.
		assertEquals("URL", deriveBindingKeyFromMethodName("isURL"));
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
	 * Delegates to the production helper. Kept as a thin wrapper so the
	 * test methods read the same way they did before this method became
	 * public on {@code RenameBindingKeyProcessor}.
	 */
	static String deriveBindingKeyFromMethodName(String methodName) {
		return RenameBindingKeyProcessor.deriveBindingKeyFromMethodName(methodName);
	}

	static String deriveBindingKeyFromFieldName(String fieldName) {
		return RenameBindingKeyProcessor.deriveBindingKeyFromFieldName(fieldName);
	}
}

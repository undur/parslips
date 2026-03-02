package org.objectstyle.wolips.wodclipse.core.refactoring;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Tests for the regex-based binding reference scanning in
 * {@link RenameBindingProcessor}.
 *
 * <p>Since the processor's public methods require Eclipse workspace objects
 * (IFile, IProject), these tests exercise the same regex patterns and scanning
 * logic used internally, applied to raw strings.
 */
public class RenameBindingProcessorTest {

	// ---- HTML inline binding scanning ----------------------------------------

	@Test
	public void html_findsSimpleBinding() {
		String html = "<wo:MyComponent item=\"$value\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals("Should find exactly one binding", 1, edits.size());
		assertEquals("item", html.substring(edits.get(0)[0], edits.get(0)[0] + edits.get(0)[1]));
	}

	@Test
	public void html_findsBindingWithSingleQuotes() {
		String html = "<wo:MyComponent item='$value' />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals(1, edits.size());
	}

	@Test
	public void html_doesNotMatchDifferentComponent() {
		String html = "<wo:OtherComponent item=\"$value\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals("Should not match different component", 0, edits.size());
	}

	@Test
	public void html_doesNotMatchPartialBindingName() {
		String html = "<wo:MyComponent itemCount=\"$value\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals("Should not match partial name 'item' in 'itemCount'", 0, edits.size());
	}

	@Test
	public void html_matchesMultipleBindingsInSameTag() {
		String html = "<wo:MyComponent item=\"$a\" item2=\"$b\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		// "item" should match but not "item2" (because "item2" has a digit after)
		assertEquals(1, edits.size());
	}

	@Test
	public void html_matchesInMultipleTags() {
		String html = "<wo:MyComponent item=\"$a\" />\n<wo:MyComponent item=\"$b\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals("Should find binding in both tags", 2, edits.size());
	}

	@Test
	public void html_matchesCaseInsensitiveWoPrefix() {
		String html = "<WO:MyComponent item=\"$value\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals(1, edits.size());
	}

	@Test
	public void html_multilineTag() {
		String html = "<wo:MyComponent\n\titem=\"$value\"\n\tother=\"$x\"\n/>";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals(1, edits.size());
	}

	@Test
	public void html_doesNotMatchInAttributeValue() {
		// The binding name "item" appears in the value, not as an attribute name
		String html = "<wo:MyComponent value=\"item\" />";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals("Should not match in attribute value", 0, edits.size());
	}

	@Test
	public void html_closingTagIgnored() {
		// Closing tags don't have attributes, so there should be no matches
		String html = "</wo:MyComponent>";
		List<int[]> edits = findHtmlBindingEdits(html, "MyComponent", "item");
		assertEquals(0, edits.size());
	}

	// ---- WOD binding scanning ------------------------------------------------

	@Test
	public void wod_findsSimpleBinding() {
		String wod = "Comp1 : MyComponent {\n\titem = value;\n}";
		List<int[]> edits = findWodBindingEdits(wod, "MyComponent", "item");
		assertEquals(1, edits.size());
		assertEquals("item", wod.substring(edits.get(0)[0], edits.get(0)[0] + edits.get(0)[1]));
	}

	@Test
	public void wod_doesNotMatchDifferentComponent() {
		String wod = "Comp1 : OtherComponent {\n\titem = value;\n}";
		List<int[]> edits = findWodBindingEdits(wod, "MyComponent", "item");
		assertEquals(0, edits.size());
	}

	@Test
	public void wod_doesNotMatchPartialName() {
		String wod = "Comp1 : MyComponent {\n\titemCount = value;\n}";
		List<int[]> edits = findWodBindingEdits(wod, "MyComponent", "item");
		assertEquals("Should not match partial name 'item' in 'itemCount'", 0, edits.size());
	}

	@Test
	public void wod_matchesMultipleElements() {
		String wod = "A : MyComponent {\n\titem = v1;\n}\nB : MyComponent {\n\titem = v2;\n}";
		List<int[]> edits = findWodBindingEdits(wod, "MyComponent", "item");
		assertEquals(2, edits.size());
	}

	// ---- Multi-rename tests (single-pass correctness) -----------------------

	@Test
	public void html_multiRename_producesNonOverlappingEdits() {
		String html = "<wo:HVDate value=\"$a\" valueWhenEmpty=\"$b\" />";
		Map<String, String> renames = new LinkedHashMap<>();
		renames.put("value", "object");
		renames.put("valueWhenEmpty", "objectWhenEmpty");

		String result = applyRenames(html, "HVDate", renames);
		assertEquals("<wo:HVDate object=\"$a\" objectWhenEmpty=\"$b\" />", result);
	}

	@Test
	public void html_multiRename_longerReplacementDoesNotCorrupt() {
		String html = "<wo:Comp a=\"$x\" b=\"$y\" />";
		Map<String, String> renames = new LinkedHashMap<>();
		renames.put("a", "alphaLong");
		renames.put("b", "betaLong");

		String result = applyRenames(html, "Comp", renames);
		assertEquals("<wo:Comp alphaLong=\"$x\" betaLong=\"$y\" />", result);
	}

	@Test
	public void html_multiRename_shorterReplacementDoesNotCorrupt() {
		String html = "<wo:Comp alphaLong=\"$x\" betaLong=\"$y\" />";
		Map<String, String> renames = new LinkedHashMap<>();
		renames.put("alphaLong", "a");
		renames.put("betaLong", "b");

		String result = applyRenames(html, "Comp", renames);
		assertEquals("<wo:Comp a=\"$x\" b=\"$y\" />", result);
	}

	@Test
	public void wod_multiRename_producesNonOverlappingEdits() {
		String wod = "D1 : HVDate {\n\tvalue = a;\n\tvalueWhenEmpty = b;\n}";
		Map<String, String> renames = new LinkedHashMap<>();
		renames.put("value", "object");
		renames.put("valueWhenEmpty", "objectWhenEmpty");

		String result = applyWodRenames(wod, "HVDate", renames);
		assertEquals("D1 : HVDate {\n\tobject = a;\n\tobjectWhenEmpty = b;\n}", result);
	}

	// ---- Helper methods that mirror RenameBindingProcessor's logic -----------

	/**
	 * Simulates HTML binding scanning — same regex patterns as
	 * {@link RenameBindingProcessor#createHtmlBindingChange}.
	 *
	 * @return list of [offset, length] pairs for each binding name match
	 */
	private List<int[]> findHtmlBindingEdits(String content, String componentName, String oldBindingName) {
		Pattern tagPattern = Pattern.compile(
				"(?i:<wo:)" + Pattern.quote(componentName) + "(?=[\\s>/$])", 0);
		Pattern bindingPattern = Pattern.compile(
				"(?<=\\s)" + Pattern.quote(oldBindingName) + "\\s*=");

		Matcher tagMatcher = tagPattern.matcher(content);
		List<int[]> edits = new ArrayList<>();

		while (tagMatcher.find()) {
			int tagEnd = findTagEnd(content, tagMatcher.end());
			if (tagEnd < 0) continue;

			String tagContent = content.substring(tagMatcher.end(), tagEnd);
			Matcher bindingMatcher = bindingPattern.matcher(tagContent);

			while (bindingMatcher.find()) {
				int absoluteOffset = tagMatcher.end() + bindingMatcher.start();
				edits.add(new int[] { absoluteOffset, oldBindingName.length() });
			}
		}

		return edits;
	}

	/**
	 * Simulates WOD binding scanning — same regex patterns as
	 * {@link RenameBindingProcessor#createWodBindingChange}.
	 */
	private List<int[]> findWodBindingEdits(String content, String componentName, String oldBindingName) {
		Pattern elementPattern = Pattern.compile(
				":\\s*" + Pattern.quote(componentName) + "\\s*\\{");
		Pattern bindingPattern = Pattern.compile(
				"(?<=^|[;\\s])" + Pattern.quote(oldBindingName) + "\\s*=");

		Matcher elementMatcher = elementPattern.matcher(content);
		List<int[]> edits = new ArrayList<>();

		while (elementMatcher.find()) {
			int braceStart = elementMatcher.end();
			int braceEnd = content.indexOf('}', braceStart);
			if (braceEnd < 0) continue;

			String braceContent = content.substring(braceStart, braceEnd);
			Matcher bindingMatcher = bindingPattern.matcher(braceContent);

			while (bindingMatcher.find()) {
				int absoluteOffset = braceStart + bindingMatcher.start();
				edits.add(new int[] { absoluteOffset, oldBindingName.length() });
			}
		}

		return edits;
	}

	/**
	 * Simulates a multi-rename on HTML content by collecting all edits in a
	 * single pass (matching the processor's single-pass behavior) and applying
	 * them back-to-front so offsets remain valid.
	 */
	private String applyRenames(String content, String componentName, Map<String, String> renames) {
		Pattern tagPattern = Pattern.compile(
				"(?i:<wo:)" + Pattern.quote(componentName) + "(?=[\\s>/$])", 0);

		Matcher tagMatcher = tagPattern.matcher(content);
		List<int[]> allEdits = new ArrayList<>(); // [offset, oldLength, renameIndex]
		List<String> newNames = new ArrayList<>();

		while (tagMatcher.find()) {
			int tagEnd = findTagEnd(content, tagMatcher.end());
			if (tagEnd < 0) continue;

			String tagContent = content.substring(tagMatcher.end(), tagEnd);
			for (Map.Entry<String, String> rename : renames.entrySet()) {
				Pattern bindingPattern = Pattern.compile(
						"(?<=\\s)" + Pattern.quote(rename.getKey()) + "\\s*=");
				Matcher bindingMatcher = bindingPattern.matcher(tagContent);
				while (bindingMatcher.find()) {
					int absoluteOffset = tagMatcher.end() + bindingMatcher.start();
					allEdits.add(new int[] { absoluteOffset, rename.getKey().length(), newNames.size() });
					newNames.add(rename.getValue());
				}
			}
		}

		// Apply edits back-to-front so earlier offsets remain valid
		allEdits.sort((a, b) -> Integer.compare(b[0], a[0]));
		StringBuilder sb = new StringBuilder(content);
		for (int[] edit : allEdits) {
			sb.replace(edit[0], edit[0] + edit[1], newNames.get(edit[2]));
		}
		return sb.toString();
	}

	/**
	 * Simulates a multi-rename on WOD content, matching the processor's
	 * single-pass behavior.
	 */
	private String applyWodRenames(String content, String componentName, Map<String, String> renames) {
		Pattern elementPattern = Pattern.compile(
				":\\s*" + Pattern.quote(componentName) + "\\s*\\{");

		Matcher elementMatcher = elementPattern.matcher(content);
		List<int[]> allEdits = new ArrayList<>();
		List<String> newNames = new ArrayList<>();

		while (elementMatcher.find()) {
			int braceStart = elementMatcher.end();
			int braceEnd = content.indexOf('}', braceStart);
			if (braceEnd < 0) continue;

			String braceContent = content.substring(braceStart, braceEnd);
			for (Map.Entry<String, String> rename : renames.entrySet()) {
				Pattern bindingPattern = Pattern.compile(
						"(?<=^|[;\\s])" + Pattern.quote(rename.getKey()) + "\\s*=");
				Matcher bindingMatcher = bindingPattern.matcher(braceContent);
				while (bindingMatcher.find()) {
					int absoluteOffset = braceStart + bindingMatcher.start();
					allEdits.add(new int[] { absoluteOffset, rename.getKey().length(), newNames.size() });
					newNames.add(rename.getValue());
				}
			}
		}

		allEdits.sort((a, b) -> Integer.compare(b[0], a[0]));
		StringBuilder sb = new StringBuilder(content);
		for (int[] edit : allEdits) {
			sb.replace(edit[0], edit[0] + edit[1], newNames.get(edit[2]));
		}
		return sb.toString();
	}

	/** Same tag-end finding logic as RenameBindingProcessor. */
	private static int findTagEnd(String content, int start) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = start; i < content.length(); i++) {
			char c = content.charAt(i);
			if (inSingleQuote) { if (c == '\'') inSingleQuote = false; }
			else if (inDoubleQuote) { if (c == '"') inDoubleQuote = false; }
			else if (c == '\'') inSingleQuote = true;
			else if (c == '"') inDoubleQuote = true;
			else if (c == '>') return i;
			else if (c == '<') return -1;
		}
		return -1;
	}
}

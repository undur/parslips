package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectstyle.wolips.bindings.wod.IWodBinding;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

/**
 * Converts a .wo bundle HTML template from WOD-reference syntax
 * ({@code <webobject name="X">}) to inline binding syntax
 * ({@code <wo:Type binding="value">}).
 *
 * <p>The transformation is purely string-based: it uses regex matching to
 * find open, close, and self-closing webobject/wo tags, then replaces them
 * with their inline equivalents using the WOD element definitions. All
 * surrounding HTML is preserved exactly.
 *
 * <p>This class has no Eclipse workspace dependencies — it operates on plain
 * strings and WOD model objects, making it fully testable without an Eclipse
 * runtime.
 *
 * <p>The algorithm uses a stack to correctly pair close tags with their
 * corresponding open tags (since {@code </webobject>} carries no name
 * attribute). A separate "skip depth" counter tracks elements that could
 * not be converted (missing WOD entry), ensuring their close tags are
 * also left untouched.
 */
public class ConvertBundleToInlineTransformer {

	/**
	 * Matches close tags for webobject/wo elements that use WOD-reference syntax.
	 *
	 * <p>Matches: {@code </webobject>}, {@code </webobjects>}, {@code </wo>}
	 * (case-insensitive, optional whitespace before {@code >}).
	 *
	 * <p>Does NOT match inline close tags like {@code </wo:Type>} because
	 * the {@code \s*>} part won't match the {@code :} character after "wo".
	 */
	private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile(
			"</webobjects?\\s*>|</wo\\s*>",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Combined pattern that matches both open/self-closing tags (via
	 * {@link WodHtmlUtils#WEBOBJECTS_PATTERN}) and close tags. The open-tag
	 * pattern is group-wrapped so we can distinguish open from close matches.
	 *
	 * <p>Groups:
	 * <ul>
	 *   <li>Group 1: entire open/self-closing tag match (null for close tags)
	 *   <li>Group 2: element name from {@code <webobject name="X">}
	 *   <li>Group 3: element name from {@code <wo name="X">}
	 *   <li>Group 4: entire close tag match (null for open tags)
	 * </ul>
	 */
	private static final Pattern COMBINED_PATTERN;

	static {
		// Wrap the existing open-tag pattern and the close-tag pattern into
		// a single alternation so we can walk the HTML in one pass.
		String openPattern = WodHtmlUtils.WEBOBJECTS_PATTERN.pattern();
		String closePattern = CLOSE_TAG_PATTERN.pattern();
		COMBINED_PATTERN = Pattern.compile(
				"(" + openPattern + ")|(" + closePattern + ")",
				Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	}

	/**
	 * Result of a bundle-to-inline conversion, containing the transformed
	 * HTML and any warnings encountered during conversion.
	 */
	public static class ConversionResult {

		private final String _html;
		private final List<String> _warnings;

		public ConversionResult(String html, List<String> warnings) {
			_html = html;
			_warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
		}

		/** The converted HTML content with inline bindings. */
		public String getHtml() {
			return _html;
		}

		/**
		 * Warnings produced during conversion (e.g. "No WOD entry found
		 * for element 'Foo'"). Empty if conversion was fully successful.
		 */
		public List<String> getWarnings() {
			return _warnings;
		}
	}

	/**
	 * Converts all WOD-reference tags in the given HTML to inline binding syntax,
	 * using spaces around {@code =} in binding attributes.
	 *
	 * <p>Convenience overload that defaults {@code spacesAroundEquals} to {@code true}.
	 *
	 * @see #convert(String, Map, String, String, boolean)
	 */
	public static ConversionResult convert(String htmlContent, Map<String, IWodElement> wodElements, String bindingPrefix, String bindingSuffix) {
		return convert(htmlContent, wodElements, bindingPrefix, bindingSuffix, true);
	}

	/**
	 * Converts all WOD-reference tags in the given HTML to inline binding syntax.
	 *
	 * <p>For each {@code <webobject name="X">} or {@code <wo name="X">} tag,
	 * looks up element X in the provided WOD element map and replaces the tag
	 * with {@code <wo:ElementType binding1="value1" ...>}. Close tags are
	 * paired using a stack. Self-closing tags are handled correctly.
	 *
	 * <p>Tags whose element name is not found in the WOD map are left unchanged
	 * and a warning is added to the result.
	 *
	 * <p>Already-inline {@code <wo:Type>} tags are not matched by the pattern
	 * and pass through untouched.
	 *
	 * @param htmlContent the HTML template content
	 * @param wodElements map from element name to WOD element definition
	 * @param bindingPrefix the inline binding prefix (e.g. "$")
	 * @param bindingSuffix the inline binding suffix (e.g. "")
	 * @param spacesAroundEquals whether to put spaces around {@code =} in
	 *        binding attributes (e.g. {@code value = "$x"} vs {@code value="$x"})
	 * @return the conversion result with transformed HTML and any warnings
	 */
	public static ConversionResult convert(String htmlContent, Map<String, IWodElement> wodElements, String bindingPrefix, String bindingSuffix, boolean spacesAroundEquals) {
		if (htmlContent == null || htmlContent.isEmpty()) {
			return new ConversionResult(htmlContent, Collections.emptyList());
		}

		List<String> warnings = new ArrayList<>();
		StringBuilder result = new StringBuilder(htmlContent.length());

		// Unified stack that tracks both converted and skipped elements in
		// document order. Entries are the inline element type (e.g. "WOString")
		// for elements we ARE converting, or null for elements we are NOT
		// converting (missing WOD entry). This ensures close tags are correctly
		// paired with their corresponding open tags regardless of interleaving.
		Stack<String> tagStack = new Stack<>();

		int lastCopyPos = 0;
		Matcher matcher = COMBINED_PATTERN.matcher(htmlContent);

		while (matcher.find()) {
			// Group 1 is non-null when the open/self-closing pattern matched
			String openTagMatch = matcher.group(1);

			if (openTagMatch != null) {
				// Open or self-closing tag — extract the element name
				// Group 2 = name from <webobject name="X">, Group 3 = name from <wo name="X">
				String elementName = matcher.group(2);
				if (elementName == null) {
					elementName = matcher.group(3);
				}

				IWodElement wodElement = wodElements.get(elementName);
				if (wodElement == null) {
					// No WOD entry — leave tag unchanged, push null to track
					warnings.add("No WOD entry found for element '" + elementName + "'");
					boolean isSelfClosing = openTagMatch.endsWith("/>");
					if (!isSelfClosing) {
						tagStack.push(null);
					}
					continue;
				}

				// Build the replacement inline tag
				String elementType = wodElement.getElementType();
				boolean isSelfClosing = openTagMatch.endsWith("/>");

				try {
					String replacement = buildOpenTag(wodElement, elementType, isSelfClosing, bindingPrefix, bindingSuffix, spacesAroundEquals);

					// Copy everything before this match, then the replacement
					result.append(htmlContent, lastCopyPos, matcher.start());
					result.append(replacement);
					lastCopyPos = matcher.end();

					if (!isSelfClosing) {
						tagStack.push(elementType);
					}
				}
				catch (IOException e) {
					// writeInlineFormat shouldn't throw for StringWriter, but
					// if it does, leave the tag unchanged and warn
					warnings.add("Failed to convert element '" + elementName + "': " + e.getMessage());
					boolean selfClosing = openTagMatch.endsWith("/>");
					if (!selfClosing) {
						tagStack.push(null);
					}
				}
			}
			else {
				// Close tag match
				if (tagStack.isEmpty()) {
					// Unmatched close tag — leave unchanged and warn
					warnings.add("Unmatched close tag at offset " + matcher.start());
					continue;
				}

				String elementType = tagStack.pop();
				if (elementType == null) {
					// Close tag for a skipped element — leave unchanged
					continue;
				}

				result.append(htmlContent, lastCopyPos, matcher.start());
				result.append("</wo:");
				result.append(elementType);
				result.append(">");
				lastCopyPos = matcher.end();
			}
		}

		// Append any remaining HTML after the last match
		result.append(htmlContent, lastCopyPos, htmlContent.length());

		return new ConversionResult(result.toString(), warnings);
	}

	/**
	 * Builds the inline open tag string for a WOD element.
	 *
	 * <p>Produces output like {@code <wo:WOString value="$name" escapeHTML="false">}
	 * or {@code <wo:WOImage src="photo.jpg"/>} for self-closing tags.
	 *
	 * <p>Uses {@link IWodBinding#writeInlineFormat} for each binding, which
	 * correctly handles literals, OGNL expressions, and key paths with the
	 * configured binding prefix/suffix.
	 */
	private static String buildOpenTag(IWodElement wodElement, String elementType, boolean isSelfClosing, String bindingPrefix, String bindingSuffix, boolean spacesAroundEquals) throws IOException {
		StringWriter writer = new StringWriter();
		writer.write("<wo:");
		writer.write(elementType);
		for (IWodBinding binding : wodElement.getBindings()) {
			binding.writeInlineFormat(writer, bindingPrefix, bindingSuffix, spacesAroundEquals);
		}
		if (isSelfClosing) {
			writer.write("/>");
		}
		else {
			writer.write(">");
		}
		return writer.toString();
	}
}

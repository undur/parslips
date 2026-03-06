package org.objectstyle.wolips.wodclipse.core.refactoring;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.bindings.wod.SimpleWodBinding;
import org.objectstyle.wolips.bindings.wod.SimpleWodElement;
import org.objectstyle.wolips.wodclipse.core.refactoring.ConvertBundleToInlineTransformer.ConversionResult;

/**
 * Tests for {@link ConvertBundleToInlineTransformer} — the core logic that
 * converts .wo bundle HTML templates from WOD-reference syntax
 * ({@code <webobject name="X">}) to inline binding syntax
 * ({@code <wo:Type binding="value">}).
 *
 * <p>All tests are pure string transformations using {@link SimpleWodElement}
 * and {@link SimpleWodBinding} — no Eclipse workspace or runtime needed.
 *
 * <p>Note on inline binding format: {@link org.objectstyle.wolips.bindings.wod.AbstractWodBinding#writeInlineFormat
 * writeInlineFormat} produces {@code name = "value"} (with spaces around
 * {@code =}), so all expected strings use that spacing.
 */
public class ConvertBundleToInlineTransformerTest {

	/** Default binding prefix for inline syntax. */
	private static final String PREFIX = "$";

	/** Default binding suffix for inline syntax. */
	private static final String SUFFIX = "";

	// -- helpers --

	/**
	 * Creates a WOD element map with a single element.
	 * Bindings are specified as name/value pairs (WOD format values).
	 */
	private Map<String, IWodElement> wodMap(String elementName, String elementType, String... bindings) {
		Map<String, IWodElement> map = new HashMap<>();
		map.put(elementName, wodElement(elementName, elementType, bindings));
		return map;
	}

	/**
	 * Creates a SimpleWodBinding with the given name and WOD-format value.
	 *
	 * <p>Note: the 3-arg {@code SimpleWodBinding(namespace, name, value)}
	 * constructor is misleading — its third parameter is actually
	 * {@code valueNamespace}, not {@code value}. We use the full constructor
	 * to place the value in the correct field.
	 */
	private static SimpleWodBinding binding(String name, String value) {
		return new SimpleWodBinding(null, name, null, value, null, null, null, null, -1);
	}

	/**
	 * Creates a SimpleWodElement with the given bindings.
	 * Binding values should be in WOD format (e.g. {@code session.user}
	 * for key paths, {@code "Hello"} for literals, {@code ~expr} for OGNL).
	 */
	private SimpleWodElement wodElement(String elementName, String elementType, String... bindings) {
		SimpleWodElement element = new SimpleWodElement(elementName, elementType);
		for (int i = 0; i < bindings.length; i += 2) {
			element.addBinding(binding(bindings[i], bindings[i + 1]));
		}
		return element;
	}

	private String convert(String html, Map<String, IWodElement> wod) {
		return ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX).getHtml();
	}

	private ConversionResult convertResult(String html, Map<String, IWodElement> wod) {
		return ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX);
	}

	// -- basic conversion --

	@Test
	public void basicConversion() {
		Map<String, IWodElement> wod = wodMap("Greeting", "WOString", "value", "name");
		String html = "<webobject name=\"Greeting\">Hello</webobject>";
		assertEquals("<wo:WOString value = \"$name\">Hello</wo:WOString>", convert(html, wod));
	}

	@Test
	public void selfClosingTag() {
		Map<String, IWodElement> wod = wodMap("Img", "WOImage", "src", "\"photo.jpg\"");
		String html = "<webobject name=\"Img\"/>";
		assertEquals("<wo:WOImage src = \"photo.jpg\"/>", convert(html, wod));
	}

	@Test
	public void selfClosingTagWithSpace() {
		Map<String, IWodElement> wod = wodMap("Img", "WOImage", "src", "\"photo.jpg\"");
		String html = "<webobject name=\"Img\" />";
		assertEquals("<wo:WOImage src = \"photo.jpg\"/>", convert(html, wod));
	}

	@Test
	public void woNameVariant() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "bar");
		String html = "<wo name=\"Foo\">x</wo>";
		assertEquals("<wo:WOString value = \"$bar\">x</wo:WOString>", convert(html, wod));
	}

	// -- binding types --

	@Test
	public void multipleBindings() {
		Map<String, IWodElement> wod = wodMap("Field", "WOTextField",
				"value", "searchTerm",
				"size", "\"30\"");
		String html = "<webobject name=\"Field\">input</webobject>";
		assertEquals("<wo:WOTextField value = \"$searchTerm\" size = \"30\">input</wo:WOTextField>", convert(html, wod));
	}

	@Test
	public void literalBindingValue() {
		// Literal values in WOD format start with a quote
		Map<String, IWodElement> wod = wodMap("Str", "WOString", "value", "\"Hello World\"");
		String html = "<webobject name=\"Str\">x</webobject>";
		assertEquals("<wo:WOString value = \"Hello World\">x</wo:WOString>", convert(html, wod));
	}

	@Test
	public void keyPathBindingValue() {
		Map<String, IWodElement> wod = wodMap("Str", "WOString", "value", "session.user.name");
		String html = "<webobject name=\"Str\">x</webobject>";
		assertEquals("<wo:WOString value = \"$session.user.name\">x</wo:WOString>", convert(html, wod));
	}

	@Test
	public void ognlBindingValue() {
		// OGNL values in WOD format start with ~
		Map<String, IWodElement> wod = wodMap("Cond", "WOConditional", "condition", "~item.price > 100");
		String html = "<webobject name=\"Cond\">x</webobject>";
		assertEquals("<wo:WOConditional condition = \"~item.price > 100\">x</wo:WOConditional>", convert(html, wod));
	}

	@Test
	public void customBindingPrefixAndSuffix() {
		Map<String, IWodElement> wod = wodMap("Str", "WOString", "value", "session.user");
		String html = "<webobject name=\"Str\">x</webobject>";
		ConversionResult result = ConvertBundleToInlineTransformer.convert(html, wod, "${", "}");
		assertEquals("<wo:WOString value = \"${session.user}\">x</wo:WOString>", result.getHtml());
	}

	// -- nesting --

	@Test
	public void nestedElements() {
		Map<String, IWodElement> wod = new HashMap<>();
		wod.put("Outer", wodElement("Outer", "WOForm", "action", "submit"));
		wod.put("Inner", wodElement("Inner", "WOString", "value", "name"));
		String html = "<webobject name=\"Outer\"><webobject name=\"Inner\">text</webobject></webobject>";
		assertEquals(
				"<wo:WOForm action = \"$submit\"><wo:WOString value = \"$name\">text</wo:WOString></wo:WOForm>",
				convert(html, wod));
	}

	@Test
	public void deeplyNestedElements() {
		Map<String, IWodElement> wod = new HashMap<>();
		wod.put("A", wodElement("A", "TypeA"));
		wod.put("B", wodElement("B", "TypeB"));
		wod.put("C", wodElement("C", "TypeC"));
		String html = "<webobject name=\"A\"><webobject name=\"B\"><webobject name=\"C\">x</webobject></webobject></webobject>";
		assertEquals(
				"<wo:TypeA><wo:TypeB><wo:TypeC>x</wo:TypeC></wo:TypeB></wo:TypeA>",
				convert(html, wod));
	}

	// -- missing WOD entries --

	@Test
	public void missingWodEntry_tagLeftUnchanged() {
		Map<String, IWodElement> wod = new HashMap<>(); // empty
		String html = "<webobject name=\"Unknown\">content</webobject>";
		ConversionResult result = convertResult(html, wod);
		assertEquals(html, result.getHtml());
		assertEquals(1, result.getWarnings().size());
		assertTrue(result.getWarnings().get(0).contains("Unknown"));
	}

	@Test
	public void missingWodEntry_selfClosing() {
		Map<String, IWodElement> wod = new HashMap<>();
		String html = "<webobject name=\"Unknown\"/>";
		ConversionResult result = convertResult(html, wod);
		assertEquals(html, result.getHtml());
		assertEquals(1, result.getWarnings().size());
	}

	@Test
	public void mixedConvertedAndSkipped() {
		Map<String, IWodElement> wod = wodMap("Known", "WOString", "value", "x");
		// Unknown is not in the map
		String html = "<webobject name=\"Unknown\"><webobject name=\"Known\">y</webobject></webobject>";
		ConversionResult result = convertResult(html, wod);
		assertEquals("<webobject name=\"Unknown\"><wo:WOString value = \"$x\">y</wo:WOString></webobject>", result.getHtml());
		assertEquals(1, result.getWarnings().size());
	}

	@Test
	public void skippedOuterWithConvertedInner() {
		// Outer is unknown, inner is known — close tags must pair correctly
		Map<String, IWodElement> wod = wodMap("Inner", "WOString", "value", "x");
		String html = "<webobject name=\"Outer\"><webobject name=\"Inner\">y</webobject></webobject>";
		ConversionResult result = convertResult(html, wod);
		assertEquals("<webobject name=\"Outer\"><wo:WOString value = \"$x\">y</wo:WOString></webobject>", result.getHtml());
	}

	// -- already-inline tags --

	@Test
	public void inlineTagsLeftAlone() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		String html = "<wo:WOConditional condition=\"$show\"><webobject name=\"Foo\">y</webobject></wo:WOConditional>";
		assertEquals(
				"<wo:WOConditional condition=\"$show\"><wo:WOString value = \"$x\">y</wo:WOString></wo:WOConditional>",
				convert(html, wod));
	}

	// -- case insensitivity --

	@Test
	public void caseInsensitiveWebobjectTag() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		String html = "<WebObject name=\"Foo\">y</WebObject>";
		assertEquals("<wo:WOString value = \"$x\">y</wo:WOString>", convert(html, wod));
	}

	@Test
	public void caseInsensitiveWoTag() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		String html = "<WO name=\"Foo\">y</WO>";
		assertEquals("<wo:WOString value = \"$x\">y</wo:WOString>", convert(html, wod));
	}

	// -- unquoted name attribute --

	@Test
	public void nameWithoutQuotes() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		String html = "<webobject name=Foo>y</webobject>";
		assertEquals("<wo:WOString value = \"$x\">y</wo:WOString>", convert(html, wod));
	}

	// -- empty / no-op --

	@Test
	public void emptyTemplate() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		assertEquals("", convert("", wod));
	}

	@Test
	public void nullTemplate() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		assertNull(ConvertBundleToInlineTransformer.convert(null, wod, PREFIX, SUFFIX).getHtml());
	}

	@Test
	public void noWebobjectTags() {
		Map<String, IWodElement> wod = wodMap("Foo", "WOString", "value", "x");
		String html = "<html><body>Hello</body></html>";
		assertEquals(html, convert(html, wod));
	}

	// -- surrounding content preserved --

	@Test
	public void surroundingHtmlPreserved() {
		Map<String, IWodElement> wod = wodMap("Str", "WOString", "value", "name");
		String html = "<html>\n  <body>\n    <webobject name=\"Str\">text</webobject>\n  </body>\n</html>";
		String expected = "<html>\n  <body>\n    <wo:WOString value = \"$name\">text</wo:WOString>\n  </body>\n</html>";
		assertEquals(expected, convert(html, wod));
	}

	// -- element with no bindings --

	@Test
	public void elementWithNoBindings() {
		Map<String, IWodElement> wod = wodMap("Container", "WOGenericContainer");
		String html = "<webobject name=\"Container\">inside</webobject>";
		assertEquals("<wo:WOGenericContainer>inside</wo:WOGenericContainer>", convert(html, wod));
	}

	// -- binding namespace --

	@Test
	public void bindingWithNamespace() {
		SimpleWodElement element = new SimpleWodElement("Cond", "WOConditional");
		element.addBinding(new SimpleWodBinding("ng", "condition", null, "isVisible", null, null, null, null, -1));
		Map<String, IWodElement> wod = new HashMap<>();
		wod.put("Cond", element);
		String html = "<webobject name=\"Cond\">x</webobject>";
		assertEquals("<wo:WOConditional ng:condition = \"$isVisible\">x</wo:WOConditional>", convert(html, wod));
	}

	// -- multiple elements in sequence --

	@Test
	public void multipleSequentialElements() {
		Map<String, IWodElement> wod = new HashMap<>();
		wod.put("A", wodElement("A", "WOString", "value", "x"));
		wod.put("B", wodElement("B", "WOString", "value", "y"));
		String html = "<webobject name=\"A\">a</webobject><webobject name=\"B\">b</webobject>";
		assertEquals(
				"<wo:WOString value = \"$x\">a</wo:WOString><wo:WOString value = \"$y\">b</wo:WOString>",
				convert(html, wod));
	}

	// -- no warnings on clean conversion --

	@Test
	public void cleanConversion_noWarnings() {
		Map<String, IWodElement> wod = wodMap("Str", "WOString", "value", "x");
		String html = "<webobject name=\"Str\">y</webobject>";
		ConversionResult result = convertResult(html, wod);
		assertTrue(result.getWarnings().isEmpty());
	}

	// -- spacesAroundEquals preference --

	@Test
	public void noSpacesAroundEquals_basicConversion() {
		Map<String, IWodElement> wod = wodMap("Greeting", "WOString", "value", "name");
		String html = "<webobject name=\"Greeting\">Hello</webobject>";
		ConversionResult result = ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX, false);
		assertEquals("<wo:WOString value=\"$name\">Hello</wo:WOString>", result.getHtml());
	}

	@Test
	public void noSpacesAroundEquals_multipleBindings() {
		Map<String, IWodElement> wod = wodMap("Field", "WOTextField",
				"value", "searchTerm",
				"size", "\"30\"");
		String html = "<webobject name=\"Field\">input</webobject>";
		ConversionResult result = ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX, false);
		assertEquals("<wo:WOTextField value=\"$searchTerm\" size=\"30\">input</wo:WOTextField>", result.getHtml());
	}

	@Test
	public void noSpacesAroundEquals_selfClosing() {
		Map<String, IWodElement> wod = wodMap("Img", "WOImage", "src", "\"photo.jpg\"");
		String html = "<webobject name=\"Img\"/>";
		ConversionResult result = ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX, false);
		assertEquals("<wo:WOImage src=\"photo.jpg\"/>", result.getHtml());
	}

	@Test
	public void spacesAroundEquals_explicitTrue() {
		// Verify explicit true matches the default convenience overload
		Map<String, IWodElement> wod = wodMap("Greeting", "WOString", "value", "name");
		String html = "<webobject name=\"Greeting\">Hello</webobject>";
		ConversionResult result = ConvertBundleToInlineTransformer.convert(html, wod, PREFIX, SUFFIX, true);
		assertEquals("<wo:WOString value = \"$name\">Hello</wo:WOString>", result.getHtml());
	}
}

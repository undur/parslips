package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectstyle.wolips.bindings.api.ApiextGenerator.FoundBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableChoose;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableRequires;

/**
 * {@link ApiToApiext}: a component's first {@code .apiext}, started from its hand-written
 * {@code .api}. The rule shapes are the ones WebObjects frameworks actually use (surveyed across
 * wonder-slim's 107 {@code .api} files). Each translation is checked for meaning, not spelling: the
 * {@code .apiext} is written back as an {@code .api} ({@link ApiextToApi}), and the two must agree
 * on whether a rule fires for every way of binding the bindings involved — unbound, bound to a
 * keypath, bound to a constant.
 */
public class ApiToApiextTest {

	@Rule
	public TemporaryFolder _temp = new TemporaryFolder();

	private static ApiSnapshot api(String bindings, String rules) throws Exception {
		return ApiParser.parseReader(new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<wodefinitions><wo wocomponentcontent=\"false\" class=\"X\">" + bindings + rules + "</wo></wodefinitions>"));
	}

	private static String bindings(String... names) {
		final StringBuilder b = new StringBuilder();
		for (final String name : names) {
			b.append("<binding name=\"").append(name).append("\"/>");
		}
		return b.toString();
	}

	private MutableApiextModel seeded(ApiSnapshot api, List<FoundBinding> found) throws Exception {
		final MutableApiextModel model = new MutableApiextModel(new File(_temp.getRoot(), "X.apiext"));
		assertEquals("every rule translates", List.of(), ApiToApiext.seed(model, api, found));
		return model;
	}

	/** The seeded .apiext, written back as .api. */
	private ApiSnapshot roundTrip(ApiSnapshot api) throws Exception {
		final StringWriter written = new StringWriter();
		ApiSerializer.serialize(ApiextToApi.convert(seeded(api, List.of()).toImmutable()), written);
		return ApiParser.parseReader(new StringReader(written.toString()));
	}

	/** Both fire, or neither does, for every way of binding the named bindings. */
	private void assertSameMeaning(ApiSnapshot original, String... names) throws Exception {
		final ApiSnapshot back = roundTrip(original);
		final String[] ways = { null, "someKey", "\"constant\"" };
		final int combinations = (int) Math.pow(ways.length, names.length);
		for (int c = 0; c < combinations; c++) {
			final Map<String, String> bound = new HashMap<>();
			int rest = c;
			for (final String name : names) {
				final String way = ways[rest % ways.length];
				rest /= ways.length;
				if (way != null) {
					bound.put(name, way);
				}
			}
			assertEquals("for " + bound, !original.getFailedValidations(bound).isEmpty(), !back.getFailedValidations(bound).isEmpty());
		}
	}

	private static MutableBinding binding(MutableApiextModel model, String name) {
		return model.bindings.stream().filter(b -> b.name.equals(name)).findFirst().orElseThrow();
	}

	// ---- the shapes in use ---------------------------------------------------------------

	@Test
	public void requiredUnbound() throws Exception {
		final ApiSnapshot api = api(bindings("componentName"), "<validation message=\"'componentName' is a required binding\"><unbound name=\"componentName\"/></validation>");
		assertTrue(binding(seeded(api, List.of()), "componentName").required);
		assertSameMeaning(api, "componentName");
	}

	@Test
	public void requiredAttribute() throws Exception {
		assertTrue(binding(seeded(api("<binding name=\"value\" required=\"YES\"/>", ""), List.of()), "value").required);
	}

	@Test
	public void mustBeSettableBecomesPush() throws Exception {
		final ApiSnapshot api = api(bindings("value"), "<validation message=\"'value' must be settable\"><unsettable name=\"value\"/></validation>");
		final MutableBinding value = binding(seeded(api, List.of(new FoundBinding("value", "java.lang.String", false))), "value");
		assertEquals("java.lang.String", value.pull.get(0).name);
		assertEquals("java.lang.String", value.push.get(0).name);
		assertSameMeaning(api, "value");
	}

	@Test
	public void onlyOneOfTwo() throws Exception {
		final ApiSnapshot api = api(bindings("updateContainerID", "ignoreActionResponse"), "<validation message=\"Only one of them\"><bound name=\"updateContainerID\"/><bound name=\"ignoreActionResponse\"/></validation>");
		final MutableChoose choose = (MutableChoose) seeded(api, List.of()).constraints.get(0);
		assertEquals(Integer.valueOf(1), choose.max);
		assertNull(choose.min);
		assertEquals("Only one of them", choose.message);
		assertSameMeaning(api, "updateContainerID", "ignoreActionResponse");
	}

	@Test
	public void notBothInAnAnd() throws Exception {
		assertSameMeaning(api(bindings("selectedValue", "selection"), "<validation message=\"not both\"><and><bound name=\"selectedValue\"/><bound name=\"selection\"/></and></validation>"), "selectedValue", "selection");
	}

	@Test
	public void requiresWhen() throws Exception {
		final ApiSnapshot api = api(bindings("updateContainerID", "action"), "<validation message=\"If 'updateContainerID' is bound, then 'action' must also be\"><bound name=\"updateContainerID\"/><unbound name=\"action\"/></validation>");
		final MutableRequires requires = (MutableRequires) seeded(api, List.of()).constraints.get(0);
		assertEquals("action", requires.binding);
		assertEquals(List.of("updateContainerID"), requires.when);
		assertSameMeaning(api, "updateContainerID", "action");
	}

	@Test
	public void eitherOfTwoMustBeBound() throws Exception {
		final ApiSnapshot api = api(bindings("list", "count"), "<validation message=\"Either 'list' or 'count' must be bound\"><and><unbound name=\"list\"/><unbound name=\"count\"/></and></validation>");
		assertEquals(Integer.valueOf(1), ((MutableChoose) seeded(api, List.of()).constraints.get(0)).min);
		assertSameMeaning(api, "list", "count");
	}

	@Test
	public void oneOfFiveMustBeBound() throws Exception {
		assertSameMeaning(api(bindings("href", "action", "pageName", "directActionName", "actionClass"),
				"<validation message=\"One of them\"><and><unbound name=\"href\"/><unbound name=\"action\"/><unbound name=\"pageName\"/><unbound name=\"directActionName\"/><unbound name=\"actionClass\"/></and></validation>"),
				"href", "action", "pageName", "directActionName", "actionClass");
	}

	@Test
	public void mustNotBeAConstant() throws Exception {
		final ApiSnapshot api = api(bindings("list"), "<validation message=\"'list' must not be a constant\"><and><bound name=\"list\"/><ungettable name=\"list\"/></and></validation>");
		final MutableRequires requires = (MutableRequires) seeded(api, List.of()).constraints.get(0);
		assertEquals(ApiextModel.Obligation.GETTABLE, requires.must);
		assertTrue(requires.when.isEmpty());
		assertSameMeaning(api, "list");
	}

	@Test
	public void settableWhenAnyOf() throws Exception {
		assertSameMeaning(api(bindings("item", "displayString", "value"),
				"<validation message=\"'item' may not be a constant when 'displayString' or 'value' are bound\"><and><or><bound name=\"displayString\"/><bound name=\"value\"/></or><unsettable name=\"item\"/></and></validation>"),
				"item", "displayString", "value");
	}

	@Test
	public void requiredWhenAnyOfInEitherOrder() throws Exception {
		assertSameMeaning(api(bindings("item", "displayString", "value"),
				"<validation message=\"'item' must be bound\"><and><unbound name=\"item\"/><or><bound name=\"displayString\"/><bound name=\"value\"/></or></and></validation>"),
				"item", "displayString", "value");
	}

	@Test
	public void countWithAnAnyOfAlternative() throws Exception {
		assertSameMeaning(api(bindings("action", "href", "directActionName", "actionClass"),
				"<validation message=\"Either one of 'action' or 'href', or 'directActionName'/'actionClass'\"><count test=\"&gt;1\"><bound name=\"action\"/><bound name=\"href\"/><or><bound name=\"directActionName\"/><bound name=\"actionClass\"/></or></count></validation>"),
				"action", "href", "directActionName", "actionClass");
	}

	@Test
	public void orOfUnboundMakesEachRequired() throws Exception {
		final ApiSnapshot api = api(bindings("list", "item", "listItemIDKeyPath"), "<validation message=\"all required\"><or><unbound name=\"list\"/><unbound name=\"item\"/><unbound name=\"listItemIDKeyPath\"/></or></validation>");
		final MutableApiextModel model = seeded(api, List.of());
		assertTrue(binding(model, "list").required && binding(model, "item").required && binding(model, "listItemIDKeyPath").required);
		assertSameMeaning(api, "list", "item", "listItemIDKeyPath");
	}

	@Test
	public void notTogetherWithAnyOf() throws Exception {
		assertSameMeaning(api(bindings("delegate", "parentKeyPath", "childrenKeyPath", "isLeafKeyPath"),
				"<validation message=\"not with delegate\"><and><or><bound name=\"parentKeyPath\"/><bound name=\"childrenKeyPath\"/><bound name=\"isLeafKeyPath\"/></or><bound name=\"delegate\"/></and></validation>"),
				"delegate", "parentKeyPath", "childrenKeyPath", "isLeafKeyPath");
	}

	// ---- what doesn't translate --------------------------------------------------------

	@Test
	public void allOfGroupDoesNotTranslateAndNothingIsSeeded() throws Exception {
		final ApiSnapshot api = api(bindings("parentKeyPath", "childrenKeyPath", "delegate"),
				"<validation message=\"'parentKeyPath' and 'childrenKeyPath', or 'delegate' must be bound\"><and><or><unbound name=\"parentKeyPath\"/><unbound name=\"childrenKeyPath\"/></or><unbound name=\"delegate\"/></and></validation>");
		final MutableApiextModel model = new MutableApiextModel(new File(_temp.getRoot(), "X.apiext"));
		assertEquals(List.of("'parentKeyPath' and 'childrenKeyPath', or 'delegate' must be bound"), ApiToApiext.seed(model, api, List.of()));
		assertTrue(model.bindings.isEmpty());
		assertTrue(model.constraints.isEmpty());
		assertFalse(model.isDirty());
	}

	@Test
	public void unnamedUntranslatableRuleIsNamedByItsBindings() throws Exception {
		final ApiSnapshot api = api(bindings("a", "b"), "<validation><not><bound name=\"a\"/><bound name=\"b\"/></not></validation>");
		assertEquals(List.of("an unnamed rule on 'a', 'b'"), ApiToApiext.seed(new MutableApiextModel(new File(_temp.getRoot(), "X.apiext")), api, List.of()));
	}

	// ---- the rest of the .api ------------------------------------------------------------

	@Test
	public void typesComeFromTheClassAndDefaultsPresetsCarryOver() throws Exception {
		final ApiSnapshot api = api("<binding name=\"value\" defaults=\"Date Format Strings\"/><binding name=\"unknownToTheClass\"/>", "");
		final MutableApiextModel model = seeded(api, List.of(new FoundBinding("value", "java.util.Date", true)));
		final MutableBinding value = binding(model, "value");
		assertEquals("Date Format Strings", value.defaults);
		assertEquals("java.util.Date", value.pull.get(0).name);
		assertEquals("java.util.Date", value.push.get(0).name);
		assertEquals("java.lang.Object", binding(model, "unknownToTheClass").pull.get(0).name);
		assertTrue(binding(model, "unknownToTheClass").push.isEmpty());
	}

	@Test
	public void requiredBindingARuleNamesWithoutDeclaringIsAdded() throws Exception {
		assertTrue(binding(seeded(api("", "<validation message=\"required\"><unbound name=\"hidden\"/></validation>"), List.of()), "hidden").required);
	}

	@Test
	public void componentContentMapsToAllowed() throws Exception {
		final ApiSnapshot wraps = ApiParser.parseReader(new StringReader("<?xml version=\"1.0\"?><wodefinitions><wo wocomponentcontent=\"true\" class=\"X\"/></wodefinitions>"));
		assertEquals(ApiextModel.Content.ALLOWED, seeded(wraps, List.of()).content);
		assertNull(seeded(api("", ""), List.of()).content);
	}

	@Test
	public void allowedRangeIsTheComplementOfTheTest() {
		assertArrayEquals(new int[] { 1, 1 }, ApiToApiext.allowedRange("!=1"));
		assertArrayEquals(new int[] { 0, 1 }, ApiToApiext.allowedRange(">1"));
		assertArrayEquals(new int[] { 0, 1 }, ApiToApiext.allowedRange(">=2"));
		assertArrayEquals(new int[] { 2, Integer.MAX_VALUE }, ApiToApiext.allowedRange("<2"));
		assertArrayEquals(new int[] { 2, Integer.MAX_VALUE }, ApiToApiext.allowedRange("<=1"));
		assertArrayEquals(new int[] { 1, Integer.MAX_VALUE }, ApiToApiext.allowedRange("==0"));
		assertArrayEquals(new int[] { 1, Integer.MAX_VALUE }, ApiToApiext.allowedRange("0"));
		assertNull(ApiToApiext.allowedRange("==2"));
		assertNull(ApiToApiext.allowedRange(">=0"));
		assertNull(ApiToApiext.allowedRange("many"));
	}

	@Test
	public void generatedApiReadsBackToTheSameConstraints() throws Exception {
		// What ApiextToApi writes, ApiToApiext reads: exactly-one, at-least, at-most and both bounds.
		final List<String> shapes = new ArrayList<>(List.of(
				"<choose min=\"1\" max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>",
				"<choose min=\"2\"><binding name=\"a\"/><binding name=\"b\"/><binding name=\"c\"/></choose>",
				"<choose max=\"1\"><binding name=\"a\"/><any-of><binding name=\"b\"/><binding name=\"c\"/></any-of></choose>",
				"<choose min=\"1\" max=\"2\"><binding name=\"a\"/><binding name=\"b\"/><binding name=\"c\"/></choose>",
				"<requires binding=\"c\" must=\"settable\"><any-of><binding name=\"a\"/><binding name=\"b\"/></any-of></requires>"));
		for (final String shape : shapes) {
			final String xml = "<?xml version=\"1.0\"?><wodefinitions><wo class=\"X\"><binding name=\"a\"/><binding name=\"b\"/><binding name=\"c\"/>" + shape + "</wo></wodefinitions>";
			final StringWriter written = new StringWriter();
			ApiSerializer.serialize(ApiextToApi.convert(ApiextModel.parse(xml.getBytes())), written);
			assertSameMeaning(ApiParser.parseReader(new StringReader(written.toString())), "a", "b", "c");
		}
	}
}

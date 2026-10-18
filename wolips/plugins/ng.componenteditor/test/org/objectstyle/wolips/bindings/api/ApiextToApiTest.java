package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * {@link ApiextToApi}: the {@code .api} written beside a WebObjects element's {@code .apiext}.
 * Each test goes through the whole path — parse the {@code .apiext}, convert, serialize as
 * {@code .api}, parse that back with {@link ApiParser} — and then asks the {@code .api} which rules
 * fire for given bindings, so what's checked is what the rules mean, not how they're spelled.
 */
public class ApiextToApiTest {

	private static ApiSnapshot api(String woAttributes, String woBody) throws Exception {
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<wodefinitions><wo class=\"X\"" + woAttributes + ">" + woBody + "</wo></wodefinitions>";
		final ApiextModel apiext = ApiextModel.parse(xml.getBytes(StandardCharsets.UTF_8));
		assertNotNull("the .apiext fixture must parse", apiext);
		final StringWriter written = new StringWriter();
		ApiSerializer.serialize(ApiextToApi.convert(apiext), "generated", written);
		return ApiParser.parseReader(new StringReader(written.toString()));
	}

	private static String binding(String name, String extra) {
		return "<binding name=\"" + name + "\"" + extra + "><pull><type>java.lang.Object</type></pull></binding>";
	}

	/** The messages of the rules that fire when exactly these bindings are bound (to keypaths). */
	private static List<String> failing(ApiSnapshot api, String... bound) {
		final Map<String, String> bindings = new HashMap<>();
		for (final String name : bound) {
			bindings.put(name, "someKey");
		}
		return api.getFailedValidations(bindings).stream().map(ApiValidation::getMessage).toList();
	}

	@Test
	public void bindingsAndDefaultsPresetCarryOver() throws Exception {
		final ApiSnapshot api = api("", binding("value", " defaults=\"Date Format Strings\"") + binding("escapeHTML", ""));
		assertEquals("X", api.getClassName());
		assertEquals(List.of("value", "escapeHTML"), api.getBindings().stream().map(IApiBinding::getName).toList());
		assertEquals("Date Format Strings", api.getBinding("value").getDefaults());
	}

	@Test
	public void requiredFiresOnlyWhenUnbound() throws Exception {
		final ApiSnapshot api = api("", binding("list", " required=\"true\"") + binding("item", ""));
		assertEquals(List.of("'list' is a required binding."), failing(api, "item"));
		assertTrue(failing(api, "list").isEmpty());
	}

	@Test
	public void exactlyOneFiresOnNoneAndOnTwo() throws Exception {
		final ApiSnapshot api = api("", binding("checked", "") + binding("value", "")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>");
		assertEquals(1, failing(api).size());
		assertTrue(failing(api, "checked").isEmpty());
		assertTrue(failing(api, "value").isEmpty());
		assertEquals(1, failing(api, "checked", "value").size());
	}

	@Test
	public void atMostOneFiresOnlyOnTwo() throws Exception {
		final ApiSnapshot api = api("", binding("a", "") + binding("b", "") + "<choose max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(failing(api).isEmpty());
		assertTrue(failing(api, "a").isEmpty());
		assertEquals(1, failing(api, "a", "b").size());
	}

	@Test
	public void anyOfAlternativeCountsOnceWhicheverOfItsBindingsIsBound() throws Exception {
		final ApiSnapshot api = api("", binding("action", "") + binding("actionClass", "") + binding("directActionName", "")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"action\"/><any-of><binding name=\"actionClass\"/><binding name=\"directActionName\"/></any-of></choose>");
		assertTrue(failing(api, "actionClass", "directActionName").isEmpty());
		assertEquals(1, failing(api, "action", "directActionName").size());
	}

	@Test
	public void requiresFiresWhenTheAntecedentHoldsAndTheConsequentIsUnbound() throws Exception {
		final ApiSnapshot api = api("", binding("selection", "") + binding("value", "") + "<requires binding=\"selection\" when=\"value\"/>");
		assertTrue(failing(api).isEmpty());
		assertEquals(1, failing(api, "value").size());
		assertTrue(failing(api, "value", "selection").isEmpty());
		assertTrue(failing(api, "selection").isEmpty());
	}

	@Test
	public void authorMessageOverridesTheGeneratedOne() throws Exception {
		final ApiSnapshot api = api("", binding("a", "") + binding("b", "") + "<requires binding=\"b\" when=\"a\" message=\"Bind b with a.\"/>");
		assertEquals(List.of("Bind b with a."), failing(api, "a"));
	}

	@Test
	public void pushedBindingMustBeSettable() throws Exception {
		final ApiSnapshot api = api("", "<binding name=\"value\"><pull><type>java.lang.String</type></pull><push><type>java.lang.String</type></push></binding>");
		final Map<String, String> constant = new HashMap<>();
		constant.put("value", "\"literal\"");
		assertEquals(List.of("'value' must be bound to a settable value."), api.getFailedValidations(constant).stream().map(ApiValidation::getMessage).toList());
		assertTrue(failing(api, "value").isEmpty());
		assertTrue(failing(api).isEmpty());
	}

	@Test
	public void componentContentUnlessForbiddenOrUndeclared() throws Exception {
		assertTrue(api(" content=\"allowed\"", binding("a", "")).isComponentContent());
		assertTrue(api(" content=\"expected\"", binding("a", "")).isComponentContent());
		assertFalse(api(" content=\"forbidden\"", binding("a", "")).isComponentContent());
		assertFalse(api("", binding("a", "")).isComponentContent());
	}

	@Test
	public void headerCommentIsWrittenAndHarmless() throws Exception {
		final StringWriter written = new StringWriter();
		ApiSerializer.serialize(new ApiSnapshot("X", false, List.of(new SimpleApiBinding("a")), List.of(), null), "Generated from X.apiext -- edit that", written);
		assertTrue(written.toString(), written.toString().contains("<!-- Generated from X.apiext - - edit that -->"));
		assertEquals("a", ApiParser.parseReader(new StringReader(written.toString())).getBindings().get(0).getName());
	}
}

package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Tests {@link ApiextJsonRenderer} — the machine-readable JSON view of an {@link ApiextModel} served
 * by the dev server's /elementApi endpoint. Asserts the shape and the load-bearing facts (directions,
 * interpretations, generated constraint messages, nullable policies) rather than exact byte output,
 * since JSON key order is an implementation detail.
 */
public class ApiextJsonRendererTest {

	private static final String CHECKBOX =
			"<?xml version=\"1.0\"?><wodefinitions>"
			+ "<wo class=\"WOCheckBox\" content=\"forbidden\" unknownAttributes=\"passthrough\">"
			+ "<doc>A checkbox.</doc>"
			+ "<binding name=\"checked\" required=\"true\">"
			+ "<pull><type interpretation=\"truthy\">java.lang.Object</type></pull>"
			+ "<push><type>java.lang.Boolean</type></push></binding>"
			+ "<binding name=\"value\"><pull><type>java.lang.Object</type></pull></binding>"
			+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>"
			+ "<requires binding=\"value\" when=\"checked\"/>"
			+ "</wo></wodefinitions>";

	private static String render(String xml) {
		final ApiextModel model = ApiextModel.parse(xml.getBytes(StandardCharsets.UTF_8));
		return ApiextJsonRenderer.render(model.getClassName(), model);
	}

	@Test
	public void rendersElementLevelFacts() {
		final String json = render(CHECKBOX);
		assertTrue(json.contains("\"name\":\"WOCheckBox\""));
		assertTrue(json.contains("\"content\":\"forbidden\""));
		assertTrue(json.contains("\"unknownAttributes\":\"passthrough\""));
		assertTrue(json.contains("\"deprecated\":false"));
	}

	@Test
	public void rendersDirectionAndInterpretation() {
		final String json = render(CHECKBOX);
		// checked is two-way, pulled by truthiness, pushed as Boolean.
		assertTrue(json.contains("\"direction\":\"both\""));
		assertTrue(json.contains("\"interpretation\":\"truthy\""));
		assertTrue(json.contains("java.lang.Boolean"));
		// value is pull-only.
		assertTrue(json.contains("\"direction\":\"pull\""));
	}

	@Test
	public void rendersRequiredFlag() {
		assertTrue(render(CHECKBOX).contains("\"required\":true"));
	}

	@Test
	public void rendersConstraintsWithGeneratedMessages() {
		final String json = render(CHECKBOX);
		assertTrue(json.contains("\"kind\":\"choose\""));
		assertTrue(json.contains("\"kind\":\"requires\""));
		// The generated message the hover would show — not authored in the file.
		assertTrue(json.contains("must be bound"));
	}

	@Test
	public void absentPolicyIsJsonNull() {
		// No content / unknownAttributes attributes → the fields are null, not synthesized.
		final String xml = "<?xml version=\"1.0\"?><wodefinitions><wo class=\"Bare\">"
				+ "<binding name=\"x\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "</wo></wodefinitions>";
		final String json = render(xml);
		assertTrue(json.contains("\"content\":null"));
		assertTrue(json.contains("\"unknownAttributes\":null"));
	}

	@Test
	public void escapesQuotesInDoc() {
		final String xml = "<?xml version=\"1.0\"?><wodefinitions><wo class=\"X\">"
				+ "<doc><![CDATA[say \"hi\"]]></doc>"
				+ "<binding name=\"x\"><pull><type>java.lang.Object</type></pull></binding>"
				+ "</wo></wodefinitions>";
		assertTrue(render(xml).contains("say \\\"hi\\\""));
	}
}

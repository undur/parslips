package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.objectstyle.wolips.bindings.api.ApiextTemplateEvaluator.Diagnostic;

/**
 * Tests for {@link ApiextTemplateEvaluator} — evaluating an element's {@code .apiext} contract against
 * the bindings actually written on a template tag (Milestone 2: {@code .apiext} enforcing, not just
 * describing). Pure logic over the model + a set of bound names.
 */
public class ApiextTemplateEvaluatorTest {

	private static ApiextModel parse(String woBody) {
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<wodefinitions><wo class=\"X\"" + woBody + "</wo></wodefinitions>";
		return ApiextModel.parse(xml.getBytes(StandardCharsets.UTF_8));
	}

	/** Builds a <wo ...> body: pass the attributes-and-close ">" then children. */
	private static ApiextModel model(String attrsAndChildren) {
		return parse(attrsAndChildren);
	}

	private static String binding(String name) {
		return "<binding name=\"" + name + "\"><pull><type>java.lang.Object</type></pull></binding>";
	}

	private static Set<String> bound(String... names) {
		return new java.util.LinkedHashSet<>(java.util.Arrays.asList(names));
	}

	private static boolean hasError(List<Diagnostic> ds, String needle) {
		return ds.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage().contains(needle));
	}

	private static boolean hasWarning(List<Diagnostic> ds, String needle) {
		return ds.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.WARNING && d.getMessage().contains(needle));
	}

	// ---- required -----------------------------------------------------------

	@Test
	public void requiredBinding_unbound_errors() {
		ApiextModel m = model(">"
				+ "<binding name=\"value\" required=\"true\"><pull><type>java.lang.Object</type></pull></binding>");
		List<Diagnostic> ds = ApiextTemplateEvaluator.evaluate(m, bound());
		assertTrue(hasError(ds, "'value' is required"));
	}

	@Test
	public void requiredBinding_bound_ok() {
		ApiextModel m = model(">"
				+ "<binding name=\"value\" required=\"true\"><pull><type>java.lang.Object</type></pull></binding>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("value")).isEmpty());
	}

	// ---- choose cardinality -------------------------------------------------

	@Test
	public void chooseExactlyOne_zeroBound_errors() {
		ApiextModel m = model(">" + binding("a") + binding("b")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound()), "Exactly one"));
	}

	@Test
	public void chooseExactlyOne_oneBound_ok() {
		ApiextModel m = model(">" + binding("a") + binding("b")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("a")).isEmpty());
	}

	@Test
	public void chooseExactlyOne_bothBound_errors() {
		ApiextModel m = model(">" + binding("a") + binding("b")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound("a", "b")), "Exactly one"));
	}

	@Test
	public void chooseAtMostOne_bothBound_errors() {
		ApiextModel m = model(">" + binding("a") + binding("b")
				+ "<choose max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound("a", "b")), "At most one"));
	}

	@Test
	public void chooseAtMostOne_noneBound_ok() {
		ApiextModel m = model(">" + binding("a") + binding("b")
				+ "<choose max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound()).isEmpty());
	}

	@Test
	public void anyOfCountsAsOneAlternative_underMax() {
		// exactly-one over { a, (b or c) }; binding BOTH b and c is one satisfied alternative → legal.
		ApiextModel m = model(">" + binding("a") + binding("b") + binding("c")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"a\"/>"
				+ "<any-of><binding name=\"b\"/><binding name=\"c\"/></any-of></choose>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("b", "c")).isEmpty());
		// but a + b is two satisfied alternatives → error.
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound("a", "b")), "Exactly one"));
	}

	// ---- requires -----------------------------------------------------------

	@Test
	public void requiresWhen_antecedentBound_consequentMissing_errors() {
		ApiextModel m = model(">" + binding("x") + binding("y")
				+ "<requires binding=\"y\" when=\"x\"/>");
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound("x")), "'y' must be bound"));
	}

	@Test
	public void requiresWhen_antecedentUnbound_ok() {
		ApiextModel m = model(">" + binding("x") + binding("y")
				+ "<requires binding=\"y\" when=\"x\"/>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound()).isEmpty());
	}

	@Test
	public void requiresSettable_notEvaluatedFromNames() {
		// settable/gettable need value analysis; the name-based evaluator must not flag them.
		ApiextModel m = model(">" + binding("item") + binding("value")
				+ "<requires binding=\"item\" must=\"settable\" when=\"value\"/>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("value")).isEmpty());
	}

	// ---- unknownAttributes --------------------------------------------------

	@Test
	public void forbidden_undeclaredBound_errors() {
		ApiextModel m = model(" unknownAttributes=\"forbidden\">" + binding("known"));
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound("known", "mystery")), "'mystery' is not a declared binding"));
	}

	@Test
	public void passthrough_undeclaredBound_ok() {
		ApiextModel m = model(" unknownAttributes=\"passthrough\">" + binding("known"));
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("known", "data-foo")).isEmpty());
	}

	@Test
	public void absentPolicy_undeclaredBound_ok() {
		ApiextModel m = model(">" + binding("known"));
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound("known", "whatever")).isEmpty());
	}

	// ---- deprecation --------------------------------------------------------

	@Test
	public void deprecatedBinding_bound_warns() {
		ApiextModel m = model(">"
				+ "<binding name=\"old\"><pull><type>java.lang.Object</type></pull><deprecated>Use `new`.</deprecated></binding>");
		List<Diagnostic> ds = ApiextTemplateEvaluator.evaluate(m, bound("old"));
		assertTrue(hasWarning(ds, "'old' is deprecated. Use `new`."));
	}

	@Test
	public void deprecatedBinding_unbound_silent() {
		ApiextModel m = model(">"
				+ "<binding name=\"old\"><pull><type>java.lang.Object</type></pull><deprecated>x</deprecated></binding>");
		assertTrue(ApiextTemplateEvaluator.evaluate(m, bound()).isEmpty());
	}

	// ---- bound means bound (default never counts) ---------------------------

	@Test
	public void defaultDoesNotSatisfyRequired() {
		// A declared <default> must NOT make a required binding count as bound.
		ApiextModel m = model(">"
				+ "<binding name=\"morph\" required=\"true\"><pull><type>java.lang.Boolean</type></pull><default>true</default></binding>");
		assertTrue(hasError(ApiextTemplateEvaluator.evaluate(m, bound()), "'morph' is required"));
	}

	@Test
	public void bindingTargetedDiagnostic_carriesBindingName() {
		ApiextModel m = model(">"
				+ "<binding name=\"value\" required=\"true\"><pull><type>java.lang.Object</type></pull></binding>");
		List<Diagnostic> ds = ApiextTemplateEvaluator.evaluate(m, bound());
		assertEquals(1, ds.size());
		assertEquals("value", ds.get(0).getBindingName());
	}

	@Test
	public void nullModel_noDiagnostics() {
		assertFalse(hasError(ApiextTemplateEvaluator.evaluate(null, bound("a")), ""));
	}
}

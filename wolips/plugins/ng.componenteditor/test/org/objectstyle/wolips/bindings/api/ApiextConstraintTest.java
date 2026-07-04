package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;
import org.objectstyle.wolips.bindings.api.ApiextConstraintValidator.Problem;
import org.objectstyle.wolips.bindings.api.ApiextConstraintValidator.Severity;
import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Obligation;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;

/**
 * Tests for {@code .apiext} typed cross-binding constraints: parsing {@code <choose>}/{@code <any-of>}/
 * {@code <requires>} into {@link ApiextModel}, the consumer-enforced integrity checks
 * ({@link ApiextConstraintValidator}), and generated messages ({@link ApiextConstraintMessages}).
 *
 * <p>The valid cases mirror the real converted fixtures (WOCheckBox, AjaxUpdateLink, AjaxPopUpButton)
 * so this doubles as the acceptance suite for the format spec; the invalid cases pin down the
 * structural rules the DTD cannot express.
 */
public class ApiextConstraintTest {

	private static ApiextModel parse(String woBody) {
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<wodefinitions><wo class=\"X\">" + woBody + "</wo></wodefinitions>";
		return ApiextModel.parse(xml.getBytes(StandardCharsets.UTF_8));
	}

	/** Minimal declared bindings so reference integrity passes; append constraints after. */
	private static String bindings(String... names) {
		final StringBuilder b = new StringBuilder();
		for (final String n : names) {
			b.append("<binding name=\"").append(n).append("\"><pull><type>java.lang.Object</type></pull></binding>");
		}
		return b.toString();
	}

	// ---- parsing ------------------------------------------------------------

	@Test
	public void parsesChoose_minMax() {
		ApiextModel m = parse(bindings("checked", "value")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>");
		assertEquals(1, m.getConstraints().size());
		Choose c = (Choose) m.getConstraints().get(0);
		assertEquals(Integer.valueOf(1), c.getMin());
		assertEquals(Integer.valueOf(1), c.getMax());
		assertEquals(2, c.getAlternatives().size());
		assertFalse(c.getAlternatives().get(0).isAnyOf());
	}

	@Test
	public void parsesChoose_absentBoundsAreNull() {
		ApiextModel m = parse(bindings("a", "b")
				+ "<choose max=\"1\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		Choose c = (Choose) m.getConstraints().get(0);
		assertNull(c.getMin());
		assertEquals(Integer.valueOf(1), c.getMax());
	}

	@Test
	public void parsesChoose_anyOfAlternative() {
		ApiextModel m = parse(bindings("action", "actionClass", "directActionName")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"action\"/>"
				+ "<any-of><binding name=\"actionClass\"/><binding name=\"directActionName\"/></any-of></choose>");
		Choose c = (Choose) m.getConstraints().get(0);
		assertEquals(2, c.getAlternatives().size());
		Alternative anyOf = c.getAlternatives().get(1);
		assertTrue(anyOf.isAnyOf());
		assertEquals(2, anyOf.getBindingNames().size());
	}

	@Test
	public void parsesRequires_whenAttribute() {
		ApiextModel m = parse(bindings("replaceID", "directActionName")
				+ "<requires binding=\"replaceID\" when=\"directActionName\"/>");
		Requires r = (Requires) m.getConstraints().get(0);
		assertEquals("replaceID", r.getBinding());
		assertEquals(Obligation.BOUND, r.getMust());
		assertFalse(r.getAntecedent().isAnyOf());
		assertEquals("directActionName", r.getAntecedent().getBindingNames().get(0));
	}

	@Test
	public void parsesRequires_anyOfAntecedentAndMust() {
		ApiextModel m = parse(bindings("item", "displayString", "value")
				+ "<requires binding=\"item\" must=\"settable\">"
				+ "<any-of><binding name=\"displayString\"/><binding name=\"value\"/></any-of></requires>");
		Requires r = (Requires) m.getConstraints().get(0);
		assertEquals(Obligation.SETTABLE, r.getMust());
		assertTrue(r.getAntecedent().isAnyOf());
	}

	@Test
	public void parsesRequires_unconditional() {
		ApiextModel m = parse(bindings("list")
				+ "<requires binding=\"list\" must=\"gettable\"/>");
		Requires r = (Requires) m.getConstraints().get(0);
		assertEquals(Obligation.GETTABLE, r.getMust());
		assertNull(r.getAntecedent());
	}

	// ---- the WOCheckBox fixture (mutual <requires> all-or-none) --------------

	@Test
	public void checkBoxFixture_parsesAndValidatesClean() {
		ApiextModel m = parse(bindings("checked", "value", "selection")
				+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>"
				+ "<requires binding=\"selection\" when=\"value\"/>"
				+ "<requires binding=\"value\" when=\"selection\"/>");
		assertEquals(3, m.getConstraints().size());
		assertTrue(ApiextConstraintValidator.validate(m).isEmpty());
	}

	// ---- integrity: reference integrity ------------------------------------

	@Test
	public void referenceIntegrity_typoInChooseIsError() {
		ApiextModel m = parse(bindings("checked", "value")
				+ "<choose max=\"1\"><binding name=\"checked\"/><binding name=\"valeu\"/></choose>");
		List<Problem> ps = ApiextConstraintValidator.validate(m);
		assertTrue(hasError(ps, "valeu"));
	}

	@Test
	public void referenceIntegrity_typoInRequiresConsequent() {
		ApiextModel m = parse(bindings("value")
				+ "<requires binding=\"seelction\" when=\"value\"/>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "seelction"));
	}

	@Test
	public void referenceIntegrity_typoInAnyOfMember() {
		ApiextModel m = parse(bindings("item", "displayString", "value")
				+ "<requires binding=\"item\"><any-of><binding name=\"displayStirng\"/><binding name=\"value\"/></any-of></requires>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "displayStirng"));
	}

	// ---- integrity: structural ---------------------------------------------

	@Test
	public void chooseWithOneAlternativeIsError() {
		ApiextModel m = parse(bindings("a") + "<choose min=\"1\"><binding name=\"a\"/></choose>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "at least 2 alternatives"));
	}

	@Test
	public void chooseWithNoBoundsIsError() {
		ApiextModel m = parse(bindings("a", "b") + "<choose><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "at least one of min or max"));
	}

	@Test
	public void chooseMaxExceedingAlternativesIsError() {
		ApiextModel m = parse(bindings("a", "b") + "<choose max=\"3\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "exceeds the number of alternatives"));
	}

	@Test
	public void chooseMinGreaterThanMaxIsError() {
		ApiextModel m = parse(bindings("a", "b", "c")
				+ "<choose min=\"2\" max=\"1\"><binding name=\"a\"/><binding name=\"b\"/><binding name=\"c\"/></choose>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "must not exceed max"));
	}

	@Test
	public void unconditionalRequiresBoundIsError() {
		ApiextModel m = parse(bindings("x") + "<requires binding=\"x\"/>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "required=\"true\""));
	}

	@Test
	public void unconditionalRequiresSettableIsValid() {
		ApiextModel m = parse(bindings("list") + "<requires binding=\"list\" must=\"gettable\"/>");
		assertTrue(ApiextConstraintValidator.validate(m).isEmpty());
	}

	// ---- generated messages -------------------------------------------------

	@Test
	public void message_exactlyOne() {
		Choose c = new Choose(1, 1, java.util.Arrays.asList(
				Alternative.binding("a"), Alternative.binding("b"), Alternative.binding("c")), null);
		assertEquals("Exactly one of 'a', 'b' and 'c' must be bound.", ApiextConstraintMessages.describe(c));
	}

	@Test
	public void message_atMostOne() {
		Choose c = new Choose(null, 1, java.util.Arrays.asList(
				Alternative.binding("dateFormat"), Alternative.binding("formatter")), null);
		assertEquals("At most one of 'dateFormat' and 'formatter' may be bound.", ApiextConstraintMessages.describe(c));
	}

	@Test
	public void message_atLeastOne() {
		Choose c = new Choose(1, null, java.util.Arrays.asList(
				Alternative.binding("action"), Alternative.binding("directActionName")), null);
		assertEquals("At least one of 'action' and 'directActionName' must be bound.", ApiextConstraintMessages.describe(c));
	}

	@Test
	public void message_requiresWhen() {
		Requires r = new Requires("replaceID", Obligation.BOUND, Alternative.binding("directActionName"), null);
		assertEquals("'replaceID' must be bound when 'directActionName' is bound.", ApiextConstraintMessages.describe(r));
	}

	@Test
	public void message_requiresAnyOfSettable() {
		Requires r = new Requires("item", Obligation.SETTABLE,
				new Alternative(java.util.Arrays.asList("displayString", "value")), null);
		assertEquals("'item' must be settable (assignable, not a constant) when 'displayString' or 'value' is bound.",
				ApiextConstraintMessages.describe(r));
	}

	@Test
	public void message_unconditionalGettable() {
		Requires r = new Requires("list", Obligation.GETTABLE, null, null);
		assertEquals("'list' must be a keypath, not a constant.", ApiextConstraintMessages.describe(r));
	}

	@Test
	public void authorMessageOverrideIsPreserved() {
		ApiextModel m = parse(bindings("a", "b")
				+ "<choose max=\"1\" message=\"pick one, please\"><binding name=\"a\"/><binding name=\"b\"/></choose>");
		Constraint c = m.getConstraints().get(0);
		assertEquals("pick one, please", c.getMessage());
	}

	// ---- legacy-cord cleanup (#12-#19) --------------------------------------

	@Test
	public void legacyValidationInApiextIsError() {
		ApiextModel m = parse(bindings("a")
				+ "<validation message=\"x\"><bound name=\"a\"/></validation>");
		assertTrue(m.getConstraints().isEmpty()); // not parsed as a constraint
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "not part of the .apiext grammar"));
	}

	@Test
	public void legacyDocumentationInApiextIsError() {
		ApiextModel m = parse(bindings("a") + "<documentation path=\"x\"/>");
		assertTrue(hasError(ApiextConstraintValidator.validate(m), "not part of the .apiext grammar"));
	}

	@Test
	public void wrapsContentIsRead_notWocomponentcontent() {
		ApiextModel m = ApiextModel.parse(("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" wrapsContent=\"true\">" + bindings("a") + "</wo></wodefinitions>").getBytes(StandardCharsets.UTF_8));
		assertTrue(m.isComponentContent());
	}

	@Test
	public void legacyWocomponentcontentIsIgnoredInApiext() {
		// A file still using the old attribute name simply doesn't set wrapsContent (defaults false).
		ApiextModel m = ApiextModel.parse(("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" wocomponentcontent=\"true\">" + bindings("a") + "</wo></wodefinitions>").getBytes(StandardCharsets.UTF_8));
		assertFalse(m.isComponentContent());
	}

	@Test
	public void strictBooleans_legacyYesIsNotTrue() {
		ApiextModel m = parse("<binding name=\"a\" required=\"YES\"><pull><type>java.lang.Object</type></pull></binding>");
		assertFalse(m.getBindings().get(0).isRequired()); // "YES" is not the enum value "true"
	}

	// ---- #1 unknownAttributes ------------------------------------------------

	@Test
	public void unknownAttributes_parsedAndPassthroughDerived() {
		ApiextModel m = ApiextModel.parse(("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" unknownAttributes=\"passthrough\">" + bindings("a") + "</wo></wodefinitions>").getBytes(StandardCharsets.UTF_8));
		assertEquals(ApiextModel.UnknownAttributes.PASSTHROUGH, m.getUnknownAttributes());
		assertTrue(m.isPassthrough()); // derived from the policy
	}

	@Test
	public void unknownAttributes_forbiddenIsNotPassthrough() {
		ApiextModel m = ApiextModel.parse(("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\" unknownAttributes=\"forbidden\">" + bindings("a") + "</wo></wodefinitions>").getBytes(StandardCharsets.UTF_8));
		assertEquals(ApiextModel.UnknownAttributes.FORBIDDEN, m.getUnknownAttributes());
		assertFalse(m.isPassthrough());
	}

	@Test
	public void unknownAttributes_absentIsNull_notSynthesized() {
		// The enum deliberately has no default: absent means "author stated no policy".
		ApiextModel m = parse(bindings("a"));
		assertNull(m.getUnknownAttributes());
		assertFalse(m.isPassthrough());
	}

	// ---- #3 <default> --------------------------------------------------------

	@Test
	public void defaultValue_parsed() {
		ApiextModel m = parse("<binding name=\"morph\"><pull><type>java.lang.Boolean</type></pull><default>true</default></binding>");
		assertEquals("true", m.getBindings().get(0).getDefaultValue());
	}

	@Test
	public void defaultValue_absentIsNull() {
		ApiextModel m = parse(bindings("a"));
		assertNull(m.getBindings().get(0).getDefaultValue());
	}

	// ---- #5 <deprecated> -----------------------------------------------------

	@Test
	public void deprecated_withNote() {
		ApiextModel m = parse("<binding name=\"old\"><pull><type>java.lang.String</type></pull>"
				+ "<deprecated>Use `new` instead.</deprecated></binding>");
		ApiextModel.Binding b = m.getBindings().get(0);
		assertTrue(b.isDeprecated());
		assertEquals("Use `new` instead.", b.getDeprecationNote());
	}

	@Test
	public void deprecated_emptyNoteStillDeprecated() {
		// Presence marks deprecation; the null-vs-empty distinction matters.
		ApiextModel m = parse("<binding name=\"old\"><pull><type>java.lang.String</type></pull><deprecated></deprecated></binding>");
		ApiextModel.Binding b = m.getBindings().get(0);
		assertTrue(b.isDeprecated());
		assertEquals("", b.getDeprecationNote());
	}

	@Test
	public void notDeprecated_isNull() {
		ApiextModel m = parse(bindings("a"));
		ApiextModel.Binding b = m.getBindings().get(0);
		assertFalse(b.isDeprecated());
		assertNull(b.getDeprecationNote());
	}

	@Test
	public void elementLevelDeprecation() {
		ApiextModel m = ApiextModel.parse(("<?xml version=\"1.0\"?><wodefinitions>"
				+ "<wo class=\"X\">" + bindings("a") + "<deprecated>Whole element is legacy.</deprecated></wo></wodefinitions>").getBytes(StandardCharsets.UTF_8));
		assertTrue(m.isDeprecated());
		assertEquals("Whole element is legacy.", m.getDeprecationNote());
	}

	@Test
	public void deprecatedPlusRequiredIsWarning() {
		ApiextModel m = parse("<binding name=\"x\" required=\"true\"><pull><type>java.lang.String</type></pull><deprecated>gone soon</deprecated></binding>");
		List<Problem> ps = ApiextConstraintValidator.validate(m);
		assertTrue(ps.stream().anyMatch(p -> p.getSeverity() == Severity.WARNING
				&& p.getMessage().contains("required and deprecated")));
	}

	// ---- helpers ------------------------------------------------------------

	private static boolean hasError(List<Problem> problems, String substring) {
		return problems.stream().anyMatch(p -> p.getSeverity() == Severity.ERROR && p.getMessage().contains(substring));
	}
}

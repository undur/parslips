package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for {@link ApiValidation#evaluate(Map)} — the pure-functional
 * validation engine that checks binding constraints at validation time.
 *
 * <p>These tests construct validation trees programmatically (not from XML)
 * and verify that evaluate() produces correct boolean results for various
 * binding configurations.
 */
public class ApiValidationTest {

	// ---- Leaf predicates ----------------------------------------------------

	@Test
	public void bound_true_whenBindingPresent() {
		ApiValidation bound = new ApiValidation(ApiValidation.Kind.BOUND, "item");
		assertTrue(bound.evaluate(Map.of("item", "value")));
	}

	@Test
	public void bound_false_whenBindingMissing() {
		ApiValidation bound = new ApiValidation(ApiValidation.Kind.BOUND, "item");
		assertFalse(bound.evaluate(Collections.emptyMap()));
	}

	@Test
	public void unbound_true_whenBindingMissing() {
		ApiValidation unbound = new ApiValidation(ApiValidation.Kind.UNBOUND, "item");
		assertTrue(unbound.evaluate(Collections.emptyMap()));
	}

	@Test
	public void unbound_false_whenBindingPresent() {
		ApiValidation unbound = new ApiValidation(ApiValidation.Kind.UNBOUND, "item");
		assertFalse(unbound.evaluate(Map.of("item", "value")));
	}

	@Test
	public void settable_true_whenValueIsKeypath() {
		ApiValidation settable = new ApiValidation(ApiValidation.Kind.SETTABLE, "item");
		assertTrue("Unquoted value is settable", settable.evaluate(Map.of("item", "myKeypath")));
	}

	@Test
	public void settable_false_whenValueIsConstant() {
		ApiValidation settable = new ApiValidation(ApiValidation.Kind.SETTABLE, "item");
		assertFalse("Quoted value is a constant, not settable",
			settable.evaluate(Map.of("item", "\"hello\"")));
	}

	@Test
	public void settable_true_whenValueIsOgnlExpression() {
		ApiValidation settable = new ApiValidation(ApiValidation.Kind.SETTABLE, "item");
		assertTrue("Quoted tilde (~) is an OGNL expression, considered settable",
			settable.evaluate(Map.of("item", "\"~myExpression\"")));
	}

	@Test
	public void settable_false_whenBindingMissing() {
		ApiValidation settable = new ApiValidation(ApiValidation.Kind.SETTABLE, "item");
		assertFalse("Missing binding is not settable",
			settable.evaluate(Collections.emptyMap()));
	}

	@Test
	public void unsettable_true_whenValueIsConstant() {
		ApiValidation unsettable = new ApiValidation(ApiValidation.Kind.UNSETTABLE, "item");
		assertTrue(unsettable.evaluate(Map.of("item", "\"constant\"")));
	}

	@Test
	public void unsettable_false_whenValueIsKeypath() {
		ApiValidation unsettable = new ApiValidation(ApiValidation.Kind.UNSETTABLE, "item");
		assertFalse(unsettable.evaluate(Map.of("item", "myKeypath")));
	}

	@Test
	public void unsettable_false_whenValueIsOgnl() {
		ApiValidation unsettable = new ApiValidation(ApiValidation.Kind.UNSETTABLE, "item");
		assertFalse("OGNL expression is not a constant",
			unsettable.evaluate(Map.of("item", "\"~expr\"")));
	}

	@Test
	public void gettable_sameAsSettable() {
		ApiValidation gettable = new ApiValidation(ApiValidation.Kind.GETTABLE, "item");
		assertTrue(gettable.evaluate(Map.of("item", "keypath")));
		assertFalse(gettable.evaluate(Map.of("item", "\"constant\"")));
	}

	@Test
	public void ungettable_sameAsUnsettable() {
		ApiValidation ungettable = new ApiValidation(ApiValidation.Kind.UNGETTABLE, "item");
		assertTrue(ungettable.evaluate(Map.of("item", "\"constant\"")));
		assertFalse(ungettable.evaluate(Map.of("item", "keypath")));
	}

	// ---- Composite operators ------------------------------------------------

	@Test
	public void and_true_whenAllChildrenTrue() {
		ApiValidation and = new ApiValidation(ApiValidation.Kind.AND, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(and.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void and_false_whenOneChildFalse() {
		ApiValidation and = new ApiValidation(ApiValidation.Kind.AND, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertFalse(and.evaluate(Map.of("a", "1")));
	}

	@Test
	public void and_true_whenEmpty() {
		ApiValidation and = new ApiValidation(ApiValidation.Kind.AND, null, null, List.of());
		assertTrue("Empty AND is vacuously true", and.evaluate(Collections.emptyMap()));
	}

	@Test
	public void or_true_whenOneChildTrue() {
		ApiValidation or = new ApiValidation(ApiValidation.Kind.OR, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(or.evaluate(Map.of("a", "1")));
	}

	@Test
	public void or_false_whenNoChildrenTrue() {
		ApiValidation or = new ApiValidation(ApiValidation.Kind.OR, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertFalse(or.evaluate(Collections.emptyMap()));
	}

	@Test
	public void or_false_whenEmpty() {
		ApiValidation or = new ApiValidation(ApiValidation.Kind.OR, null, null, List.of());
		assertFalse("Empty OR has no true child", or.evaluate(Collections.emptyMap()));
	}

	@Test
	public void not_true_whenAllChildrenFalse() {
		ApiValidation not = new ApiValidation(ApiValidation.Kind.NOT, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(not.evaluate(Collections.emptyMap()));
	}

	@Test
	public void not_false_whenAnyChildTrue() {
		ApiValidation not = new ApiValidation(ApiValidation.Kind.NOT, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a")
		));
		assertFalse(not.evaluate(Map.of("a", "1")));
	}

	// ---- COUNT operator -----------------------------------------------------

	@Test
	public void count_equals() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "==2", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b"),
			new ApiValidation(ApiValidation.Kind.BOUND, "c")
		));
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2")));
		assertFalse(count.evaluate(Map.of("a", "1")));
		assertFalse(count.evaluate(Map.of("a", "1", "b", "2", "c", "3")));
	}

	@Test
	public void count_notEquals() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "!=1", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(count.evaluate(Collections.emptyMap()));  // 0 != 1
		assertFalse(count.evaluate(Map.of("a", "1")));       // 1 != 1 is false
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2"))); // 2 != 1
	}

	@Test
	public void count_greaterThan() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, ">1", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b"),
			new ApiValidation(ApiValidation.Kind.BOUND, "c")
		));
		assertFalse(count.evaluate(Map.of("a", "1")));
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void count_lessThan() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "<2", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b"),
			new ApiValidation(ApiValidation.Kind.BOUND, "c")
		));
		assertTrue(count.evaluate(Map.of("a", "1")));
		assertFalse(count.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void count_greaterThanOrEqual() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, ">=2", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b"),
			new ApiValidation(ApiValidation.Kind.BOUND, "c")
		));
		assertFalse(count.evaluate(Map.of("a", "1")));
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2")));
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2", "c", "3")));
	}

	@Test
	public void count_lessThanOrEqual() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "<=1", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(count.evaluate(Collections.emptyMap()));
		assertTrue(count.evaluate(Map.of("a", "1")));
		assertFalse(count.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void count_singleEqualsOperator() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "=1", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(count.evaluate(Map.of("a", "1")));
		assertFalse(count.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void count_reverseOperatorOrder() {
		// => is accepted as >=
		ApiValidation count1 = new ApiValidation(ApiValidation.Kind.COUNT, null, "=>2", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(count1.evaluate(Map.of("a", "1", "b", "2")));

		// =< is accepted as <=
		ApiValidation count2 = new ApiValidation(ApiValidation.Kind.COUNT, null, "=<1", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b")
		));
		assertTrue(count2.evaluate(Map.of("a", "1")));
		assertFalse(count2.evaluate(Map.of("a", "1", "b", "2")));
	}

	@Test
	public void count_noOperator_defaultsToEquals() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, "2", List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a"),
			new ApiValidation(ApiValidation.Kind.BOUND, "b"),
			new ApiValidation(ApiValidation.Kind.BOUND, "c")
		));
		assertTrue(count.evaluate(Map.of("a", "1", "b", "2")));
		assertFalse(count.evaluate(Map.of("a", "1")));
	}

	@Test
	public void count_nullTest_alwaysTrue() {
		ApiValidation count = new ApiValidation(ApiValidation.Kind.COUNT, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.BOUND, "a")
		));
		assertTrue(count.evaluate(Collections.emptyMap()));
		assertTrue(count.evaluate(Map.of("a", "1")));
	}

	// ---- VALIDATION (top-level) has AND semantics ---------------------------

	@Test
	public void validation_hasAndSemantics() {
		ApiValidation validation = new ApiValidation(ApiValidation.Kind.VALIDATION,
			"All must be bound.", null, List.of(
				new ApiValidation(ApiValidation.Kind.BOUND, "a"),
				new ApiValidation(ApiValidation.Kind.BOUND, "b")
			));
		assertTrue(validation.evaluate(Map.of("a", "1", "b", "2")));
		assertFalse(validation.evaluate(Map.of("a", "1")));
	}

	// ---- isAffectedByBindingNamed -------------------------------------------

	@Test
	public void isAffectedByBindingNamed_leafMatch() {
		ApiValidation bound = new ApiValidation(ApiValidation.Kind.BOUND, "item");
		assertTrue(bound.isAffectedByBindingNamed("item"));
		assertFalse(bound.isAffectedByBindingNamed("other"));
	}

	@Test
	public void isAffectedByBindingNamed_nestedMatch() {
		ApiValidation tree = new ApiValidation(ApiValidation.Kind.AND, null, null, List.of(
			new ApiValidation(ApiValidation.Kind.OR, null, null, List.of(
				new ApiValidation(ApiValidation.Kind.BOUND, "deep")
			))
		));
		assertTrue(tree.isAffectedByBindingNamed("deep"));
		assertFalse(tree.isAffectedByBindingNamed("missing"));
	}
}

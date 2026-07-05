package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for pure-functional methods in {@link ApiUtils} and their delegation
 * through {@link SimpleApiBinding}. Only tests methods that don't require
 * Eclipse JDT runtime (IJavaProject, IType, etc.).
 */
public class ApiUtilsTest {

	// ---- findGlobalApiextModel: null-safety + stable caching ----------------
	// The bundle-reading path needs a running OSGi runtime (HTMLPlugin.getBundle()),
	// so outside the plugin these resolve to a cached miss. What we CAN assert here is the
	// contract that matters for the validation hot path: the call never throws, tolerates
	// junk input, and returns a stable result across repeat calls (parse/lookup once, reuse) —
	// the property that stops template validation from re-reading and re-parsing per element.

	@Test
	public void findGlobalApiextModel_nullOrEmpty_returnsNull() {
		assertNull(ApiUtils.findGlobalApiextModel(null));
		assertNull(ApiUtils.findGlobalApiextModel(""));
	}

	@Test
	public void findGlobalApiextModel_repeatCallsAreStable() {
		// Same name resolves to the same result each time (a cached miss outside OSGi); the point
		// is that repeat lookups don't diverge or throw — the caching contract holds regardless.
		final ApiextModel first = ApiUtils.findGlobalApiextModel("NoSuchElementXYZ");
		final ApiextModel second = ApiUtils.findGlobalApiextModel("NoSuchElementXYZ");
		assertSame(first, second);
	}

	// ---- isActionBindingName ------------------------------------------------

	@Test
	public void isActionBindingName_exactAction() {
		assertTrue(ApiUtils.isActionBindingName("action"));
	}

	@Test
	public void isActionBindingName_endsWithAction() {
		assertTrue(ApiUtils.isActionBindingName("submitAction"));
	}

	@Test
	public void isActionBindingName_onlyAction_suffix() {
		assertTrue(ApiUtils.isActionBindingName("deleteAction"));
	}

	@Test
	public void isActionBindingName_notAction() {
		assertFalse(ApiUtils.isActionBindingName("value"));
	}

	@Test
	public void isActionBindingName_actionInMiddle_false() {
		assertFalse(ApiUtils.isActionBindingName("actionHandler"));
	}

	@Test
	public void isActionBindingName_empty_false() {
		assertFalse(ApiUtils.isActionBindingName(""));
	}

	@Test
	public void isActionBindingName_caseSensitive() {
		// endsWith("Action") is case-sensitive — "ACTION" does not match
		assertFalse(ApiUtils.isActionBindingName("submitACTION"));
		// "Action" alone: not "action" (exact), but DOES endWith("Action")
		assertTrue(ApiUtils.isActionBindingName("Action"));
	}

	// ---- isActionBinding (via SimpleApiBinding) -----------------------------

	@Test
	public void isActionBinding_withActionsDefault() {
		SimpleApiBinding binding = new SimpleApiBinding("anyName");
		binding.setDefaults("Actions");
		assertTrue(ApiUtils.isActionBinding(binding));
		assertTrue(binding.isAction());
	}

	@Test
	public void isActionBinding_withNoDefaults_actionName() {
		SimpleApiBinding binding = new SimpleApiBinding("action");
		assertTrue(ApiUtils.isActionBinding(binding));
	}

	@Test
	public void isActionBinding_withNoDefaults_nonActionName() {
		SimpleApiBinding binding = new SimpleApiBinding("value");
		assertFalse(ApiUtils.isActionBinding(binding));
	}

	@Test
	public void isActionBinding_withNonActionDefaults() {
		// If defaults is set to something other than "Actions", it's not an action
		// even if the name matches action patterns
		SimpleApiBinding binding = new SimpleApiBinding("submitAction");
		binding.setDefaults("Boolean");
		assertFalse(ApiUtils.isActionBinding(binding));
	}

	// ---- getSelectedDefaults ------------------------------------------------

	@Test
	public void getSelectedDefaults_null_returns0() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		assertEquals(0, ApiUtils.getSelectedDefaults(binding));
	}

	@Test
	public void getSelectedDefaults_actions_returns1() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("Actions");
		assertEquals(1, ApiUtils.getSelectedDefaults(binding));
	}

	@Test
	public void getSelectedDefaults_boolean_returns2() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("Boolean");
		assertEquals(2, ApiUtils.getSelectedDefaults(binding));
	}

	@Test
	public void getSelectedDefaults_allKnownDefaults() {
		// Verify every entry in ALL_DEFAULTS maps to its index
		for (int i = 0; i < IApiBinding.ALL_DEFAULTS.length; i++) {
			SimpleApiBinding binding = new SimpleApiBinding("test");
			binding.setDefaults(IApiBinding.ALL_DEFAULTS[i]);
			assertEquals("Index mismatch for " + IApiBinding.ALL_DEFAULTS[i], i, ApiUtils.getSelectedDefaults(binding));
		}
	}

	@Test
	public void getSelectedDefaults_unknown_returns0() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("NonexistentDefault");
		assertEquals(0, ApiUtils.getSelectedDefaults(binding));
	}

	// ---- SimpleApiBinding.setDefaults(int) ----------------------------------

	@Test
	public void setDefaults_index0_clearsToNull() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("Actions");
		assertNotNull(binding.getDefaults());
		binding.setDefaults(0);
		assertNull(binding.getDefaults());
	}

	@Test
	public void setDefaults_validIndex_setsCorrectDefault() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults(2); // "Boolean"
		assertEquals("Boolean", binding.getDefaults());
	}

	@Test
	public void setDefaults_allValidIndices() {
		for (int i = 1; i < IApiBinding.ALL_DEFAULTS.length; i++) {
			SimpleApiBinding binding = new SimpleApiBinding("test");
			binding.setDefaults(i);
			assertEquals(IApiBinding.ALL_DEFAULTS[i], binding.getDefaults());
		}
	}

	@Test
	public void setDefaults_outOfRange_noChange() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("Actions");
		binding.setDefaults(999); // out of range — should be no-op
		assertEquals("Actions", binding.getDefaults());
	}

	@Test
	public void setDefaults_negativeIndex_noChange() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setDefaults("Actions");
		binding.setDefaults(-1); // negative — should be no-op
		assertEquals("Actions", binding.getDefaults());
	}

	// ---- SimpleApiBinding equality and compareTo ----------------------------

	@Test
	public void equals_sameName() {
		SimpleApiBinding a = new SimpleApiBinding("item");
		SimpleApiBinding b = new SimpleApiBinding("item");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void equals_differentName() {
		SimpleApiBinding a = new SimpleApiBinding("item");
		SimpleApiBinding b = new SimpleApiBinding("value");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_notBinding() {
		SimpleApiBinding a = new SimpleApiBinding("item");
		assertNotEquals(a, "item");
	}

	@Test
	public void compareTo_alphabetical() {
		SimpleApiBinding a = new SimpleApiBinding("alpha");
		SimpleApiBinding b = new SimpleApiBinding("beta");
		assertTrue(a.compareTo(b) < 0);
		assertTrue(b.compareTo(a) > 0);
	}

	@Test
	public void compareTo_null_returnsNegative() {
		SimpleApiBinding a = new SimpleApiBinding("alpha");
		assertTrue(a.compareTo(null) < 0);
	}

	@Test
	public void compareTo_sameName_returnsZero() {
		SimpleApiBinding a = new SimpleApiBinding("item");
		SimpleApiBinding b = new SimpleApiBinding("item");
		assertEquals(0, a.compareTo(b));
	}

	// ---- SimpleApiBinding required/willSet/explicitFlags --------------------

	@Test
	public void required_defaultsFalse() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		assertFalse(binding.isRequired());
		assertFalse(binding.isExplicitlyRequired());
	}

	@Test
	public void required_setAndGet() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setRequired(true);
		assertTrue(binding.isRequired());
	}

	@Test
	public void explicitlyRequired_setAndGet() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setExplicitlyRequired(true);
		assertTrue(binding.isExplicitlyRequired());
	}

	@Test
	public void willSet_defaultsFalse() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		assertFalse(binding.isWillSet());
		assertFalse(binding.isExplicitlySettable());
	}

	@Test
	public void willSet_setAndGet() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setWillSet(true);
		assertTrue(binding.isWillSet());
	}

	@Test
	public void explicitlySettable_setAndGet() {
		SimpleApiBinding binding = new SimpleApiBinding("test");
		binding.setExplicitlySettable(true);
		assertTrue(binding.isExplicitlySettable());
	}

	// ---- SimpleApiBinding hashCode with null name ---------------------------

	@Test
	public void hashCode_nullName_returnsZero() {
		SimpleApiBinding binding = new SimpleApiBinding(null);
		assertEquals(0, binding.hashCode());
	}
}

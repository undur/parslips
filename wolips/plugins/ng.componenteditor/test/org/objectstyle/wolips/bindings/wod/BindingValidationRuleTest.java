package org.objectstyle.wolips.bindings.wod;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Tests for {@link BindingValidationRule} — preference string serialization,
 * deserialization, equality, clone, and list change detection.
 */
public class BindingValidationRuleTest {

	// ---- Basic construction -------------------------------------------------

	@Test
	public void constructor_setsFields() {
		BindingValidationRule rule = new BindingValidationRule("WOString", "value|escapeHTML");
		assertEquals("WOString", rule.getTypeRegex());
		assertEquals("value|escapeHTML", rule.getValidBindingRegex());
	}

	// ---- Mutators -----------------------------------------------------------

	@Test
	public void setTypeRegex_updates() {
		BindingValidationRule rule = new BindingValidationRule("Old", "binding");
		rule.setTypeRegex("New");
		assertEquals("New", rule.getTypeRegex());
	}

	@Test
	public void setValidBindingRegex_updates() {
		BindingValidationRule rule = new BindingValidationRule("Type", "old");
		rule.setValidBindingRegex("new");
		assertEquals("new", rule.getValidBindingRegex());
	}

	// ---- Equality -----------------------------------------------------------

	@Test
	public void equals_sameValues() {
		BindingValidationRule a = new BindingValidationRule("WOString", "value");
		BindingValidationRule b = new BindingValidationRule("WOString", "value");
		assertEquals(a, b);
	}

	@Test
	public void equals_differentType() {
		BindingValidationRule a = new BindingValidationRule("WOString", "value");
		BindingValidationRule b = new BindingValidationRule("WOTextField", "value");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_differentBinding() {
		BindingValidationRule a = new BindingValidationRule("WOString", "value");
		BindingValidationRule b = new BindingValidationRule("WOString", "otherValue");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_notSameType() {
		BindingValidationRule a = new BindingValidationRule("WOString", "value");
		assertNotEquals(a, "WOString");
	}

	// ---- Clone --------------------------------------------------------------

	@Test
	public void clone_isEqualButNotSame() {
		BindingValidationRule original = new BindingValidationRule("WOString", "value");
		BindingValidationRule cloned = original.clone();
		assertEquals(original, cloned);
		assertNotSame(original, cloned);
	}

	@Test
	public void clone_mutatingCloneDoesNotAffectOriginal() {
		BindingValidationRule original = new BindingValidationRule("WOString", "value");
		BindingValidationRule cloned = original.clone();
		cloned.setTypeRegex("Changed");
		assertEquals("WOString", original.getTypeRegex());
	}

	// ---- Preference string serialization/deserialization --------------------

	@Test
	public void preferenceString_emptyList() {
		List<BindingValidationRule> list = new ArrayList<>();
		String pref = BindingValidationRule.toPreferenceString(list);
		assertEquals("", pref);
		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(pref);
		assertTrue(parsed.isEmpty());
	}

	@Test
	public void preferenceString_singleRule() {
		List<BindingValidationRule> list = new ArrayList<>();
		list.add(new BindingValidationRule("WOString", "value"));
		String pref = BindingValidationRule.toPreferenceString(list);
		assertTrue(pref.contains("WOString\tvalue"));

		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(pref);
		assertEquals(1, parsed.size());
		assertEquals("WOString", parsed.get(0).getTypeRegex());
		assertEquals("value", parsed.get(0).getValidBindingRegex());
	}

	@Test
	public void preferenceString_multipleRules_roundTrip() {
		List<BindingValidationRule> original = new ArrayList<>();
		original.add(new BindingValidationRule("WOString", "value|escapeHTML"));
		original.add(new BindingValidationRule("WOConditional", "condition|negate"));
		original.add(new BindingValidationRule("WORepetition", "list|item|index"));

		String pref = BindingValidationRule.toPreferenceString(original);
		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(pref);

		assertEquals(3, parsed.size());
		for (int i = 0; i < original.size(); i++) {
			assertEquals(original.get(i), parsed.get(i));
		}
	}

	@Test
	public void preferenceString_regexWithSpecialChars() {
		List<BindingValidationRule> list = new ArrayList<>();
		list.add(new BindingValidationRule("WO.*", "value|escape.*"));
		String pref = BindingValidationRule.toPreferenceString(list);
		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(pref);
		assertEquals(1, parsed.size());
		assertEquals("WO.*", parsed.get(0).getTypeRegex());
		assertEquals("value|escape.*", parsed.get(0).getValidBindingRegex());
	}

	@Test
	public void fromPreferenceString_null() {
		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(null);
		assertTrue(parsed.isEmpty());
	}

	@Test
	public void fromPreferenceString_skipsMalformedLines() {
		String pref = "WOString\tvalue\nbrokenline\nWOConditional\tcondition\n";
		List<BindingValidationRule> parsed = BindingValidationRule.fromPreferenceString(pref);
		assertEquals(2, parsed.size());
		assertEquals("WOString", parsed.get(0).getTypeRegex());
		assertEquals("WOConditional", parsed.get(1).getTypeRegex());
	}

	// ---- hasChange ----------------------------------------------------------

	@Test
	public void hasChange_identicalLists_false() {
		List<BindingValidationRule> a = new ArrayList<>();
		a.add(new BindingValidationRule("WOString", "value"));
		List<BindingValidationRule> b = new ArrayList<>();
		b.add(new BindingValidationRule("WOString", "value"));
		assertFalse(BindingValidationRule.hasChange(a, b));
	}

	@Test
	public void hasChange_differentSizes_true() {
		List<BindingValidationRule> a = new ArrayList<>();
		a.add(new BindingValidationRule("WOString", "value"));
		List<BindingValidationRule> b = new ArrayList<>();
		assertTrue(BindingValidationRule.hasChange(a, b));
	}

	@Test
	public void hasChange_sameSize_differentContent_true() {
		List<BindingValidationRule> a = new ArrayList<>();
		a.add(new BindingValidationRule("WOString", "value"));
		List<BindingValidationRule> b = new ArrayList<>();
		b.add(new BindingValidationRule("WOConditional", "condition"));
		assertTrue(BindingValidationRule.hasChange(a, b));
	}

	@Test
	public void hasChange_emptyLists_false() {
		assertFalse(BindingValidationRule.hasChange(new ArrayList<>(), new ArrayList<>()));
	}
}

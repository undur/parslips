package org.objectstyle.wolips.bindings.wod;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for {@link TagShortcut} — serialization, deserialization, equality,
 * and the preference-string round-trip used by the Eclipse preference store.
 */
public class TagShortcutTest {

	// ---- Basic construction -------------------------------------------------

	@Test
	public void constructor_twoArg_emptyAttributes() {
		TagShortcut ts = new TagShortcut("str", "WOString");
		assertEquals("str", ts.getShortcut());
		assertEquals("WOString", ts.getActual());
		assertTrue(ts.getAttributes().isEmpty());
	}

	@Test
	public void constructor_threeArg_parsesAttributes() {
		TagShortcut ts = new TagShortcut("if", "WOConditional", "negate=true");
		assertEquals("if", ts.getShortcut());
		assertEquals("WOConditional", ts.getActual());
		assertEquals("true", ts.getAttributes().get("negate"));
	}

	@Test
	public void constructor_threeArgMap_usesProvidedMap() {
		Map<String, String> attrs = new HashMap<>();
		attrs.put("list", "items");
		TagShortcut ts = new TagShortcut("rep", "WORepetition", attrs);
		assertEquals("items", ts.getAttributes().get("list"));
	}

	// ---- Attribute string parsing -------------------------------------------

	@Test
	public void setAttributesAsString_singlePair() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.setAttributesAsString("color=red");
		assertEquals(1, ts.getAttributes().size());
		assertEquals("red", ts.getAttributes().get("color"));
	}

	@Test
	public void setAttributesAsString_multiplePairs() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.setAttributesAsString("color=red,size=large,weight=bold");
		assertEquals(3, ts.getAttributes().size());
		assertEquals("red", ts.getAttributes().get("color"));
		assertEquals("large", ts.getAttributes().get("size"));
		assertEquals("bold", ts.getAttributes().get("weight"));
	}

	@Test
	public void setAttributesAsString_trimsWhitespace() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.setAttributesAsString("  color = red , size = large ");
		assertEquals("red", ts.getAttributes().get("color"));
		assertEquals("large", ts.getAttributes().get("size"));
	}

	@Test
	public void setAttributesAsString_skipsMalformedEntries() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.setAttributesAsString("good=value,badentry,also=ok");
		assertEquals(2, ts.getAttributes().size());
		assertEquals("value", ts.getAttributes().get("good"));
		assertEquals("ok", ts.getAttributes().get("also"));
	}

	@Test
	public void setAttributesAsString_clearsExistingAttributes() {
		TagShortcut ts = new TagShortcut("x", "Y", "old=value");
		assertEquals("value", ts.getAttributes().get("old"));
		ts.setAttributesAsString("new=data");
		assertNull(ts.getAttributes().get("old"));
		assertEquals("data", ts.getAttributes().get("new"));
	}

	// ---- Attribute string serialization -------------------------------------

	@Test
	public void getAttributesAsString_empty() {
		TagShortcut ts = new TagShortcut("x", "Y");
		assertEquals("", ts.getAttributesAsString());
	}

	@Test
	public void getAttributesAsString_singlePair() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.getAttributes().put("color", "red");
		assertEquals("color=red", ts.getAttributesAsString());
	}

	@Test
	public void getAttributesAsString_roundTrip() {
		TagShortcut ts = new TagShortcut("x", "Y");
		ts.setAttributesAsString("alpha=1");
		String serialized = ts.getAttributesAsString();
		TagShortcut ts2 = new TagShortcut("x", "Y");
		ts2.setAttributesAsString(serialized);
		assertEquals(ts.getAttributes(), ts2.getAttributes());
	}

	// ---- Preference string serialization/deserialization --------------------

	@Test
	public void preferenceString_emptyList() {
		List<TagShortcut> list = new ArrayList<>();
		String pref = TagShortcut.toPreferenceString(list);
		assertEquals("", pref);
		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(pref);
		assertTrue(parsed.isEmpty());
	}

	@Test
	public void preferenceString_singleShortcut_noAttributes() {
		List<TagShortcut> list = new ArrayList<>();
		list.add(new TagShortcut("str", "WOString"));
		String pref = TagShortcut.toPreferenceString(list);
		assertTrue(pref.contains("str\tWOString"));

		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(pref);
		assertEquals(1, parsed.size());
		assertEquals("str", parsed.get(0).getShortcut());
		assertEquals("WOString", parsed.get(0).getActual());
		assertTrue(parsed.get(0).getAttributes().isEmpty());
	}

	@Test
	public void preferenceString_singleShortcut_withAttributes() {
		Map<String, String> attrs = new HashMap<>();
		attrs.put("negate", "true");
		List<TagShortcut> list = new ArrayList<>();
		list.add(new TagShortcut("not", "WOConditional", attrs));
		String pref = TagShortcut.toPreferenceString(list);

		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(pref);
		assertEquals(1, parsed.size());
		assertEquals("not", parsed.get(0).getShortcut());
		assertEquals("WOConditional", parsed.get(0).getActual());
		assertEquals("true", parsed.get(0).getAttributes().get("negate"));
	}

	@Test
	public void preferenceString_multipleShortcuts_roundTrip() {
		List<TagShortcut> original = new ArrayList<>();
		original.add(new TagShortcut("str", "WOString"));
		Map<String, String> attrs = new HashMap<>();
		attrs.put("list", "items");
		original.add(new TagShortcut("rep", "WORepetition", attrs));
		original.add(new TagShortcut("if", "WOConditional"));

		String pref = TagShortcut.toPreferenceString(original);
		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(pref);

		assertEquals(3, parsed.size());
		assertEquals("str", parsed.get(0).getShortcut());
		assertEquals("WOString", parsed.get(0).getActual());
		assertEquals("rep", parsed.get(1).getShortcut());
		assertEquals("WORepetition", parsed.get(1).getActual());
		assertEquals("items", parsed.get(1).getAttributes().get("list"));
		assertEquals("if", parsed.get(2).getShortcut());
		assertEquals("WOConditional", parsed.get(2).getActual());
	}

	@Test
	public void fromPreferenceString_null() {
		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(null);
		assertTrue(parsed.isEmpty());
	}

	@Test
	public void fromPreferenceString_skipsMalformedLines() {
		// A line with only one tab-separated field (no actual) is skipped
		String pref = "str\tWOString\nbroken\nif\tWOConditional\n";
		List<TagShortcut> parsed = TagShortcut.fromPreferenceString(pref);
		assertEquals(2, parsed.size());
		assertEquals("str", parsed.get(0).getShortcut());
		assertEquals("if", parsed.get(1).getShortcut());
	}

	// ---- Equality -----------------------------------------------------------

	@Test
	public void equals_sameShortcutAndActual() {
		TagShortcut a = new TagShortcut("str", "WOString");
		TagShortcut b = new TagShortcut("str", "WOString");
		assertEquals(a, b);
	}

	@Test
	public void equals_differentShortcut() {
		TagShortcut a = new TagShortcut("str", "WOString");
		TagShortcut b = new TagShortcut("string", "WOString");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_differentActual() {
		TagShortcut a = new TagShortcut("str", "WOString");
		TagShortcut b = new TagShortcut("str", "WOTextField");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_differentAttributes() {
		TagShortcut a = new TagShortcut("if", "WOConditional", "negate=true");
		TagShortcut b = new TagShortcut("if", "WOConditional");
		assertNotEquals(a, b);
	}

	@Test
	public void equals_notTagShortcut() {
		TagShortcut a = new TagShortcut("str", "WOString");
		assertNotEquals(a, "str");
	}

	// ---- Clone --------------------------------------------------------------

	@Test
	public void clone_isEqualButNotSame() {
		Map<String, String> attrs = new HashMap<>();
		attrs.put("k", "v");
		TagShortcut original = new TagShortcut("str", "WOString", attrs);
		TagShortcut cloned = original.clone();
		assertEquals(original, cloned);
		assertNotSame(original, cloned);
		// Verify attribute map is a separate copy
		assertNotSame(original.getAttributes(), cloned.getAttributes());
	}

	@Test
	public void clone_mutatingCloneDoesNotAffectOriginal() {
		TagShortcut original = new TagShortcut("str", "WOString", "k=v");
		TagShortcut cloned = original.clone();
		cloned.getAttributes().put("k", "changed");
		assertEquals("v", original.getAttributes().get("k"));
	}

	// ---- hasChange ----------------------------------------------------------

	@Test
	public void hasChange_identicalLists_false() {
		List<TagShortcut> a = new ArrayList<>();
		a.add(new TagShortcut("str", "WOString"));
		List<TagShortcut> b = new ArrayList<>();
		b.add(new TagShortcut("str", "WOString"));
		assertFalse(TagShortcut.hasChange(a, b));
	}

	@Test
	public void hasChange_differentSizes_true() {
		List<TagShortcut> a = new ArrayList<>();
		a.add(new TagShortcut("str", "WOString"));
		List<TagShortcut> b = new ArrayList<>();
		assertTrue(TagShortcut.hasChange(a, b));
	}

	@Test
	public void hasChange_sameSize_differentContent_true() {
		List<TagShortcut> a = new ArrayList<>();
		a.add(new TagShortcut("str", "WOString"));
		List<TagShortcut> b = new ArrayList<>();
		b.add(new TagShortcut("if", "WOConditional"));
		assertTrue(TagShortcut.hasChange(a, b));
	}

	@Test
	public void hasChange_emptyLists_false() {
		assertFalse(TagShortcut.hasChange(new ArrayList<>(), new ArrayList<>()));
	}

	// ---- WO→NG class name translation ---------------------------------------

	@Test
	public void woToNGClassName_swapsWOPrefix() {
		assertEquals("NGConditional", TagShortcut.woToNGClassName("WOConditional"));
	}

	@Test
	public void woToNGClassName_swapsWOString() {
		assertEquals("NGString", TagShortcut.woToNGClassName("WOString"));
	}

	@Test
	public void woToNGClassName_usesNGSpellingWhereItDiverges() {
		// ng-objects spells this one NGCheckbox, not the prefix-swapped NGCheckBox — the
		// editor must resolve <wo:checkbox> to the class that actually exists.
		assertEquals("NGCheckbox", TagShortcut.woToNGClassName("WOCheckBox"));
	}

	@Test
	public void woToNGClassName_nonWOPrefix_unchanged() {
		assertEquals("ERXLocalizedString", TagShortcut.woToNGClassName("ERXLocalizedString"));
	}

	@Test
	public void woToNGClassName_null_unchanged() {
		assertNull(TagShortcut.woToNGClassName(null));
	}

	@Test
	public void woToNGClassName_empty_unchanged() {
		assertEquals("", TagShortcut.woToNGClassName(""));
	}

	@Test
	public void woToNGClassName_justWO_becomesNG() {
		// Edge case: a class named exactly "WO" (unlikely but handles boundary)
		assertEquals("NG", TagShortcut.woToNGClassName("WO"));
	}

	@Test
	public void getActual_nullParsleyProject_returnsOriginal() {
		TagShortcut ts = new TagShortcut("if", "WOConditional");
		assertEquals("WOConditional", ts.getActual(null));
	}
}

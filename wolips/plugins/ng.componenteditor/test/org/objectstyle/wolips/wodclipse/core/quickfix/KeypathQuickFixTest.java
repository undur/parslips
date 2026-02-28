package org.objectstyle.wolips.wodclipse.core.quickfix;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for the keypath quick-fix utility methods in
 * {@link KeypathQuickFixGenerator} and {@link ReplaceKeypathQuickFix}.
 */
public class KeypathQuickFixTest {

  // --- KeypathQuickFixGenerator.extractInvalidKey ---

  @Test
  public void extractInvalidKey_simpleKey() {
    assertEquals("nme", KeypathQuickFixGenerator.extractInvalidKey(
        "There is no key 'nme' in MyComponent"));
  }

  @Test
  public void extractInvalidKey_withKeypath() {
    assertEquals("nme", KeypathQuickFixGenerator.extractInvalidKey(
        "There is no key 'nme' for the keypath 'application' in MyComponent"));
  }

  @Test
  public void extractInvalidKey_withSuggestion() {
    assertEquals("nme", KeypathQuickFixGenerator.extractInvalidKey(
        "There is no key 'nme' in MyComponent. Did you mean 'name'?"));
  }

  @Test
  public void extractInvalidKey_noMatch() {
    assertNull(KeypathQuickFixGenerator.extractInvalidKey(
        "Some other error message"));
  }

  @Test
  public void extractInvalidKey_null() {
    assertNull(KeypathQuickFixGenerator.extractInvalidKey(null));
  }

  // --- ReplaceKeypathQuickFix.findKeySegmentOffset ---

  @Test
  public void findKeySegment_singleKey() {
    assertEquals(0, ReplaceKeypathQuickFix.findKeySegmentOffset("nme", "nme"));
  }

  @Test
  public void findKeySegment_lastSegment() {
    assertEquals(12, ReplaceKeypathQuickFix.findKeySegmentOffset("application.nme", "nme"));
  }

  @Test
  public void findKeySegment_middleSegment() {
    assertEquals(4, ReplaceKeypathQuickFix.findKeySegmentOffset("app.nme.value", "nme"));
  }

  @Test
  public void findKeySegment_withDollarPrefix() {
    // In inline bindings, value might include the $ prefix
    assertEquals(1, ReplaceKeypathQuickFix.findKeySegmentOffset("$nme", "nme"));
  }

  @Test
  public void findKeySegment_dollarPrefixedKeypath() {
    assertEquals(13, ReplaceKeypathQuickFix.findKeySegmentOffset("$application.nme", "nme"));
  }

  @Test
  public void findKeySegment_noMatch() {
    assertEquals(-1, ReplaceKeypathQuickFix.findKeySegmentOffset("application.name", "nme"));
  }

  @Test
  public void findKeySegment_partialMatchNotSegment() {
    // "nme" appears inside "unnamed" but isn't a complete segment
    assertEquals(-1, ReplaceKeypathQuickFix.findKeySegmentOffset("unnamed", "nme"));
  }

  @Test
  public void findKeySegment_nullInputs() {
    assertEquals(-1, ReplaceKeypathQuickFix.findKeySegmentOffset(null, "nme"));
    assertEquals(-1, ReplaceKeypathQuickFix.findKeySegmentOffset("nme", null));
  }

  // --- KeypathQuickFixGenerator.extractInvalidElementType ---

  @Test
  public void extractInvalidElementType_standard() {
    assertEquals("Str", KeypathQuickFixGenerator.extractInvalidElementType(
        "The class for 'Str' is either missing or does not extend a known element root type (NGElement/WOElement)."));
  }

  @Test
  public void extractInvalidElementType_withSuggestion() {
    assertEquals("WOStirng", KeypathQuickFixGenerator.extractInvalidElementType(
        "The class for 'WOStirng' is either missing or does not extend a known element root type (NGElement/WOElement). Did you mean 'WOString'?"));
  }

  @Test
  public void extractInvalidElementType_noMatch() {
    assertNull(KeypathQuickFixGenerator.extractInvalidElementType(
        "There is no key 'nme' in MyComponent"));
  }

  @Test
  public void extractInvalidElementType_null() {
    assertNull(KeypathQuickFixGenerator.extractInvalidElementType(null));
  }

  @Test
  public void extractInvalidElementType_empty() {
    assertNull(KeypathQuickFixGenerator.extractInvalidElementType(""));
  }

  // --- findKeySegmentOffset works for element types (full match) ---

  @Test
  public void findKeySegment_elementType_fullMatch() {
    // For element type errors, the entire marker range IS the invalid name
    assertEquals(0, ReplaceKeypathQuickFix.findKeySegmentOffset("Str", "Str"));
  }

  @Test
  public void findKeySegment_elementType_capitalizedWO() {
    assertEquals(0, ReplaceKeypathQuickFix.findKeySegmentOffset("WOStirng", "WOStirng"));
  }

  // --- findKeySegmentOffset with namespace prefix (colon delimiter) ---

  @Test
  public void findKeySegment_colonPrefix() {
    // In inline templates, element type markers cover "wo:WOStrig" (the full
    // tag name including namespace). The invalid name is just "WOStrig", so
    // ':' must be recognized as a valid segment delimiter.
    assertEquals(3, ReplaceKeypathQuickFix.findKeySegmentOffset("wo:WOStrig", "WOStrig"));
  }

  @Test
  public void findKeySegment_colonPrefix_shortName() {
    // Capitalization error: <wo:Str> instead of <wo:str>
    assertEquals(3, ReplaceKeypathQuickFix.findKeySegmentOffset("wo:Str", "Str"));
  }

  @Test
  public void findKeySegment_colonPrefix_ngNamespace() {
    // ng-objects uses ng: prefix
    assertEquals(3, ReplaceKeypathQuickFix.findKeySegmentOffset("ng:Strig", "Strig"));
  }

  // --- extractInvalidElementType also handles shortcut case mismatches ---
  // Shortcut case mismatches use the same "The class for 'X' is either missing..."
  // message format, so extractInvalidElementType handles them uniformly.

  @Test
  public void extractInvalidElementType_shortcutCaseMismatch() {
    // Shortcut case mismatch uses the same message format as element type errors
    assertEquals("Repetition", KeypathQuickFixGenerator.extractInvalidElementType(
        "The class for 'Repetition' is either missing or does not extend a known element root type (NGElement/WOElement). Did you mean 'repetition'?"));
  }

  // --- findKeySegmentOffset for shortcut case mismatch ---

  @Test
  public void findKeySegment_shortcutCaseMismatch() {
    // For shortcut case mismatch on <wo:Repetition>, marker covers
    // "wo:Repetition" and invalid name is "Repetition"
    assertEquals(3, ReplaceKeypathQuickFix.findKeySegmentOffset("wo:Repetition", "Repetition"));
  }
}

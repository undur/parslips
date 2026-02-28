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
}

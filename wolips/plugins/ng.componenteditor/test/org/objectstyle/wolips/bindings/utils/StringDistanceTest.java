package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Tests for {@link StringDistance}: Damerau–Levenshtein distance computation
 * and "did you mean?" suggestion generation.
 */
public class StringDistanceTest {

  // --- damerauLevenshtein: basic cases ---

  @Test
  public void identicalStrings_distanceZero() {
    assertEquals(0, StringDistance.damerauLevenshtein("hello", "hello"));
  }

  @Test
  public void emptyStrings_distanceZero() {
    assertEquals(0, StringDistance.damerauLevenshtein("", ""));
  }

  @Test
  public void oneEmpty_distanceIsLength() {
    assertEquals(5, StringDistance.damerauLevenshtein("hello", ""));
    assertEquals(3, StringDistance.damerauLevenshtein("", "abc"));
  }

  @Test
  public void singleSubstitution() {
    assertEquals(1, StringDistance.damerauLevenshtein("cat", "bat"));
  }

  @Test
  public void singleInsertion() {
    assertEquals(1, StringDistance.damerauLevenshtein("cat", "cats"));
  }

  @Test
  public void singleDeletion() {
    assertEquals(1, StringDistance.damerauLevenshtein("cats", "cat"));
  }

  @Test
  public void singleTransposition() {
    // "ab" → "ba" should be distance 1 (adjacent transposition)
    assertEquals(1, StringDistance.damerauLevenshtein("ab", "ba"));
  }

  @Test
  public void transposition_typicalTypo() {
    // "value" → "vlaue" — transposition of 'a' and 'l'
    assertEquals(1, StringDistance.damerauLevenshtein("value", "vlaue"));
  }

  @Test
  public void caseInsensitive() {
    assertEquals(0, StringDistance.damerauLevenshtein("Name", "name"));
    assertEquals(0, StringDistance.damerauLevenshtein("VALUE", "value"));
  }

  @Test
  public void nullHandling() {
    assertEquals(0, StringDistance.damerauLevenshtein(null, null));
    assertEquals(Integer.MAX_VALUE, StringDistance.damerauLevenshtein(null, "abc"));
    assertEquals(Integer.MAX_VALUE, StringDistance.damerauLevenshtein("abc", null));
  }

  @Test
  public void multipleEdits() {
    // "kitten" → "sitting": k→s, e→i, +g = 3 edits
    assertEquals(3, StringDistance.damerauLevenshtein("kitten", "sitting"));
  }

  // --- closestMatches: suggestion generation ---

  @Test
  public void closestMatches_findsExactTypo() {
    List<String> candidates = Arrays.asList("name", "value", "item", "list");
    List<String> matches = StringDistance.closestMatches("nme", candidates, 3);
    assertEquals(1, matches.size());
    assertEquals("name", matches.get(0));
  }

  @Test
  public void closestMatches_returnsEmptyForNoMatch() {
    List<String> candidates = Arrays.asList("name", "value", "item", "list");
    List<String> matches = StringDistance.closestMatches("xyz", candidates, 3);
    assertTrue(matches.isEmpty());
  }

  @Test
  public void closestMatches_excludesExactMatch() {
    List<String> candidates = Arrays.asList("name", "value", "item");
    List<String> matches = StringDistance.closestMatches("name", candidates, 3);
    assertTrue("Exact match should not be suggested", matches.isEmpty());
  }

  @Test
  public void closestMatches_sortsClosestFirst() {
    // "valu" is distance 1 from "value", distance 2+ from others
    List<String> candidates = Arrays.asList("value", "valid", "vault");
    List<String> matches = StringDistance.closestMatches("valu", candidates, 3);
    assertFalse(matches.isEmpty());
    assertEquals("value", matches.get(0));
  }

  @Test
  public void closestMatches_respectsMaxResults() {
    List<String> candidates = Arrays.asList("aa", "ab", "ac", "ad");
    List<String> matches = StringDistance.closestMatches("a", candidates, 2);
    assertTrue(matches.size() <= 2);
  }

  @Test
  public void closestMatches_nullInput() {
    List<String> matches = StringDistance.closestMatches(null, Arrays.asList("a"), 3);
    assertTrue(matches.isEmpty());
  }

  @Test
  public void closestMatches_nullCandidates() {
    List<String> matches = StringDistance.closestMatches("a", null, 3);
    assertTrue(matches.isEmpty());
  }

  @Test
  public void closestMatches_emptyCandidates() {
    List<String> matches = StringDistance.closestMatches("a", Collections.<String>emptyList(), 3);
    assertTrue(matches.isEmpty());
  }

  @Test
  public void closestMatches_transpositionTypo() {
    // "vlaue" should match "value" at distance 1
    List<String> candidates = Arrays.asList("value", "valid", "vault", "volume");
    List<String> matches = StringDistance.closestMatches("vlaue", candidates, 3);
    assertFalse(matches.isEmpty());
    assertEquals("value", matches.get(0));
  }

  @Test
  public void closestMatches_caseInsensitive() {
    List<String> candidates = Arrays.asList("userName", "userAge");
    List<String> matches = StringDistance.closestMatches("userNme", candidates, 3);
    assertFalse(matches.isEmpty());
    assertEquals("userName", matches.get(0));
  }

  // --- maxDistanceForLength: adaptive thresholds ---

  @Test
  public void maxDistance_shortNames() {
    assertEquals(1, StringDistance.maxDistanceForLength(1));
    assertEquals(1, StringDistance.maxDistanceForLength(4));
  }

  @Test
  public void maxDistance_mediumNames() {
    assertEquals(2, StringDistance.maxDistanceForLength(5));
    assertEquals(2, StringDistance.maxDistanceForLength(8));
  }

  @Test
  public void maxDistance_longNames() {
    assertEquals(3, StringDistance.maxDistanceForLength(9));
    assertEquals(3, StringDistance.maxDistanceForLength(20));
  }
}

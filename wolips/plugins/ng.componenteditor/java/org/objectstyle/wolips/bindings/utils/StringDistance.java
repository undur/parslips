package org.objectstyle.wolips.bindings.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * String distance utilities for generating "did you mean?" suggestions.
 *
 * <p>Uses the Damerau–Levenshtein distance algorithm, which counts
 * insertions, deletions, substitutions, and transpositions of adjacent
 * characters.  Transpositions are a common typo pattern in key names
 * (e.g. "vlaue" instead of "value"), so DL distance is a better fit than
 * plain Levenshtein for this use case.
 */
public class StringDistance {

  /**
   * Computes the Damerau–Levenshtein distance between two strings.
   * The comparison is case-insensitive since binding key names are
   * conventionally matched case-insensitively.
   *
   * @param a first string
   * @param b second string
   * @return the edit distance (0 = identical after case folding)
   */
  public static int damerauLevenshtein(String a, String b) {
    if (a == null || b == null) {
      return a == null && b == null ? 0 : Integer.MAX_VALUE;
    }
    String s = a.toLowerCase();
    String t = b.toLowerCase();
    int sLen = s.length();
    int tLen = t.length();

    if (sLen == 0) return tLen;
    if (tLen == 0) return sLen;

    // Optimal string alignment variant (restricted edit distance).
    // Uses a (sLen+1) x (tLen+1) matrix.
    int[][] d = new int[sLen + 1][tLen + 1];
    for (int i = 0; i <= sLen; i++) d[i][0] = i;
    for (int j = 0; j <= tLen; j++) d[0][j] = j;

    for (int i = 1; i <= sLen; i++) {
      for (int j = 1; j <= tLen; j++) {
        int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
        d[i][j] = min(
          d[i - 1][j] + 1,       // deletion
          d[i][j - 1] + 1,       // insertion
          d[i - 1][j - 1] + cost  // substitution
        );
        // Transposition of adjacent characters
        if (i > 1 && j > 1
            && s.charAt(i - 1) == t.charAt(j - 2)
            && s.charAt(i - 2) == t.charAt(j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
        }
      }
    }
    return d[sLen][tLen];
  }

  /**
   * Finds the closest matches for {@code input} from a list of candidates.
   * Returns up to {@code maxResults} suggestions, sorted by distance
   * (closest first), filtered to a maximum distance threshold.
   *
   * <p>The threshold is adaptive: for short names (≤4 chars), only distance
   * 1 is accepted; for medium names (5–8 chars), up to distance 2; for
   * longer names, up to distance 3.  This prevents absurd suggestions like
   * "list" → "item" (distance 4) while still catching reasonable typos.
   *
   * @param input      the (possibly mistyped) name
   * @param candidates the valid names to compare against
   * @param maxResults maximum number of suggestions to return
   * @return a list of suggestions, closest first; may be empty
   */
  public static List<String> closestMatches(String input, List<String> candidates, int maxResults) {
    if (input == null || candidates == null || candidates.isEmpty()) {
      return Collections.emptyList();
    }

    int maxDistance = maxDistanceForLength(input.length());

    List<Match> matches = new ArrayList<Match>();
    for (String candidate : candidates) {
      int dist = damerauLevenshtein(input, candidate);
      if (dist > 0 && dist <= maxDistance) {
        matches.add(new Match(candidate, dist));
      }
    }

    Collections.sort(matches, new Comparator<Match>() {
      public int compare(Match a, Match b) {
        return Integer.compare(a.distance, b.distance);
      }
    });

    List<String> result = new ArrayList<String>();
    for (int i = 0; i < Math.min(maxResults, matches.size()); i++) {
      result.add(matches.get(i).name);
    }
    return result;
  }

  /**
   * Returns the maximum acceptable edit distance for a name of the given
   * length.  Short names are held to stricter thresholds to avoid noisy
   * suggestions.
   */
  static int maxDistanceForLength(int length) {
    if (length <= 4) return 1;
    if (length <= 8) return 2;
    return 3;
  }

  private static int min(int a, int b, int c) {
    return Math.min(a, Math.min(b, c));
  }

  /** A candidate name paired with its distance from the input. */
  private static class Match {
    final String name;
    final int distance;

    Match(String name, int distance) {
      this.name = name;
      this.distance = distance;
    }
  }
}

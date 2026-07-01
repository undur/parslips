package org.objectstyle.wolips.wodclipse.core.util;

import java.util.regex.Pattern;

import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

import jp.aonir.fuzzyxml.FuzzyXMLElement;

public class WodHtmlUtils {
  public static Pattern WEBOBJECTS_PATTERN;

  static {
    StringBuffer patterns = new StringBuffer();
    patterns.append("<webobjects{0,1}\\s+name\\s*=\\s*\"{0,1}([^>\"/\\s]+)\"{0,1}\\s*/{0,1}>");
    patterns.append("|");
    patterns.append("<wo\\s+name\\s*=\\s*\"{0,1}([^>\"/\\s]+)\"{0,1}\\s*/{0,1}>");
    WodHtmlUtils.WEBOBJECTS_PATTERN = Pattern.compile(patterns.toString(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  }

  public static boolean isInline(FuzzyXMLElement element) {
    return element != null && WodHtmlUtils.isInline(element.getName());
  }

  public static boolean isInline(String tagName) {
    boolean isWOTag = false;
    if (tagName != null) {
      String lowercaseTagName = tagName.toLowerCase();
      if (lowercaseTagName.startsWith("wo:")) {
        isWOTag = true;
      }
    }
    return isWOTag;
  }

  public static boolean isWOTag(FuzzyXMLElement element) {
    return element != null && WodHtmlUtils.isWOTag(element.getName());
  }

  /**
   * Returns {@code true} if the tag is a parser control directive ({@code p:raw}
   * or {@code p:comment}). These tags are handled specially by the parser —
   * {@code p:raw} treats its content as literal text (no dynamic tag processing),
   * and {@code p:comment} ignores content entirely (template-level comment).
   */
  public static boolean isParserDirective(String tagName) {
    if (tagName != null) {
      String lower = tagName.trim().toLowerCase();
      if (lower.equals("p:raw") || lower.startsWith("p:raw ") ||
          lower.equals("p:comment") || lower.startsWith("p:comment ")) {
        return true;
      }
    }
    return false;
  }

  public static boolean isWOTag(String tagName) {
    boolean isWOTag = false;
    if (tagName != null) {
      String lowercaseTagName = tagName.trim().toLowerCase();
      if (lowercaseTagName.startsWith("webobject") || lowercaseTagName.equals("wo") || lowercaseTagName.startsWith("wo ") || lowercaseTagName.startsWith("wo:")) {
        isWOTag = true;
      }
    }
    return isWOTag;
  }

  /**
   * Returns the line number from the offset.
   * 
   * @param offset the offset
   * @return the line number.
   * 
   * Needs to be offset+1 in the substring to make sure 
   * that the text is included in the substring.  Otherwise 
   * an offset at the start of the line is not included.
   */
  public static int getLineAtOffset(String contents, int offset) {
    int lineCount = 1;
    for (int i = 0; i < offset + 1; i++) {
      char ch = contents.charAt(i);
      if (ch == '\n') {
        lineCount++;
      }
    }
    return lineCount;
  }

  /**
   * Given a dotted keypath and an offset <em>relative to the start of that keypath string</em>,
   * returns the index of the segment the offset falls within — so "open declaration" (F3) and
   * Cmd+click can target the segment under the cursor rather than the whole path.
   * <p>
   * For {@code "activeUser.address.name"}: offsets inside {@code activeUser} → 0, inside
   * {@code address} → 1, inside {@code name} → 2. A dot separator belongs to the segment it
   * follows (the cursor sitting on the {@code .} after {@code activeUser} still resolves to
   * {@code activeUser}). Offsets at or past the end map to the last segment; a negative offset
   * maps to the first. Any leading operator character ({@code @} for {@code @count}, etc.) is
   * part of segment 0's text and doesn't shift the indexing.
   * <p>
   * Purely textual and null/empty-safe — it counts unescaped dots and does no type resolution;
   * the resolved-key array may be shorter (an unresolvable mid-path key), so callers clamp the
   * returned index into that array's range.
   *
   * @param keyPath the keypath text (no binding prefix/suffix, e.g. no leading {@code $})
   * @param offsetInKeyPath caret/click offset measured from the first character of {@code keyPath}
   * @return the zero-based segment index, or 0 for a null/empty keypath
   */
  public static int segmentIndexAt(String keyPath, int offsetInKeyPath) {
    if (keyPath == null || keyPath.isEmpty()) {
      return 0;
    }
    if (offsetInKeyPath <= 0) {
      return 0;
    }
    // Count the dots strictly before the offset; that's the segment index. Clamp the offset to
    // the string length so an offset at/after the end lands on the final segment.
    int limit = Math.min(offsetInKeyPath, keyPath.length());
    int index = 0;
    for (int i = 0; i < limit; i++) {
      if (keyPath.charAt(i) == '.') {
        index++;
      }
    }
    return index;
  }

  /**
   * Returns the character span {@code [start, length]} of the given segment within a dotted
   * keypath, so the Cmd+hover underline can cover just the segment that a click will open
   * (rather than the whole keypath). Offsets are relative to the start of {@code keyPath}.
   * <p>
   * For {@code "activeUser.address.name"}: segment 0 → {@code [0, 10]} (activeUser), 1 →
   * {@code [11, 7]} (address), 2 → {@code [19, 4]} (name). A {@code segmentIndex < 0} or past
   * the last segment resolves to the last segment (matching {@link #segmentIndexAt}'s clamping).
   * Returns the whole-string span for a null/empty keypath.
   *
   * @return a two-element array {@code {startOffset, length}}
   */
  public static int[] segmentSpan(String keyPath, int segmentIndex) {
    if (keyPath == null || keyPath.isEmpty()) {
      return new int[] { 0, keyPath == null ? 0 : keyPath.length() };
    }
    // Split on '.' tracking each segment's start; operators/text within a segment are included.
    int segStart = 0;
    int index = 0;
    int lastStart = 0;
    for (int i = 0; i <= keyPath.length(); i++) {
      if (i == keyPath.length() || keyPath.charAt(i) == '.') {
        if (index == segmentIndex) {
          return new int[] { segStart, i - segStart };
        }
        lastStart = segStart;
        segStart = i + 1; // skip the '.'
        index++;
      }
    }
    // segmentIndex < 0 or past the end → the last segment (start = lastStart, to end of string).
    return new int[] { lastStart, keyPath.length() - lastStart };
  }

  public static class BindingValue {
    private String _valueNamespace;
    private String _value;
    private boolean _literal;

    public BindingValue(String valueNamespace, String value, boolean literal) {
      _valueNamespace = valueNamespace;
      _value = value;
      _literal = literal;
    }

    public String getValue() {
      return _value;
    }

    public String getValueNamespace() {
      return _valueNamespace;
    }

    public boolean isLiteral() {
      return _literal;
    }
  }

  public static BindingValue toBindingValue(String rawValue, String inlineBindingPrefix, String inlineBindingSuffix) {
    String valueNamespace = null;
    String value = rawValue;
    boolean literal;
    if (value.startsWith(inlineBindingPrefix) && (inlineBindingSuffix.length() == 0 || value.endsWith(inlineBindingSuffix))) {
      value = value.substring(inlineBindingPrefix.length(), value.length() - inlineBindingSuffix.length());
      int colonIndex = value.indexOf(':');
      if (colonIndex != -1) {
        valueNamespace = value.substring(0, colonIndex).trim();
        value = value.substring(colonIndex + 1).trim();
      }
      literal = false;
    }
    else {
      value = "\"" + value + "\"";
      literal = true;
    }
    return new BindingValue(valueNamespace, value, literal);
  }

  /**
   * If the element is inline bindings, create a SimpleWodElement.  If the element is not inline, then
   * return the corresponding WOD element entry.
   * 
   * @param element the XML element to process
   * @param parsleyProject the project model (for tag shortcut resolution and inline binding settings)
   * @param resolveWodElement if true, webobject tags will resolve to their DocumentWodElement
   * @param cache the WodParserCache
   * @return an IWodElement corresponding to the node
   * @throws Exception
   */
  public static IWodElement getWodElement(FuzzyXMLElement element, ParsleyProject parsleyProject, boolean resolveWodElement, WodParserCache cache) throws Exception {
    IWodElement wodElement;
    if (WodHtmlUtils.isWOTag(element)) {
      if (WodHtmlUtils.isInline(element.getName()) || !resolveWodElement) {
        wodElement = new FuzzyXMLWodElement(element, parsleyProject);
      }
      else {
        String elementName = element.getAttributeValue("name");
        if (cache != null && cache.getWodEntry() != null && cache.getWodEntry().getModel() != null) {
          wodElement = cache.getWodEntry().getModel().getElementNamed(elementName);
        }
        else {
          wodElement = null;
        }
      }
    }
    else {
      wodElement = null;
    }
    return wodElement;
  }
}

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

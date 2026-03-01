package jp.aonir.fuzzyxml.internal;

import jp.aonir.fuzzyxml.FuzzyXMLNode;
import jp.aonir.fuzzyxml.FuzzyXMLText;

public class FuzzyXMLTextImpl extends AbstractFuzzyXMLNode implements FuzzyXMLText {

  /** The decoded text value (entities resolved to characters). */
  private String _value;
  /**
   * The raw text exactly as it appeared in the source, with entities
   * intact. Used by {@link #toXMLString} so the formatter preserves
   * the author's entity choices (e.g. {@code &nbsp;} stays as
   * {@code &nbsp;}, single quotes in script blocks stay as {@code '}).
   */
  private String _rawValue;
  private boolean _escape = true;

  public FuzzyXMLTextImpl(String value) {
    this(null, value, -1, -1);
  }

  public FuzzyXMLTextImpl(FuzzyXMLNode parent, String value, int offset, int length) {
    super(parent, offset, length);
    this._value = value;
    this._rawValue = value;
  }

  /**
   * Sets the raw (un-decoded) source text. Called by the parser after
   * construction so the formatter can output text without an
   * encode/decode roundtrip that changes entities.
   */
  public void setRawValue(String rawValue) {
    this._rawValue = rawValue;
  }

  public String getRawValue() {
    return _rawValue;
  }

  public String getValue() {
    if (getDocument() == null) {
      return FuzzyXMLUtil.decode(_value, false);
    }
    return FuzzyXMLUtil.decode(_value, getDocument().isHTML());
  }

  public String toDebugString() {
    StringBuffer sb = new StringBuffer();
    toDebugString(sb, 0);
    return sb.toString();
  }

  public void toDebugString(StringBuffer buffer, int indent) {
    for (int i = 0; i < indent; i ++) {
      buffer.append("  ");
    }
    buffer.append("text: '" + _value + "'\n");
  }

  /**
   * Renders this text node to the output buffer. Uses the raw source text
   * (with original entities intact) so the formatter never converts to or
   * from entities. Whitespace trimming is applied when formatting is
   * enabled, but entity content is always preserved as-is.
   */
  public void toXMLString(RenderContext renderContext, StringBuffer xmlBuffer) {
    // Use the raw source text so entities pass through unchanged.
    String value = _rawValue;
    if (renderContext.isTrim()) {
      if (value.trim().length() == 0) {
        return;
      }
      // Collapse trailing whitespace. If it contained a newline, keep a
      // newline so the following close tag can land on its own line.
      // Otherwise collapse to a single space.
      String trailingReplace = value.matches("(?s).*[\r\n][\\s]*$") ? "\n" : " ";
      value = value.replaceFirst("[\r\n\t ]+$", trailingReplace);
      String replace = "";
      if (xmlBuffer.length() >= 1 && !Character.isWhitespace(xmlBuffer.charAt(xmlBuffer.length()-1))) {
        replace = " ";
      }
      value = value.replaceFirst("^[\r\n\t ]+", replace);
    }
    xmlBuffer.append(value);
  }

  @Override
  public String toString() {
    return "text: " + getValue();
  }

  public void setEscape(boolean escape) {
    this._escape = escape;
  }

  public boolean isEscape() {
    return this._escape;
  }

  @Override
  public boolean isNonBreaking() {
    if (isHidden())
      return false;

    String value = getValue();//.trim();
    
    boolean result = FuzzyXMLUtil.isAllWhitescape(value) || FuzzyXMLUtil.getSpaceIndex(value) == -1;
    return result;
  }
  
  @Override
  public boolean isHidden() {
    boolean result = _value == null || (FuzzyXMLUtil.isAllWhitescape(_value));
    return result;
  }
  
  @Override
  public boolean hasLineBreaks() {
    return _value != null && _value.trim().contains("\n");
  }
}

package jp.aonir.fuzzyxml.internal;

import jp.aonir.fuzzyxml.FuzzyXMLComment;
import jp.aonir.fuzzyxml.FuzzyXMLFormat;
import jp.aonir.fuzzyxml.FuzzyXMLNode;

public class FuzzyXMLCommentImpl extends FuzzyXMLElementImpl implements FuzzyXMLComment, FuzzyXMLFormat {

  private final String _value;
  private final boolean _hasCloseTag;

  public FuzzyXMLCommentImpl(String value) {
    this(null, value, 0, 0);
  }

  public FuzzyXMLCommentImpl(FuzzyXMLNode parent, String value, int offset, int length) {
    super(parent, "comment", offset, length, -1);

    _hasCloseTag = value.trim().endsWith("-->");
    String text = value.replaceFirst("<!--", "").replaceFirst("-->", "");
    _value = text;
  }

  @Override
  public String getValue() {
    return _value;
  }
  
  /**
   * Renders the comment preserving its content exactly as written.
   * The formatter should not modify whitespace or text inside comments
   * — the author's formatting is intentional.
   */
  @Override
  public void toXMLString(RenderContext renderContext, StringBuffer xmlBuffer) {
    boolean renderSurroundingTags = true;

    RenderDelegate delegate = renderContext.getDelegate();
    if (delegate != null) {
      renderSurroundingTags = delegate.beforeOpenTag(this, renderContext, xmlBuffer);
    }
    if (renderSurroundingTags) {
      xmlBuffer.append("<!--");
      String commentString = getValue();
      if (commentString != null) {
        xmlBuffer.append(commentString);
      }
      if (_hasCloseTag) {
        xmlBuffer.append("-->");
      }
      if (delegate != null) {
        delegate.afterCloseTag(this, renderContext, xmlBuffer);
      }
    }
  }

  @Override
  public String toString() {
    return "comment: " + getValue();
  }
}

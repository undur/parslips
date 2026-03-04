package jp.aonir.fuzzyxml.internal;

import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.FuzzyXMLNode;

public abstract class AbstractFuzzyXMLNode implements FuzzyXMLNode {

  private int _offset = -1;
  private int _length = -1;
  private FuzzyXMLNode _parent;
  private FuzzyXMLDocumentImpl _doc;

  public AbstractFuzzyXMLNode() {
    super();
  }

  public AbstractFuzzyXMLNode(FuzzyXMLNode parent, int offset, int length) {
    super();
    setParentNode(parent);
    setOffset(offset);
    setLength(length);
  }

  public void setLength(int length) {
    this._length = length;
  }

  public int getLength() {
    return _length;
  }

  public int getOffset() {
    return _offset;
  }

  public FuzzyXMLNode getParentNode() {
    return _parent;
  }

  public void setOffset(int offset) {
    this._offset = offset;
  }

  public void setParentNode(FuzzyXMLNode parent) {
    this._parent = parent;
  }

  /**
   * �c���[�̍X�V�C�x���g�𔭉΂��܂��B
   * 
   * @param newText
   * @param offset
   * @param length
   */
  protected void fireModifyEvent(String newText, int offset, int length) {
    // �܂��m�[�h���c���[�ɒǉ�����Ă��Ȃ��ꍇ�͂Ȃɂ����Ȃ�
    FuzzyXMLDocumentImpl doc = getDocument();
    if (doc == null) {
      return;
    }
    doc.fireModifyEvent(newText, offset, length);
  }

  /**
   * �m�[�h�̈ʒu�����X�V���܂��B�c���[�̕ύX�O�ɌĂяo���܂��B
   * 
   * @param parent
   * @param offset
   * @param append
   */
  protected void appendOffset(FuzzyXMLElement parent, int offset, int append) {
    // �܂��m�[�h���c���[�ɒǉ�����Ă��Ȃ��ꍇ�͂Ȃɂ����Ȃ�
    FuzzyXMLDocumentImpl doc = getDocument();
    if (doc == null) {
      return;
    }
    doc.appendOffset(parent, offset, append);
  }

  public void setDocument(FuzzyXMLDocumentImpl doc) {
    this._doc = doc;
  }

  public FuzzyXMLDocumentImpl getDocument() {
    return _doc;
  }

  public String toXMLString(RenderContext renderContext) {
    StringBuffer xmlBuffer = new StringBuffer();
    RenderDelegate delegate = renderContext.getDelegate();
    if (delegate != null) {
      delegate.beforeRender(renderContext, xmlBuffer);
    }
    if (delegate == null || delegate.renderNode(this, renderContext, xmlBuffer)) {
      toXMLString(renderContext, xmlBuffer);
    }
    if (delegate != null) {
      delegate.afterRender(renderContext, xmlBuffer);
    }
    return xmlBuffer.toString();
  }

  public boolean isNonBreaking() {
    return true;
  }

  public boolean isHidden() {
    return false;
  }

  public boolean hasLineBreaks() {
    return true;
  }
  
  public boolean isSynthetic() {
  	return false;
  }
}

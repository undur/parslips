package jp.aonir.fuzzyxml.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.Objects;

import jp.aonir.fuzzyxml.FuzzyXMLAttribute;
import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.FuzzyXMLException;
import jp.aonir.fuzzyxml.FuzzyXMLNode;
import jp.aonir.fuzzyxml.FuzzyXMLParser;
import jp.aonir.fuzzyxml.FuzzyXMLText;
import jp.aonir.fuzzyxml.TextRegion;
import tk.eclipse.plugin.htmleditor.HTMLPlugin;

public class FuzzyXMLElementImpl extends AbstractFuzzyXMLNode implements FuzzyXMLElement {

  /**
   * HTML void elements — tags that must not have a close tag and are always
   * rendered as self-closing (e.g. {@code <br />}, {@code <img />}).
   *
   * @see <a href="https://html.spec.whatwg.org/multipage/syntax.html#void-elements">HTML spec: void elements</a>
   */
  private static final Set<String> VOID_ELEMENTS = new HashSet<String>(
      Arrays.asList("area", "base", "basefont", "br", "col", "embed",
          "frame", "hr", "img", "input", "isindex", "link", "meta",
          "param", "source", "track", "wbr"));
  private List<FuzzyXMLNode> _children = new ArrayList<FuzzyXMLNode>();
  private List<FuzzyXMLAttribute> _attributes = new ArrayList<FuzzyXMLAttribute>();
  private String _name;
  private int _nameOffset;

  private int _openTagLength;
  private int _closeTagOffset;
  private int _closeTagLength;
  private int _closeNameOffset;
  
  private Boolean _isNonBreaking;
  
  private boolean _synthetic;

  public FuzzyXMLElementImpl(String name) {
    this(null, name, -1, -1, -1);
  }

  public FuzzyXMLElementImpl(FuzzyXMLNode parent, String name, int offset, int length, int nameOffset) {
    super(parent, offset, length);
    this._name = name;
    _nameOffset = nameOffset;
    _closeTagOffset = -1;
    _closeNameOffset = -1;
    _openTagLength = length - 2;
  }

  public int getOpenTagLength() {
    return _openTagLength;
  }

  public int getNameOffset() {
    return _nameOffset;
  }

  public int getNameLength() {
    return _name != null ? _name.length() : 0;
  }

  public boolean hasCloseTag() {
    return _closeTagOffset != -1 && _closeTagLength > 0;
  }

  public void setCloseTagOffset(int closeTagOffset) {
    _closeTagOffset = closeTagOffset;
  }

  public int getCloseTagOffset() {
    return _closeTagOffset;
  }

  public void setCloseTagLength(int closeTagLength) {
    _closeTagLength = closeTagLength;
  }

  public int getCloseTagLength() {
    return _closeTagLength;
  }

  public void setCloseNameOffset(int closeNameOffset) {
    _closeNameOffset = closeNameOffset;
  }

  public int getCloseNameOffset() {
    return _closeNameOffset;
  }

  public int getCloseNameLength() {
    return getNameLength();
  }

  public String getName() {
    return _name;
  }

  /**
   * XML�̒f�Ѓe�L�X�g����q�m�[�h�Q��ǉ����܂��B
   * <p>
   * �ʏ��<code>appendChild()</code>�Ŏq�m�[�h��ǉ������ꍇ�A
   * ���X�i�ɂ�<code>FuzzyXMLNode#toXMLString()</code>�̌��ʂ��V�����e�L�X�g�Ƃ��Ēʒm����܂����A
   * ���̃��\�b�h��p���Ďq�m�[�h��ǉ������ꍇ�A��œn�����e�L�X�g���V�����e�L�X�g�Ƃ��ēn����܂��B
   * �s����XML���p�[�X���A���̃e�L�X�g����ێ�����K�v������ꍇ�Ɏg�p���Ă��������B
   * </p>
   * @param text �ǉ�����q�v�f���܂�XML�̒f�ЁB
   */
  public void appendChildrenFromText(String text, boolean wo54) {
    if (text.length() == 0) {
      return;
    }
    // ��x�G�������g��}��ăI�t�Z�b�g���擾
    FuzzyXMLElement test = new FuzzyXMLElementImpl("test");
    appendChild(test);
    int offset = test.getOffset();
    // �I�t�Z�b�g���擾�����炷���폜
    removeChild(test);

    String parseText = "<root>" + text + "</root>";

    FuzzyXMLElement root = new FuzzyXMLParser(wo54).parse(parseText).getDocumentElement();
    ((AbstractFuzzyXMLNode) root).appendOffset(root, 0, -6);
    ((AbstractFuzzyXMLNode) root).appendOffset(root, 0, offset);
    FuzzyXMLNode[] nodes = ((FuzzyXMLElement) root.getChildren()[0]).getChildren();

    appendOffset(this, offset, text.length());

    for (int i = 0; i < nodes.length; i++) {
      appendChild(nodes[i], false, false);
    }

    fireModifyEvent(text, offset, 0);
  }

  /**
   * ���̃G�������g�Ɏq�m�[�h��ǉ����܂��B
   * �ȉ��̏ꍇ�̓m�[�h��ǉ����邱�Ƃ͂ł��܂���iFuzzyXMLException���������܂��j�B
   * 
   * <ul>
   *   <li>�G�������g�����̃c���[�ɑ����Ă���ꍇ�i�e�G�������g����remove����Βǉ��ł��܂��j</li>
   *   <li>�G�������g���q�m�[�h�������Ă���ꍇ</li>
   * </ul>
   * 
   * @param node �ǉ�����m�[�h�B
   *   �G�������g�̏ꍇ�A�q�������Ȃ��G�������g���w�肵�Ă��������B
   *   ���łɎq�v�f���\�z�ς݂̃G�������g��n���Ɠ����ŕێ����Ă���ʒu��񂪓����܂���B
   *   
   * @exception jp.aonir.fuzzyxml.FuzzyXMLException �m�[�h��ǉ��ł��Ȃ��ꍇ
   */
  public void appendChild(FuzzyXMLNode node) {
    appendChild(node, true, true);
  }

  /**
   * �p�[�X����<code>appendChild()</code>���\�b�h�̑���Ɏg�p���܂��B
   */
  public void appendChildWithNoCheck(FuzzyXMLNode node) {
    appendChild(node, true, false);
  }

  /**
   * ���̃G�������g�Ɏq�m�[�h��ǉ��B
   * 
   * @param node �ǉ�����m�[�h�B
   *   �G�������g�̏ꍇ�A�q�������Ȃ��G�������g���w�肵�Ă��������B
   *   ���łɎq�v�f���\�z�ς݂̃G�������g��n���Ɠ����ŕێ����Ă���ʒu��񂪓����܂���B
   * @param fireEvent �C�x���g�𔭉΂��邩�ǂ����B
   *   false���w�肵���ꍇ�A�m�[�h�������Ă���ʒu���̓������s���܂���B
   * @param check �ǉ�����m�[�h�̌��؂��s�����ǂ����B
   *   true���w�肵���ꍇ�A�ȉ��̂ɊY������ꍇFuzzyXMLException��throw���܂��B
   *   <ul>
   *     <li>�m�[�h�����̃c���[�ɑ����Ă���ꍇ</li>
   *     <li>�G�������g�����łɎq���������Ă���ꍇ</li>
   *   </ul>
   *   �p�[�X���ȂǁA���؂��s�������Ȃ��ꍇ��false���w�肵�܂��B
   *   
   * @exception jp.aonir.fuzzyxml.FuzzyXMLException �m�[�h��ǉ��ł��Ȃ��ꍇ
   */
  private void appendChild(FuzzyXMLNode node, boolean fireEvent, boolean check) {
    if (check) {
      if (((AbstractFuzzyXMLNode) node).getDocument() != null) {
        throw new FuzzyXMLException("Appended node already has a parent.");
      }

      if (node instanceof FuzzyXMLElement) {
        if (((FuzzyXMLElement) node).getChildren().length != 0) {
          throw new FuzzyXMLException("Appended node has children.");
        }
      }
    }

    AbstractFuzzyXMLNode nodeImpl = (AbstractFuzzyXMLNode) node;
    nodeImpl.setParentNode(this);
    nodeImpl.setDocument(getDocument());
    if (node instanceof FuzzyXMLAttribute) {
      setAttribute((FuzzyXMLAttribute) node);
    }
    else {
      if (_children.contains(node)) {
        return;
      }
      if (getDocument() == null) {
        _children.add(node);
        return;
      }
      // �ǉ�����m�[�h�̈ʒu(�Ō�)���v�Z
      FuzzyXMLNode[] nodes = getChildren();
      int offset = 0;
      if (nodes.length == 0) {
        int length = getLength();
        FuzzyXMLAttribute[] attrs = getAttributes();
        offset = getOffset() + getName().length();
        for (int i = 0; i < attrs.length; i++) {
          offset = offset + attrs[i].toXMLString(new RenderContext(getDocument().isHTML())).length();
        }
        // ���������H
        offset = offset + 2;

        nodeImpl.setOffset(offset);
        if (fireEvent) {
          nodeImpl.setLength(node.toXMLString(new RenderContext(getDocument().isHTML())).length());
        }

        _children.add(node);
        String xml = toXMLString(new RenderContext(getDocument().isHTML()));
        _children.remove(node);

        // �C�x���g�̔���
        if (fireEvent) {
          fireModifyEvent(xml, getOffset(), getLength());
          // �ʒu���̍X�V
          appendOffset(this, offset, xml.length() - length);
        }

        _children.add(node);

      }
      else {
        for (int i = 0; i < nodes.length; i++) {
          offset = nodes[i].getOffset() + nodes[i].getLength();
        }
        // �C�x���g�̔���
        if (fireEvent) {
          fireModifyEvent(nodeImpl.toXMLString(new RenderContext(getDocument().isHTML())), offset, 0);
          // �ʒu���̍X�V
          appendOffset(this, offset, node.toXMLString(new RenderContext(getDocument().isHTML())).length());
        }

        // �Ō�ɒǉ�
        nodeImpl.setOffset(offset);
        if (fireEvent) {
          nodeImpl.setLength(node.toXMLString(new RenderContext(getDocument().isHTML())).length());
        }

        _children.add(node);
      }
    }
  }

  public FuzzyXMLAttribute[] getAttributes() {
    return _attributes.toArray(new FuzzyXMLAttribute[_attributes.size()]);
  }

  public FuzzyXMLNode getChild(int index) {
    return _children.get(index);
  }

  public FuzzyXMLElement getChildElement(int index) {
    return (FuzzyXMLElement) _children.get(index);
  }
  
  public FuzzyXMLNode[] getChildren() {
    // �A�g���r���[�g�͊܂܂Ȃ��H
    return _children.toArray(new FuzzyXMLNode[_children.size()]);
  }

  public boolean hasChildren() {
    return _children.size() > 0;
  }

  public boolean isEmpty() {
    boolean empty = !hasChildren();
    if (!empty) {
      empty = true;
      for (FuzzyXMLNode child : _children) {
        if (child instanceof FuzzyXMLText) {
          FuzzyXMLText text = (FuzzyXMLText) child;
          String textValue = text.getValue();
          if (textValue != null && textValue.trim().length() > 0) {
            empty = false;
            break;
          }
        }
        else {
          empty = false;
          break;
        }
      }
    }
    return empty;
  }

  public void insertAfter(FuzzyXMLNode newChild, FuzzyXMLNode refChild) {
    // �A�g���r���[�g�̏ꍇ�͂Ȃɂ����Ȃ�
    if (newChild instanceof FuzzyXMLAttribute || refChild instanceof FuzzyXMLAttribute) {
      return;
    }
    // �}���ʒu��T��
    FuzzyXMLNode[] children = getChildren();
    FuzzyXMLNode targetNode = null;
    boolean flag = false;
    for (int i = 0; i < children.length; i++) {
      if (flag) {
        targetNode = children[i];
      }
      if (children[i] == refChild) {
        flag = true;
      }
    }
    if (targetNode == null && flag) {
      appendChild(newChild);
    }
    else {
      insertBefore(newChild, targetNode);
    }
  }

  public void insertBefore(FuzzyXMLNode newChild, FuzzyXMLNode refChild) {
    // �A�g���r���[�g�̏ꍇ�͂Ȃɂ����Ȃ�
    if (newChild instanceof FuzzyXMLAttribute || refChild instanceof FuzzyXMLAttribute) {
      return;
    }
    // �}���ʒu��T��
    FuzzyXMLNode target = null;
    int index = -1;
    FuzzyXMLNode[] children = getChildren();
    for (int i = 0; i < children.length; i++) {
      if (children[i] == refChild) {
        target = children[i];
        index = i;
        break;
      }
    }
    if (target == null) {
      return;
    }
    int offset = target.getOffset();
    // �C�x���g�̔���
    fireModifyEvent(newChild.toXMLString(new RenderContext(getDocument().isHTML())), offset, 0);

    AbstractFuzzyXMLNode nodeImpl = (AbstractFuzzyXMLNode) newChild;
    nodeImpl.setParentNode(this);
    nodeImpl.setDocument(getDocument());
    nodeImpl.setOffset(offset);
    nodeImpl.setLength(newChild.toXMLString(new RenderContext(getDocument().isHTML())).length());

    // �ʒu���̍X�V
    appendOffset(this, offset, nodeImpl.toXMLString(new RenderContext(getDocument().isHTML())).length());

    // �Ō�ɒǉ�
    this._children.add(index, nodeImpl);
  }

  public void replaceChild(FuzzyXMLNode newChild, FuzzyXMLNode refChild) {
    // �A�g���r���[�g�̏ꍇ�͂Ȃɂ����Ȃ�
    if (newChild instanceof FuzzyXMLAttribute || refChild instanceof FuzzyXMLAttribute) {
      return;
    }
    // �u������m�[�h�̃C���f�b�N�X���擾
    int index = -1;
    for (int i = 0; i < _children.size(); i++) {
      if (refChild == _children.get(i)) {
        index = i;
        break;
      }
    }
    // �m�[�h��������Ȃ�������Ȃɂ����Ȃ�
    if (index == -1) {
      return;
    }
    _children.remove(index);

    AbstractFuzzyXMLNode nodeImpl = (AbstractFuzzyXMLNode) newChild;
    nodeImpl.setParentNode(this);
    nodeImpl.setDocument(getDocument());
    nodeImpl.setOffset(refChild.getOffset());
    nodeImpl.setLength(newChild.toXMLString(new RenderContext(getDocument().isHTML())).length());

    // �C�x���g�̔���
    fireModifyEvent(newChild.toXMLString(new RenderContext(getDocument().isHTML())), refChild.getOffset(), refChild.getLength());
    // �ʒu���̍X�V
    appendOffset(this, refChild.getOffset(), newChild.getLength() - refChild.getLength());

    _children.add(index, newChild);
  }

  public void removeChild(FuzzyXMLNode oldChild) {
    if (oldChild instanceof FuzzyXMLAttribute) {
      removeAttributeNode((FuzzyXMLAttribute) oldChild);
      return;
    }
    if (_children.contains(oldChild)) {
      // �f�^�b�`
      ((AbstractFuzzyXMLNode) oldChild).setParentNode(null);
      ((AbstractFuzzyXMLNode) oldChild).setDocument(null);
      // ���X�g����폜
      _children.remove(oldChild);
      // �C�x���g�̔���
      fireModifyEvent("", oldChild.getOffset(), oldChild.getLength());
      // �ʒu���̍X�V
      appendOffset(this, oldChild.getOffset(), oldChild.getLength() * -1);
    }
  }

  public void setAttribute(FuzzyXMLAttribute attr) {
    FuzzyXMLAttribute attrNode = getAttributeNode(attr.getNamespaceName());
    if (attrNode == null) {
      if (_attributes.contains(attr)) {
        return;
      }
      if (getDocument() == null) {
        _attributes.add(attr);
        return;
      }
      FuzzyXMLAttributeImpl attrImpl = (FuzzyXMLAttributeImpl) attr;
      attrImpl.setDocument(getDocument());
      attrImpl.setParentNode(this);
      // �ǉ�����A�g���r���[�g�̈ʒu������
      FuzzyXMLAttribute[] attrs = getAttributes();
      int offset = getOffset() + getName().length() + 1;
      for (int i = 0; i < attrs.length; i++) {
        offset = offset + attrs[i].toXMLString(new RenderContext(getDocument().isHTML())).length();
      }
      // �X�V�C�x���g�𔭉�
      fireModifyEvent(attr.toXMLString(new RenderContext(getDocument().isHTML())), offset, 0);
      // �ʒu���̍X�V
      appendOffset(this, offset, attr.toXMLString(new RenderContext(getDocument().isHTML())).length());
      // �Ō�ɒǉ�
      attrImpl.setOffset(offset);
      attrImpl.setLength(attrImpl.toXMLString(new RenderContext(getDocument().isHTML())).length());
      _attributes.add(attrImpl);
    }
    else {
      // ���̏ꍇ�̓A�g���r���[�g��setValue���\�b�h���ŃC�x���g����
      FuzzyXMLAttributeImpl attrImpl = (FuzzyXMLAttributeImpl) attrNode;
      attrImpl.setValue(attr.getValue());
    }
  }

  public FuzzyXMLAttribute getAttributeNode(String name) {
    String namespace;
    int colonIndex = name.indexOf(':');
    if (colonIndex == -1) {
      namespace = null;
    }
    else {
      namespace = name.substring(0, colonIndex);
      name = name.substring(colonIndex + 1);
    }
    
    FuzzyXMLAttribute[] attrs = getAttributes();
    for (int i = 0; i < attrs.length; i++) {
      if (Objects.equals(namespace, attrs[i].getNamespace()) && attrs[i].getName().equalsIgnoreCase(name)) {
        return attrs[i];
      }
    }
    return null;
  }

  public boolean hasAttribute(String name) {
    return getAttributeNode(name) != null;
  }

  public void removeAttributeNode(FuzzyXMLAttribute attr) {
    if (_attributes.contains(attr)) {
      // �f�^�b�`
      ((AbstractFuzzyXMLNode) attr).setParentNode(null);
      ((AbstractFuzzyXMLNode) attr).setDocument(null);
      // ���X�g����폜
      _attributes.remove(attr);
      // �C�x���g�̔���
      fireModifyEvent("", attr.getOffset(), attr.getLength());
      // �ʒu���̍X�V
      appendOffset(this, attr.getOffset(), attr.getLength() * -1);
    }
  }

  public String getValue() {
    RenderContext rc = new RenderContext(false);
    rc.setIndent(0);
    rc.setIndentSize(2);
    rc.setSpaceInEmptyTags(true);
    return getValue(rc, new StringBuffer());
  }
  
  public String getValue(RenderContext renderContext, StringBuffer xmlBuffer) {
    StringBuffer sb = new StringBuffer(xmlBuffer);
    int length = xmlBuffer.length();
    FuzzyXMLNode[] children = getChildren();
    RenderDelegate delegate = renderContext.getDelegate();

    for (int i = 0; i < children.length; i++) {
      if (delegate == null || delegate.renderNode(children[i], renderContext, sb)) {
        children[i].toXMLString(renderContext, sb);
      }
    }
    sb.delete(0, length);
    return sb.toString();
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
    String name = getName();
    if (name != null && name.trim().length() > 0) {
      buffer.append(name);
    }
    else {
      buffer.append("[unknown: '" + name + "']");
    }
    if (_attributes != null && _attributes.size() > 0) {
      buffer.append(", attributes={");
      FuzzyXMLAttribute[] attributes = getAttributes();
      for (int i = 0; i < attributes.length; i ++) {
        attributes[i].toDebugString(buffer, 0);
        if (i < attributes.length - 1) {
          buffer.append("; ");
        }
      }
      buffer.append("}");
    }
    buffer.append("\n");
    for (FuzzyXMLNode child : getChildren()) {
      child.toDebugString(buffer, indent + 1);
    }
  }
  
  public void toXMLString(RenderContext renderContext, StringBuffer xmlBuffer) {
  	if (isSynthetic()) {
  		return;
  	}
  	
    boolean isHTML = renderContext.isHtml();

    boolean renderSurroundingTags = true;
    RenderDelegate delegate = renderContext.getDelegate();
    if (delegate != null) {
      renderSurroundingTags = delegate.beforeOpenTag(this, renderContext, xmlBuffer);
    }
    try {
      String tagName = FuzzyXMLUtil.escape(getName(), isHTML);
      if (renderContext.isLowercaseTags() && FuzzyXMLUtil.isAllUppercase(tagName)) {
        tagName = tagName.toLowerCase();
      }

      if (renderSurroundingTags) {
        xmlBuffer.append("<").append(tagName);
        FuzzyXMLAttribute[] attrs = getAttributes();
        for (int i = 0; i < attrs.length; i++) {
          attrs[i].toXMLString(renderContext, xmlBuffer);
        }
      }

      if (isSelfClosing()) {
        // wo: and p: tags — render as <wo:str />
        if (renderSurroundingTags) {
          if (renderContext.isSpaceInEmptyTags()) {
            xmlBuffer.append(" ");
          }
          xmlBuffer.append("/>");
        }

        xmlBuffer.append(getValue(renderContext, xmlBuffer));
      }
      else if (isForbiddenFromHavingChildren()) {
        // HTML void elements (br, img, hr, etc.) — render as <br> with
        // no closing tag and no self-closing slash, matching modern HTML.
        if (renderSurroundingTags) {
          xmlBuffer.append(">");
        }
      }
      else {
        if (renderSurroundingTags) {
          xmlBuffer.append(">");
        }

        FuzzyXMLNode[] children = getChildren();
        if (children.length == 0) {
          // Empty non-void element (e.g. <script src="..."></script>) —
          // render the close tag immediately with no whitespace inserted.
          if (renderSurroundingTags) {
            xmlBuffer.append("</").append(tagName).append(">");
          }
        }
        else {
          if (delegate != null) {
            delegate.afterOpenTag(this, renderContext, xmlBuffer);
          }

          xmlBuffer.append(getValue(renderContext, xmlBuffer));

          if (delegate != null) {
            delegate.beforeCloseTag(this, renderContext, xmlBuffer);
          }

          if (renderSurroundingTags) {
            xmlBuffer.append("</").append(tagName).append(">");
          }
        }
      }
    } catch (Exception e) {
      HTMLPlugin.logException(e);
    }
    finally {
      if (delegate != null) {
        delegate.afterCloseTag(this, renderContext, xmlBuffer);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof FuzzyXMLElement) {
      FuzzyXMLElement element = (FuzzyXMLElement) obj;

      // �^�O�̖��O���������false
      if (!element.getName().equals(getName())) {
        return false;
      }

      // �e������Ƃ�null��������true
      FuzzyXMLNode parent = element.getParentNode();
      if (parent == null) {
        if (getParentNode() == null) {
          return true;
        }
        return false;
      }

      // �J�n�I�t�Z�b�g��������������true
      if (element.getOffset() == getOffset()) {
        return true;
      }

    }
    return false;
  }

  public String getAttributeValue(String name) {
    FuzzyXMLAttribute attr = getAttributeNode(name);
    if (attr != null) {
      return attr.getValue();
    }
    return null;
  }

  public void setAttribute(String namespace, String name, String value) {
    FuzzyXMLAttribute attr = new FuzzyXMLAttributeImpl(namespace, name, value, value);
    setAttribute(attr);
  }

  public void removeAttribute(String name) {
    FuzzyXMLAttribute attr = getAttributeNode(name);
    if (attr != null) {
      removeAttributeNode(attr);
    }
  }

  @Override
  public void setDocument(FuzzyXMLDocumentImpl doc) {
    super.setDocument(doc);
    FuzzyXMLNode[] nodes = getChildren();
    for (int i = 0; i < nodes.length; i++) {
      ((AbstractFuzzyXMLNode) nodes[i]).setDocument(doc);
    }
    FuzzyXMLAttribute[] attrs = getAttributes();
    for (int i = 0; i < attrs.length; i++) {
      ((AbstractFuzzyXMLNode) attrs[i]).setDocument(doc);
    }
  }

  @Override
  public String toString() {
    return "element: " + getName() + "; attributes = " + _attributes;
  }

  public void removeAllChildren() {
    FuzzyXMLNode[] children = getChildren();
    for (int i = 0; i < children.length; i++) {
      removeChild(children[i]);
    }
  }

  @Override
  public TextRegion getRegionAtOffset(int offset, String source, boolean regionForInsert) {
    int openTagOffset = getOffset();
    int openTagLength = getOpenTagLength() + 2;
    int openTagEndOffset = openTagOffset + openTagLength;
    if (hasCloseTag()) {
      int closeTagOffset = getCloseTagOffset();
      int closeTagEndOffset = closeTagOffset + getCloseTagLength();
      if (!regionForInsert) {
        return new TextRegion(openTagOffset, closeTagOffset - openTagOffset + getCloseTagLength() + 2);
      }
      if ((offset >= openTagOffset && offset < openTagEndOffset)
          || (offset >= closeTagOffset && offset < closeTagEndOffset)) {
        // If the open tag ends exactly at end-of-line (its '>' is the last
        // character before the line terminator), extend the region by one
        // character so the caret lands on the next line — preserving the
        // visual line break the user expects.
        if (source != null && isOffsetAtEndOfLine(source, openTagEndOffset)) {
          openTagEndOffset++;
          openTagLength++;
        }
        return new TextRegion(openTagOffset, openTagLength);
      }
      return new TextRegion(offset, 0);
    }
    return new TextRegion(getOffset(), getLength());
  }

  /**
   * Returns true if {@code offset} points just past the last non-terminator
   * character of a line — i.e. the character at {@code offset - 1} is the
   * last visible character before a line break.
   *
   * <p>Pure-Java replacement for {@code IDocument.getLineInformationOfOffset}
   * — we only need the "is this offset at end of line?" predicate.
   */
  private static boolean isOffsetAtEndOfLine(String source, int offset) {
    if (offset < 0 || offset > source.length()) {
      return false;
    }
    if (offset == source.length()) {
      return true; // end of document counts as end of last line
    }
    char c = source.charAt(offset);
    return c == '\n' || c == '\r';
  }
  
  /**
   * Determines whether this element's content should stay on one line
   * (non-breaking) or be expanded with newlines and indentation (breaking).
   * <p>
   * The primary signal is the <b>original source structure</b>: if the
   * author placed a newline between the open tag and the first child, the
   * element is treated as breaking and the formatter will indent its
   * children. If the content was on a single line, it stays that way.
   * <p>
   * This respects intentional formatting — {@code <td><a>x</a></td>} stays
   * inline — while still expanding elements that were already multi-line or
   * that have complex content (multiple child elements or text blocks).
   * <p>
   * <b>Note:</b> This heuristic is a first pass. It may need refinement for
   * edge cases — e.g. very long single-line content that should wrap, or
   * minified input where everything is on one line.
   */
  @Override
  public boolean isNonBreaking() {
    if (_isNonBreaking != null) {
      return _isNonBreaking;
    }

    // The document root always breaks.
    if (getParentNode() == null) {
      _isNonBreaking = false;
      return false;
    }

    FuzzyXMLNode children[] = getChildren();

    // Check if the original source had newlines in the whitespace between
    // children. If so, the author intended this element to break — respect
    // that. If all inter-child whitespace is flat (spaces/tabs only), the
    // original was inline.
    boolean originalHadNewlines = false;

    for (int i = 0; i < children.length; i++) {
      FuzzyXMLNode child = children[i];
      if (child instanceof FuzzyXMLText) {
        FuzzyXMLText text = (FuzzyXMLText) child;
        if (text.isHidden()) {
          // Whitespace-only text node — check if it contains a newline.
          // getValue() returns the raw whitespace unchanged (no entities
          // to decode), so contains("\n") reliably detects line breaks.
          if (text.getValue().contains("\n")) {
            originalHadNewlines = true;
          }
        } else {
          // Visible text node — when there's no separate whitespace node
          // (e.g. the parser creates a single text node for
          // "\n\t\tHello\n\t"), check whether it starts or ends with a
          // newline. This catches cases like <wo:if>\n  text\n</wo:if>.
          String value = text.getValue();
          if (value.length() > 0) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == '\n' || first == '\r' || last == '\n' || last == '\r') {
              originalHadNewlines = true;
            }
          }
        }
      }
    }

    // If the original source had newlines in the whitespace between
    // children, the author intended multi-line layout — break it.
    // Otherwise, the content was inline — keep it that way.
    _isNonBreaking = !originalHadNewlines;
    return _isNonBreaking;
  }
  
  public boolean isSelfClosing() {
    return isSelfClosing(this);
  }
  
  public boolean isForbiddenFromHavingChildren() {
    String tagName = getName().toLowerCase();
    return VOID_ELEMENTS.contains(tagName);
  }

  /**
   * Determines whether an element should be rendered as a self-closing tag
   * ({@code <tag />}) rather than an open+close pair ({@code <tag></tag>}).
   * <p>
   * Only {@code wo:} and {@code p:} namespaced tags self-close when empty.
   * HTML elements — including void elements like {@code <br>} and
   * {@code <img>} — are rendered without a closing tag or as open+close
   * pairs, matching modern HTML conventions where self-closing syntax
   * ({@code <br />}) is unnecessary.
   */
  public static boolean isSelfClosing(FuzzyXMLElement node) {
    FuzzyXMLNode[] children = node.getChildren();
    if (children.length != 0) {
      return false;
    }

    String tagName = node.getName().toLowerCase();

    // wo: and p: namespaced tags can self-close when empty.
    if (tagName.startsWith("wo:") || tagName.startsWith("p:")) {
      return true;
    }

    // HTML elements are never self-closed — void elements get their
    // close tag suppressed by isForbiddenFromHavingChildren() instead.
    return false;
  }
  
  @Override
  public boolean isHidden() {
    return getName() == null || getName().equals("");
  }
  
  public void setSynthetic(boolean synthetic) {
  	_synthetic = synthetic;
  }
  
  public boolean isSynthetic() {
  	return _synthetic;
  }
}

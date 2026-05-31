package tk.eclipse.plugin.htmleditor.assist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.editor.template.InlineWodTagInfo;
import org.objectstyle.wolips.wodclipse.core.preferences.WodFormattingPreferences;

import jp.aonir.fuzzyxml.FuzzyXMLAttribute;
import jp.aonir.fuzzyxml.FuzzyXMLDocument;
import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.FuzzyXMLNode;
import jp.aonir.fuzzyxml.FuzzyXMLParser;
import tk.eclipse.plugin.htmleditor.HTMLPlugin;
import tk.eclipse.plugin.htmleditor.HTMLUtil;
import tk.eclipse.plugin.htmleditor.IFileAssistProcessor;
import tk.eclipse.plugin.htmleditor.editors.HTMLSourceEditor;
import tk.eclipse.plugin.htmleditor.template.HTMLTemplateAssistProcessor;

/**
 * An implementation of <code>IContentAssistProcessor</code>.
 * This processor provides code-completion for the <code>HTMLSourceEditor</code>.
 * 
 * @author Naoki Takezoe
 */
public class HTMLAssistProcessor extends HTMLTemplateAssistProcessor { /*implements IContentAssistProcessor {*/

  private boolean _xhtmlMode = false;
  private char[] _chars = {};
  private Image _tagImage;
  private Image _attrImage;
  private Image _valueImage;
  private boolean _assistCloseTag = true;
  private List<CustomAttribute> _customAttrs = CustomAttribute.loadFromPreference(false);
  private List<CustomElement> _customElems = CustomElement.loadFromPreference(false);
  private Set<String> _customElemNames = new HashSet<String>();
  protected CSSAssistProcessor _cssAssist = new CSSAssistProcessor();
  protected IFileAssistProcessor[] _fileAssistProcessors;

  private int _offset;
  private FuzzyXMLDocument _doc;
  private ITextViewer _textViewer;

  /**
   * The constructor.
   */
  public HTMLAssistProcessor() {
    _tagImage = HTMLPlugin.getDefault().getImageRegistry().get(HTMLPlugin.ICON_TAG);
    _attrImage = HTMLPlugin.getDefault().getImageRegistry().get(HTMLPlugin.ICON_ATTR);
    _valueImage = HTMLPlugin.getDefault().getImageRegistry().get(HTMLPlugin.ICON_VALUE);
    _fileAssistProcessors = HTMLPlugin.getDefault().getFileAssistProcessors();

    for (int i = 0; i < _customElems.size(); i++) {
      _customElemNames.add(_customElems.get(i).getDisplayName());
    }
  }

  public boolean enableTemplate() {
    return true;
  }

  public void setXHTMLMode(boolean xhtmlMode) {
    this._xhtmlMode = xhtmlMode;
  }

  public void setAutoAssistChars(char[] chars) {
    if (chars != null) {
      this._chars = chars;
    }
  }

  public void setAssistCloseTag(boolean assistCloseTag) {
    this._assistCloseTag = assistCloseTag;
  }

  /**
   * Returns an array of attribute value proposals.
   * 
   * @param tagName the tag name
   * @param value the attribute value
   * @param attrInfo the attribute information
   * @return the array of attribute value proposals
   */
  protected AssistInfo[] getAttributeValues(String tagName, String value, TagInfo tagInfo, AttributeInfo attrInfo) {
    // CSS
    if (attrInfo.getAttributeType() == AttributeInfo.CSS) {
      return _cssAssist.getAssistInfo(tagName, value);
    }
    // FILE
    if (attrInfo.getAttributeType() == AttributeInfo.FILE) {
      ArrayList<AssistInfo> list = new ArrayList<AssistInfo>();
      for (int i = 0; i < _fileAssistProcessors.length; i++) {
        AssistInfo[] assists = _fileAssistProcessors[i].getAssistInfo(value);
        for (int j = 0; j < assists.length; j++) {
          list.add(assists[j]);
        }
      }
      return list.toArray(new AssistInfo[list.size()]);
    }
    // IDREF
    if (attrInfo.getAttributeType() == AttributeInfo.IDREF) {
      ArrayList<AssistInfo> list = new ArrayList<AssistInfo>();
      String[] ids = getIDs();
      for (int i = 0; i < ids.length; i++) {
        list.add(new AssistInfo(ids[i]));
      }
      return list.toArray(new AssistInfo[list.size()]);
    }
    // IDREFS
    if (attrInfo.getAttributeType() == AttributeInfo.IDREFS) {
      ArrayList<AssistInfo> list = new ArrayList<AssistInfo>();
      String[] ids = getIDs();
      String prefix = value;
      if (prefix.length() != 0 && !prefix.endsWith(" ")) {
        prefix = prefix + " ";
      }
      for (int i = 0; i < ids.length; i++) {
        list.add(new AssistInfo(prefix + ids[i], ids[i]));
      }
      return list.toArray(new AssistInfo[list.size()]);
    }
    // ETC
    String[] values = AttributeValueDefinition.getAttributeValues(attrInfo.getAttributeType());
    AssistInfo[] infos = new AssistInfo[values.length];
    for (int i = 0; i < infos.length; i++) {
      infos[i] = new AssistInfo(values[i]);
    }
    return infos;
  }

  /**
   * Returns ID attribute values.
   * 
   * @return the array which contans ID attribute values
   */
  protected String[] getIDs() {
    FuzzyXMLDocument doc = getDocument();
    List<String> list = new ArrayList<String>();
    if (doc != null) {
      FuzzyXMLElement element = doc.getDocumentElement();
      extractID(element, list);
    }
    return list.toArray(new String[list.size()]);
  }

  private void extractID(FuzzyXMLElement element, List<String> list) {
    FuzzyXMLAttribute[] attrs = element.getAttributes();
    for (int i = 0; i < attrs.length; i++) {
      TagInfo tagInfo = getTagInfo(element.getName());
      if (tagInfo != null) {
        AttributeInfo attrInfo = tagInfo.getAttributeInfo(attrs[i].getName());
        if (attrInfo != null) {
          if (attrInfo.getAttributeType() == AttributeInfo.ID) {
            list.add(attrs[i].getValue());
          }
        }
      }
    }
    FuzzyXMLNode[] nodes = element.getChildren();
    for (int i = 0; i < nodes.length; i++) {
      if (nodes[i] instanceof FuzzyXMLElement) {
        extractID((FuzzyXMLElement) nodes[i], list);
      }
    }
  }

  /**
   * Returns the <code>List</code> which contains 
   * all <code>TagInfo</code>s.
   * 
   * @return the <code>List</code> which contains
   *    all <code>TagInfo</code>s
   */
  protected List<TagInfo> getTagList() {
    return TagDefinition.getTagInfoAsList();
  }

  /**
   * Returns the <code>TagInfo</code> which has the specified name.
   * 
   * @param name a tag name
   * @return the <code>TagInfo</code>
   */
  protected TagInfo getTagInfo(String name) {
    List tagList = TagDefinition.getTagInfoAsList();
    for (int i = 0; i < tagList.size(); i++) {
      TagInfo info = (TagInfo) tagList.get(i);
      if (info.getTagName().equals(name)) {
        return info;
      }
    }

    return null;
  }

  /**
   * Returns the <code>FuzzyXMLElement</code> by the offset.
   * 
   * @return the <code>FuzzyXMLElement</code>
   */
  protected FuzzyXMLElement getOffsetElement() {
    return _doc.getElementByOffset(_offset);
  }

  /**
   * Returns the <code>FuzzyXMLDocument</code>.
   * 
   * @return the <code>FuzzyXMLDocument</code>
   */
  protected FuzzyXMLDocument getDocument() {
    return _doc;
  }

  protected ITextViewer getTextViewer() {
    return _textViewer;
  }

  @Override
  public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int documentOffset) {
    _textViewer = viewer;
    IDocument document = viewer.getDocument();
    String text = document.get().substring(0, documentOffset);
    String[] dim = getLastWord(text);
    String word = dim[0].toLowerCase();
    String prev = dim[1].toLowerCase();
    String last = dim[2];
    String attr = dim[3];
    boolean inTag = false;
    boolean spacesAroundEquals = WodFormattingPreferences.spacesAroundEquals();
    String equals = spacesAroundEquals?" = ":"=";

    try {
      for (int i = documentOffset; i < document.getLength(); i++) {
        char ch = document.getChar(i);
        if (ch == '<') {
          break;
        }
        else if (ch == '>') {
          inTag = true;
          break;
        }
      }
    }
    catch (BadLocationException e) {
      HTMLPlugin.logException(e);
    }
    String next = document.get().substring(documentOffset);

    this._offset = documentOffset;
    this._doc = new FuzzyXMLParser(false).parse(document.get());

    List<ICompletionProposal> list = new ArrayList<ICompletionProposal>();
    List<TagInfo> tagList = getTagList();

    // attribute value
    if ((word.startsWith("\"") && (word.length() == 1 || !word.endsWith("\""))) || (word.startsWith("'") && (word.length() == 1 || !word.endsWith("\'")))) {
      String value = dim[0].substring(1);
      TagInfo tagInfo = getTagInfo(last.toLowerCase());
      if (tagInfo != null) {
        AttributeInfo attrInfo = tagInfo.getAttributeInfo(attr);
        if (attrInfo == null) {
          attrInfo = new AttributeInfo(attr, true);
        }
        if (attrInfo != null) {
          AssistInfo[] keywords = getAttributeValues(last, dim[0].substring(1), tagInfo, attrInfo);
          for (int i = 0; i < keywords.length; i++) {
            if (keywords[i].getOffset() > 0 || keywords[i].getReplaceString().toLowerCase().startsWith(value.toLowerCase())) {
              //							list.add(new CompletionProposal(
              //									keywords[i].getReplaceString(),
              //									documentOffset - value.length(), value.length(),
              //									keywords[i].getReplaceString().length(),
              //									keywords[i].getImage()==null ? valueImage : keywords[i].getImage(),
              //									keywords[i].getDisplayString(), null, null));
              list.add(keywords[i].toCompletionProposal(documentOffset, value, _valueImage));
            }
          }
        }
      }
      // tag
    }
    else if (word.startsWith("<") && !word.startsWith("</")) {
      if (supportTagRelation()) {
        TagInfo parent = getTagInfo(last);
        tagList = new ArrayList<TagInfo>();
        if (parent != null) {
          String[] childNames = parent.getChildTagNames();
          for (int i = 0; i < childNames.length; i++) {
            tagList.add(getTagInfo(childNames[i]));
          }
        }
      }
      List<TagInfo> dynamicTagInfo = getDynamicTagInfo(word.substring(1));
      if (dynamicTagInfo != null) {
        tagList.addAll(dynamicTagInfo);
      }
      for (int i = 0; i < tagList.size(); i++) {
        TagInfo tagInfo = tagList.get(i);
        if (tagInfo instanceof TextInfo) {
          TextInfo textInfo = (TextInfo) tagInfo;
          if ((textInfo.getText().toLowerCase()).indexOf(word) == 0) {
            list.add(new CompletionProposal(textInfo.getText(), documentOffset - word.length(), word.length(), textInfo.getPosition(), _tagImage, textInfo.getDisplayString(), null, tagInfo.getDescription()));
          }
          continue;
        }
        String tagName = tagInfo.getTagName();
        String tagNameMatch = "<" + tagName.toLowerCase();
        if (tagNameMatch.startsWith(word) && !(tagNameMatch.equals(word) && next.startsWith(">"))) {
          String assistKeyword = tagName;
          int position = 0;
          // required attributes
          AttributeInfo[] requireAttrs;
          if (inTag) {
            requireAttrs = new AttributeInfo[0];
            position = tagName.length() + 1;
          }
          else {
            requireAttrs = tagInfo.getRequiredAttributeInfo();
            for (int j = 0; j < requireAttrs.length; j++) {
              assistKeyword = assistKeyword + " " + requireAttrs[j].getAttributeName();
              if (requireAttrs[j].hasValue()) {
                assistKeyword = assistKeyword + equals + "\"\"";
                if (j == 0) {
                  position = tagName.length() + requireAttrs[j].getAttributeName().length() + (spacesAroundEquals ? 5 : 3);
                }
              }
            }
          }
          boolean forceAttributePosition = (requireAttrs.length == 0 && tagInfo.requiresAttributes());
          if (!inTag) {
            if (tagInfo.hasBody()) {
              assistKeyword = assistKeyword + ">";
              if (_assistCloseTag) {
                if (position == 0) {
                  position = assistKeyword.length();
                }
                assistKeyword = assistKeyword + "</" + tagName + ">";
              }
            }
            else {
              if (tagInfo.isEmptyTag() && _xhtmlMode == false) {
                assistKeyword = assistKeyword + ">";
              }
              else {
                assistKeyword = assistKeyword + " />";
              }
            }
          }
          if (position == 0) {
            position = assistKeyword.length();
          }
          if (forceAttributePosition && position > 0) {
            if (tagInfo.hasBody()) {
              position--;
            }
            else if (tagInfo.isEmptyTag()) {
              if (_xhtmlMode) {
                position -= 2;
              }
              else {
                position--;
              }
            }
            else {
              // Self-closing non-void tag (e.g. <wo:foo />): back up past " />"
              // so the cursor lands where attributes should be typed.
              position -= 3;
            }
          }
          try {
            if (tagInfo instanceof InlineWodTagInfo && BindingReflectionUtils.memberIsDeprecated(((InlineWodTagInfo)tagInfo).getElementType())) {
              list.add(new HTMLDeprecatedCompletionProposal(assistKeyword, documentOffset - word.length() + 1, word.length() - 1, position, _tagImage, tagName, null, tagInfo.getDescription()));
            } else {
              list.add(new CompletionProposal(assistKeyword, documentOffset - word.length() + 1, word.length() - 1, position, _tagImage, tagName, null, tagInfo.getDescription()));
            }
          }
          catch (Exception ex) {
            HTMLPlugin.logException(ex);
          }
        }
      }
      // custom elements
      for (int i = 0; i < _customElems.size(); i++) {
        CustomElement element = _customElems.get(i);
        if ((element.getAssistString().toLowerCase()).indexOf(word) == 0) {
          int position = element.getAssistString().indexOf('"');
          if (position == -1) {
            position = element.getAssistString().indexOf("><");
          }
          if (position == -1) {
            position = element.getAssistString().length();
          }
          list.add(new CompletionProposal(element.getAssistString(), documentOffset - word.length(), word.length(), position + 1, _tagImage, element.getDisplayName(), null, null));
        }
      }
      // attribute
    }
    else if (!prev.equals("")) {
      String tagName = prev;
      TagInfo tagInfo = getTagInfo(tagName);
      if (tagInfo != null) {
        AttributeInfo[] attrList = tagInfo.getAttributeInfo();
        for (int j = 0; j < attrList.length; j++) {
          if (attrList[j].getAttributeName().toLowerCase().indexOf(word) == 0) {
            String assistKeyword = null;
            int position = 0;
            if (attrList[j].hasValue()) {
              assistKeyword = attrList[j].getAttributeName() + equals + "\"\"";
              position = equals.length() + 1;
            }
            else {
              assistKeyword = attrList[j].getAttributeName();
              position = 0;
            }
            list.add(new CompletionProposal(assistKeyword, documentOffset - word.length(), word.length(), attrList[j].getAttributeName().length() + position, _attrImage, attrList[j].getAttributeName(), null, attrList[j].getDescription()));
          }
        }
      }
      // custom attributes
      for (int i = 0; i < _customAttrs.size(); i++) {
        CustomAttribute attrInfo = _customAttrs.get(i);
        if (attrInfo.getTargetTag().equals("*") || attrInfo.getTargetTag().equals(tagName)) {
          if (tagName.indexOf(":") < 0 || _customElemNames.contains(tagName)) {
            list.add(new CompletionProposal(attrInfo.getAttributeName() + equals + "\"\"", documentOffset - word.length(), word.length(), attrInfo.getAttributeName().length() + 2, _attrImage, attrInfo.getAttributeName(), null, null));
          }
        }
      }
      // close tag
    }
    else if (!last.equals("")) {
      TagInfo info = getTagInfo(last);
      if (info == null || _xhtmlMode == true || info.hasBody() || !info.isEmptyTag()) {
        String assistKeyword = "</" + last + ">";
        int length = 0;
        if (assistKeyword.toLowerCase().startsWith(word)) {
          length = word.length();
        }
        list.add(new CompletionProposal(assistKeyword, documentOffset - length, length, assistKeyword.length(), _tagImage, assistKeyword, null, null));
      }
    }

    HTMLUtil.sortCompilationProposal(list);

    if (enableTemplate()) {
      ICompletionProposal[] templates = super.computeCompletionProposals(viewer, documentOffset);
      for (int i = 0; i < templates.length; i++) {
        list.add(templates[i]);
      }
    }

    ICompletionProposal[] prop = list.toArray(new ICompletionProposal[list.size()]);
    return prop;
  }

  protected List<TagInfo> getDynamicTagInfo(String tagName) {
    return null;
  }

  /**
   * Returns true if this processor support parent and child relation.
   * In the default, this method returns false.
   */
  protected boolean supportTagRelation() {
    return false;
  }

  /**
   * Returns same informations for code completion from calet position.
   * 
   * @return
   * <ul>
   *   <li>0 - last word from calet position (if it's tag, it contains &lt;)</li>
   *   <li>1 - target of attribute completion (only tag name, not contains &lt;)</li>
   *   <li>2 - target of close tag completion (only tag name, not contains &lt;)</li>
   *   <li>3 - previous attribute name</li>
   * </ul>
   */
  /**
   * Delegates to {@link TagStackAnalyzer#getLastWord(String)}.
   */
  protected String[] getLastWord(String text) {
    return TagStackAnalyzer.getLastWord(text);
  }

  /**
   * Delegates to {@link TagStackAnalyzer#isDelimiter(char)}.
   */
  protected boolean isDelimiter(char c) {
    return TagStackAnalyzer.isDelimiter(c);
  }

  @Override
  public IContextInformation[] computeContextInformation(ITextViewer viewer, int documentOffset) {
    ContextInformation[] info = new ContextInformation[0];
    return info;
  }

  @Override
  public char[] getCompletionProposalAutoActivationCharacters() {
    return _chars;
  }

  @Override
  public char[] getContextInformationAutoActivationCharacters() {
    return _chars;
  }

  @Override
  public IContextInformationValidator getContextInformationValidator() {
    return new ContextInformationValidator(this);
  }

  @Override
  public String getErrorMessage() {
    return "Error";
  }

  /**
   * Updates internal informations.
   * 
   * @param editor the <code>HTMLSourceEditor</code> instance
   * @param source editing source code
   */
  public void update(HTMLSourceEditor editor, String source) {
    IEditorInput editorInput = editor.getEditorInput();
    if (editorInput instanceof IFileEditorInput) {
      IFileEditorInput input = (IFileEditorInput) editorInput;
      _cssAssist.reload(input.getFile(), source);
      _customAttrs = CustomAttribute.loadFromPreference(false);
      _customElems = CustomElement.loadFromPreference(false);

      _customElemNames.clear();
      for (int i = 0; i < _customElems.size(); i++) {
        _customElemNames.add(_customElems.get(i).getDisplayName());
      }

      for (int i = 0; i < _fileAssistProcessors.length; i++) {
        _fileAssistProcessors[i].reload(input.getFile());
      }
    }
  }

}
package org.objectstyle.wolips.wodclipse.core.util;

import java.util.Map;

import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.wod.SimpleWodBinding;
import org.objectstyle.wolips.bindings.wod.SimpleWodElement;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils.BindingValue;

import jp.aonir.fuzzyxml.FuzzyXMLAttribute;
import jp.aonir.fuzzyxml.FuzzyXMLElement;

public class FuzzyXMLWodElement extends SimpleWodElement {
  private FuzzyXMLElement _xmlElement;

  public FuzzyXMLWodElement(FuzzyXMLElement element, ParsleyProject parsleyProject) {
    _xmlElement = element; // keep the source node — used by getXmlElement()/hasContent()
    BuildProperties buildProperties = parsleyProject != null ? parsleyProject.getBuildProperties() : null;
    String inlineBindingPrefix = buildProperties != null ? buildProperties.getInlineBindingPrefix() : "$";
    String inlineBindingSuffix = buildProperties != null ? buildProperties.getInlineBindingSuffix() : "";

    String elementName = element.getName();
    String namespaceElementName = elementName.substring("wo:".length()).trim();
    int elementTypePosition = element.getOffset() + element.getNameOffset() + "wo:".length() + 1;
    int elementTypeLength = namespaceElementName.length();

    // Resolve the tag to its actual element type. When the project declares Parsley tag
    // aliases (the new mechanism), resolve recursively through them, matching the runtime;
    // otherwise use the legacy WOLips tag-shortcut preference (which also carries default
    // attribute bindings, applied below).
    final org.eclipse.jdt.core.IJavaProject javaProject = parsleyProject != null
        ? org.eclipse.jdt.core.JavaCore.create(parsleyProject.getProject()) : null;
    TagShortcut matchingTagShortcut = null;
    if (javaProject != null && org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.isActiveFor(javaProject)) {
      // Resolve to the binding-source element (the documented ancestor, e.g. WOString rather
      // than ERXWOString) — both more correct and far cheaper, since the replacement has no
      // .api and would force slow per-tag reflection during validation.
      namespaceElementName = org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.resolveForBindings(javaProject, namespaceElementName);
    }
    else {
      for (TagShortcut tagShortcut : ApiCache.getTagShortcuts()) {
        if (namespaceElementName.equalsIgnoreCase(tagShortcut.getShortcut())) {
          matchingTagShortcut = tagShortcut;
        }
      }
      if (matchingTagShortcut != null) {
        // Flag case mismatches: user wrote "Repetition" but shortcut is "repetition".
        // We still expand to the actual class name so binding validation works,
        // but record the mismatch so fillInProblems() can warn about it.
        if (!namespaceElementName.equals(matchingTagShortcut.getShortcut())) {
          setTagShortcutCaseMismatch(namespaceElementName, matchingTagShortcut.getShortcut());
        }
        namespaceElementName = matchingTagShortcut.getActual(parsleyProject);
      }
    }

    _setElementName("_temp");
    _setElementType(namespaceElementName);
    setElementTypePosition(new Position(elementTypePosition, elementTypeLength));
    setInline(true);

    if (matchingTagShortcut != null) {
      for (Map.Entry<String, String> shortcutAttribute : matchingTagShortcut.getAttributes().entrySet()) {
        BindingValue value = WodHtmlUtils.toBindingValue(shortcutAttribute.getValue(), inlineBindingPrefix, inlineBindingSuffix);
        SimpleWodBinding wodBinding = new SimpleWodBinding(null, shortcutAttribute.getKey(), value.getValue());
        addBinding(wodBinding);
      }
    }

    FuzzyXMLAttribute[] attributes = element.getAttributes();
    for (FuzzyXMLAttribute attribute : attributes) {
      String namespace = attribute.getNamespace();
      String name = attribute.getName();
      String originalValue = attribute.getValue();
      BindingValue value = WodHtmlUtils.toBindingValue(originalValue, inlineBindingPrefix, inlineBindingSuffix);
      Position valuePosition;
      Position valueNamespacePosition = null;
      if (value.getValueNamespace() != null) {
        int valueNamespaceOffset = originalValue.indexOf(value.getValueNamespace());
        valueNamespacePosition = new Position(element.getOffset() + attribute.getValueDataOffset() + valueNamespaceOffset + 1, value.getValueNamespace().length());
        int valueOffset = originalValue.indexOf(value.getValue(), valueNamespaceOffset + value.getValueNamespace().length());
        valuePosition = new Position(valueNamespacePosition.offset + valueOffset, attribute.getValueDataLength() - valueOffset);
      }
      else {
        valuePosition = new Position(element.getOffset() + attribute.getValueDataOffset() + 1, attribute.getValueDataLength());
      }
      
      SimpleWodBinding wodBinding = new SimpleWodBinding(namespace, name, value.getValueNamespace(), value.getValue(), new Position(attribute.getNamespaceOffset(), attribute.getNamespaceLength()), new Position(attribute.getNameOffset(), attribute.getNameLength()), valueNamespacePosition, valuePosition, -1);
      wodBinding.setStartOffset(attribute.getOffset());
      wodBinding.setEndOffset(attribute.getOffset() + attribute.getLength());
      addBinding(wodBinding);
    }
    
    setStartOffset(element.getOffset());
    setEndOffset(element.getOffset() + element.getOpenTagLength() + 2);
    if (element.hasCloseTag()) {
      setFullEndOffset(element.getCloseTagOffset() + element.getCloseTagLength() + 2);
    }
    else {
      setFullEndOffset(getEndOffset());
    }
    if (attributes.length > 0) {
      int endOffset = attributes[attributes.length - 1].getOffset() + attributes[attributes.length - 1].getLength();
      setNewBindingOffset(endOffset);
    }
    else {
      setNewBindingOffset(element.getOffset() + element.getNameOffset() + element.getNameLength() + 1);
    }
    
    setNewBindingIndent(1);
  }

  public FuzzyXMLElement getXmlElement() {
    return _xmlElement;
  }

  /**
   * An inline element has content iff its tag has an explicit close tag — {@code <wo:x>…</wo:x>},
   * even when empty — rather than being self-closed ({@code <wo:x/>}). This is the "content" a
   * {@code content="forbidden"} .apiext policy forbids.
   */
  @Override
  public boolean hasContent() {
    return _xmlElement != null && _xmlElement.hasCloseTag();
  }
}

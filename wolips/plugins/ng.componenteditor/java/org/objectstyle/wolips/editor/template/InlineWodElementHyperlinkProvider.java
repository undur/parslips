package org.objectstyle.wolips.editor.template;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.objectstyle.wolips.bindings.wod.IWodBinding;
import org.objectstyle.wolips.bindings.wod.SimpleWodElement;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.document.WodBindingValueHyperlink;
import org.objectstyle.wolips.wodclipse.core.document.WodElementTypeHyperlink;
import org.objectstyle.wolips.wodclipse.core.util.FuzzyXMLWodElement;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

import jp.aonir.fuzzyxml.FuzzyXMLDocument;
import jp.aonir.fuzzyxml.FuzzyXMLElement;
import tk.eclipse.plugin.htmleditor.HTMLPlugin;
import tk.eclipse.plugin.htmleditor.IHyperlinkProvider;
import tk.eclipse.plugin.htmleditor.editors.HTMLHyperlinkInfo;

public class InlineWodElementHyperlinkProvider implements IHyperlinkProvider {
  public HTMLHyperlinkInfo getHyperlinkInfo(IFile file, FuzzyXMLDocument doc, FuzzyXMLElement element, String attrName, String attrValue, int offset) {
    HTMLHyperlinkInfo hyperlinkInfo = null;
    try {
      if (WodHtmlUtils.isWOTag(element.getName()) && WodHtmlUtils.isInline(element.getName())) {
        ParsleyProject parsleyProject = (ParsleyProject)file.getProject().getAdapter(ParsleyProject.class);
        if (attrName == null) {
          WodParserCache cache = WodParserCache.parser(file);
          SimpleWodElement wodElement = new FuzzyXMLWodElement(element, parsleyProject);
          if (wodElement.isTypeWithin(new Region(offset, 0))) {
            hyperlinkInfo = new HTMLHyperlinkInfo();
            hyperlinkInfo.setOffset(wodElement.getElementTypePosition().getOffset());
            hyperlinkInfo.setLength(wodElement.getElementTypePosition().getLength());
            hyperlinkInfo.setObject(WodElementTypeHyperlink.toElementTypeHyperlink(wodElement, cache));
          }
        }
        else {
          WodParserCache cache;
          cache = WodParserCache.parser(file);
          SimpleWodElement wodElement = new FuzzyXMLWodElement(element, parsleyProject);
          IWodBinding wodBinding = wodElement.getBindingNamed(attrName);
          if (wodBinding != null) {
            Position valuePosition = wodBinding.getValuePosition();
            if (valuePosition != null) {
              // Compute the keypath segment under the pointer so Cmd+click opens e.g. activeUser
              // in "$activeUser.address.name" rather than the terminal key. NOTE the offset
              // convention here differs from the .wod detector: HTMLHyperlinkDetector passes an
              // offset RELATIVE TO THE RAW ATTRIBUTE VALUE (attrValue, which still carries the
              // inline binding prefix like "$"). The keypath text (wodBinding.getValue()) has the
              // prefix stripped, so shift by the prefix length (where the keypath starts within
              // the raw value) before mapping to a segment index.
              String keyPath = wodBinding.getValue();
              int prefixLength = attrValue != null ? attrValue.indexOf(keyPath) : -1;
              int segmentIndex = -1;
              if (prefixLength >= 0) {
                segmentIndex = WodHtmlUtils.segmentIndexAt(keyPath, offset - prefixLength);
              }
              hyperlinkInfo = new HTMLHyperlinkInfo();
              // Offset/length are placeholders: because the object below is an IHyperlink,
              // HTMLHyperlinkDetector returns it directly and uses ITS region (narrowed to the
              // hovered segment inside toBindingValueHyperlinkForSegment), so these are unread
              // for this path. Set to the value position for the non-IHyperlink contract.
              hyperlinkInfo.setOffset(valuePosition.getOffset());
              hyperlinkInfo.setLength(valuePosition.getLength());
              // Pass prefixLength as the keypath's start WITHIN the value position: here the value
              // position spans the raw attribute value including the inline prefix ("$"), so the
              // underline must be shifted right by the prefix width to land on the keypath.
              hyperlinkInfo.setObject(WodBindingValueHyperlink.toBindingValueHyperlinkForSegment(wodElement, attrName, cache, segmentIndex, Math.max(prefixLength, 0)));
            }
          }
        }
      }
    }
    catch (Exception e) {
      HTMLPlugin.logException(e);
    }
    return hyperlinkInfo;
  }
}
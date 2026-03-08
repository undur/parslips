package org.objectstyle.wolips.wodclipse.core.completion;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.wod.WodBindingDeprecationProblem;
import org.objectstyle.wolips.bindings.wod.WodBindingNameProblem;
import org.objectstyle.wolips.bindings.wod.WodBindingValueProblem;
import org.objectstyle.wolips.bindings.wod.WodProblem;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;
import org.objectstyle.wolips.wodclipse.core.util.WodModelUtils;

import jp.aonir.fuzzyxml.FuzzyXMLAttribute;
import jp.aonir.fuzzyxml.FuzzyXMLElement;

public class InlineWodProblem {
  private WodParserCache _cache;
  private List<WodProblem> _wodProblems;
  private FuzzyXMLElement _element;

  public InlineWodProblem(FuzzyXMLElement element, String message, boolean warning, WodParserCache cache) {
    _cache = cache;
    _element = element;
    int offset = element.getOffset() + 1;
    int length = element.getName().length();
    int lineNumber = WodHtmlUtils.getLineAtOffset(_cache.getHtmlEntry().getContents(), offset);
    WodProblem problem = new WodProblem(message, new Position(offset, length), lineNumber, warning);
    _wodProblems = new LinkedList<WodProblem>();
    _wodProblems.add(problem);
  }

  public InlineWodProblem(FuzzyXMLElement element, WodProblem wodProblem, WodParserCache cache) {
    _cache = cache;
    _element = element;
    _wodProblems = new LinkedList<WodProblem>();
    _wodProblems.add(wodProblem);
  }

  public InlineWodProblem(FuzzyXMLElement element, List<WodProblem> problems, WodParserCache cache) {
    _cache = cache;
    _element = element;
    _wodProblems = problems;
  }

  public FuzzyXMLElement getElement() {
    return _element;
  }

  public void createProblemMarkers() {
    for (WodProblem wodProblem : _wodProblems) {
      IMarker marker = WodModelUtils.createMarker(_cache.getHtmlEntry().getFile(), wodProblem);
      try {
        if (marker != null) {
          boolean elementError = true;
          if (wodProblem instanceof WodBindingNameProblem) {
            String name = ((WodBindingNameProblem) wodProblem).getBindingName();
            FuzzyXMLAttribute attribute = _element.getAttributeNode(name);
            if (attribute != null) {
              elementError = false;
              int offset = attribute.getOffset() + 1;
              marker.setAttribute(IMarker.LINE_NUMBER, WodHtmlUtils.getLineAtOffset(_cache.getHtmlEntry().getContents(), offset));
              marker.setAttribute(IMarker.CHAR_START, offset);
              marker.setAttribute(IMarker.CHAR_END, offset + attribute.getName().length());
            }
          }
          else if (wodProblem instanceof WodBindingValueProblem) {
            String name = ((WodBindingValueProblem) wodProblem).getBindingName();
            FuzzyXMLAttribute attribute = _element.getAttributeNode(name);
            if (attribute != null) {
              elementError = false;
              int offset = attribute.getOffset() + 1;
              marker.setAttribute(IMarker.LINE_NUMBER, WodHtmlUtils.getLineAtOffset(_cache.getHtmlEntry().getContents(), offset));
              marker.setAttribute(IMarker.CHAR_START, _element.getOffset() + attribute.getValueDataOffset() + 1);
              marker.setAttribute(IMarker.CHAR_END, _element.getOffset() + attribute.getValueDataOffset() + 1 + attribute.getValueDataLength());
            }
          }
          else if (wodProblem instanceof WodBindingDeprecationProblem) {
            String name = ((WodBindingDeprecationProblem) wodProblem).getBindingName();
            FuzzyXMLAttribute attribute = _element.getAttributeNode(name);
            if (attribute != null) {
              elementError = false;
              int offset = _element.getOffset() + attribute.getValueDataOffset() + 1;
              marker.setAttribute(IMarker.LINE_NUMBER, WodHtmlUtils.getLineAtOffset(_cache.getHtmlEntry().getContents(), offset));
              marker.setAttribute(IMarker.CHAR_START, offset);
              marker.setAttribute(IMarker.CHAR_END, offset + attribute.getValueDataLength());
            }
          }

          if (elementError) {
            int offset = _element.getOffset() + 1;
            marker.setAttribute(IMarker.LINE_NUMBER, WodHtmlUtils.getLineAtOffset(_cache.getHtmlEntry().getContents(), offset));
            marker.setAttribute(IMarker.CHAR_START, offset);
            marker.setAttribute(IMarker.CHAR_END, offset + _element.getName().length());
            // Store closing tag position so quick-fix can fix both tags.
            // The FuzzyXMLElement already knows about closing tags — the same
            // data is used by QuickRenameRefactoring.renameHtmlTag().
            if (_element.hasCloseTag()) {
              int closeNameStart = _element.getCloseTagOffset() + _element.getCloseNameOffset() + 1;
              marker.setAttribute("closeTagStart", closeNameStart);
              marker.setAttribute("closeTagEnd", closeNameStart + _element.getCloseNameLength());
            }
          }
        }
      }
      catch (CoreException e) {
        WodclipsePlugin.getDefault().log(e);
      }
    }
  }
}
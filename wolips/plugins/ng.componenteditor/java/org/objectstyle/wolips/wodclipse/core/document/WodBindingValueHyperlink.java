package org.objectstyle.wolips.wodclipse.core.document;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.wod.IWodBinding;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionUtils;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

/**
 * Hyperlink for binding values in WOD/template editors. Ctrl+click on a
 * binding value opens the corresponding Java source, or offers to create
 * a key/action if the keypath doesn't exist.
 */
public class WodBindingValueHyperlink implements IHyperlink {
  private IRegion _region;
  private String _bindingValue;
  private boolean _isAction;
  private IType _componentType;
  /**
   * Which keypath segment to open ({@code -1} = the last, i.e. the whole-path default). Set from
   * the click location so Cmd+clicking {@code activeUser} in {@code activeUser.address.name} opens
   * {@code activeUser}, not the terminal {@code name}. See {@link WodHtmlUtils#segmentIndexAt}.
   */
  private int _segmentIndex;

  public WodBindingValueHyperlink(IRegion region, String bindingValue, boolean isAction, IType componentType) {
    this(region, bindingValue, isAction, componentType, -1);
  }

  public WodBindingValueHyperlink(IRegion region, String bindingValue, boolean isAction, IType componentType, int segmentIndex) {
    _region = region;
    _bindingValue = bindingValue;
    _isAction = isAction;
    _componentType = componentType;
    _segmentIndex = segmentIndex;
  }

  public IRegion getHyperlinkRegion() {
    return _region;
  }

  public String getTypeLabel() {
    return null;
  }

  public String getHyperlinkText() {
    return null;
  }

  public void open() {
    try {
      WodCompletionUtils.openBinding(_bindingValue, _isAction, _componentType, false, _segmentIndex);
    }
    catch (Exception ex) {
      Activator.getDefault().log(ex);
    }
  }

  /**
   * Creates a hyperlink for the given binding's value, or returns null if
   * the binding doesn't exist or isn't a keypath. Opens the terminal segment
   * (whole-keypath default); see {@link #toBindingValueHyperlink(IWodElement, String, WodParserCache, int)}
   * to target a specific segment.
   */
  public static WodBindingValueHyperlink toBindingValueHyperlink(IWodElement wodElement, String bindingName, WodParserCache cache) throws JavaModelException, CoreException, LocateException {
    return toBindingValueHyperlink(wodElement, bindingName, cache, -1);
  }

  /**
   * Creates a hyperlink for the given binding's value, targeting the keypath segment at
   * {@code caretOffset} — an <em>absolute document offset</em> (as the {@code .wod} hyperlink
   * detector supplies). The offset is translated to a position within the keypath text and then
   * to a segment index via {@link WodHtmlUtils#segmentIndexAt}. If the offset is negative or
   * outside the value region, the terminal segment is used. Returns null if the binding doesn't
   * exist or isn't a keypath.
   */
  public static WodBindingValueHyperlink toBindingValueHyperlink(IWodElement wodElement, String bindingName, WodParserCache cache, int caretOffset) throws JavaModelException, CoreException, LocateException {
    int segmentIndex = -1;
    IWodBinding wodBinding = wodElement.getBindingNamed(bindingName);
    if (wodBinding != null && wodBinding.isKeyPath()) {
      Position valuePosition = wodBinding.getValuePosition();
      if (valuePosition != null && caretOffset >= 0) {
        int offsetInKeyPath = caretOffset - valuePosition.getOffset();
        if (offsetInKeyPath >= 0 && offsetInKeyPath <= valuePosition.getLength()) {
          segmentIndex = WodHtmlUtils.segmentIndexAt(wodBinding.getValue(), offsetInKeyPath);
        }
      }
    }
    return toBindingValueHyperlinkForSegment(wodElement, bindingName, cache, segmentIndex);
  }

  /**
   * Creates a hyperlink for the given binding's value, opening the keypath segment at the given
   * (already-computed) {@code segmentIndex}, with the keypath assumed to start at the beginning
   * of the binding's value position (i.e. no prefix offset — the {@code .wod} convention).
   */
  public static WodBindingValueHyperlink toBindingValueHyperlinkForSegment(IWodElement wodElement, String bindingName, WodParserCache cache, int segmentIndex) throws JavaModelException, CoreException, LocateException {
    return toBindingValueHyperlinkForSegment(wodElement, bindingName, cache, segmentIndex, 0);
  }

  /**
   * Creates a hyperlink for the given binding's value, opening the keypath segment at the given
   * (already-computed) {@code segmentIndex}, and underlining <em>only that segment</em> so the
   * Cmd+hover highlight matches what a click opens.
   * <p>
   * {@code keyPathStartInValue} is where the keypath text begins <em>within the binding's value
   * position</em>. The two hyperlink sources differ here: the {@code .wod} detector's value
   * position is exactly the keypath (start 0), while the inline-template provider's value position
   * spans the raw attribute value <em>including the inline binding prefix</em> (e.g. the {@code $}
   * in {@code "$current.byteValue"}), so it passes the prefix length. Without this, the underline
   * is shifted left by the prefix width. {@code segmentIndex < 0} spans the terminal segment.
   * Returns null if the binding doesn't exist or isn't a keypath.
   */
  public static WodBindingValueHyperlink toBindingValueHyperlinkForSegment(IWodElement wodElement, String bindingName, WodParserCache cache, int segmentIndex, int keyPathStartInValue) throws JavaModelException, CoreException, LocateException {
    WodBindingValueHyperlink hyperlink = null;
    IWodBinding wodBinding = wodElement.getBindingNamed(bindingName);
    if (wodBinding != null && wodBinding.isKeyPath()) {
      Position valuePosition = wodBinding.getValuePosition();
      if (valuePosition != null) {
        IType componentType = cache.getComponentType();
        if (componentType != null) {
          boolean isAction = ApiUtils.isActionBindingName(wodBinding.getName());
          int[] span = WodHtmlUtils.segmentSpan(wodBinding.getValue(), segmentIndex);
          Region segmentRegion = new Region(valuePosition.getOffset() + keyPathStartInValue + span[0], span[1]);
          hyperlink = new WodBindingValueHyperlink(segmentRegion, wodBinding.getValue(), isAction, componentType, segmentIndex);
        }
      }
    }
    return hyperlink;
  }
}

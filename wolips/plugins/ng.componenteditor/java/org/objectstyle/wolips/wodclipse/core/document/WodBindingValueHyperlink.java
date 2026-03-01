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

  public WodBindingValueHyperlink(IRegion region, String bindingValue, boolean isAction, IType componentType) {
    _region = region;
    _bindingValue = bindingValue;
    _isAction = isAction;
    _componentType = componentType;
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
      WodCompletionUtils.openBinding(_bindingValue, _isAction, _componentType, false);
    }
    catch (Exception ex) {
      Activator.getDefault().log(ex);
    }
  }

  /**
   * Creates a hyperlink for the given binding's value, or returns null if
   * the binding doesn't exist or isn't a keypath.
   */
  public static WodBindingValueHyperlink toBindingValueHyperlink(IWodElement wodElement, String bindingName, WodParserCache cache) throws JavaModelException, CoreException, LocateException {
    WodBindingValueHyperlink hyperlink = null;
    IWodBinding wodBinding = wodElement.getBindingNamed(bindingName);
    if (wodBinding != null && wodBinding.isKeyPath()) {
      Position valuePosition = wodBinding.getValuePosition();
      if (valuePosition != null) {
        Region elementRegion = new Region(valuePosition.getOffset(), valuePosition.getLength());
        IType componentType = cache.getComponentType();
        if (componentType != null) {
          boolean isAction = ApiUtils.isActionBindingName(wodBinding.getName());
          hyperlink = new WodBindingValueHyperlink(elementRegion, wodBinding.getValue(), isAction, componentType);
        }
      }
    }
    return hyperlink;
  }
}

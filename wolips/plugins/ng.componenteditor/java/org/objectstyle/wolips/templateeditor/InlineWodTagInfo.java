package org.objectstyle.wolips.templateeditor;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.IApiBinding;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionProposal;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionUtils;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;
import tk.eclipse.plugin.htmleditor.assist.AttributeInfo;
import tk.eclipse.plugin.htmleditor.assist.TagInfo;

public class InlineWodTagInfo extends TagInfo {
  private String _elementTypeName;
  private TagShortcut _tagShortcut;
  private IJavaProject _javaProject;
  private boolean _attributeInfoCached;
  private TypeCache _cache;

  public InlineWodTagInfo(String elementTypeName, TypeCache cache) {
    // hasBody=true (default until API is loaded), emptyTag=false so that
    // non-content elements get <wo:foo /> instead of <wo:foo> (the emptyTag
    // flag is for HTML void elements like <br> and <hr>, not wo: tags).
    super("wo:" + elementTypeName, true, false);
    setRequiresAttributes(true);
    _cache = cache;
    _elementTypeName = elementTypeName;
    _tagShortcut = ApiCache.getTagShortcutNamed(elementTypeName);
  }

  public void setJavaProject(IJavaProject javaProject) {
    _javaProject = javaProject;
  }

  public IJavaProject getJavaProject() {
    return _javaProject;
  }
  
  public IType getElementType() {
    IType elementType = null;
    try {
      elementType = BindingReflectionUtils.findElementType(_javaProject, getExpandedElementTypeName(), false, _cache);
    } catch (JavaModelException e) {
      // ignore;
    }
    return elementType;
  }

  public String getElementTypeName() {
    return _elementTypeName;
  }

  public String getExpandedElementTypeName() {
    String elementTypeName = _elementTypeName;
    if (_tagShortcut != null) {
      elementTypeName = _tagShortcut.getActual();
    }
    return elementTypeName;
  }

  /**
   * Overridden to ensure the API is loaded before checking whether the tag
   * has a body. The base class constructor sets {@code hasBody = true}, but
   * {@link #loadAttributeInfo()} may change it based on the element's
   * {@code wocomponentcontent} attribute. Since {@code HTMLAssistProcessor}
   * calls {@code hasBody()} before {@code getAttributeInfo()}, we must
   * trigger the load here.
   */
  @Override
  public boolean hasBody() {
    loadAttributeInfo();
    return super.hasBody();
  }

  protected void loadAttributeInfo() {
    if (!_attributeInfoCached) {
      IType elementType;
      try {
        elementType = BindingReflectionUtils.findElementType(_javaProject, getExpandedElementTypeName(), false, _cache);
        if (elementType != null) {
          Set<WodCompletionProposal> proposals = new HashSet<WodCompletionProposal>();
          WodCompletionUtils.fillInBindingNameCompletionProposals(_javaProject, elementType, "", 0, 0, proposals, false, _cache);
          for (WodCompletionProposal proposal : proposals) {
            AttributeInfo attrInfo = new AttributeInfo(proposal.getProposal(), true);
            addAttributeInfo(attrInfo);
          }

          // Check the element's API for componentContent to determine
          // whether autocomplete should insert a self-closing tag or
          // an opening+closing tag pair.
          updateHasBodyFromApi(elementType);
        }
        else {
          // Element type not found in classpath; fall back to global WebObjectDefinitions.xml
          String expandedName = getExpandedElementTypeName();
          ApiSnapshot api = ApiUtils.findGlobalApiSnapshotByClassName(expandedName);
          if (api != null) {
            java.util.List<IApiBinding> bindings = api.getBindings();
            for (IApiBinding binding : bindings) {
              AttributeInfo attrInfo = new AttributeInfo(binding.getName(), true);
              addAttributeInfo(attrInfo);
            }

            // Use the global API's componentContent flag
            setHasBody(api.isComponentContent());
          }
        }
        _attributeInfoCached = true;
      }
      catch (JavaModelException e) {
        HTMLPlugin.logException(e);
      }
    }
  }

  /**
   * Looks up the element's {@code .api} file (project-local or global) and
   * sets {@link #setHasBody(boolean)} based on the {@code wocomponentcontent}
   * attribute. Elements that don't accept child content (e.g. WOString,
   * WOImage) get self-closing tags; content components (e.g. WOForm,
   * WOConditional) get opening+closing tag pairs.
   */
  private void updateHasBodyFromApi(IType elementType) {
    try {
      ApiSnapshot api = ApiUtils.findApiSnapshot(elementType, _cache.getApiCache(_javaProject));
      if (api != null) {
        setHasBody(api.isComponentContent());
      }
    } catch (ApiModelException e) {
      // Non-fatal — fall back to default (hasBody=true)
    }
  }

  @Override
  public AttributeInfo[] getAttributeInfo() {
    loadAttributeInfo();
    return super.getAttributeInfo();
  }

  @Override
  public AttributeInfo getAttributeInfo(String name) {
    loadAttributeInfo();
    return super.getAttributeInfo(name);
  }
}

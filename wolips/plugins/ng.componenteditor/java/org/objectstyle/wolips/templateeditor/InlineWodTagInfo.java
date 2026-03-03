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
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionProposal;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionUtils;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;
import tk.eclipse.plugin.htmleditor.assist.AttributeInfo;
import tk.eclipse.plugin.htmleditor.assist.TagInfo;

public class InlineWodTagInfo extends TagInfo {
  private String _elementTypeName;
  private TagShortcut _tagShortcut;
  private IJavaProject _javaProject;
  private BuildProperties _buildProperties;
  private boolean _attributeInfoCached;
  private IType _resolvedElementType;
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

  public void setBuildProperties(BuildProperties buildProperties) {
    _buildProperties = buildProperties;
  }
  
  /**
   * Returns the resolved element type. If {@link #loadAttributeInfo()} has
   * already been triggered (by {@code hasBody()}, {@code getAttributeInfo()},
   * etc.), the cached result is returned immediately — no JDT lookup.
   */
  public IType getElementType() {
    if (_attributeInfoCached) {
      return _resolvedElementType;
    }
    // Attribute info hasn't been loaded yet; resolve the type directly.
    // This path is rare — HTMLAssistProcessor normally calls hasBody() or
    // getRequiredAttributeInfo() before getElementType().
    try {
      return BindingReflectionUtils.findElementType(_javaProject, getExpandedElementTypeName(), false, _cache);
    } catch (JavaModelException e) {
      return null;
    }
  }

  public String getElementTypeName() {
    return _elementTypeName;
  }

  public String getExpandedElementTypeName() {
    String elementTypeName = _elementTypeName;
    if (_tagShortcut != null) {
      elementTypeName = _tagShortcut.getActual(_buildProperties);
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
      try {
        IType elementType = BindingReflectionUtils.findElementType(_javaProject, getExpandedElementTypeName(), false, _cache);
        _resolvedElementType = elementType;
        // Try to get binding info from the global API definitions
        // (WebObjectDefinitions.xml). This covers both framework elements
        // whose bindings aren't discoverable via reflection (e.g. NG
        // elements that use private association fields) and elements
        // whose type isn't on the classpath at all.
        String expandedName = getExpandedElementTypeName();
        ApiSnapshot api = ApiUtils.findGlobalApiSnapshotByClassName(expandedName);

        if (elementType != null) {
          Set<WodCompletionProposal> proposals = new HashSet<WodCompletionProposal>();
          WodCompletionUtils.fillInBindingNameCompletionProposals(_javaProject, elementType, "", 0, 0, proposals, false, _cache);
          for (WodCompletionProposal proposal : proposals) {
            AttributeInfo attrInfo = new AttributeInfo(proposal.getProposal(), true);
            addAttributeInfo(attrInfo);
          }

          // Supplement with global API bindings if available. This adds
          // bindings that aren't discoverable via Java reflection (e.g.
          // NG dynamic elements that declare bindings via association maps).
          if (api != null) {
            Set<String> existingNames = new HashSet<String>();
            for (AttributeInfo existing : super.getAttributeInfo()) {
              existingNames.add(existing.getAttributeName());
            }
            for (IApiBinding binding : api.getBindings()) {
              if (!existingNames.contains(binding.getName())) {
                addAttributeInfo(new AttributeInfo(binding.getName(), true, AttributeInfo.NONE, binding.isRequired()));
              }
            }
          }

          applyApiMetadata(elementType);
        }
        else if (api != null) {
          // Element type not found in classpath; use global API only
          for (IApiBinding binding : api.getBindings()) {
            addAttributeInfo(new AttributeInfo(binding.getName(), true, AttributeInfo.NONE, binding.isRequired()));
          }
          setHasBody(api.isComponentContent());
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
   * applies metadata from it:
   * <ul>
   *   <li>{@code wocomponentcontent} &rarr; {@link #setHasBody(boolean)} (controls
   *       self-closing vs opening+closing tag in autocomplete)</li>
   *   <li>{@code required} bindings &rarr; marks corresponding {@link AttributeInfo}
   *       entries so they are pre-inserted in the completion with cursor
   *       positioned inside the quotes</li>
   * </ul>
   */
  private void applyApiMetadata(IType elementType) {
    try {
      ApiSnapshot api = ApiUtils.findApiSnapshot(elementType, _cache.getApiCache(_javaProject));
      if (api != null) {
        setHasBody(api.isComponentContent());

        // Build a set of required binding names from the API
        Set<String> requiredBindings = new HashSet<String>();
        for (IApiBinding binding : api.getBindings()) {
          if (binding.isRequired()) {
            requiredBindings.add(binding.getName());
          }
        }

        // Mark matching AttributeInfo entries as required
        if (!requiredBindings.isEmpty()) {
          for (AttributeInfo attr : super.getAttributeInfo()) {
            if (requiredBindings.contains(attr.getAttributeName())) {
              attr.setRequired(true);
            }
          }
        }
      }
    } catch (ApiModelException e) {
      // Non-fatal — fall back to defaults
    }
  }

  @Override
  public AttributeInfo[] getAttributeInfo() {
    loadAttributeInfo();
    return super.getAttributeInfo();
  }

  @Override
  public AttributeInfo[] getRequiredAttributeInfo() {
    loadAttributeInfo();
    return super.getRequiredAttributeInfo();
  }

  @Override
  public AttributeInfo getAttributeInfo(String name) {
    loadAttributeInfo();
    return super.getAttributeInfo(name);
  }
}

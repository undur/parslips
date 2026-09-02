package org.objectstyle.wolips.editor.template;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiextModel;
import org.objectstyle.wolips.bindings.api.ElementApiResolver;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionProposal;
import org.objectstyle.wolips.wodclipse.core.completion.WodCompletionUtils;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;
import tk.eclipse.plugin.htmleditor.assist.AttributeInfo;
import tk.eclipse.plugin.htmleditor.assist.TagInfo;

public class InlineWodTagInfo extends TagInfo {
  private String _elementTypeName;
  private TagShortcut _tagShortcut;
  private IJavaProject _javaProject;
  private ParsleyProject _parsleyProject;
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

  public void setParsleyProject(ParsleyProject parsleyProject) {
    _parsleyProject = parsleyProject;
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
    // When the project declares Parsley tag aliases, resolve through them (matching the
    // runtime); otherwise expand via the legacy tag-shortcut.
    if (_javaProject != null && org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.isActiveFor(_javaProject)) {
      return org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.resolve(_javaProject, _elementTypeName);
    }
    String elementTypeName = _elementTypeName;
    if (_tagShortcut != null) {
      elementTypeName = _tagShortcut.getActual(_parsleyProject);
    }
    return elementTypeName;
  }

  /**
   * The element name whose bindings should drive completion. When aliases are active this is
   * the binding-source element — the documented ancestor (WOString) rather than the bare
   * replacement (ERXWOString) — both more correct and far cheaper. See
   * {@link org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver#resolveForBindings}.
   */
  private String bindingSourceElementName() {
    if (_javaProject != null && org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.isActiveFor(_javaProject)) {
      return org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.resolveForBindings(_javaProject, _elementTypeName);
    }
    return getExpandedElementTypeName();
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
        // The element whose bindings we offer. When the project uses Parsley aliases and the
        // resolved element (e.g. ERXWOString) has no API of its own, walk back up the alias
        // chain to an element that does (WOString) — the replacement shares its bindings — so
        // binding completion still works. (Mirrors the hover's doc-fallback.)
        String expandedName = bindingSourceElementName();
        IType elementType = BindingReflectionUtils.findElementType(_javaProject, expandedName, false, _cache);
        _resolvedElementType = elementType;

        // The element's API, resolved through the same seam validation uses: its own .apiext
        // (e.g. the one ng-appserver ships for each ng element), the bundled/global .apiext,
        // then legacy .api / WebObjectDefinitions.xml. Works with or without a type on the
        // classpath, and covers bindings reflection can't see (ng elements read theirs from an
        // association map — nothing to reflect over).
        final ApiextModel api = ElementApiResolver.resolve(elementType, _javaProject, expandedName, expandedName).getModel();

        if (elementType != null) {
          Set<WodCompletionProposal> proposals = new HashSet<WodCompletionProposal>();
          WodCompletionUtils.fillInBindingNameCompletionProposals(_javaProject, elementType, "", 0, 0, proposals, false, _cache);
          for (WodCompletionProposal proposal : proposals) {
            AttributeInfo attrInfo = new AttributeInfo(proposal.getProposal(), true);
            addAttributeInfo(attrInfo);
          }
        }

        if (api != null) {
          // Supplement with (or, without a type, consist of) the declared bindings, and carry
          // the declaration's metadata: required bindings are pre-inserted by the completion,
          // and the content policy decides between a self-closing and an opening+closing tag.
          Set<String> existingNames = new HashSet<String>();
          for (AttributeInfo existing : super.getAttributeInfo()) {
            existingNames.add(existing.getAttributeName());
          }
          for (ApiextModel.Binding binding : api.getBindings()) {
            if (!existingNames.contains(binding.getName())) {
              addAttributeInfo(new AttributeInfo(binding.getName(), true, AttributeInfo.NONE, binding.isRequired()));
            }
            else if (binding.isRequired()) {
              for (AttributeInfo attr : super.getAttributeInfo()) {
                if (attr.getAttributeName().equals(binding.getName())) {
                  attr.setRequired(true);
                }
              }
            }
          }
          setHasBody(api.getContent() != ApiextModel.Content.FORBIDDEN);
        }
        _attributeInfoCached = true;
      }
      catch (JavaModelException e) {
        HTMLPlugin.logException(e);
      }
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

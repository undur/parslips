/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2005 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 1.
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. The end-user documentation
 * included with the redistribution, if any, must include the following
 * acknowlegement: "This product includes software developed by the ObjectStyle
 * Group (http://objectstyle.org/)." Alternately, this acknowlegement may
 * appear in the software itself, if and wherever such third-party
 * acknowlegements normally appear. 4. The names "ObjectStyle Group" and
 * "Cayenne" must not be used to endorse or promote products derived from this
 * software without prior written permission. For written permission, please
 * contact andrus@objectstyle.org. 5. Products derived from this software may
 * not be called "ObjectStyle" nor may "ObjectStyle" appear in their names
 * without prior written permission of the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/> .
 *  
 */
package org.objectstyle.wolips.bindings.wod;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.preferences.BindingValidationPreferences;
import org.objectstyle.wolips.bindings.preferences.SeverityPolicy;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.ElementApiResolver;
import org.objectstyle.wolips.bindings.api.ApiValidation;
import org.objectstyle.wolips.bindings.api.IApiBinding;
import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.utils.StringDistance;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;

/**
 * @author mschrag
 */
public abstract class AbstractWodElement implements IWodElement, Comparable<IWodElement> {
  private List<IWodBinding> _bindings;

  private boolean _inline;

  private String _tagName;
  
  private boolean _inherited;

  public AbstractWodElement() {
    _bindings = new LinkedList<IWodBinding>();
  }
  
  public void setInherited(boolean inherited) {
		_inherited = inherited;
	}
  
  public boolean isInherited() {
		return _inherited;
	}

  public boolean isInline() {
    return _inline;
  }

  public void setInline(boolean inline) {
    _inline = inline;
  }

  /**
   * Whether this element has child content in the template — an explicit close tag (even an empty
   * {@code <wo:x></wo:x>}), as opposed to a self-closed {@code <wo:x/>}. Used to enforce an
   * {@code .apiext} {@code content="forbidden"} policy. Defaults to false (unknown → don't flag);
   * overridden where the underlying template node is available (inline elements).
   */
  public boolean hasContent() {
    return false;
  }

  public void addBinding(IWodBinding binding) {
    _bindings.add(binding);
  }
  
  public void removeBinding(IWodBinding binding) {
    _bindings.remove(binding);
  }

  public List<IWodBinding> getBindings() {
    return _bindings;
  }

  public IWodBinding getBindingNamed(String name) {
    IWodBinding matchingBinding = null;
    Iterator<IWodBinding> wodBindingsIter = _bindings.iterator();
    while (matchingBinding == null && wodBindingsIter.hasNext()) {
      IWodBinding wodBinding = wodBindingsIter.next();
      if (name.equals(wodBinding.getName())) {
        matchingBinding = wodBinding;
      }
    }
    return matchingBinding;
  }

  public String getBindingValue(String name) {
    String value = null;
    IWodBinding binding = getBindingNamed(name);
    if (binding != null) {
      value = binding.getValue();
    }
    return value;
  }

  public Map<String, String> getBindingsMap() {
    Map<String, String> bindingsMap = new HashMap<String, String>();
    Iterator<IWodBinding> bindingsIter = _bindings.iterator();
    while (bindingsIter.hasNext()) {
      IWodBinding binding = bindingsIter.next();
      bindingsMap.put(binding.getName(), binding.getValue());
    }
    return bindingsMap;
  }

  public int compareTo(IWodElement otherElement) {
    String otherName = otherElement.getElementName();
    int comparison = getElementName().compareTo(otherName);
    return comparison;
  }

  public void writeWodFormat(Writer writer, boolean alphabetize) throws IOException {
    List<IWodBinding> bindings = getBindings();
    if (alphabetize) {
      bindings = new LinkedList<IWodBinding>(bindings);
      Collections.sort(bindings, new WodBindingComparator());
    }
    writer.write(getElementName());
    writer.write(" : ");
    writer.write(getElementType());
    writer.write(" {");
    writer.write("\n");
    for (IWodBinding binding : bindings) {
      binding.writeWodFormat(writer);
      writer.write("\n");
    }
    writer.write("}\n");
  }

  public void writeInlineFormat(Writer writer, String content, boolean alphabetize, String bindingPrefix, String bindingSuffix) throws IOException {
    writeInlineFormat(writer, content, alphabetize, true, true, true, bindingPrefix, bindingSuffix, true);
  }

  public void writeInlineFormat(Writer writer, String content, boolean alphabetize, boolean showOpenTag, boolean showContent, boolean showCloseTag, String bindingPrefix, String bindingSuffix) throws IOException {
    writeInlineFormat(writer, content, alphabetize, showOpenTag, showContent, showCloseTag, bindingPrefix, bindingSuffix, true);
  }

  public void writeInlineFormat(Writer writer, String content, boolean alphabetize, boolean showOpenTag, boolean showContent, boolean showCloseTag, String bindingPrefix, String bindingSuffix, boolean spacesAroundEquals) throws IOException {
    List<IWodBinding> bindings = getBindings();
    if (alphabetize) {
      bindings = new LinkedList<IWodBinding>(bindings);
      Collections.sort(bindings, new WodBindingComparator());
    }
    if (showOpenTag) {
      writer.write("<");
      writer.write(getTagName());
      for (IWodBinding binding : bindings) {
        binding.writeInlineFormat(writer, bindingPrefix, bindingSuffix, spacesAroundEquals);
      }
      if (content == null) {
        writer.write("/>");
      }
      else {
        writer.write(">");
      }
    }
    if (content != null) {
      if (showContent) {
        writer.write(content);
      }
      if (showCloseTag) {
        writer.write("</");
        writer.write(getTagName());
        writer.write(">");
      }
    }
  }

  public void setTagName(String tagName) {
    _tagName = tagName;
  }

  public String getTagName() {
    String tagName;
    if (_tagName == null) {
      tagName = "wo:" + getElementType();
    }
    else {
      tagName = _tagName;
    }
    return tagName;
  }

  public ApiSnapshot getApi(IJavaProject javaProject, TypeCache cache) throws JavaModelException, ApiModelException {
    String elementTypeName = getElementType();
    IType elementType = BindingReflectionUtils.findElementType(javaProject, elementTypeName, false, cache);
    return ApiUtils.findApiSnapshot(elementType, cache.getApiCache(javaProject));
  }

  /**
   * Returns all bindings visible for this element: API-defined bindings first,
   * then any WOD bindings not defined in the API. Each binding is wrapped in
   * a {@link VisibleBinding} so consumers can distinguish API-defined bindings
   * from WOD-only bindings without relying on {@code instanceof} checks.
   *
   * @param api the API snapshot for this element type, or null if not available
   * @return array of visible bindings, never null
   */
  public VisibleBinding[] getVisibleBindings(ApiSnapshot api) {
    try {
      if (api != null) {
        List<VisibleBinding> result = new LinkedList<VisibleBinding>();
        List<IApiBinding> apiBindings = api.getBindings();

        // Add all API-defined bindings
        for (IApiBinding apiBinding : apiBindings) {
          result.add(VisibleBinding.fromApi(apiBinding));
        }

        // Add WOD bindings that are NOT defined in the API
        Set<String> apiNames = new HashSet<String>();
        for (IApiBinding apiBinding : apiBindings) {
          apiNames.add(apiBinding.getName());
        }
        for (IWodBinding wodBinding : getBindings()) {
          if (!apiNames.contains(wodBinding.getName())) {
            result.add(VisibleBinding.fromWod(wodBinding));
          }
        }

        return result.toArray(new VisibleBinding[result.size()]);
      }
    }
    catch (Throwable t) {
      Activator.getDefault().log("Failed to retrieve bindings for " + this + ".", t);
    }

    // No API available — wrap all WOD bindings as WOD-only
    List<IWodBinding> currentBindings = getBindings();
    VisibleBinding[] result = new VisibleBinding[currentBindings.size()];
    for (int i = 0; i < currentBindings.size(); i++) {
      result[i] = VisibleBinding.fromWod(currentBindings.get(i));
    }
    return result;
  }

  public abstract int getLineNumber();

  public void fillInProblems(IJavaProject javaProject, IType javaFileType, boolean checkBindingValues, List<WodProblem> problems, TypeCache typeCache, HtmlElementCache htmlCache) throws CoreException {
    String elementTypeName = getElementType();

    String elementName = getElementName();
    int lineNumber = getLineNumber();

  	String wodMissingComponentSeverity = BindingValidationPreferences.severity(PreferenceConstants.WOD_MISSING_COMPONENT_SEVERITY_KEY);

    // Check for tag shortcut case mismatch (e.g. user wrote "Repetition"
    // but the shortcut is defined as "repetition"). Produce the same error
    // message format as a missing element type — the user shouldn't need to
    // know whether what they typed was a shortcut or a class name.
    if (!SeverityPolicy.isIgnored(wodMissingComponentSeverity) && this instanceof SimpleWodElement) {
      SimpleWodElement simpleElement = (SimpleWodElement) this;
      if (simpleElement.getTagShortcutCaseMismatch() != null) {
        String originalText = simpleElement.getTagShortcutCaseMismatch();
        String correctCase = simpleElement.getTagShortcutCorrectCase();
        List<String> suggestions = Collections.singletonList(correctCase);
        String message = "The class for '" + originalText + "' is either missing or does not extend a known element root type (NGElement/WOElement). Did you mean '" + correctCase + "'?";
        WodElementProblem problem = new WodElementProblem(this, message, getElementTypePosition(), lineNumber, SeverityPolicy.isWarning(wodMissingComponentSeverity));
        problem.setSuggestions(suggestions);
        problems.add(problem);
      }
    }
  	String unusedWodElementSeverity = BindingValidationPreferences.severity(PreferenceConstants.UNUSED_WOD_ELEMENT_SEVERITY_KEY);
    if (!SeverityPolicy.isIgnored(unusedWodElementSeverity) && !_inline && !htmlCache.containsElementNamed(elementName)) {
      problems.add(new WodElementProblem(this, "There is no element named '" + elementName + "' in your component HTML file", getElementNamePosition(), lineNumber, SeverityPolicy.isWarning(unusedWodElementSeverity)));
    }
    
    String deprecationSeverity = BindingValidationPreferences.severity(PreferenceConstants.DEPRECATED_BINDING_SEVERITY_KEY);
    if (!SeverityPolicy.isIgnored(deprecationSeverity)) {
      IType elementType = BindingReflectionUtils.findElementType(javaProject, elementTypeName, false, typeCache);
      if (BindingReflectionUtils.memberIsDeprecated(elementType)) {
        problems.add(new WodElementDeprecationProblem(this, "The component named '" + elementTypeName + "' is deprecated.", getElementTypePosition(), lineNumber, SeverityPolicy.isWarning(deprecationSeverity)));
      }
    }

    ApiSnapshot wo = null;
    if (!SeverityPolicy.isIgnored(wodMissingComponentSeverity)) {
    	IType elementType = BindingReflectionUtils.findElementType(javaProject, elementTypeName, false, typeCache);
	    if (elementType == null || (!elementType.getElementName().equals(elementTypeName) && !elementType.getFullyQualifiedName().equals(elementTypeName))) {
	      // Compute "did you mean?" suggestions for the mistyped element type name.
	      List<String> suggestions = suggestElementTypeNames(javaProject, elementTypeName, typeCache);
	      String message = "The class for '" + elementTypeName + "' is either missing or does not extend a known element root type (NGElement/WOElement).";
	      if (!suggestions.isEmpty()) {
	        if (suggestions.size() == 1) {
	          message += " Did you mean '" + suggestions.get(0) + "'?";
	        }
	        else {
	          StringBuilder sb = new StringBuilder();
	          sb.append(" Did you mean ");
	          for (int i = 0; i < suggestions.size(); i++) {
	            if (i > 0) sb.append(", ");
	            sb.append("'").append(suggestions.get(i)).append("'");
	          }
	          sb.append("?");
	          message += sb.toString();
	        }
	      }
	      WodElementProblem problem = new WodElementProblem(this, message, getElementTypePosition(), lineNumber, SeverityPolicy.isWarning(wodMissingComponentSeverity));
	      problem.setSuggestions(suggestions);
	      problems.add(problem);
	    }
	    else {
	    	String wodApiProblemSeverity = BindingValidationPreferences.severity(PreferenceConstants.WOD_API_PROBLEMS_SEVERITY_KEY);
	    	if (!SeverityPolicy.isIgnored(wodApiProblemSeverity)) {
		      try {
		        // .apiext-wins per element: if an .apiext owns this element, evaluate its typed
		        // constraints and ignore the legacy .api entirely (the "successor, not superset"
		        // principle at the validation layer). Otherwise fall through to the legacy .api path.
		        final ElementApiResolver.ResolvedElementApi resolved =
		            ElementApiResolver.resolve(elementType, elementType.getElementName(), elementTypeName,
		                elementType.getElementName(), typeCache.getApiCache(javaProject));
		        if (resolved.isApiext()) {
		          fillInApiextProblems(resolved.getModel(), lineNumber, SeverityPolicy.isWarning(wodApiProblemSeverity), problems);
		        }
		        else {
		          wo = ApiUtils.findApiSnapshot(elementType, typeCache.getApiCache(javaProject));
		          if (wo != null) {
		            Map<String, String> bindingsMap = getBindingsMap();
		            List<IApiBinding> apiBindings = wo.getBindings();
		            for (IApiBinding binding : apiBindings) {
		              String bindingName = binding.getName();
		              if (binding.isExplicitlyRequired() && !bindingsMap.containsKey(bindingName)) {
		                problems.add(new ApiBindingValidationProblem(this, binding, wo.getClassName(), getElementNamePosition(), lineNumber, SeverityPolicy.isWarning(wodApiProblemSeverity)));
		              }
		            }
		            List<ApiValidation> failedValidations = wo.getFailedValidations(bindingsMap);
		            for (ApiValidation failedValidation : failedValidations) {
		              problems.add(new ApiElementValidationProblem(this, failedValidation, getElementNamePosition(), lineNumber, SeverityPolicy.isWarning(wodApiProblemSeverity)));
		            }
		          }
		        }
		      }
		      catch (Throwable e) {
		        Activator.getDefault().log(e);
		      }
	    	}
	    }
    }

    Set<String> bindingNames = new HashSet<String>();
    Iterator<IWodBinding> checkForDuplicateBindingsIter = getBindings().iterator();
    while (checkForDuplicateBindingsIter.hasNext()) {
      IWodBinding binding = checkForDuplicateBindingsIter.next();
      String bindingName = binding.getName();
      if (bindingNames.contains(bindingName)) {
        problems.add(new WodBindingNameProblem(this, bindingName, "Duplicate binding named '" + bindingName + "'", binding.getNamePosition(), binding.getLineNumber(), false));
      }
      else {
        bindingNames.add(bindingName);
      }
    }

    JavaModelException javaModelException = null;

    if (checkBindingValues && javaFileType != null) {
      Iterator<IWodBinding> bindingsIter = getBindings().iterator();
      while (bindingsIter.hasNext()) {
        IWodBinding binding = bindingsIter.next();
        try {
          IApiBinding apiBinding = null;
          if (wo != null) {
            apiBinding = wo.getBinding(binding.getName());
          }
          binding.fillInBindingProblems(this, apiBinding, javaProject, javaFileType, problems, typeCache, htmlCache);
        }
        catch (JavaModelException t) {
          javaModelException = t;
          Activator.getDefault().log("Failed to check wod binding values.", t);
        }
        catch (Throwable t) {
          Activator.getDefault().log("Failed to check wod binding values.", t);
        }
      }
    }

    if (javaModelException != null) {
      throw javaModelException;
    }
  }

  /**
   * Evaluates an {@code .apiext}-owned element's typed contract against this tag's bindings and adds
   * the resulting problems. Binding-targeted diagnostics (required, deprecation, forbidden unknowns)
   * are positioned on the offending binding when it's present on the tag, else on the element name;
   * cross-binding constraint failures sit on the element name. The {@code apiSeverityIsWarning} flag
   * carries the user's "WOD API problems" severity preference for contract errors; deprecation is
   * always a warning per the format spec.
   */
  private void fillInApiextProblems(org.objectstyle.wolips.bindings.api.ApiextModel model, int lineNumber,
      boolean apiSeverityIsWarning, List<WodProblem> problems) {
    // content="forbidden": the element must be self-closed — giving it any content (even an empty
    // <wo:x></wo:x>) is an error (#22). Element-level, so it needs this element's content-presence,
    // which the name-based ApiextTemplateEvaluator doesn't see.
    if (model.getContent() == org.objectstyle.wolips.bindings.api.ApiextModel.Content.FORBIDDEN && hasContent()) {
      problems.add(new WodElementProblem(this, "'" + getElementType() + "' does not allow content — write it self-closed.",
          getElementNamePosition(), lineNumber, apiSeverityIsWarning));
    }

    final Map<String, String> bindingsMap = getBindingsMap();
    final java.util.List<org.objectstyle.wolips.bindings.api.ApiextTemplateEvaluator.Diagnostic> diagnostics =
        org.objectstyle.wolips.bindings.api.ApiextTemplateEvaluator.evaluate(model, bindingsMap.keySet());
    for (final org.objectstyle.wolips.bindings.api.ApiextTemplateEvaluator.Diagnostic d : diagnostics) {
      final boolean warning =
          d.getKind() == org.objectstyle.wolips.bindings.api.ApiextTemplateEvaluator.Diagnostic.Kind.WARNING
          || apiSeverityIsWarning;
      final String bindingName = d.getBindingName();
      if (bindingName != null) {
        // Point at the binding's own position if it's present on the tag; otherwise the element name
        // (e.g. a required binding that's missing has no position of its own).
        final IWodBinding binding = getBindingNamed(bindingName);
        final Position position = binding != null ? binding.getNamePosition() : getElementNamePosition();
        problems.add(new WodBindingNameProblem(this, bindingName, d.getMessage(), position, lineNumber, warning));
      }
      else {
        problems.add(new WodElementProblem(this, d.getMessage(), getElementNamePosition(), lineNumber, warning));
      }
    }
  }

  /**
   * Finds element type names similar to the given (mistyped) name.
   * First checks for case-only mismatches (e.g. "Str" → "str"), which are
   * the most common error, then uses Damerau–Levenshtein distance for
   * typo-based suggestions (e.g. "WOStirng" → "WOString").
   *
   * @param javaProject the project to search for element types
   * @param invalidName the mistyped element type name
   * @return suggestions sorted by relevance (case-only matches first, then by edit distance)
   */
  private List<String> suggestElementTypeNames(IJavaProject javaProject, String invalidName, TypeCache typeCache) {
    try {
      TypeNameCollector collector = new TypeNameCollector(javaProject, false);
      BindingReflectionUtils.findMatchingElementClassNames("", SearchPattern.R_PREFIX_MATCH, collector, new NullProgressMonitor());

      // Extract simple class names from the fully-qualified names
      Set<String> qualifiedNames = collector.getTypeNames();
      List<String> simpleNames = new ArrayList<String>(qualifiedNames.size());
      for (String qualifiedName : qualifiedNames) {
        int lastDot = qualifiedName.lastIndexOf('.');
        simpleNames.add(lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName);
      }

      // Also include tag shortcut names as candidates. A user typing
      // "repetiti" is more likely trying to type the shortcut "repetition"
      // (distance 2) than the class name "WORepetition" (distance 4). Use the
      // project's Parsley aliases when present, otherwise the legacy shortcuts.
      if (org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.isActiveFor(javaProject)) {
        for (String alias : org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.aliasMap(javaProject).keySet()) {
          if (!simpleNames.contains(alias)) {
            simpleNames.add(alias);
          }
        }
      }
      else {
        // Legacy shortcuts — only the ones that apply to this project (an ng project is never
        // told it "meant" VBScript).
        final org.objectstyle.wolips.variables.ParsleyProject parsleyProject = javaProject == null ? null
            : (org.objectstyle.wolips.variables.ParsleyProject) javaProject.getProject().getAdapter(org.objectstyle.wolips.variables.ParsleyProject.class);
        for (TagShortcut tagShortcut : TagShortcut.applicableTo(javaProject, parsleyProject, typeCache)) {
          String shortcutName = tagShortcut.getShortcut();
          if (!simpleNames.contains(shortcutName)) {
            simpleNames.add(shortcutName);
          }
        }
      }

      List<String> suggestions = new ArrayList<String>();

      // Case-only mismatches: StringDistance is case-insensitive and returns
      // distance 0 for these, so closestMatches() would filter them out.
      // Check explicitly since capitalization errors are the most common mistake.
      for (String candidate : simpleNames) {
        if (candidate.equalsIgnoreCase(invalidName) && !candidate.equals(invalidName)) {
          suggestions.add(candidate);
        }
      }

      // Typo-based suggestions via edit distance
      List<String> typoSuggestions = StringDistance.closestMatches(invalidName, simpleNames, 3);
      for (String suggestion : typoSuggestions) {
        if (!suggestions.contains(suggestion)) {
          suggestions.add(suggestion);
        }
      }

      // Cap at 3 total suggestions
      if (suggestions.size() > 3) {
        suggestions = suggestions.subList(0, 3);
      }

      return suggestions;
    }
    catch (Exception e) {
      Activator.getDefault().log("Failed to compute element type suggestions for '" + invalidName + "'.", e);
      return Collections.emptyList();
    }
  }

  public boolean isWithin(IRegion region) {
    return getStartOffset() <= region.getOffset() && getEndOffset() > region.getOffset();
  }

  public boolean isTypeWithin(IRegion region) {
    Position typePosition = getElementTypePosition();
    return typePosition != null && typePosition.getOffset() <= region.getOffset() && typePosition.getOffset() + typePosition.getLength() > region.getOffset();
  }

  @Override
  public String toString() {
    return "[" + getClass().getName() + ": elementName = " + getElementName() + ";  elementType = " + getElementType() + "; bindings = " + _bindings + "]";
  }
}

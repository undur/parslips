package org.objectstyle.wolips.wodclipse.core.completion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.IApiBinding;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.bindings.wod.HtmlElementCache;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionDialog;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionInfo;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyDialog;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyInfo;

public class WodCompletionUtils {
  // Cache of all element type names per project.  Populated once via a full
  // JDT search, then filtered by prefix on each autocomplete invocation.
  // Cleared when Java files change (see clearElementTypeCacheForProject).
  private static Map<IProject, TypeNameCollector> _elementTypeCache = new HashMap<IProject, TypeNameCollector>();

  /**
   * Clears the cached element type list for the given project.
   * Should be called when Java files are added, removed, or changed.
   */
  public static synchronized void clearElementTypeCacheForProject(IProject project) {
    _elementTypeCache.remove(project);
  }

  /**
   * Returns a TypeNameCollector containing ALL element types for the project.
   * The result is cached; subsequent calls return the cached collector.
   */
  private static synchronized TypeNameCollector getElementTypeCollector(IJavaProject project, IProgressMonitor progressMonitor) throws JavaModelException {
    IProject iProject = project.getProject();
    TypeNameCollector cached = _elementTypeCache.get(iProject);
    if (cached != null) {
      return cached;
    }
    // Build full element type list — empty prefix with R_PREFIX_MATCH matches all types
    TypeNameCollector collector = new TypeNameCollector(project, false);
    BindingReflectionUtils.findMatchingElementClassNames("", SearchPattern.R_PREFIX_MATCH, collector, progressMonitor);
    _elementTypeCache.put(iProject, collector);
    return collector;
  }

  /**
   * Returns the simple (unqualified) class names of all element types
   * available in the given project.  Used by the element type validation
   * to generate "did you mean?" suggestions for mistyped tag names.
   *
   * @param project the Java project to search
   * @param progressMonitor progress monitor (may be null)
   * @return list of simple names like "WOString", "WOConditional", "str", etc.
   */
  public static List<String> getSimpleElementTypeNames(IJavaProject project, IProgressMonitor progressMonitor) throws JavaModelException {
    TypeNameCollector collector = getElementTypeCollector(project, progressMonitor);
    Set<String> qualifiedNames = collector.getTypeNames();
    List<String> simpleNames = new ArrayList<String>(qualifiedNames.size());
    for (String qualifiedName : qualifiedNames) {
      int lastDot = qualifiedName.lastIndexOf('.');
      simpleNames.add(lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName);
    }
    return simpleNames;
  }

  /**
   * Opens the Java source for the binding's value keypath, or if the key
   * doesn't exist, offers to create it as a key or action.
   *
   * @param bindingValue the binding value string (e.g. "session.user")
   * @param isAction whether this binding is an action binding
   * @param componentType the component's Java type
   * @param onlyIfMissing if true, only opens the dialog if the key is missing
   */
  public static void openBinding(String bindingValue, boolean isAction, IType componentType, boolean onlyIfMissing) throws CoreException {
    BindingValueKeyPath bindingValueKeyPath = new BindingValueKeyPath(bindingValue, componentType, componentType.getJavaProject(), WodParserCache.getTypeCache());
    if (bindingValueKeyPath.isValid() && bindingValueKeyPath.exists()) {
      if (!onlyIfMissing) {
        IMember member = bindingValueKeyPath.getLastBindingKey().getBindingMember();
        if (member != null) {
          JavaUI.openInEditor(member, true, true);
        }
      }
    }
    else if (!bindingValueKeyPath.exists() && bindingValueKeyPath.isSingleKey()) {
      WodCompletionUtils.addKeyOrAction(bindingValueKeyPath, isAction, componentType);
    }
  }

  /**
   * Opens a dialog to create a new key or action method on the component type.
   *
   * @param bindingValueKeyPath the keypath to create
   * @param isAction true to create an action method, false to create a property
   * @param componentType the component's Java type
   * @return the name chosen in the dialog
   */
  public static String addKeyOrAction(BindingValueKeyPath bindingValueKeyPath, boolean isAction, IType componentType) throws CoreException {
    String name = null;
    if (isAction) {
      AddActionInfo info = new AddActionInfo(componentType);
      info.setName(bindingValueKeyPath.getOriginalKeyPath());
      AddActionDialog.open(info, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
      name = info.getName();
    }
    else {
      AddKeyInfo info = new AddKeyInfo(componentType);
      info.setName(bindingValueKeyPath.getOriginalKeyPath());
      AddKeyDialog.open(info, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
      name = info.getName();
    }
    return name;
  }
  
  protected static boolean shouldSmartInsert() {
    return true;
  }

  public static void fillInElementNameCompletionProposals(Set<String> alreadyUsedElementNames, String token, int tokenOffset, int offset, Set<WodCompletionProposal> completionProposalsSet, boolean guessed, HtmlElementCache validElementNames) {
    String partialToken = partialToken(token, tokenOffset, offset).toLowerCase();
    for (String validElementName : validElementNames.elementNames()) {
      if (validElementName.toLowerCase().startsWith(partialToken) && !alreadyUsedElementNames.contains(validElementName)) {
        WodCompletionProposal completionProposal;
        if (WodCompletionUtils.shouldSmartInsert() && guessed) {
          completionProposal = new WodCompletionProposal(token, tokenOffset, offset, validElementName + " : ", validElementName, validElementName.length() + 3);
        }
        else {
          completionProposal = new WodCompletionProposal(token, tokenOffset, offset, validElementName);
        }
        completionProposalsSet.add(completionProposal);
      }
    }
  }

  public static void fillInElementTypeCompletionProposals(IJavaProject project, String token, int tokenOffset, int offset, Set<WodCompletionProposal> completionProposalsSet, boolean guessed, IProgressMonitor progressMonitor) throws JavaModelException {
    String partialToken = partialToken(token, tokenOffset, offset);
    if (partialToken.length() > 0) {
      // Use the cached full element type list and filter by prefix
      TypeNameCollector allTypes = getElementTypeCollector(project, progressMonitor);
      String lowercasePartialToken = partialToken.toLowerCase();
      boolean includePackageName = token.indexOf('.') != -1;
      Iterator<String> allTypeNamesIter = allTypes.typeNames();
      while (allTypeNamesIter.hasNext()) {
        String matchingElementTypeName = allTypeNamesIter.next();
        String shortName = BindingReflectionUtils.getShortClassName(matchingElementTypeName);
        // Match against short name or full name depending on whether user typed a dot
        String matchTarget = includePackageName ? matchingElementTypeName : shortName;
        if (!matchTarget.toLowerCase().startsWith(lowercasePartialToken)) {
          continue;
        }
        String elementTypeName = includePackageName ? matchingElementTypeName : shortName;
        WodCompletionProposal completionProposal;
        IType type = allTypes.getTypeForClassName(matchingElementTypeName);
        if (WodCompletionUtils.shouldSmartInsert() && guessed) {
          if (BindingReflectionUtils.memberIsDeprecated(type)) {
            completionProposal = new WodDeprecatedCompletionProposal(token, tokenOffset, offset, elementTypeName + " {\n\t\n}", elementTypeName, elementTypeName.length() + 4);
          } else {
            completionProposal = new WodCompletionProposal(token, tokenOffset, offset, elementTypeName + " {\n\t\n}", elementTypeName, elementTypeName.length() + 4);
          }
        }
        else {
          if (BindingReflectionUtils.memberIsDeprecated(type)) {
            completionProposal = new WodDeprecatedCompletionProposal(token, tokenOffset, offset, elementTypeName);
          } else {
            completionProposal = new WodCompletionProposal(token, tokenOffset, offset, elementTypeName);
          }
        }
        completionProposalsSet.add(completionProposal);
      }
    }
  }

  public static void fillInBindingNameCompletionProposals(IJavaProject project, IType elementType, String token, int tokenOffset, int offset, Set<WodCompletionProposal> completionProposalsSet, boolean guessed, TypeCache cache) throws JavaModelException {
    String partialToken = WodCompletionUtils.partialToken(token, tokenOffset, offset);
    boolean showReflectionBindings = true;

    // API files:
    try {
      ApiSnapshot api = ApiUtils.findApiSnapshot(elementType, cache.getApiCache(project));
      if (api != null) {
        String lowercasePartialToken = partialToken.toLowerCase();
        List<IApiBinding> bindings = api.getBindings();
        for (IApiBinding binding : bindings) {
          String bindingName = binding.getName();
          String lowercaseBindingName = bindingName.toLowerCase();
          if (lowercaseBindingName.startsWith(lowercasePartialToken)) {
            WodCompletionProposal completionProposal;
            if (WodCompletionUtils.shouldSmartInsert() && guessed) {
              completionProposal = new WodCompletionProposal(token, tokenOffset, offset, bindingName + " = ", bindingName, bindingName.length() + 3);
            }
            else {
              completionProposal = new WodCompletionProposal(token, tokenOffset, offset, bindingName);
            }
            completionProposalsSet.add(completionProposal);
          }
        }

        if (bindings != null && bindings.size() > 0) {
          showReflectionBindings = false;
        }
      }
    }
    catch (Throwable t) {
      // It's not that big a deal ... give up on api files
      WodclipsePlugin.getDefault().log(t);
    }

    if (showReflectionBindings) {
      List<BindingValueKey> bindingKeys = BindingReflectionUtils.getBindingKeys(project, elementType, partialToken, false, BindingReflectionUtils.MUTATORS_ONLY, false, cache);
      WodCompletionUtils._fillInCompletionProposals(bindingKeys, token, tokenOffset, offset, completionProposalsSet, false);
    }
  }

  public static boolean fillInBindingValueCompletionProposals(IJavaProject project, IType elementType, String token, int tokenOffset, int offset, Set<WodCompletionProposal> completionProposalsSet, TypeCache cache) throws JavaModelException {
    boolean checkBindingType = false;
    String partialToken = partialToken(token, tokenOffset, offset);
    BindingValueKeyPath bindingKeyPath = new BindingValueKeyPath(partialToken, elementType, project, cache);
    List<BindingValueKey> possibleBindingKeyMatchesList = bindingKeyPath.getPartialMatchesForLastBindingKey(false);
    if (possibleBindingKeyMatchesList != null) {
      String bindingKeyName;
      if (bindingKeyPath.getOperator() != null) {
        bindingKeyName = "@" + bindingKeyPath.getOperator();
      }
      else {
        bindingKeyName = bindingKeyPath.getLastBindingKeyName();
      }
      WodCompletionUtils._fillInCompletionProposals(possibleBindingKeyMatchesList, bindingKeyName, tokenOffset + partialToken.lastIndexOf('.') + 1, offset, completionProposalsSet, true);
    }

    // Only do binding type checks if you're on the first of a keypath ...
    if (bindingKeyPath != null && bindingKeyPath.getLength() == 1) {
      checkBindingType = true;
    }
    return checkBindingType;
  }

  public static String partialToken(String token, int tokenOffset, int offset) {
    String partialToken;
    int partialIndex = offset - tokenOffset;
    if (partialIndex > token.length()) {
      partialToken = token;
    }
    else {
      partialToken = token.substring(0, offset - tokenOffset);
    }
    return partialToken;
  }

  protected static void _fillInCompletionProposals(List<BindingValueKey> bindingKeys, String token, int tokenOffset, int offset, Set<WodCompletionProposal> completionProposalsSet, boolean showUsefulSystemBindings) {
    Iterator<BindingValueKey> bindingKeysIter = BindingReflectionUtils.filterSystemBindingValueKeys(bindingKeys, showUsefulSystemBindings).iterator();
    while (bindingKeysIter.hasNext()) {
      BindingValueKey bindingKey = bindingKeysIter.next();
      WodCompletionProposal completionProposal;
      if (BindingReflectionUtils.bindingPointsToDeprecatedValue(bindingKey)) {
    	  completionProposal = new WodDeprecatedCompletionProposal(token, tokenOffset, offset, bindingKey.getBindingName());
      } else {
    	  completionProposal = new WodCompletionProposal(token, tokenOffset, offset, bindingKey.getBindingName());
      }
      completionProposalsSet.add(completionProposal);
    }
  }
}

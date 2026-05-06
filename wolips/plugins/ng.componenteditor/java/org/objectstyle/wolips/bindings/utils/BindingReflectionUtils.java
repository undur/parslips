package org.objectstyle.wolips.bindings.utils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;
import org.objectstyle.wolips.core.resources.types.WOHierarchyScope;

/**
 * Utility methods for reflecting on Java types to discover binding keys
 * (fields, getters, setters) and for resolving element type names. This is
 * the core of the binding autocomplete and validation system — it figures
 * out what keys are available on a given type and filters out framework
 * noise.
 *
 * <p>The main entry points are:
 * <ul>
 *   <li>{@link #getBindingKeys} — finds matching binding keys for a type,
 *       used by autocomplete and validation</li>
 *   <li>{@link #getGroupedBindingValueKeys} — returns all keys grouped by
 *       declaring type, used by the Bindings Inspector's key browser</li>
 *   <li>{@link #findElementType} — resolves an element tag name to a Java
 *       {@link IType}, used during WOD parsing</li>
 *   <li>{@link #filterSystemBindingValueKeys} — removes framework-internal
 *       bindings from a key list</li>
 * </ul>
 *
 * <h3>Binding key discovery</h3>
 * A "binding key" is a property name derived from a field or method on a
 * component class. The discovery logic strips common prefixes (get/set/is/_)
 * and lowercases the first letter to produce a KVC-style key name. For
 * example, {@code getName()} → "name", {@code _title} → "title".
 *
 * <h3>System binding filtering</h3>
 * Bindings declared on framework base types (WOComponent, NGComponent, etc.)
 * are filtered to reduce noise. See {@link #_systemTypeNames},
 * {@link #_uselessSystemBindings}, and {@link #_usefulSystemBindings}.
 */
public class BindingReflectionUtils {
  /**
   * Prefixes stripped from field names to derive binding key names.
   * A field {@code _name} becomes binding key "name"; a field {@code name}
   * stays as "name".
   */
  public static final String[] FIELD_PREFIXES = { "", "_" };

  /**
   * Prefixes for setter methods. {@code setName()} and {@code _setName()}
   * both map to binding key "name".
   */
  public static final String[] SET_METHOD_PREFIXES = { "set", "_set" };

  /**
   * Prefixes for getter methods, tried in order. {@code getName()},
   * {@code name()}, {@code _getName()}, {@code isEnabled()},
   * {@code _isEnabled()}, and {@code _name()} all produce binding keys.
   */
  public static final String[] GET_METHOD_PREFIXES = { "get", "", "_get", "is", "_is", "_" };

  /** Filter mode: only methods with a non-void return type and no parameters. */
  public static final int ACCESSORS_ONLY = 0;

  /** Filter mode: only void methods with exactly one parameter (setters). */
  public static final int MUTATORS_ONLY = 1;

  /** Filter mode: methods with no parameters, regardless of return type. */
  public static final int ACCESSORS_OR_VOID = 2;

  /** Filter mode: only void methods with no parameters. */
  public static final int VOID_ONLY = 3;

  /**
   * Returns whether the given type name represents a boolean type
   * (primitive {@code boolean} or boxed {@code java.lang.Boolean}).
   */
  public static boolean isBoolean(String typeName) {
    return "boolean".equals(typeName) || "Boolean".equals(typeName) || "java.lang.Boolean".equals(typeName);
  }

  /**
   * Returns whether the given type name requires an import statement —
   * i.e. it is not a primitive and not in {@code java.lang}.
   */
  public static boolean isImportRequired(String typeName) {
    return typeName != null && !isPrimitive(typeName) && !typeName.equals("java.lang." + Signature.getSimpleName(typeName));
  }

  /**
   * Returns whether the given type name is a Java primitive type.
   */
  public static boolean isPrimitive(String typeName) {
    return ("boolean".equals(typeName) || "byte".equals(typeName) || "char".equals(typeName) || "int".equals(typeName) || "short".equals(typeName) || "float".equals(typeName) || "double".equals(typeName));
  }

  /**
   * Resolves a short (unqualified) class name to its fully qualified form
   * by searching the given project's classpath. Returns the input unchanged
   * if it is already qualified, a primitive, or a common {@code java.lang}
   * type. Logs a warning if the name is ambiguous or not found.
   *
   * @param javaProject the project to search
   * @param shortClassName the simple class name (e.g. "String" or "MyComponent")
   * @return the fully qualified class name, or the original name if resolution fails
   */
  public static String getFullClassName(IJavaProject javaProject, String shortClassName) throws JavaModelException {
    String expandedClassName = shortClassName;
    if (expandedClassName != null && expandedClassName.indexOf('.') == -1) {
      if ("String".equals(shortClassName)) {
        expandedClassName = "java.lang.String";
      }
      else if ("Object".equals(shortClassName)) {
        expandedClassName = "java.lang.Object";
      }
      else if (isPrimitive(expandedClassName)) {
        // ignore primitives
      }
      else {
        SearchEngine searchEngine = new SearchEngine();
        IJavaSearchScope searchScope = JavaSearchScopeFactory.getInstance().createJavaProjectSearchScope(javaProject, JavaSearchScopeFactory.ALL);
      	NullProgressMonitor progressMonitor = new NullProgressMonitor();
        TypeNameCollector typeNameCollector = new TypeNameCollector(javaProject, false);
        searchEngine.searchAllTypeNames(null, SearchPattern.R_EXACT_MATCH, shortClassName.toCharArray(), IJavaSearchConstants.TYPE, IJavaSearchConstants.TYPE, searchScope, typeNameCollector, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, progressMonitor);
        Set<String> typeNames = typeNameCollector.getTypeNames();
        if (typeNames.size() == 1) {
          expandedClassName = typeNames.iterator().next();
        }
        else if (typeNames.size() == 0) {
          Activator.getDefault().log("BindingReflectionUtils.getFullClassName: Unknown type name " + shortClassName);
        }
        else {
          Activator.getDefault().log("BindingReflectionUtils.getFullClassName: Ambiguous type name " + shortClassName + " (" + typeNames + ")");
        }
      }
    }
    return expandedClassName;
  }

  /**
   * Extracts the simple class name from a fully qualified name.
   * For example, {@code "com.example.MyClass"} → {@code "MyClass"}.
   * Returns the input unchanged if it contains no dots.
   */
  public static String getShortClassName(String fullClassName) {
    String shortClassName;
    int lastDotIndex = fullClassName.lastIndexOf('.');
    if (lastDotIndex == -1) {
      shortClassName = fullClassName;
    }
    else {
      shortClassName = fullClassName.substring(lastDotIndex + 1);
    }
    return shortClassName;
  }

  /**
   * Resolves an element tag name (e.g. "WOString", "ERXConditional") to
   * its Java {@link IType}. First checks the API cache for a previously
   * resolved mapping; if not cached, performs a project-scoped type search.
   * When multiple types match, uses the first one found (not ideal, but
   * matches legacy behavior). Caches the result for subsequent lookups.
   *
   * @param javaProject the project whose classpath to search
   * @param elementTypeName the element tag name to resolve
   * @param requireTypeInProject if true, only matches types defined in the
   *        project itself (not just on its classpath)
   * @param cache the shared type cache
   * @return the resolved type, or null if not found
   */
  public static IType findElementType(IJavaProject javaProject, String elementTypeName, boolean requireTypeInProject, TypeCache cache) throws JavaModelException {
    String typeName = cache.getApiCache(javaProject).getElementTypeNamed(elementTypeName);
    IType type = null;
    if (typeName != null) {
      type = javaProject.findType(typeName);
    }
    else {
    	NullProgressMonitor progressMonitor = new NullProgressMonitor();
      TypeNameCollector typeNameCollector = new TypeNameCollector(javaProject, requireTypeInProject);
      BindingReflectionUtils.findMatchingElementClassNames(elementTypeName, SearchPattern.R_EXACT_MATCH, typeNameCollector, progressMonitor);
      if (typeNameCollector.isExactMatch()) {
        String matchingElementClassName = typeNameCollector.firstTypeName();
        type = typeNameCollector.getTypeForClassName(matchingElementClassName);
      }
      else if (!typeNameCollector.isEmpty()) {
        // Ambiguous match — multiple classes share this name. Pick the
        // first one (arbitrary but consistent with legacy behavior).
        String matchingElementClassName = typeNameCollector.firstTypeName();
        type = typeNameCollector.getTypeForClassName(matchingElementClassName);
      }
      if (type != null) {
        cache.getApiCache(javaProject).setElementTypeForName(type, elementTypeName);
      }
    }
    return type;
  }

  /**
   * Searches for element class names matching the given name and match type.
   *
   * <p>For exact-match lookups ({@code matchType == R_EXACT_MATCH}), uses a
   * simple project-scoped search instead of building the expensive
   * {@link WOHierarchyScope} (which requires computing the full type
   * hierarchy). The hierarchy scope is only needed for prefix/pattern
   * matching (e.g. the "find all element types" query for autocomplete).
   */
  public static void findMatchingElementClassNames(String elementTypeName, int matchType, TypeNameCollector typeNameCollector, IProgressMonitor progressMonitor) throws JavaModelException {
    if (elementTypeName != null) {
      int lastDotIndex = elementTypeName.lastIndexOf('.');
      char[] packageName;
      char[] typeName;
      if (lastDotIndex == -1) {
        packageName = null;
        typeName = elementTypeName.toCharArray();
      }
      else {
        packageName = elementTypeName.substring(0, lastDotIndex).toCharArray();
        typeName = elementTypeName.substring(lastDotIndex + 1).toCharArray();
      }

      IType superclassType = typeNameCollector.getSuperclassType();

      // For exact-match lookups, a project-scoped search is sufficient and
      // much faster than building a WOHierarchyScope (which computes the
      // full type hierarchy). The hierarchy scope is only valuable for
      // prefix/pattern searches where we need to enumerate all subtypes.
      if (superclassType == null || matchType == SearchPattern.R_EXACT_MATCH) {
        SearchEngine searchEngine = new SearchEngine();
        IJavaSearchScope searchScope = SearchEngine.createJavaSearchScope(new IJavaElement[] { typeNameCollector.getProject() }, true);
        searchEngine.searchAllTypeNames(packageName, SearchPattern.R_EXACT_MATCH, typeName, matchType, IJavaSearchConstants.CLASS, searchScope, typeNameCollector, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, progressMonitor);
        return;
      }

      // Prefix/pattern match: use the hierarchy scope to restrict results
      // to subtypes of the element superclass (WOElement/NGElement).
      SearchEngine searchEngine = new SearchEngine();
      IJavaSearchScope searchScope = WOHierarchyScope.hierarchyScope(superclassType, typeNameCollector.getProject().getProject());
      searchEngine.searchAllTypeNames(packageName, SearchPattern.R_EXACT_MATCH, typeName, matchType, IJavaSearchConstants.CLASS, searchScope, typeNameCollector, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, progressMonitor);
    }
  }

  /**
   * Returns whether the given type is a component type — either
   * {@code NGComponent} (ng-objects) or {@code WOComponent} (WebObjects).
   */
  public static boolean isWOComponent(IType type, TypeCache cache) throws JavaModelException {
    return BindingReflectionUtils.isType(type, new String[] { "ng.appserver.templating.NGComponent", "com.webobjects.appserver.WOComponent" }, cache);
  }

  /**
   * Returns whether the given type implements NSKeyValueCoding.
   * Always returns false — ng-objects does not use NSKeyValueCoding,
   * and this check is no longer meaningful.
   *
   * @deprecated This method is a no-op stub retained for compatibility.
   */
  public static boolean isNSKeyValueCoding(IType type, TypeCache cache) throws JavaModelException {
    return false;
  }

  /**
   * Returns whether the given type is a Java collection type
   * (Map, List, Set, or Collection).
   */
  public static boolean isNSCollection(IType type, TypeCache cache) throws JavaModelException {
    return BindingReflectionUtils.isType(type, new String[] { "java.util.Map", "java.util.List", "java.util.Set", "java.util.Collection" }, cache);
  }

  /**
   * Checks whether the given type is, or extends/implements, any of the
   * specified fully qualified type names. Uses the cached type hierarchy
   * from {@link TypeCache#getSupertypesOf}, which includes the type itself.
   *
   * @param type the type to check
   * @param possibleTypes fully qualified names to match against
   * @param cache the shared type cache
   * @return true if the type or any of its supertypes matches
   */
  public static boolean isType(IType type, String[] possibleTypes, TypeCache cache) throws JavaModelException {
    boolean isType = false;
    List<IType> types = cache.getSupertypesOf(type);
    for (int typeNum = 0; !isType && typeNum < types.size(); typeNum++) {
      String name = types.get(typeNum).getFullyQualifiedName();
      for (int possibleTypeNum = 0; !isType && possibleTypeNum < possibleTypes.length; possibleTypeNum++) {
        if (possibleTypes[possibleTypeNum].equals(name)) {
          isType = true;
        }
      }
    }
    return isType;
  }

  /**
   * Returns the set of KVC array operator names ({@code @count},
   * {@code @sum}, etc.) that are valid on NSArray/collection types.
   * The returned names do not include the {@code @} prefix.
   */
  public static Set<String> getArrayOperators() {
    Set<String> operators = new HashSet<String>();
    operators.add("avg");
    operators.add("count");
    operators.add("min");
    operators.add("max");
    operators.add("sum");
    operators.add("sort");
    operators.add("sortAsc");
    operators.add("sortDesc");
    return operators;
  }

  /**
   * Discovers binding keys on a type by introspecting its fields and methods
   * (and those of its supertypes). This is the workhorse behind binding
   * autocomplete and validation.
   *
   * <p>The method walks the type hierarchy and collects keys whose names
   * match the given prefix. Special handling exists for:
   * <ul>
   *   <li>WOApplication/WOSession/WODirectAction subtypes — walks the
   *       subtype hierarchy instead, so user subclass keys are included</li>
   *   <li>NSArray types — adds KVC array operators ({@code @count}, etc.)</li>
   *   <li>Java records — includes record component accessors</li>
   * </ul>
   *
   * @param javaProject the project context
   * @param type the type to introspect
   * @param nameStartingWith prefix filter, or "" for all keys
   * @param requireExactNameMatch if true, only exact name matches are returned
   *        (stops after first match)
   * @param accessorsOrMutators one of {@link #ACCESSORS_ONLY},
   *        {@link #MUTATORS_ONLY}, {@link #ACCESSORS_OR_VOID}, or
   *        {@link #VOID_ONLY}
   * @param allowInheritanceDuplicates if true, a key can appear multiple
   *        times when declared at different levels of the hierarchy
   * @param cache the shared type cache
   * @return the list of matching binding keys (unfiltered — caller should
   *         apply {@link #filterSystemBindingValueKeys} if needed)
   */
  public static List<BindingValueKey> getBindingKeys(IJavaProject javaProject, IType type, String nameStartingWith, boolean requireExactNameMatch, int accessorsOrMutators, boolean allowInheritanceDuplicates, TypeCache cache) throws JavaModelException {
    List<BindingValueKey> bindingKeys = new LinkedList<BindingValueKey>();
    if (type != null) {
      String lowercaseNameStartingWith = nameStartingWith.toLowerCase();

      Set<String> additionalProposals = new HashSet<String>();

      // Walk the supertype hierarchy. For commonly-subclassed framework
      // types (WOApplication, WOSession, WODirectAction), switch to
      // walking the subtype hierarchy so user subclass keys are found.
      List<IType> types = cache.getSupertypesOf(type);
      if (types != null) {
        IType usuallySubclassedSupertype = null;
        IType nextType = null;
        for (int typeNum = 0; usuallySubclassedSupertype == null && typeNum < types.size(); typeNum++) {
          String typeName = types.get(typeNum).getElementName();
          if ("WOApplication".equals(typeName) || "WOSession".equals(typeName) || "WODirectAction".equals(typeName)) {
            usuallySubclassedSupertype = types.get(typeNum);
          }
          else if ("NSArray".equals(typeName)) {
            for (String operator : BindingReflectionUtils.getArrayOperators()) {
              additionalProposals.add("@" + operator);
            }
            nextType = types.get(typeNum);
          }
        }

        for (String additionalProposal : additionalProposals) {
          if (additionalProposal.startsWith(nameStartingWith)) {
            // Array operators have no declaring type or member — the key's
            // next type is unknown (would need generic type resolution).
            BindingValueKey additionalKey = new BindingValueKey(additionalProposal, null, null, javaProject, cache);
            bindingKeys.add(additionalKey);
          }
        }

        if (usuallySubclassedSupertype != null) {
          types = cache.getSubtypesOfInProject(usuallySubclassedSupertype, javaProject);
        }
      }

      if (types != null) {
        for (int typeNum = 0; (!requireExactNameMatch || bindingKeys.size() == 0) && typeNum < types.size(); typeNum++) {
          BindingReflectionUtils.fillInBindingKeys(type, types.get(typeNum), lowercaseNameStartingWith, requireExactNameMatch, accessorsOrMutators, allowInheritanceDuplicates, javaProject, bindingKeys, cache);
        }
      }

      // Java record components expose their accessors as plain methods
      // (e.g. record Foo(String bar) → bar()). These aren't picked up
      // by fillInBindingKeys() since they lack getter prefixes, so we
      // handle them separately here.
      if( type.isRecord() ) {
    	  for (IField rc : type.getRecordComponents() ) {
    		  final String rcName = rc.getElementName();
    		  
    		  if( requireExactNameMatch ) {
    			  if (rcName.equals(nameStartingWith)) {
    				  final BindingValueKey key = new BindingValueKey(rcName, rc.getDeclaringType(), rc, javaProject, cache);
    				  bindingKeys.add(key);
    			  }	    			
    		  }
    		  else if (rcName.startsWith(nameStartingWith)) {
    			  final BindingValueKey key = new BindingValueKey(rcName, rc.getDeclaringType(), rc, javaProject, cache);
    			  bindingKeys.add(key);
    		  }
    	  }
      }
    }

    return bindingKeys;
  }

  /**
   * Scans a single type's fields and methods to find binding keys matching
   * the given prefix. Iterates fields first (with {@link #FIELD_PREFIXES}),
   * then methods (with getter or setter prefixes depending on
   * {@code accessorsOrMutators}). Each candidate is checked by
   * {@link #getBindingKeyIfMatches}.
   *
   * @param declaringType the original type being introspected (used for
   *        the key's declaring type reference, which may differ from
   *        {@code type} when walking supertypes)
   * @param type the specific type in the hierarchy to scan
   * @param lowercaseNameStartingWith lowercased prefix filter
   * @param requireExactNameMatch if true, stop after the first exact match
   * @param accessorsOrMutators filter mode constant
   * @param allowInheritanceDuplicates if false, skip keys already in the list
   * @param javaProject the project context
   * @param bindingKeys accumulator list (modified in place)
   * @param cache the shared type cache
   */
  private static void fillInBindingKeys(IType declaringType, IType type, String lowercaseNameStartingWith, boolean requireExactNameMatch, int accessorsOrMutators, boolean allowInheritanceDuplicates, IJavaProject javaProject, List<BindingValueKey> bindingKeys, TypeCache cache) throws JavaModelException {

    IField[] fields = type.getFields();
    for (int fieldNum = 0; (!requireExactNameMatch || bindingKeys.size() == 0) && fieldNum < fields.length; fieldNum++) {
      for (String prefix : BindingReflectionUtils.FIELD_PREFIXES) {
        BindingValueKey bindingKey = BindingReflectionUtils.getBindingKeyIfMatches(javaProject, declaringType, fields[fieldNum], prefix + lowercaseNameStartingWith, prefix, requireExactNameMatch, accessorsOrMutators, cache);
        if (bindingKey != null) {
          bindingKeys.add(bindingKey);
          break;
        }
      }
    }

    if (!requireExactNameMatch || bindingKeys.size() == 0) {
      IMethod[] methods = type.getMethods();
      String[] prefixes;
      if (accessorsOrMutators == BindingReflectionUtils.ACCESSORS_ONLY) {
        prefixes = BindingReflectionUtils.GET_METHOD_PREFIXES;
      }
      else if (accessorsOrMutators == BindingReflectionUtils.ACCESSORS_OR_VOID) {
        prefixes = BindingReflectionUtils.GET_METHOD_PREFIXES;
      }
      else if (accessorsOrMutators == BindingReflectionUtils.MUTATORS_ONLY) {
        prefixes = BindingReflectionUtils.SET_METHOD_PREFIXES;
      }
      else {
        prefixes = new String[0];
      }

      for (int methodNum = 0; (!requireExactNameMatch || bindingKeys.size() == 0) && methodNum < methods.length; methodNum++) {
        for (String prefix : prefixes) {
          BindingValueKey bindingKey = BindingReflectionUtils.getBindingKeyIfMatches(javaProject, declaringType, methods[methodNum], prefix + lowercaseNameStartingWith, prefix, requireExactNameMatch, accessorsOrMutators, cache);
          if (bindingKey != null) {
            if (allowInheritanceDuplicates || !bindingKeys.contains(bindingKey)) {
              bindingKeys.add(bindingKey);
            }
            break;
          }
        }
      }
    }
  }

  /**
   * Returns whether the given member's declaring type is in the default
   * (unnamed) package. Components in the default package can access
   * protected and package-private members via KVC.
   */
  private static boolean isDefaultPackage(IMember member) {
    IType declaringType = member.getDeclaringType();
    String declaringTypePackageName = declaringType.getPackageFragment().getElementName();
    return declaringTypePackageName == null || declaringTypePackageName.length() == 0;
  }

  /**
   * Three-state visibility used during binding key discovery.
   * {@link #MaybeVisible} means the member is protected/package-private
   * in a non-default package — it becomes {@link #Visible} only if a
   * {@code KeyValueCodingProtectedAccessor} class exists in the same package.
   */
  private static enum Visibility {
  	Hidden,
  	Visible,
  	MaybeVisible
  }
  
  /**
   * Tests whether a single field or method matches the binding key search
   * criteria and returns a {@link BindingValueKey} if it does.
   *
   * <p>The matching logic is:
   * <ol>
   *   <li>Visibility check — private and static members are excluded.
   *       Public members and interface members are always visible.
   *       Protected/package-private members are visible if the declaring
   *       type is in the default package, or if a
   *       {@code KeyValueCodingProtectedAccessor} exists in the same package.</li>
   *   <li>Signature check — methods must match the expected parameter count
   *       and return type for the given {@code accessorsOrMutators} mode.
   *       Constructors are always excluded.</li>
   *   <li>Name check — the member name (with prefix stripped and first
   *       letter lowercased for methods) must match or start with the
   *       search string.</li>
   * </ol>
   *
   * @param javaProject the project context
   * @param type the declaring type (for the resulting key's type reference)
   * @param member the field or method to test
   * @param nameStartingWith the lowercased prefix (including the field/method
   *        prefix) to match against
   * @param prefix the field/method prefix being tested (e.g. "get", "_", "set")
   * @param requireExactNameMatch if true, only exact matches count
   * @param accessorsOrMutators filter mode constant
   * @param cache the shared type cache
   * @return a BindingValueKey if the member matches, or null
   */
  private static BindingValueKey getBindingKeyIfMatches(IJavaProject javaProject, IType type, IMember member, String nameStartingWith, String prefix, boolean requireExactNameMatch, int accessorsOrMutators, TypeCache cache) throws JavaModelException {
    BindingValueKey bindingKey = null;

    int flags = member.getFlags();
    Visibility visible = Visibility.Hidden;

    if (Flags.isPrivate(flags)) {
      visible = Visibility.Hidden;
    }
    else if (Flags.isStatic(flags)) {
      visible = Visibility.Hidden;
    }
    else if (Flags.isPublic(flags) || type.isInterface()) {
      visible = Visibility.Visible;
    }
    else if (Flags.isProtected(flags) || Flags.isPackageDefault(flags)) {
    	if (BindingReflectionUtils.isDefaultPackage(member)) {
    		visible = Visibility.Visible;
    	}
    	else {
    		visible = Visibility.MaybeVisible;
    	}
    }
    
    if (visible != Visibility.Hidden) {
      boolean memberSignatureMatches;
      if (member instanceof IMethod) {
        IMethod method = (IMethod) member;
        if (method.isConstructor()) {
          memberSignatureMatches = false;
        }
        else {
          int parameterCount = method.getParameterTypes().length;
          String returnType = method.getReturnType();
          if (accessorsOrMutators == BindingReflectionUtils.ACCESSORS_ONLY) {
            memberSignatureMatches = (parameterCount == 0 && !"V".equals(returnType));
          }
          else if (accessorsOrMutators == BindingReflectionUtils.ACCESSORS_OR_VOID) {
            memberSignatureMatches = (parameterCount == 0);
          }
          else if (accessorsOrMutators == BindingReflectionUtils.VOID_ONLY) {
            memberSignatureMatches = (parameterCount == 0 && "V".equals(returnType));
          }
          else {
            memberSignatureMatches = (parameterCount == 1 && "V".equals(returnType));
          }
        }
      }
      else {
        memberSignatureMatches = true;
      }

      if (memberSignatureMatches) {
        String memberName = member.getElementName();
        String lowercaseMemberName = memberName.toLowerCase();

        int prefixLength = prefix.length();
        if ((requireExactNameMatch && lowercaseMemberName.equals(nameStartingWith)) || (!requireExactNameMatch && lowercaseMemberName.startsWith(nameStartingWith))) {
        	final String bindingName;
        	String remainder = memberName.substring(prefixLength);
        	if (member instanceof IField) {
        		bindingName = remainder;
        	}
        	// For "" (bare method) and "_" (underscore-only) prefixes, the
        	// remainder IS the binding key — preserve the case exactly. At
        	// runtime, key K looks up method "K" or "_K" verbatim, so a
        	// method named "HttpServer" is found by key "HttpServer", not
        	// "httpServer". Only the bean-style "get"/"set"/"is" (and
        	// underscore variants) trigger the lowercase-first transformation.
        	else if (prefix.isEmpty() || "_".equals(prefix)) {
        		bindingName = remainder;
        	}
        	else {
        		bindingName = BindingReflectionUtils.toLowercaseFirstLetter(remainder);
        	}
          if (nameStartingWith.length() > 0 || !bindingName.startsWith("_")) {
          	if (visible == Visibility.MaybeVisible) {
          		String packageName = type.getPackageFragment().getElementName();
          		String kvcProtectedAccessorClassName = packageName.length() == 0 ? "KeyValueCodingProtectedAccessor" : (packageName + ".KeyValueCodingProtectedAccessor");
              IType kvcProtectedAccessor = type.getJavaProject().findType(kvcProtectedAccessorClassName);
          		if (kvcProtectedAccessor != null) {
          			visible = Visibility.Visible;
          		}
          	}
          	
          	if (visible == Visibility.Visible) {
          		bindingKey = new BindingValueKey(bindingName, type, member, javaProject, cache);
          	}
          }
        }
      }
    }

    return bindingKey;
  }

  /**
   * Converts a method name (with KVC prefix already stripped) to its
   * binding-key form, following Cocoa / WebObjects KVC reverse-lookup
   * convention.
   *
   * <p>Rules:
   * <ul>
   *   <li>Empty string → unchanged</li>
   *   <li>First character already lowercase → unchanged</li>
   *   <li>First character uppercase, second character also uppercase →
   *       <b>unchanged</b> (preserves acronyms like {@code URL},
   *       {@code HTTPServer}). At runtime, {@code valueForKey("URL")} finds
   *       method {@code getURL()} or {@code URL()}; the binding key for
   *       those methods is therefore {@code "URL"}, not {@code "uRL"}.</li>
   *   <li>First character uppercase, second character not uppercase →
   *       first character lowercased ({@code Title} → {@code title})</li>
   * </ul>
   */
  public static String toLowercaseFirstLetter(String _memberName) {
    if (_memberName.isEmpty()) {
      return _memberName;
    }
    char firstChar = _memberName.charAt(0);
    if (!Character.isUpperCase(firstChar)) {
      return _memberName;
    }
    // Preserve acronym prefixes: if the second character is also uppercase
    // (e.g. "URL", "HTTPServer"), leave the name as-is. This matches the
    // KVC reverse-lookup convention used at runtime.
    if (_memberName.length() > 1 && Character.isUpperCase(_memberName.charAt(1))) {
      return _memberName;
    }
    return Character.toLowerCase(firstChar) + _memberName.substring(1);
  }

  /**
   * Simple names of framework base types whose bindings are subject to
   * filtering. Keys declared on these types are checked against
   * {@link #_uselessSystemBindings} and {@link #_usefulSystemBindings}.
   */
  private static Set<String> _systemTypeNames = createSystemTypeNames();

  /**
   * Binding names from system types that are <b>always filtered out</b> —
   * framework internals that are never useful in component bindings.
   */
  private static Set<String> _uselessSystemBindings = createUselessSystemBindings();

  /**
   * Binding names from system types that are <b>conditionally kept</b> —
   * commonly used framework bindings (application, context, session) that
   * are shown when {@code showUsefulSystemBindings} is true.
   */
  private static Set<String> _usefulSystemBindings = createUsefulSystemBindings();

  private static Set<String> createSystemTypeNames() {
    Set<String> names = new HashSet<String>();
    names.add("Object");
    // ng-objects types
    names.add("NGElement");
    names.add("NGActionResults");
    names.add("NGComponent");
    // WebObjects types
    names.add("WOElement");
    names.add("WOActionResults");
    names.add("WOComponent");
    return names;
  }

  private static Set<String> createUselessSystemBindings() {
    Set<String> names = new HashSet<String>();
    names.add("baseURL");
    names.add("bindingKeys");
    names.add("cachingEnabled");
    names.add("childTemplate");
    names.add("class");
    names.add("clone");
    names.add("componentDefinition");
    names.add("componentUnroll");
    names.add("eventLoggingEnabled");
    names.add("frameworkName");
    names.add("generateResponse");
    names.add("getClass");
    names.add("hashCode");
    names.add("isCachingEnabled");
    names.add("isEventLoggingEnabled");
    names.add("isPage");
    names.add("isStateless");
    names.add("keyAssociations");
    names.add("name");
    names.add("page");
    names.add("parent");
    names.add("path");
    names.add("pathURL");
    names.add("stateless");
    names.add("synchronizesVariablesWithBindings");
    names.add("template");
    names.add("toString");
    names.add("unroll");
    return names;
  }

  private static Set<String> createUsefulSystemBindings() {
    Set<String> names = new HashSet<String>();
    names.add("application");
    names.add("context");
    names.add("hasSession");
    names.add("session");
    return names;
  }

  /**
   * Determines whether a binding key should be filtered out as a "system"
   * binding. A key is considered a system binding if:
   * <ul>
   *   <li>Its declaring type is in {@link #_systemTypeNames} and its name
   *       is in {@link #_uselessSystemBindings} (always filtered)</li>
   *   <li>Its declaring type is in {@link #_systemTypeNames} and its name
   *       is in {@link #_usefulSystemBindings} and
   *       {@code showUsefulSystemBindings} is false</li>
   *   <li>Its name starts with underscore (private/internal convention)</li>
   * </ul>
   *
   * @param bindingValueKey the key to check
   * @param showUsefulSystemBindings if true, useful system bindings
   *        (application, context, session, hasSession) are kept
   * @return true if the key should be filtered out
   */
  public static boolean isSystemBindingValueKey(BindingValueKey bindingValueKey, boolean showUsefulSystemBindings) {
    boolean isSystemBinding = false;
    if (bindingValueKey != null && bindingValueKey.getDeclaringType() != null) {
      String declaringTypeName = bindingValueKey.getDeclaringType().getElementName();
      String bindingName = bindingValueKey.getBindingName();
      if (BindingReflectionUtils._systemTypeNames.contains(declaringTypeName)) {
        if (!showUsefulSystemBindings && BindingReflectionUtils._usefulSystemBindings.contains(bindingName)) {
          isSystemBinding = true;
        }
        else if (BindingReflectionUtils._uselessSystemBindings.contains(bindingName)) {
          isSystemBinding = true;
        }
      }
      else if (bindingName.startsWith("_")) {
        isSystemBinding = true;
      }
    }
    return isSystemBinding;
  }

  /**
   * Returns whether the given key path string is a boolean literal value
   * ("true", "false", "yes", or "no", case-insensitive).
   */
  public static boolean isBooleanValue(String keyPath) {
    return "true".equalsIgnoreCase(keyPath) || "false".equalsIgnoreCase(keyPath) || "yes".equalsIgnoreCase(keyPath) || "no".equalsIgnoreCase(keyPath);
  }

  /**
   * Returns a copy of the given binding key list with system bindings
   * removed (as determined by {@link #isSystemBindingValueKey}). If no
   * system bindings are found, returns the original list as-is (no copy).
   *
   * @param bindingKeys the unfiltered key list
   * @param showUsefulSystemBindings if true, commonly-used framework
   *        bindings (application, context, session, hasSession) are kept
   * @return the filtered list
   */
  public static List<BindingValueKey> filterSystemBindingValueKeys(List<BindingValueKey> bindingKeys, boolean showUsefulSystemBindings) {
    Set<BindingValueKey> systemBindingValueKeys = new HashSet<BindingValueKey>();
    for (BindingValueKey bindingKey : bindingKeys) {
      if (BindingReflectionUtils.isSystemBindingValueKey(bindingKey, showUsefulSystemBindings)) {
        systemBindingValueKeys.add(bindingKey);
      }
    }

    List<BindingValueKey> filteredBindingValueKeys;
    if (systemBindingValueKeys.isEmpty()) {
      filteredBindingValueKeys = bindingKeys;
    }
    else {
      filteredBindingValueKeys = new LinkedList<BindingValueKey>();
      for (BindingValueKey bindingKey : bindingKeys) {
        if (!systemBindingValueKeys.contains(bindingKey)) {
          filteredBindingValueKeys.add(bindingKey);
        }
      }
    }

    return filteredBindingValueKeys;
  }

  /**
   * Returns all binding keys for a type, grouped by declaring class.
   * The map is ordered by type depth (deepest subclass first) using
   * {@link TypeDepthComparator}. System bindings are filtered with
   * {@code showUsefulSystemBindings=true}, so commonly-used framework
   * keys (application, context, session, hasSession) are included.
   *
   * <p>This is the primary data source for the Bindings Inspector's
   * key browser (WOBrowserColumn).
   *
   * @param startingWith prefix filter, or "" for all keys
   * @param type the type to introspect
   * @param cache the shared type cache
   * @return a map from declaring type to the set of keys it contributes
   */
  public static Map<IType, Set<BindingValueKey>> getGroupedBindingValueKeys(String startingWith, IType type, TypeCache cache) throws JavaModelException {
    BindingValueKeyPath bindingValueKeyPath = new BindingValueKeyPath(startingWith, type, type.getJavaProject(), cache);
    List<BindingValueKey> bindingValueKeys = bindingValueKeyPath.getPartialMatchesForLastBindingKey(true);
    List<BindingValueKey> filteredBindingValueKeys = BindingReflectionUtils.filterSystemBindingValueKeys(bindingValueKeys, true);
    Set<BindingValueKey> uniqueBingingValueKeys = new TreeSet<BindingValueKey>(filteredBindingValueKeys);

    Map<IType, Set<BindingValueKey>> typeKeys = new TreeMap<IType, Set<BindingValueKey>>(new TypeDepthComparator(cache));
    for (BindingValueKey key : uniqueBingingValueKeys) {
      IType declaringType = key.getDeclaringType();
      Set<BindingValueKey> typeKeysSet = typeKeys.get(declaringType);
      if (typeKeysSet == null) {
        typeKeysSet = new TreeSet<BindingValueKey>();
        typeKeys.put(declaringType, typeKeysSet);
      }
      typeKeysSet.add(key);
    }

    return typeKeys;
  }

  /**
   * Check if the member the given binding key points to is deprecated.
   * 
   * @param bindingKey the binding
   * @return <code>true</code> if binding points to something deprecated
   */
  public static boolean bindingPointsToDeprecatedValue(BindingValueKey bindingKey) {
    return memberIsDeprecated(bindingKey.getBindingMember());
  }
  
  /**
   * Check if the given member is deprecated.
   * 
   * @param member the member
   * @return <code>true</code> if member is deprecated
   */
  public static boolean memberIsDeprecated(IMember member) {
    boolean isDeprecated = false;
    if (member != null) {
      try {
        isDeprecated = ((member.getFlags() & Flags.AccDeprecated) == Flags.AccDeprecated);
      } catch (JavaModelException e) {
        // ignore
      }
    }
    return isDeprecated;
  }
}

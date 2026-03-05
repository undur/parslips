package org.objectstyle.wolips.wodclipse.core.refactoring;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Participates in Eclipse's Rename refactoring to update binding key references
 * in a component's own template files when a method or field is renamed.
 *
 * <p>When a developer renames e.g. {@code title()} to {@code heading()} in
 * {@code MyComponent.java} via Refactor &gt; Rename, this participant:
 * <ul>
 *   <li>Derives the old and new binding keys from the method/field names
 *       (stripping KVC prefixes like {@code get}, {@code set}, {@code is},
 *       {@code _}).</li>
 *   <li>Scans the component's own WOD file for {@code value = oldKey;} and
 *       similar binding value references.</li>
 *   <li>Scans the component's own HTML template for inline binding references
 *       like {@code value="$oldKey"}.</li>
 *   <li>Creates text edits to replace the old key with the new key in both
 *       files.</li>
 * </ul>
 *
 * <p>This is a <em>local</em> refactoring — only the component's own template
 * files are updated. Other components' templates that might reference this
 * key through parent bindings are not affected (that would be a cross-project
 * refactoring with very different semantics).
 *
 * <p>The participant only activates when the declaring type is a
 * WOComponent/NGComponent subclass. Plain WOElement subclasses don't have
 * template files with binding keys.
 *
 * <p>Registered via plugin.xml as a renameParticipant for {@code IMethod}
 * and {@code IField}.
 */
public class RenameBindingKeyParticipant extends RenameParticipant {

	/** The Java member (method or field) being renamed. */
	private IMember _member;

	/** The declaring type (the component class). */
	private IType _declaringType;

	/** The old binding key derived from the member's current name. */
	private String _oldKey;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IMethod) && !(element instanceof IField)) {
			return false;
		}

		_member = (IMember) element;
		_declaringType = _member.getDeclaringType();

		if (_declaringType == null) {
			return false;
		}

		// When WOLips is installed, only participate for projects that have
		// explicitly opted in via project.base in build.properties.
		if (!ParsleyProject.isParsleyProject(_declaringType.getJavaProject().getProject())) {
			return false;
		}

		// Only participate if the declaring type is a WOComponent/NGComponent
		if (!isComponent(_declaringType)) {
			return false;
		}

		// Derive the binding key from the old member name
		_oldKey = deriveBindingKey(_member);

		// If the key is empty or null, there's nothing to rename
		return _oldKey != null && _oldKey.length() > 0;
	}

	@Override
	public String getName() {
		return "Rename WO Binding Key References";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		String newMemberName = getArguments().getNewName();

		// Derive the new binding key from the new member name.
		// We create a temporary name to run through the same derivation logic.
		String newKey = deriveBindingKeyFromName(_member, newMemberName);

		// If old and new keys are the same, nothing to do
		// (can happen if the rename only affects the prefix, which is unusual)
		if (_oldKey.equals(newKey)) {
			return null;
		}

		String componentName = _declaringType.getElementName();
		IProject project = _declaringType.getJavaProject().getProject();

		// Determine the inline binding prefix for this project
		BuildProperties buildProperties = (BuildProperties) project.getAdapter(BuildProperties.class);
		String inlineBindingPrefix = buildProperties != null ? buildProperties.getInlineBindingPrefix() : "$";

		return RenameBindingKeyProcessor.computeBindingKeyChanges(
				project, componentName, _oldKey, newKey, inlineBindingPrefix);
	}

	/**
	 * Derives a binding key from a member (method or field) using its current name.
	 */
	private static String deriveBindingKey(IMember member) {
		String name = member.getElementName();
		if (member instanceof IField) {
			return deriveBindingKeyFromFieldName(name);
		}
		return deriveBindingKeyFromMethodName(name);
	}

	/**
	 * Derives a binding key from a member type and a (possibly new) name.
	 *
	 * <p>Used for computing the new key: we know the member type (method or
	 * field) and the new name, but don't have the actual renamed member yet.
	 */
	private static String deriveBindingKeyFromName(IMember member, String name) {
		if (member instanceof IField) {
			return deriveBindingKeyFromFieldName(name);
		}
		return deriveBindingKeyFromMethodName(name);
	}

	/**
	 * Derives a binding key from a method name by stripping KVC getter/setter
	 * prefixes and lowercasing the first letter.
	 *
	 * <p>Prefix stripping order:
	 * <ol>
	 *   <li>Explicit getter prefixes: {@code get}, {@code _get}, {@code is},
	 *       {@code _is} — require uppercase after prefix</li>
	 *   <li>Setter prefixes: {@code set}, {@code _set} — require uppercase
	 *       after prefix</li>
	 *   <li>Bare underscore: {@code _} — no uppercase requirement (KVC
	 *       convention for private accessors)</li>
	 *   <li>No prefix: the method name IS the key (e.g. {@code title()})</li>
	 * </ol>
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code title()} → "title"</li>
	 *   <li>{@code getTitle()} → "title"</li>
	 *   <li>{@code isEnabled()} → "enabled"</li>
	 *   <li>{@code setTitle()} → "title"</li>
	 *   <li>{@code _getTitle()} → "title"</li>
	 *   <li>{@code _title()} → "title"</li>
	 * </ul>
	 */
	static String deriveBindingKeyFromMethodName(String methodName) {
		// 1. Try explicit getter prefixes (skip "" and bare "_")
		// GET_METHOD_PREFIXES = { "get", "", "_get", "is", "_is", "_" }
		for (String prefix : BindingReflectionUtils.GET_METHOD_PREFIXES) {
			if (prefix.length() > 1 && methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 2. Try setter prefixes: { "set", "_set" }
		for (String prefix : BindingReflectionUtils.SET_METHOD_PREFIXES) {
			if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 3. Bare "_" prefix — doesn't require uppercase after stripping
		if (methodName.startsWith("_") && methodName.length() > 1) {
			return methodName.substring(1);
		}

		// 4. No recognized prefix — the method name IS the key
		return methodName;
	}

	/**
	 * Derives a binding key from a field name by stripping the underscore
	 * prefix if present.
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code title} → "title"</li>
	 *   <li>{@code _title} → "title"</li>
	 * </ul>
	 */
	static String deriveBindingKeyFromFieldName(String fieldName) {
		if (fieldName.startsWith("_") && fieldName.length() > 1) {
			return fieldName.substring(1);
		}
		return fieldName;
	}

	/**
	 * Checks whether the given type is a WOComponent or NGComponent subclass
	 * by walking its supertype hierarchy.
	 *
	 * <p>Only components have template files with binding keys. Plain
	 * WOElement/WODynamicElement subclasses don't use template bindings
	 * in the same way.
	 */
	private static boolean isComponent(IType type) {
		try {
			ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
			IType[] allSupertypes = hierarchy.getAllSupertypes(type);

			for (IType supertype : allSupertypes) {
				String fqn = supertype.getFullyQualifiedName();
				if (ParsleyProject.NG_COMPONENT_CLASS.equals(fqn) || ParsleyProject.WO_COMPONENT_CLASS.equals(fqn)) {
					return true;
				}
			}
		}
		catch (CoreException e) {
			// If we can't resolve the hierarchy, don't participate.
			// Conservative — better to skip than to break the rename.
		}
		return false;
	}
}

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

		// Use shouldRefactor() instead of shouldHandleProject() to avoid
		// double-fire with WOLips' refactoring participant (see javadoc).
		if (!ParsleyProject.shouldRefactor(_declaringType.getJavaProject().getProject())) {
			return false;
		}

		// Only participate if the declaring type is a WOComponent/NGComponent
		if (!isComponent(_declaringType)) {
			return false;
		}

		// Derive the binding key from the old member name
		_oldKey = RenameBindingKeyProcessor.deriveBindingKey(_member);

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
	 * Derives a binding key from a member type and a (possibly new) name.
	 *
	 * <p>Used for computing the new key: we know the member type (method or
	 * field) and the new name, but don't have the actual renamed member yet.
	 */
	private static String deriveBindingKeyFromName(IMember member, String name) {
		if (member instanceof IField) {
			return RenameBindingKeyProcessor.deriveBindingKeyFromFieldName(name);
		}
		return RenameBindingKeyProcessor.deriveBindingKeyFromMethodName(name);
	}

	/**
	 * Checks whether the given type is a WOComponent or NGComponent subclass
	 * by walking its supertype hierarchy.
	 *
	 * <p>Only components have template files with binding keys. Plain
	 * WOElement/WODynamicElement subclasses don't use template bindings
	 * in the same way.
	 *
	 * <p>Public so that the search participant can reuse this check.
	 */
	public static boolean isComponent(IType type) {
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

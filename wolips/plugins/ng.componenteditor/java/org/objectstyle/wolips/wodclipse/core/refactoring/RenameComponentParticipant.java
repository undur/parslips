package org.objectstyle.wolips.wodclipse.core.refactoring;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.objectstyle.wolips.variables.BuildProperties;

/**
 * Participates in Eclipse's Rename Type refactoring to also rename a component's
 * template files when a WOComponent/NGComponent Java class is renamed.
 *
 * When a user renames e.g. {@code Main.java} to {@code HomePage.java} via
 * Refactor > Rename, this participant detects that Main is a component class
 * and adds changes to rename {@code Main.wo/} (and its contents), standalone
 * {@code Main.html}, and {@code Main.api} to match.
 *
 * Registered via plugin.xml as a renameParticipant for {@code IType}.
 * Only activates when the renamed type extends WOComponent or NGComponent.
 */
public class RenameComponentParticipant extends RenameParticipant {

	/** The Java type being renamed. */
	private IType _type;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IType)) {
			return false;
		}
		_type = (IType) element;
		return isComponentClass(_type);
	}

	@Override
	public String getName() {
		return "Rename WO Component Files";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		String newName = getArguments().getNewName();
		IProject project = _type.getJavaProject().getProject();

		// Check for name conflicts
		if (RenameComponentProcessor.componentExists(project, newName)) {
			return RefactoringStatus.createWarningStatus(
					"A component named '" + newName + "' already exists in this project. "
					+ "Template files may conflict after renaming.");
		}

		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		String oldName = _type.getElementName();
		String newName = getArguments().getNewName();
		IProject project = _type.getJavaProject().getProject();

		// Compute template file renames (Java class rename is handled by LTK itself)
		return RenameComponentProcessor.computeChanges(project, oldName, newName);
	}

	/**
	 * Checks whether the given type is a WOComponent or NGComponent subclass.
	 *
	 * Uses the type hierarchy to walk supertypes and check against the known
	 * component root classes for both frameworks.
	 */
	private static boolean isComponentClass(IType type) {
		try {
			ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
			IType[] allSupertypes = hierarchy.getAllClasses();
			for (IType supertype : allSupertypes) {
				String fqn = supertype.getFullyQualifiedName();
				if (BuildProperties.NG_COMPONENT_CLASS.equals(fqn) || BuildProperties.WO_COMPONENT_CLASS.equals(fqn)) {
					return true;
				}
			}
		}
		catch (CoreException e) {
			// If we can't resolve the hierarchy, don't participate.
			// This is conservative — better to skip than to break the rename.
		}
		return false;
	}
}

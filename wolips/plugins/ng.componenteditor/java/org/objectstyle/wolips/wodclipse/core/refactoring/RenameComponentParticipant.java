package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Participates in Eclipse's Rename Type refactoring to update template files
 * and references when a WOElement/NGElement subclass is renamed.
 *
 * <p>When a user renames e.g. {@code MyWidget.java} to {@code FancyWidget.java}
 * via Refactor &gt; Rename, this participant:
 * <ul>
 *   <li>If the type is a <b>component</b> (extends WOComponent/NGComponent):
 *       renames the {@code .wo} folder, contained template files, standalone
 *       {@code .html}, and {@code .api} file to match the new name.</li>
 *   <li>For <b>any element</b> (extends WOElement/NGElement, which includes
 *       components): renames the {@code .api} file (if present) and scans
 *       all templates in the project, rewriting {@code <wo:OldName>} tags
 *       and {@code Foo : OldName &#123;&#125;} WOD entries to use the new
 *       name.</li>
 * </ul>
 *
 * <p>Registered via plugin.xml as a renameParticipant for {@code IType}.
 */
public class RenameComponentParticipant extends RenameParticipant {

	/** The Java type being renamed. */
	private IType _type;

	/**
	 * True if the type extends WOComponent/NGComponent — triggers file renames
	 * (the component's own .wo folder, template files, .api).
	 */
	private boolean _isComponent;

	/**
	 * True if the type extends WOElement/NGElement (which includes all
	 * components) — triggers cross-reference updating in other templates.
	 */
	private boolean _isElement;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IType)) {
			return false;
		}
		_type = (IType) element;
		classifyType(_type);
		return _isElement;
	}

	@Override
	public String getName() {
		return "Rename WO Element References";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		if (_isComponent) {
			String newName = getArguments().getNewName();
			IProject project = _type.getJavaProject().getProject();

			// Check for name conflicts (only relevant for components with template bundles)
			if (RenameComponentProcessor.componentExists(project, newName)) {
				return RefactoringStatus.createWarningStatus(
						"A component named '" + newName + "' already exists in this project. "
						+ "Template files may conflict after renaming.");
			}
		}

		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		String oldName = _type.getElementName();
		String newName = getArguments().getNewName();
		IProject project = _type.getJavaProject().getProject();

		CompositeChange allChanges = new CompositeChange("Rename '" + oldName + "' to '" + newName + "'");

		// 1. For components: rename the component's own template files
		//    (.wo folder, contained files, standalone .html)
		Set<IPath> ownFilePaths = null;
		if (_isComponent) {
			CompositeChange fileRenames = RenameComponentProcessor.computeChanges(project, oldName, newName);
			if (fileRenames != null) {
				allChanges.add(fileRenames);
			}
			// Collect own file paths to exclude from reference scanning
			ownFilePaths = RenameComponentProcessor.collectOwnFilePaths(project, oldName);
		}

		// 2. For all elements (including non-component WOElement subclasses):
		//    rename the .api file if one exists
		if (!_isComponent) {
			Change apiRename = RenameComponentProcessor.computeApiRename(project, oldName, newName);
			if (apiRename != null) {
				allChanges.add(apiRename);
			}
		}

		// 3. For all elements: update <wo:OldName> and WOD references in
		//    other components' templates
		CompositeChange referenceChanges = RenameComponentProcessor.computeReferenceChanges(
				project, oldName, newName, ownFilePaths);
		if (referenceChanges != null) {
			allChanges.add(referenceChanges);
		}

		if (allChanges.getChildren().length == 0) {
			return null;
		}
		return allChanges;
	}

	/**
	 * Classifies the type by walking its supertype hierarchy, setting
	 * {@link #_isElement} and {@link #_isComponent} flags.
	 *
	 * <p>A component is always also an element (WOComponent extends WOElement),
	 * but a WODynamicElement subclass is an element without being a component.
	 */
	private void classifyType(IType type) {
		_isElement = false;
		_isComponent = false;
		try {
			ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
			IType[] allSupertypes = hierarchy.getAllClasses();
			StringBuilder debugInfo = new StringBuilder();
			debugInfo.append("classifyType(").append(type.getFullyQualifiedName()).append("): supertypes=[");
			for (int i = 0; i < allSupertypes.length; i++) {
				if (i > 0) debugInfo.append(", ");
				debugInfo.append(allSupertypes[i].getFullyQualifiedName());
			}
			debugInfo.append("]");
			org.objectstyle.wolips.bindings.Activator.getDefault().log(debugInfo.toString());

			for (IType supertype : allSupertypes) {
				String fqn = supertype.getFullyQualifiedName();
				if (ParsleyProject.NG_COMPONENT_CLASS.equals(fqn) || ParsleyProject.WO_COMPONENT_CLASS.equals(fqn)) {
					_isComponent = true;
					_isElement = true;
					return; // Component implies element — no need to keep looking
				}
				if (ParsleyProject.NG_ELEMENT_CLASS.equals(fqn) || ParsleyProject.WO_ELEMENT_CLASS.equals(fqn)) {
					_isElement = true;
					// Don't return — keep looking to see if it's also a component
				}
			}
			org.objectstyle.wolips.bindings.Activator.getDefault().log(
					"classifyType result: isElement=" + _isElement + ", isComponent=" + _isComponent);
		}
		catch (CoreException e) {
			org.objectstyle.wolips.bindings.Activator.getDefault().log(
					"classifyType failed for " + type.getFullyQualifiedName() + ": " + e.getMessage());
		}
	}
}

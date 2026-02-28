package org.objectstyle.wolips.componenteditor.actions;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Context menu action on .wo folders: "Rename Component..."
 *
 * Prompts for a new component name, then renames all component files
 * (Java class, .wo folder, contained html/wod/woo files, .api file)
 * in a single undoable operation.
 *
 * <p>If a Java class exists for the component, the rename is delegated to
 * Eclipse's JDT Rename Type refactoring via {@link RenameSupport}. This
 * automatically triggers our {@link org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentParticipant
 * RenameComponentParticipant}, which handles the template file renames — so
 * both the Java class and template files are renamed atomically.
 *
 * <p>If no Java class exists (template-only component), the rename is performed
 * directly via {@link RenameComponentProcessor}.
 */
public class RenameComponentAction implements IObjectActionDelegate {

	private IFolder _woFolder;
	private Shell _shell;

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		_shell = targetPart.getSite().getShell();
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		_woFolder = null;
		if (selection instanceof IStructuredSelection) {
			Object first = ((IStructuredSelection) selection).getFirstElement();
			if (first instanceof IFolder) {
				IFolder folder = (IFolder) first;
				if (folder.getName().endsWith(".wo")) {
					_woFolder = folder;
				}
			}
		}
	}

	@Override
	public void run(IAction action) {
		if (_woFolder == null) {
			return;
		}

		String oldName = stripWoExtension(_woFolder.getName());
		IProject project = _woFolder.getProject();

		// Show rename dialog
		InputDialog dialog = new InputDialog(
				getShell(),
				"Rename Component",
				"Enter new component name:",
				oldName,
				new ComponentNameValidator(project, oldName));

		if (dialog.open() != Window.OK) {
			return;
		}

		String newName = dialog.getValue().trim();
		if (newName.equals(oldName)) {
			return;
		}

		// Find the Java type for this component
		IType javaType = findComponentJavaType(project, oldName);

		try {
			if (javaType != null) {
				// Direction 2 via LTK: rename the Java type, which triggers our
				// RenameComponentParticipant to also rename template files
				renameViaJdt(javaType, newName);
			}
			else {
				// No Java class — rename template files directly
				renameTemplateFilesOnly(project, oldName, newName);
			}
		}
		catch (CoreException e) {
			WodclipsePlugin.getDefault().log(e);
		}
	}

	/**
	 * Triggers Eclipse's Rename Type refactoring on the given Java type.
	 *
	 * Uses {@code openDialog()} rather than {@code perform()} so the rename
	 * goes through the standard refactoring wizard. This ensures proper undo
	 * support — {@code perform()} executes immediately and records an undo
	 * entry that can lose track of the compilation unit after the participant
	 * renames surrounding template files.
	 *
	 * The dialog opens pre-filled with the new name from our input dialog.
	 * The user can adjust it or click OK to proceed.
	 */
	private void renameViaJdt(IType type, String newName) throws CoreException {
		RenameSupport renameSupport = RenameSupport.create(type, newName,
				RenameSupport.UPDATE_REFERENCES);
		renameSupport.openDialog(getShell());
	}

	/**
	 * Renames template files directly when no Java class exists.
	 * Used for template-only components.
	 */
	private void renameTemplateFilesOnly(IProject project, String oldName, String newName) throws CoreException {
		Change change = RenameComponentProcessor.computeChanges(project, oldName, newName);
		if (change != null) {
			change.perform(new NullProgressMonitor());
		}
	}

	/**
	 * Finds the primary Java type for a component by name.
	 * Returns null if no Java class exists for this component.
	 */
	private IType findComponentJavaType(IProject project, String componentName) {
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
			if (result != null) {
				return result.getDotJavaType();
			}
		}
		catch (CoreException | LocateException e) {
			// Fall through — treat as no Java class
		}
		return null;
	}

	private Shell getShell() {
		if (_shell != null) {
			return _shell;
		}
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}

	/**
	 * Strips the ".wo" extension from a folder name.
	 */
	private static String stripWoExtension(String folderName) {
		if (folderName.endsWith(".wo")) {
			return folderName.substring(0, folderName.length() - 3);
		}
		return folderName;
	}

	/**
	 * Validates that a component name is a valid Java identifier and doesn't
	 * conflict with an existing component.
	 */
	private static class ComponentNameValidator implements IInputValidator {
		private final IProject _project;
		private final String _oldName;

		ComponentNameValidator(IProject project, String oldName) {
			_project = project;
			_oldName = oldName;
		}

		@Override
		public String isValid(String newText) {
			String name = newText.trim();

			if (name.isEmpty()) {
				return "Component name cannot be empty.";
			}

			if (name.equals(_oldName)) {
				return null; // Same name is technically valid (no-op)
			}

			// Must be a valid Java identifier
			if (!Character.isJavaIdentifierStart(name.charAt(0))) {
				return "Component name must start with a letter or underscore.";
			}
			for (int i = 1; i < name.length(); i++) {
				if (!Character.isJavaIdentifierPart(name.charAt(i))) {
					return "Component name contains invalid character: '" + name.charAt(i) + "'";
				}
			}

			// Check for conflicts with existing components
			if (RenameComponentProcessor.componentExists(_project, name)) {
				return "A component named '" + name + "' already exists in this project.";
			}

			return null;
		}
	}
}

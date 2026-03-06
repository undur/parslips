package org.objectstyle.wolips.componenteditor.actions;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Context menu action that converts standalone templates (single .html
 * files) to bundle templates (.wo folders).
 *
 * <p>This is the reverse of {@link ConvertBundleToInlineAction}. Since
 * standalone templates already use inline binding syntax, no HTML rewriting
 * is needed &mdash; the conversion simply:
 * <ol>
 *   <li>Creates a {@code ComponentName.wo/} folder next to the HTML file
 *   <li>Moves the HTML file into the new folder
 *   <li>Creates an empty {@code .wod} file in the folder
 * </ol>
 *
 * <p>The action is registered on {@code .html} files and filters out files
 * that are already inside a {@code .wo} folder (those are already bundle templates).
 * Supports multi-selection.
 *
 * @see ConvertBundleToInlineAction
 */
public class ConvertInlineToBundleAction implements IObjectActionDelegate {

	private ISelection _selection;
	private Shell _shell;

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		_shell = targetPart.getSite().getShell();
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		_selection = selection;
	}

	@Override
	public void run(IAction action) {
		if (!(_selection instanceof IStructuredSelection)) {
			return;
		}

		// Collect all standalone HTML files (skip any already inside .wo folders)
		List<IFile> htmlFiles = new ArrayList<>();
		Object[] selectedObjects = ((IStructuredSelection) _selection).toArray();
		for (Object obj : selectedObjects) {
			if (obj instanceof IFile) {
				IFile file = (IFile) obj;
				if ("html".equals(file.getFileExtension()) && !isInsideWoFolder(file)) {
					htmlFiles.add(file);
				}
			}
		}

		if (htmlFiles.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Convert to Bundle Template",
					"No standalone templates found to convert.\n\n"
					+ "(HTML files already inside .wo folders are excluded.)");
			return;
		}

		// Confirm when converting many files at once
		if (htmlFiles.size() > 1) {
			boolean proceed = MessageDialog.openConfirm(getShell(), "Convert to Bundle Template",
					"Convert " + htmlFiles.size() + " standalone templates to bundle templates?\n\n"
					+ "This will create a .wo folder for each template, move the HTML file inside, "
					+ "and create an empty .wod file.");
			if (!proceed) {
				return;
			}
		}

		// Convert each file
		List<String> errors = new ArrayList<>();
		int convertedCount = 0;
		IProgressMonitor monitor = new NullProgressMonitor();

		for (IFile htmlFile : htmlFiles) {
			try {
				convertToBundle(htmlFile, monitor);
				convertedCount++;
			}
			catch (Exception e) {
				errors.add(htmlFile.getName() + ": " + e.getMessage());
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		// Show summary if there were errors
		if (!errors.isEmpty()) {
			StringBuilder message = new StringBuilder();
			message.append("Converted " + convertedCount + " of " + htmlFiles.size() + " templates.\n\n");
			message.append("Errors:\n");
			for (String error : errors) {
				message.append("  \u2022 " + error + "\n");
			}
			MessageDialog.openWarning(getShell(), "Convert to Bundle Template", message.toString());
		}
	}

	/**
	 * Converts a single standalone template to a bundle template.
	 *
	 * <p>Steps:
	 * <ol>
	 *   <li>Derive the component name from the HTML filename (minus extension)
	 *   <li>Create {@code ComponentName.wo/} folder in the same directory
	 *   <li>Move the HTML file into the folder
	 *   <li>Create an empty {@code ComponentName.wod} file in the folder
	 * </ol>
	 *
	 * @param htmlFile the standalone template to convert
	 * @param monitor progress monitor
	 */
	private static void convertToBundle(IFile htmlFile, IProgressMonitor monitor) throws CoreException {
		String componentName = stripExtension(htmlFile.getName());
		IContainer parent = htmlFile.getParent();

		// Create the .wo folder
		IFolder woFolder = parent.getFolder(new org.eclipse.core.runtime.Path(componentName + ".wo"));
		if (woFolder.exists()) {
			throw new IllegalStateException(
					woFolder.getProjectRelativePath() + " already exists");
		}
		woFolder.create(true, true, monitor);

		// Move the HTML file into the .wo folder
		IPath targetPath = woFolder.getFullPath().append(htmlFile.getName());
		htmlFile.move(targetPath, true, monitor);

		// Create an empty .wod file
		IFile wodFile = woFolder.getFile(componentName + ".wod");
		wodFile.create(new ByteArrayInputStream(new byte[0]), true, monitor);
	}

	/**
	 * Returns true if the file is inside a .wo folder (i.e. already a bundle template).
	 */
	private static boolean isInsideWoFolder(IFile file) {
		IContainer parent = file.getParent();
		return parent != null && parent.getName().endsWith(".wo");
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	private Shell getShell() {
		if (_shell != null) {
			return _shell;
		}
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}
}

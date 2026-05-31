package org.objectstyle.wolips.componenteditor.actions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.bindings.wod.IWodModel;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.preferences.WodFormattingPreferences;
import org.objectstyle.wolips.wodclipse.core.refactoring.ConvertBundleToInlineTransformer;
import org.objectstyle.wolips.wodclipse.core.refactoring.ConvertBundleToInlineTransformer.ConversionResult;
import org.objectstyle.wolips.wodclipse.core.util.WodModelUtils;

/**
 * Context menu action that converts bundle templates (.wo folders) to
 * standalone templates (single .html files with inline bindings).
 *
 * <p>Handles two scenarios:
 * <ul>
 *   <li><b>Right-click on .wo folders:</b> Converts the selected bundle templates
 *       directly. Supports multi-selection ({@code enablesFor="*"}).
 *   <li><b>Right-click on a regular folder:</b> Recursively finds all bundle
 *       templates inside and converts them all.
 * </ul>
 *
 * <p>For each bundle template, the conversion:
 * <ol>
 *   <li>Reads the HTML template and WOD file from the .wo folder
 *   <li>Replaces {@code <webobject name="X">} tags with inline
 *       {@code <wo:Type binding="value">} syntax using the WOD definitions
 *   <li>Creates the converted HTML file alongside the .wo folder (one level up)
 *   <li>Deletes the .wo folder
 * </ol>
 *
 * <p>If any WOD entries are missing, a warning dialog lets the user choose
 * to proceed with partial conversion or cancel.
 *
 * @see ConvertBundleToInlineTransformer
 */
public class ConvertBundleToInlineAction implements IObjectActionDelegate {

	private ISelection _selection;
	private Shell _shell;

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		_shell = targetPart.getSite().getShell();
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		_selection = selection;
		if (action != null && selection instanceof IStructuredSelection) {
			// Disable the "Convert All" action if the selection is a .wo folder
			// (the direct conversion action handles that case)
			IStructuredSelection structured = (IStructuredSelection) selection;
			if (structured.size() == 1) {
				Object first = structured.getFirstElement();
				if (first instanceof IFolder) {
					IFolder folder = (IFolder) first;
					if (folder.getName().endsWith(".wo") && "ng.componenteditor.convertAllBundlesToInlineAction".equals(action.getId())) {
						action.setEnabled(false);
					}
				}
			}
		}
	}

	@Override
	public void run(IAction action) {
		if (!(_selection instanceof IStructuredSelection)) {
			return;
		}

		// Collect all .wo folders to convert
		List<IFolder> woFolders = new ArrayList<>();
		Object[] selectedObjects = ((IStructuredSelection) _selection).toArray();
		for (Object obj : selectedObjects) {
			if (obj instanceof IFolder) {
				IFolder folder = (IFolder) obj;
				if (folder.getName().endsWith(".wo")) {
					woFolders.add(folder);
				}
				else {
					// Regular folder — find all .wo folders recursively
					collectWoFolders(folder, woFolders);
				}
			}
		}

		if (woFolders.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Convert to Standalone Template",
					"No bundle templates (.wo folders) found to convert.");
			return;
		}

		// Confirm when converting many components at once
		if (woFolders.size() > 1) {
			boolean proceed = MessageDialog.openConfirm(getShell(), "Convert to Standalone Template",
					"Convert " + woFolders.size() + " bundle templates to standalone templates?\n\n"
					+ "This will replace <webobject> tags with inline <wo:> syntax, "
					+ "move HTML files out of the .wo folders, and delete the .wo folders.");
			if (!proceed) {
				return;
			}
		}

		// Convert each bundle
		List<String> errors = new ArrayList<>();
		int convertedCount = 0;
		IProgressMonitor monitor = new NullProgressMonitor();

		for (IFolder woFolder : woFolders) {
			try {
				convertBundle(woFolder, monitor);
				convertedCount++;
			}
			catch (Exception e) {
				String componentName = stripWoExtension(woFolder.getName());
				errors.add(componentName + ": " + e.getMessage());
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		// Show summary if there were errors
		if (!errors.isEmpty()) {
			StringBuilder message = new StringBuilder();
			message.append("Converted " + convertedCount + " of " + woFolders.size() + " components.\n\n");
			message.append("Errors:\n");
			for (String error : errors) {
				message.append("  \u2022 " + error + "\n");
			}
			MessageDialog.openWarning(getShell(), "Convert to Standalone Template", message.toString());
		}
	}

	/**
	 * Converts a single bundle template (.wo folder) to a standalone template.
	 *
	 * @param woFolder the .wo folder to convert
	 * @param monitor progress monitor
	 */
	private void convertBundle(IFolder woFolder, IProgressMonitor monitor) throws Exception {
		String componentName = stripWoExtension(woFolder.getName());

		// Find the HTML file inside the .wo folder
		IFile htmlFile = findFileWithExtension(woFolder, "html");
		if (htmlFile == null) {
			throw new IllegalStateException("No .html file found in " + woFolder.getName());
		}

		// Read HTML content
		String htmlContent = readFileContent(htmlFile);

		// Find and parse the WOD file (optional — template may have no WOD references)
		IFile wodFile = findFileWithExtension(woFolder, "wod");
		Map<String, IWodElement> wodElements = new HashMap<>();
		if (wodFile != null && wodFile.exists()) {
			String wodContent = readFileContent(wodFile);
			if (wodContent != null && !wodContent.trim().isEmpty()) {
				IWodModel wodModel = WodModelUtils.createWodModel(wodFile, new Document(wodContent));
				for (IWodElement element : wodModel.getElements()) {
					wodElements.put(element.getElementName(), element);
				}
			}
		}

		// Get inline binding prefix/suffix from project settings
		String bindingPrefix = "$";
		String bindingSuffix = "";
		ParsleyProject parsleyProject = (ParsleyProject) woFolder.getProject().getAdapter(ParsleyProject.class);
		if (parsleyProject != null) {
			BuildProperties buildProperties = parsleyProject.getBuildProperties();
			if (buildProperties != null) {
				bindingPrefix = buildProperties.getInlineBindingPrefix();
				bindingSuffix = buildProperties.getInlineBindingSuffix();
			}
		}

		// Read the "spaces around equals" formatting preference
		boolean spacesAroundEquals = WodFormattingPreferences.spacesAroundEquals();

		// Transform HTML
		ConversionResult result = ConvertBundleToInlineTransformer.convert(
				htmlContent, wodElements, bindingPrefix, bindingSuffix, spacesAroundEquals);

		// Show warnings for missing WOD entries and let user decide
		if (!result.getWarnings().isEmpty()) {
			StringBuilder message = new StringBuilder();
			message.append("Warnings for " + componentName + ":\n\n");
			for (String warning : result.getWarnings()) {
				message.append("  \u2022 " + warning + "\n");
			}
			message.append("\nProceed with partial conversion?");

			boolean proceed = MessageDialog.openQuestion(getShell(),
					"Convert to Standalone Template", message.toString());
			if (!proceed) {
				return;
			}
		}

		// Check for name collision at the target location
		IFolder parentFolder = (IFolder) woFolder.getParent();
		IFile targetFile = parentFolder.getFile(componentName + ".html");
		if (targetFile.exists()) {
			throw new IllegalStateException(
					targetFile.getProjectRelativePath() + " already exists");
		}

		// Create the new HTML file at the parent level
		byte[] bytes = result.getHtml().getBytes(StandardCharsets.UTF_8);
		targetFile.create(new ByteArrayInputStream(bytes), true, monitor);

		// Delete the .wo folder
		woFolder.delete(true, monitor);
	}

	/**
	 * Recursively collects all .wo folders inside the given folder.
	 */
	private static void collectWoFolders(IFolder folder, List<IFolder> result) {
		try {
			for (IResource member : folder.members()) {
				if (member instanceof IFolder) {
					IFolder child = (IFolder) member;
					if (child.getName().endsWith(".wo")) {
						result.add(child);
					}
					else {
						collectWoFolders(child, result);
					}
				}
			}
		}
		catch (CoreException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

	/**
	 * Finds the first file with the given extension inside a folder.
	 * Returns null if not found.
	 */
	private static IFile findFileWithExtension(IFolder folder, String extension) throws CoreException {
		for (IResource member : folder.members()) {
			if (member instanceof IFile) {
				IFile file = (IFile) member;
				String fileExtension = file.getFileExtension();
				if (extension.equals(fileExtension)) {
					return file;
				}
			}
		}
		return null;
	}

	/**
	 * Reads the entire content of an IFile as a UTF-8 string.
	 */
	private static String readFileContent(IFile file) throws Exception {
		try (InputStream is = file.getContents()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String stripWoExtension(String folderName) {
		if (folderName.endsWith(".wo")) {
			return folderName.substring(0, folderName.length() - 3);
		}
		return folderName;
	}

	private Shell getShell() {
		if (_shell != null) {
			return _shell;
		}
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}
}

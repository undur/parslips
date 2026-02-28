package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;

/**
 * Computes the set of resource changes needed to rename a component from
 * oldName to newName within a project.
 *
 * Handles:
 * <ul>
 *   <li>Renaming .wo folders (e.g. Main.wo -> NewName.wo)</li>
 *   <li>Renaming files inside .wo folders (Main.html -> NewName.html, etc.)</li>
 *   <li>Renaming standalone .html templates (ng-objects style)</li>
 *   <li>Renaming the .api file</li>
 * </ul>
 *
 * Does NOT handle:
 * <ul>
 *   <li>Renaming the Java class (Direction 1 relies on LTK handling that;
 *       Direction 2 triggers LTK which handles it)</li>
 *   <li>Updating cross-project references (deferred to a future phase)</li>
 * </ul>
 *
 * Both {@link RenameComponentParticipant} and {@link RenameComponentAction}
 * delegate to this class for computing file-level changes.
 */
public class RenameComponentProcessor {

	/**
	 * Computes all resource-level changes needed to rename a component's template
	 * files from oldName to newName. Does NOT include the Java class rename —
	 * callers are responsible for that (either via LTK or directly).
	 *
	 * <p>Child files inside .wo folders are renamed explicitly before the folder
	 * itself is renamed. The existing WodParserCacheInvalidator also auto-renames
	 * children on folder rename, but that mechanism uses asyncExec and doesn't
	 * fire reliably during LTK refactoring transactions.
	 *
	 * @param project the project containing the component
	 * @param oldName the current component name (without extension)
	 * @param newName the desired new component name (without extension)
	 * @return a CompositeChange containing all file/folder renames, or null if
	 *         no template files were found
	 */
	public static CompositeChange computeChanges(IProject project, String oldName, String newName) throws CoreException {
		LocalizedComponentsLocateResult locateResult;
		try {
			locateResult = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, oldName);
		}
		catch (LocateException e) {
			throw new CoreException(new org.eclipse.core.runtime.Status(
					org.eclipse.core.runtime.IStatus.ERROR,
					"ng.componenteditor",
					"Failed to locate component '" + oldName + "'", e));
		}

		if (locateResult == null || !locateResult.hasContent()) {
			return null;
		}

		List<Change> changes = new ArrayList<>();

		// 1. Rename files inside .wo folders BEFORE renaming the folders themselves.
		//    RenameResourceChange captures the path at creation time, so child paths
		//    must be computed while the folder still has its old name.
		IFolder[] woFolders = locateResult.getComponents();
		for (IFolder woFolder : woFolders) {
			if (woFolder.exists()) {
				addChildFileRenames(woFolder, oldName, newName, changes);
			}
		}

		// 2. Rename the .wo folders themselves
		for (IFolder woFolder : woFolders) {
			if (woFolder.exists()) {
				changes.add(new RenameResourceChange(woFolder.getFullPath(), newName + ".wo"));
			}
		}

		// 3. Rename standalone HTML template (ng-objects style — not inside .wo folder)
		if (woFolders.length == 0) {
			IFile htmlFile = locateResult.getFirstHtmlFile();
			if (htmlFile != null && htmlFile.exists()) {
				String htmlName = htmlFile.getName();
				String newHtmlName = computeNewFileName(htmlName, oldName, newName);
				if (newHtmlName != null) {
					changes.add(new RenameResourceChange(htmlFile.getFullPath(), newHtmlName));
				}
			}
		}

		// 4. Rename the .api file (lives alongside the .wo folder, not inside it)
		IFile apiFile = locateResult.getDotApi();
		if (apiFile != null && apiFile.exists()) {
			changes.add(new RenameResourceChange(apiFile.getFullPath(), newName + ".api"));
		}

		if (changes.isEmpty()) {
			return null;
		}

		CompositeChange composite = new CompositeChange("Rename component '" + oldName + "' to '" + newName + "'");
		for (Change change : changes) {
			composite.add(change);
		}
		return composite;
	}

	/**
	 * Renames files inside a .wo folder that match the old component name.
	 * For example, inside Main.wo: Main.html -> NewName.html, Main.wod -> NewName.wod, etc.
	 */
	private static void addChildFileRenames(IFolder woFolder, String oldName, String newName, List<Change> changes) throws CoreException {
		IResource[] members = woFolder.members();
		for (IResource member : members) {
			if (member.getType() == IResource.FILE) {
				String fileName = member.getName();
				String newFileName = computeNewFileName(fileName, oldName, newName);
				if (newFileName != null) {
					changes.add(new RenameResourceChange(member.getFullPath(), newFileName));
				}
			}
		}
	}

	/**
	 * Computes the new file name by replacing the old component name prefix with
	 * the new one. Returns null if the file name doesn't start with the old name.
	 *
	 * Examples:
	 *   computeNewFileName("Main.html", "Main", "NewName") -> "NewName.html"
	 *   computeNewFileName("Main.wod", "Main", "NewName") -> "NewName.wod"
	 *   computeNewFileName("unrelated.txt", "Main", "NewName") -> null
	 */
	private static String computeNewFileName(String fileName, String oldName, String newName) {
		// The file name should be OldName.ext or OldName.wo.html
		if (fileName.startsWith(oldName + ".")) {
			String suffix = fileName.substring(oldName.length());
			return newName + suffix;
		}
		return null;
	}

	/**
	 * Checks whether a component with the given name already exists in the project.
	 *
	 * @return true if a component with newName already exists
	 */
	public static boolean componentExists(IProject project, String name) {
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, name);
			return result != null && result.hasContent();
		}
		catch (CoreException | LocateException e) {
			return false;
		}
	}

	/**
	 * Locates the .wo folder for a component by name in the given project.
	 *
	 * @return the first .wo folder found, or null
	 */
	public static IFolder findWoFolder(IProject project, String componentName) {
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
			if (result != null) {
				IFolder[] folders = result.getComponents();
				if (folders.length > 0) {
					return folders[0];
				}
			}
		}
		catch (CoreException | LocateException e) {
			// fall through
		}
		return null;
	}
}

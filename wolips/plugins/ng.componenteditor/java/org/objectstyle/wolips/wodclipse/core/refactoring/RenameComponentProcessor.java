package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.objectstyle.wolips.componenteditor.actions.RenameComponentAction;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;

/**
 * Computes the set of changes needed to rename a WOElement/WOComponent.
 *
 * <p>Two levels of changes:
 * <ul>
 *   <li><b>File renames</b> ({@link #computeChanges}): renames .wo folders,
 *       contained template files, standalone .html templates, and .api files.
 *       Only applicable for WOComponent/NGComponent subclasses that have
 *       template bundles.</li>
 *   <li><b>Reference updates</b> ({@link #computeReferenceChanges}): scans all
 *       templates in the project for {@code <wo:OldName>} tags and
 *       {@code Foo : OldName &#123; &#125;} WOD entries, rewriting them to use
 *       the new name. Applicable for any WOElement/NGElement subclass.</li>
 * </ul>
 *
 * Both {@link RenameComponentParticipant} and {@link RenameComponentAction}
 * delegate to this class for computing changes.
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

	/**
	 * Computes a rename change for just the .api file of an element.
	 *
	 * <p>This is used for non-component WOElement subclasses, which don't have
	 * template bundles but can still have .api files declaring their bindings.
	 * Components handle .api renames as part of {@link #computeChanges}.
	 *
	 * @return a RenameResourceChange, or null if no .api file exists
	 */
	public static Change computeApiRename(IProject project, String oldName, String newName) throws CoreException {
		LocalizedComponentsLocateResult locateResult;
		try {
			locateResult = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, oldName);
		}
		catch (LocateException e) {
			return null;
		}

		if (locateResult == null) {
			return null;
		}

		IFile apiFile = locateResult.getDotApi();
		if (apiFile != null && apiFile.exists()) {
			return new RenameResourceChange(apiFile.getFullPath(), newName + ".api");
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Element reference detection (used by rename refactoring and Usages tab)
	// -----------------------------------------------------------------------

	/**
	 * The regex pattern for matching a component name in an HTML inline binding
	 * tag. Matches {@code <wo:Name} and {@code </wo:Name} with a case-insensitive
	 * {@code wo:} prefix and a case-sensitive component name.
	 *
	 * <p>The lookahead ensures we don't match a prefix of a longer name —
	 * the name must be followed by whitespace, {@code >}, {@code /}, or end
	 * of string.
	 */
	private static Pattern htmlElementPattern(String componentName) {
		return Pattern.compile(
				"(?i:</?wo:)" + Pattern.quote(componentName) + "(?=[\\s>/$])", 0);
	}

	/**
	 * The regex pattern for matching a component name in a WOD element type
	 * declaration. Matches {@code : ComponentName &#123;} with optional
	 * whitespace around the name.
	 */
	private static Pattern wodElementPattern(String componentName) {
		return Pattern.compile(
				":\\s*" + Pattern.quote(componentName) + "\\s*\\{");
	}

	/**
	 * Returns whether the given HTML content contains an inline binding
	 * reference to the named component (e.g. {@code <wo:ComponentName>}).
	 *
	 * <p>Used by the Usages tab to find which components reference a given
	 * element type, and by the rename refactoring to detect references that
	 * need updating.
	 *
	 * @param content the HTML file content
	 * @param componentName the component name to search for (case-sensitive)
	 * @return true if at least one reference is found
	 */
	public static boolean htmlContainsElementReference(String content, String componentName) {
		return htmlElementPattern(componentName).matcher(content).find();
	}

	/**
	 * Counts the number of element usages in the given HTML content.
	 * Only counts opening tags ({@code <wo:ComponentName>} and self-closing
	 * {@code <wo:ComponentName />}), not closing tags, so each logical
	 * usage of the element is counted once.
	 *
	 * @param content the HTML file content
	 * @param componentName the component name to search for (case-sensitive)
	 * @return the number of element usages found
	 */
	public static int htmlCountElementReferences(String content, String componentName) {
		// Only match opening tags: <wo:Name — exclude </wo:Name closing tags
		Pattern openingOnly = Pattern.compile(
				"(?i:<wo:)" + Pattern.quote(componentName) + "(?=[\\s>/$])", 0);
		int count = 0;
		Matcher matcher = openingOnly.matcher(content);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/**
	 * Returns whether the given WOD content contains an element type
	 * declaration referencing the named component (e.g. {@code Foo : ComponentName &#123; &#125;}).
	 *
	 * @param content the WOD file content
	 * @param componentName the component name to search for (case-sensitive)
	 * @return true if at least one reference is found
	 */
	public static boolean wodContainsElementReference(String content, String componentName) {
		return wodElementPattern(componentName).matcher(content).find();
	}

	/**
	 * Counts the number of element type declarations referencing the named
	 * component in the given WOD content. Each {@code : ComponentName &#123;}
	 * declaration counts as one reference.
	 *
	 * @param content the WOD file content
	 * @param componentName the component name to search for (case-sensitive)
	 * @return the number of references found
	 */
	public static int wodCountElementReferences(String content, String componentName) {
		int count = 0;
		Matcher matcher = wodElementPattern(componentName).matcher(content);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	// -----------------------------------------------------------------------
	// Offset-only scanning (shared by refactoring and search)
	// -----------------------------------------------------------------------

	/**
	 * Finds all HTML inline binding tag references to the named element type
	 * and returns their character offsets.
	 *
	 * <p>Only matches opening tags ({@code <wo:Name} and self-closing
	 * {@code <wo:Name />}), not closing tags, so each logical usage is
	 * reported once — matching the behavior of {@link #htmlCountElementReferences}.
	 *
	 * @param content the HTML file content
	 * @param elementName the element type name to find
	 * @return list of {@code [offset, length]} pairs for the element name
	 */
	public static List<int[]> findHtmlElementOffsets(String content, String elementName) {
		// Only match opening tags: <wo:Name — exclude </wo:Name closing tags
		Pattern openingOnly = Pattern.compile(
				"(?i:<wo:)" + Pattern.quote(elementName) + "(?=[\\s>/$])", 0);
		Matcher matcher = openingOnly.matcher(content);
		List<int[]> offsets = new ArrayList<>();
		while (matcher.find()) {
			int nameStart = matcher.end() - elementName.length();
			offsets.add(new int[] { nameStart, elementName.length() });
		}
		return offsets;
	}

	/**
	 * Finds all WOD element type declarations referencing the named element
	 * type and returns their character offsets.
	 *
	 * <p>Matches {@code : ElementName &#123;} declarations in WOD content.
	 *
	 * @param content the WOD file content
	 * @param elementName the element type name to find
	 * @return list of {@code [offset, length]} pairs for the element name
	 */
	public static List<int[]> findWodElementOffsets(String content, String elementName) {
		Matcher matcher = wodElementPattern(elementName).matcher(content);
		List<int[]> offsets = new ArrayList<>();
		while (matcher.find()) {
			String matchText = matcher.group();
			int nameOffset = matchText.indexOf(elementName);
			int nameStart = matcher.start() + nameOffset;
			offsets.add(new int[] { nameStart, elementName.length() });
		}
		return offsets;
	}

	// -----------------------------------------------------------------------
	// Cross-reference updating: rewrite element type references in templates
	// -----------------------------------------------------------------------

	/**
	 * Scans all templates in the project for references to oldName and creates
	 * text edits to rewrite them to newName.
	 *
	 * <p>Finds references in two forms:
	 * <ul>
	 *   <li>Inline binding tags in HTML: {@code <wo:OldName ...>} and
	 *       {@code </wo:OldName>}</li>
	 *   <li>WOD element type declarations: {@code Foo : OldName &#123; &#125;}</li>
	 * </ul>
	 *
	 * <p>Skips files that belong to the component being renamed (its own .wo
	 * folder and standalone template), since those files are being physically
	 * renamed by {@link #computeChanges} and would conflict with text edits.
	 *
	 * @param project the project to scan
	 * @param oldName the current element type name
	 * @param newName the new element type name
	 * @param ownFilePaths paths of files belonging to the renamed component
	 *        itself (to skip), or null to skip none
	 * @return a CompositeChange containing all text edits, or null if no
	 *         references were found
	 */
	public static CompositeChange computeReferenceChanges(IProject project, String oldName, String newName, Set<IPath> ownFilePaths) throws CoreException {
		List<Change> changes = new ArrayList<>();

		project.accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				// Skip derived resources (build output, bin directories)
				if (resource.isDerived()) {
					return false;
				}

				if (resource.getType() != IResource.FILE) {
					return true;
				}

				IFile file = (IFile) resource;

				// Skip the component's own files — they're being renamed at
				// the resource level by computeChanges()
				if (ownFilePaths != null && ownFilePaths.contains(file.getFullPath())) {
					return false;
				}

				String extension = file.getFileExtension();
				if (extension == null) {
					return false;
				}

				try {
					if ("html".equalsIgnoreCase(extension)) {
						TextFileChange change = createHtmlReferenceChange(file, oldName, newName);
						if (change != null) {
							changes.add(change);
						}
					}
					else if ("wod".equalsIgnoreCase(extension)) {
						TextFileChange change = createWodReferenceChange(file, oldName, newName);
						if (change != null) {
							changes.add(change);
						}
					}
				}
				catch (IOException e) {
					// Log but don't fail the entire refactoring for one unreadable file
					org.objectstyle.wolips.bindings.Activator.getDefault().log(
							"Failed to scan " + file.getFullPath() + " for element type references", e);
				}

				return false;
			}
		});

		if (changes.isEmpty()) {
			return null;
		}

		CompositeChange composite = new CompositeChange("Update '" + oldName + "' references to '" + newName + "'");
		for (Change change : changes) {
			composite.add(change);
		}
		return composite;
	}

	/**
	 * Creates a TextFileChange for an HTML template file, replacing all
	 * {@code <wo:OldName>} and {@code </wo:OldName>} occurrences with the
	 * new element type name.
	 *
	 * <p>The {@code wo:} prefix is matched case-insensitively (the parser
	 * accepts both {@code wo:} and {@code WO:}), but the element type name
	 * is matched case-sensitively since element types are case-sensitive.
	 *
	 * @return a TextFileChange, or null if no references were found
	 */
	private static TextFileChange createHtmlReferenceChange(IFile file, String oldName, String newName) throws CoreException, IOException {
		String content = readFileContent(file);
		if (content == null) {
			return null;
		}

		Matcher matcher = htmlElementPattern(oldName).matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (matcher.find()) {
			// The match includes the <wo: or </wo: prefix — we only want to
			// replace the element type name portion at the end of the match
			int nameStart = matcher.end() - oldName.length();
			edits.add(new ReplaceEdit(nameStart, oldName.length(), newName));
		}

		if (edits.isEmpty()) {
			return null;
		}

		TextFileChange change = new TextFileChange("Update element type in " + file.getName(), file);
		MultiTextEdit multiEdit = new MultiTextEdit();
		for (ReplaceEdit edit : edits) {
			multiEdit.addChild(edit);
		}
		change.setEdit(multiEdit);
		return change;
	}

	/**
	 * Creates a TextFileChange for a WOD file, replacing all element type
	 * declarations that reference the old name.
	 *
	 * <p>WOD format: {@code ElementName : ElementType &#123; ... &#125;}
	 * <br>We match {@code : OldName} followed by optional whitespace and
	 * {@code &#123;}, replacing only the type name portion.
	 *
	 * @return a TextFileChange, or null if no references were found
	 */
	private static TextFileChange createWodReferenceChange(IFile file, String oldName, String newName) throws CoreException, IOException {
		String content = readFileContent(file);
		if (content == null) {
			return null;
		}

		Matcher matcher = wodElementPattern(oldName).matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (matcher.find()) {
			// Find the exact position of oldName within the match
			String matchText = matcher.group();
			int nameOffset = matchText.indexOf(oldName);
			int nameStart = matcher.start() + nameOffset;
			edits.add(new ReplaceEdit(nameStart, oldName.length(), newName));
		}

		if (edits.isEmpty()) {
			return null;
		}

		TextFileChange change = new TextFileChange("Update element type in " + file.getName(), file);
		MultiTextEdit multiEdit = new MultiTextEdit();
		for (ReplaceEdit edit : edits) {
			multiEdit.addChild(edit);
		}
		change.setEdit(multiEdit);
		return change;
	}

	/**
	 * Collects the file paths of all files belonging to a component, so they
	 * can be excluded from reference scanning (they're being renamed at the
	 * resource level, not edited).
	 *
	 * @return a set of full paths (IPath) for the component's own files,
	 *         or an empty set if the component can't be located
	 */
	public static Set<IPath> collectOwnFilePaths(IProject project, String componentName) {
		Set<IPath> paths = new HashSet<>();
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
			if (result == null) {
				return paths;
			}

			// Add all files inside .wo folders
			for (IFolder woFolder : result.getComponents()) {
				if (woFolder.exists()) {
					paths.add(woFolder.getFullPath());
					for (IResource member : woFolder.members()) {
						paths.add(member.getFullPath());
					}
				}
			}

			// Add standalone HTML template
			IFile htmlFile = result.getFirstHtmlFile();
			if (htmlFile != null && htmlFile.exists()) {
				paths.add(htmlFile.getFullPath());
			}

			// Add .api file
			IFile apiFile = result.getDotApi();
			if (apiFile != null && apiFile.exists()) {
				paths.add(apiFile.getFullPath());
			}
		}
		catch (CoreException | LocateException e) {
			// Best effort — return whatever we collected
		}
		return paths;
	}

	/**
	 * Reads the full content of an IFile as a String.
	 *
	 * <p>Public so callers like the Usages tab and {@link RenameBindingProcessor}
	 * can reuse it.
	 *
	 * @return the file content, or null if the file can't be read
	 */
	public static String readFileContent(IFile file) throws CoreException, IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(file.getContents(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			char[] buffer = new char[8192];
			int read;
			while ((read = reader.read(buffer)) != -1) {
				sb.append(buffer, 0, read);
			}
			return sb.toString();
		}
	}
}

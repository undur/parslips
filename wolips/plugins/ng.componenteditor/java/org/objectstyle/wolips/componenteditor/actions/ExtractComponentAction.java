package org.objectstyle.wolips.componenteditor.actions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditorPart;
import org.objectstyle.wolips.templateeditor.TemplateEditor;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Editor action: extracts the selected HTML from the template into a new
 * WO component.
 *
 * <p>When triggered (via Edit &gt; Refactor &gt; Extract Component... or
 * {@code Cmd+2, E}):
 * <ol>
 *   <li>Prompts for a component name via an input dialog</li>
 *   <li>Creates a new {@code .wo} component (folder + .html + .wod + .woo + .java)
 *       alongside the current component</li>
 *   <li>Sets the new component's template to the selected HTML</li>
 *   <li>Replaces the selection in the parent template with
 *       {@code <wo:NewComponentName/>}</li>
 *   <li>Opens the new component for editing</li>
 * </ol>
 *
 * <p>The selected HTML is placed as-is — no automatic binding detection.
 * The user wires up bindings manually afterward.
 *
 * <p>Follows the same pattern as {@link ConvertInlineToWodAction}: extends
 * {@link AbstractTemplateAction} to get access to the template editor and
 * its selection.
 */
public class ExtractComponentAction extends AbstractTemplateAction {

	@Override
	public void run(IAction action) {
		try {
			ComponentEditorPart componentEditorPart = getComponentEditorPart();
			if (componentEditorPart == null) {
				return;
			}

			IEditorPart activeEditorPart = componentEditorPart.getActiveEditor();
			TemplateEditor templateEditor = getTemplateEditor();
			if (templateEditor == null || activeEditorPart != templateEditor) {
				return;
			}

			// Get the current text selection
			ITextSelection selection = (ITextSelection) templateEditor.getSourceEditor()
					.getSelectionProvider().getSelection();
			String selectedText = selection.getText();

			if (selectedText == null || selectedText.trim().isEmpty()) {
				MessageDialog.openInformation(getShell(),
						"Extract Component",
						"Please select some HTML to extract into a new component.");
				return;
			}

			// Get project context from the parser cache
			WodParserCache cache = templateEditor.getSourceEditor().getParserCache();
			IProject project = cache.getProject();

			// Show the name input dialog
			InputDialog dialog = new InputDialog(
					getShell(),
					"Extract Component",
					"Enter the name for the new component:",
					"",
					new NewComponentNameValidator(project));

			if (dialog.open() != Window.OK) {
				return;
			}

			String newComponentName = dialog.getValue().trim();

			// Determine the target folder for the new component.
			// Place it alongside the current component's .wo folder.
			IContainer woFolder = cache.getWoFolder();
			IContainer targetFolder = determineTargetFolder(woFolder, project);

			if (targetFolder == null || !targetFolder.exists()) {
				MessageDialog.openError(getShell(),
						"Extract Component",
						"Could not determine where to create the new component.");
				return;
			}

			// Determine the superclass and package for the new component
			String superclassName = ParsleyProject.getComponentClass(project);
			String packageName = determinePackageName(cache);

			// Dedent the selected HTML: strip the common leading whitespace
			// from all lines so the new component's template starts at column 0.
			// For example, if every line has at least 3 tabs of indentation,
			// those 3 tabs are removed from all lines.
			String dedentedHtml = dedent(selectedText);

			// Create the new component files
			createComponent(project, targetFolder, newComponentName,
					packageName, superclassName, dedentedHtml);

			// Replace the selection in the parent template with the new
			// component tag. We want the tag placed at the indentation level
			// of the first content line, not at the raw selection start
			// (which may be in the middle of a blank line or leading whitespace).
			IDocument document = templateEditor.getSourceEditor().getViewer().getDocument();
			int replaceOffset = selection.getOffset();
			int replaceLength = selection.getLength();

			// Calculate leading whitespace in the selection — the gap between
			// the selection start and the first non-whitespace character.
			// We preserve that whitespace and place the tag right after it.
			String leadingWhitespace = getLeadingWhitespace(selectedText);
			String replacement = leadingWhitespace + "<wo:" + newComponentName + "/>";

			document.replace(replaceOffset, replaceLength, replacement);

			// Open the new component's HTML file in the editor
			IFolder newWoFolder = ((IFolder) targetFolder).getFolder(newComponentName + ".wo");
			IFile newHtmlFile = newWoFolder.getFile(newComponentName + ".html");
			if (newHtmlFile.exists()) {
				IWorkbenchPage page = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				IDE.openEditor(page, newHtmlFile, true);
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
			MessageDialog.openError(getShell(),
					"Extract Component",
					"Failed to extract component: " + e.getMessage());
		}
	}

	/**
	 * Determines the folder where the new component should be created.
	 *
	 * <p>Uses the parent folder of the current component's .wo folder.
	 * For standalone HTML templates (not inside .wo), falls back to
	 * the file's parent directory. As a last resort, searches for a
	 * "components" folder under src/main/.
	 */
	private IContainer determineTargetFolder(IContainer woFolder, IProject project) {
		if (woFolder != null) {
			// The .wo folder's parent is the components directory
			IContainer parent = woFolder.getParent();
			if (parent != null && parent.exists()) {
				return parent;
			}
		}

		// Fallback: search for a "components" folder in the project
		return findComponentsFolder(project);
	}

	/**
	 * Searches for a "components" folder in the given project.
	 *
	 * @see ParsleyProject#findComponentsFolder(IProject)
	 */
	private IFolder findComponentsFolder(IProject project) {
		return ParsleyProject.findComponentsFolder(project);
	}

	/**
	 * Determines the Java package name for the new component.
	 *
	 * <p>Uses the same package as the current component's Java class, if it has
	 * one. Falls back to empty string (default package) if the type can't be
	 * resolved.
	 */
	private String determinePackageName(WodParserCache cache) {
		try {
			IType componentType = cache.getComponentType();
			if (componentType != null) {
				String fqn = componentType.getFullyQualifiedName();
				int lastDot = fqn.lastIndexOf('.');
				if (lastDot > 0) {
					return fqn.substring(0, lastDot);
				}
			}
		}
		catch (Exception e) {
			// Fall through to default package
		}
		return "";
	}

	/**
	 * Creates a new WO component by writing each file directly.
	 *
	 * <p>We generate the files ourselves rather than using
	 * {@code ComponentEngine} because Velocity (which ComponentEngine
	 * depends on) requires {@code commons-lang} which isn't available
	 * in our OSGi class loader context. The files are simple enough
	 * to generate inline.
	 *
	 * <p>Creates:
	 * <ul>
	 *   <li>{@code ComponentName.wo/ComponentName.html} — the selected HTML</li>
	 *   <li>{@code ComponentName.wo/ComponentName.wod} — empty WOD file</li>
	 *   <li>{@code ComponentName.wo/ComponentName.woo} — encoding metadata</li>
	 *   <li>{@code ComponentName.java} — stub Java class in the source folder</li>
	 * </ul>
	 */
	private void createComponent(IProject project, IContainer targetFolder,
			String componentName, String packageName, String superclassName,
			String selectedHtml) throws Exception {

		IProgressMonitor monitor = new NullProgressMonitor();
		IFolder componentFolder = ((IFolder) targetFolder).getFolder(componentName + ".wo");

		// Find the Java source folder for the .java file
		IJavaProject javaProject = JavaCore.create(project);
		IPath javaPath = findJavaSourcePath(javaProject);
		if (packageName != null && packageName.length() > 0) {
			javaPath = javaPath.append(packageName.replace('.', '/'));
		}

		// Ensure the .wo folder exists
		prepareFolder(componentFolder, monitor);

		// Ensure the Java source folder exists
		IPath projectRelativeJavaPath = javaPath.removeFirstSegments(
				project.getLocation().segmentCount());
		IFolder javaSourceFolder = project.getFolder(projectRelativeJavaPath);
		prepareFolder(javaSourceFolder, monitor);

		// 1. Create the .html file with the extracted HTML
		createFile(componentFolder.getFile(componentName + ".html"),
				selectedHtml, monitor);

		// 2. Create an empty .wod file
		createFile(componentFolder.getFile(componentName + ".wod"),
				"\n", monitor);

		// 3. Create the .woo file with encoding metadata
		String wooContent = "{\n\t\"WebObjects Release\" = \"WebObjects 5.0\";\n\tencoding = \"UTF-8\";\n}\n";
		createFile(componentFolder.getFile(componentName + ".woo"),
				wooContent, monitor);

		// 4. Create the .java file in the source folder
		String javaContent = generateJavaSource(componentName, packageName, superclassName);
		createFile(javaSourceFolder.getFile(componentName + ".java"),
				javaContent, monitor);

		// Refresh to make Eclipse aware of the new files
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}

	/**
	 * Generates the Java source for a new component class.
	 *
	 * <p>Derives the context class from the superclass: ng-objects components
	 * use {@code NGContext}, WebObjects components use {@code WOContext}.
	 */
	private String generateJavaSource(String componentName, String packageName,
			String superclassName) {

		StringBuilder sb = new StringBuilder();

		// Package declaration
		if (packageName != null && !packageName.isEmpty()) {
			sb.append("package ").append(packageName).append(";\n\n");
		}

		// Determine the context class from the superclass
		// ng-objects uses NGContext, WebObjects uses WOContext
		boolean isNg = superclassName.startsWith("ng.");
		String contextClass = isNg
				? "ng.appserver.NGContext"
				: "com.webobjects.appserver.WOContext";
		String contextShortName = isNg ? "NGContext" : "WOContext";

		// Import the context class
		sb.append("import ").append(contextClass).append(";\n");

		// Import the superclass if it's in a different package
		String superclassShortName = superclassName;
		String superclassPackage = null;
		int lastDot = superclassName.lastIndexOf('.');
		if (lastDot > 0) {
			superclassPackage = superclassName.substring(0, lastDot);
			superclassShortName = superclassName.substring(lastDot + 1);
			// Only import if in a different package
			if (!superclassPackage.equals(packageName)) {
				sb.append("import ").append(superclassName).append(";\n");
			}
		}

		sb.append("\n");
		sb.append("public class ").append(componentName);
		sb.append(" extends ").append(superclassShortName).append(" {\n");
		sb.append("\tpublic ").append(componentName).append("(").append(contextShortName).append(" context) {\n");
		sb.append("\t\tsuper(context);\n");
		sb.append("\t}\n");
		sb.append("}\n");

		return sb.toString();
	}

	/**
	 * Creates a file with the given content. If the file already exists,
	 * it is overwritten.
	 */
	private void createFile(IFile file, String content, IProgressMonitor monitor)
			throws CoreException {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				content.getBytes(StandardCharsets.UTF_8));
		if (file.exists()) {
			file.setContents(stream, true, false, monitor);
		}
		else {
			file.create(stream, true, monitor);
		}
	}

	/**
	 * Removes the common leading whitespace from all lines of the given text.
	 *
	 * <p>Finds the minimum indentation (tabs/spaces) across all non-empty
	 * lines, then strips that prefix from every line. This way the extracted
	 * HTML starts at column 0 in the new component's template, regardless
	 * of how deeply it was nested in the parent.
	 *
	 * <p>Empty lines (or whitespace-only lines) are preserved but not
	 * considered when calculating the minimum indent — otherwise a blank
	 * line would always make the minimum zero.
	 */
	private String dedent(String text) {
		String[] lines = text.split("\n", -1);

		// Find the minimum indentation across all non-empty lines
		int minIndent = Integer.MAX_VALUE;
		for (String line : lines) {
			if (line.trim().isEmpty()) {
				continue; // skip blank lines
			}
			int indent = 0;
			while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
				indent++;
			}
			minIndent = Math.min(minIndent, indent);
		}

		// Nothing to strip
		if (minIndent == 0 || minIndent == Integer.MAX_VALUE) {
			return text;
		}

		// Strip the common prefix from each line
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			if (lines[i].length() >= minIndent) {
				sb.append(lines[i].substring(minIndent));
			}
			else {
				// Line is shorter than minIndent (blank/whitespace-only) — emit empty
				sb.append(lines[i].trim());
			}
		}
		return sb.toString();
	}

	/**
	 * Returns the leading whitespace characters (spaces and tabs) from the
	 * beginning of the text, stopping at the first non-whitespace character
	 * or newline. This is used to preserve the indentation level when
	 * replacing extracted HTML with the new component tag.
	 *
	 * <p>For example, if the selection is {@code "\t\t\t<div>..."}, this
	 * returns {@code "\t\t\t"}, so the replacement tag gets placed at the
	 * same indent level as the original first line of content.
	 */
	private String getLeadingWhitespace(String text) {
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (ch == ' ' || ch == '\t') {
				i++;
			}
			else {
				break;
			}
		}
		return text.substring(0, i);
	}

	/**
	 * Finds the first Java source folder in the project.
	 * This is where the new component's .java file will be placed
	 * (within the appropriate package subfolder).
	 */
	private IPath findJavaSourcePath(IJavaProject javaProject) throws Exception {
		IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
		for (IPackageFragmentRoot root : roots) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				return root.getCorrespondingResource().getLocation();
			}
		}
		// Fallback: project root
		return javaProject.getProject().getLocation();
	}

	/**
	 * Recursively creates folders if they don't exist.
	 * Same pattern as {@code WOComponentCreator.prepareFolder()}.
	 */
	private void prepareFolder(IFolder folder, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists()) {
			IContainer parent = folder.getParent();
			if (parent instanceof IFolder) {
				prepareFolder((IFolder) parent, monitor);
			}
			folder.create(false, true, monitor);
		}
	}

	/**
	 * Returns the active shell, falling back to the workbench window's shell.
	 */
	private Shell getShell() {
		Shell shell = Display.getCurrent().getActiveShell();
		if (shell != null) {
			return shell;
		}
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}

	/**
	 * Validates that a new component name is a valid Java identifier and
	 * doesn't conflict with an existing component.
	 */
	private static class NewComponentNameValidator implements IInputValidator {
		private final IProject _project;

		NewComponentNameValidator(IProject project) {
			_project = project;
		}

		@Override
		public String isValid(String newText) {
			String name = newText.trim();

			if (name.isEmpty()) {
				return "Component name cannot be empty.";
			}

			// Must start with an uppercase letter (convention for component names)
			if (!Character.isJavaIdentifierStart(name.charAt(0))) {
				return "Component name must start with a letter or underscore.";
			}

			// Check all characters are valid Java identifier parts
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

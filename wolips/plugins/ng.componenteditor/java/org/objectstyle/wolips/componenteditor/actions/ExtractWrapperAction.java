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
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Editor action: extracts the "wrapping" HTML around the selection into a new
 * wrapper component, leaving the selection in place wrapped by the new component.
 *
 * <p>This is the inverse of "Extract Component." Instead of extracting the
 * <em>selected</em> HTML into a new component, it extracts everything
 * <em>except</em> the selection — the surrounding "chrome" — into a new
 * wrapper component that uses {@code <wo:content />} as a placeholder for
 * the wrapped content.
 *
 * <p>When triggered:
 * <ol>
 *   <li>Prompts for a component name via an input dialog</li>
 *   <li>Creates a new component whose template is the <em>entire current
 *       template</em> with the selected region replaced by
 *       {@code <wo:content />}</li>
 *   <li>Replaces the <em>entire</em> current template with
 *       {@code <wo:NewComponentName>} + selection + {@code </wo:NewComponentName>}</li>
 *   <li>Opens the new wrapper component for editing</li>
 * </ol>
 *
 * <p>Typical use case: extracting a page layout (nav, header, footer) into a
 * reusable wrapper, leaving only the page-specific content in the original
 * component.
 *
 * @see ExtractComponentAction
 */
public class ExtractWrapperAction extends AbstractTemplateAction {

	private static final String DIALOG_TITLE = "Extract Wrapper";

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
						DIALOG_TITLE,
						"Please select the content that should remain in this component.\n"
						+ "Everything else will be extracted into the new wrapper.");
				return;
			}

			// Get project context from the parser cache
			WodParserCache cache = templateEditor.getSourceEditor().getParserCache();
			IProject project = cache.getProject();

			// Show the name input dialog
			InputDialog dialog = new InputDialog(
					getShell(),
					DIALOG_TITLE,
					"Enter the name for the new wrapper component:",
					"",
					new NewComponentNameValidator(project));

			if (dialog.open() != Window.OK) {
				return;
			}

			String newComponentName = dialog.getValue().trim();

			// Determine target folder for the new component
			IContainer woFolder = cache.getWoFolder();
			IContainer targetFolder = determineTargetFolder(woFolder, project);

			if (targetFolder == null || !targetFolder.exists()) {
				MessageDialog.openError(getShell(),
						DIALOG_TITLE,
						"Could not determine where to create the new component.");
				return;
			}

			// Determine the superclass and package for the new component
			String superclassName = ParsleyProject.getComponentClass(project);
			String packageName = determinePackageName(cache);

			// Determine whether this is an ng-objects project (standalone templates)
			boolean isNG = isNGProject(project);

			// Build the wrapper template: the entire current template, but with
			// the selection replaced by <wo:content />
			IDocument document = templateEditor.getSourceEditor().getViewer().getDocument();
			String fullTemplate = document.get();
			int selectionOffset = selection.getOffset();
			int selectionLength = selection.getLength();

			// Calculate the indentation at the selection point so we can
			// indent <wo:content /> to match
			String indent = getLineIndent(fullTemplate, selectionOffset);
			String wrapperTemplate = fullTemplate.substring(0, selectionOffset)
					+ indent + "<wo:content />\n"
					+ fullTemplate.substring(selectionOffset + selectionLength);

			// Create the new wrapper component
			IFile newHtmlFile = createComponent(project, targetFolder,
					newComponentName, packageName, superclassName,
					wrapperTemplate, isNG);

			// Replace the entire current template with the selection wrapped
			// in the new component tag
			String wrappedContent = "<wo:" + newComponentName + ">\n"
					+ selectedText + "\n"
					+ "</wo:" + newComponentName + ">\n";
			document.replace(0, fullTemplate.length(), wrappedContent);

			// Open the new wrapper component's HTML file in the editor
			if (newHtmlFile != null && newHtmlFile.exists()) {
				IWorkbenchPage page = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				IDE.openEditor(page, newHtmlFile, true);
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
			MessageDialog.openError(getShell(),
					DIALOG_TITLE,
					"Failed to extract wrapper: " + e.getMessage());
		}
	}

	/**
	 * Returns the indentation (leading whitespace) of the line containing the
	 * given offset. Used to match the {@code <wo:content />} indent to the
	 * selection's line.
	 *
	 * <p>Scans backward from the offset to find the start of the line, then
	 * forward to collect whitespace characters.
	 */
	private String getLineIndent(String text, int offset) {
		// Find the start of the line
		int lineStart = offset;
		while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
			lineStart--;
		}

		// Collect leading whitespace
		int i = lineStart;
		while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
			i++;
		}
		return text.substring(lineStart, i);
	}

	/**
	 * Determines the folder where the new component should be created.
	 *
	 * <p>For bundle templates, the woFolder is the {@code .wo} folder itself
	 * (e.g. {@code components/Main.wo/}), so we go up one level to get the
	 * components directory. For standalone templates, the woFolder is the
	 * file's parent directory, which <em>is</em> the components directory —
	 * use it directly.
	 */
	private IContainer determineTargetFolder(IContainer woFolder, IProject project) {
		if (woFolder != null) {
			if (woFolder.getName().endsWith(".wo")) {
				// Bundle template: go up from the .wo folder to get the
				// components directory
				IContainer parent = woFolder.getParent();
				if (parent != null && parent.exists()) {
					return parent;
				}
			}
			else if (woFolder.exists()) {
				// Standalone template: woFolder is already the components
				// directory (the parent of the .html file)
				return woFolder;
			}
		}
		return ParsleyProject.findComponentsFolder(project);
	}

	/**
	 * Determines the Java package name for the new component.
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
	 * Returns whether the given project is an ng-objects project.
	 */
	private boolean isNGProject(IProject project) {
		ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
		return pp != null && pp.isNGProject();
	}

	/**
	 * Creates a new component with the given template content.
	 *
	 * <p>For ng-objects projects, creates a standalone {@code .html} file.
	 * For WebObjects projects, creates a {@code .wo} bundle (folder + .html
	 * + .wod + .woo). In both cases, creates a {@code .java} file in the
	 * Java source folder.
	 *
	 * @return the created HTML file (for opening in the editor)
	 */
	private IFile createComponent(IProject project, IContainer targetFolder,
			String componentName, String packageName, String superclassName,
			String templateHtml, boolean isNG) throws Exception {

		IProgressMonitor monitor = new NullProgressMonitor();

		// Find and prepare the Java source folder
		IJavaProject javaProject = JavaCore.create(project);
		IPath javaPath = findJavaSourcePath(javaProject);
		if (packageName != null && packageName.length() > 0) {
			javaPath = javaPath.append(packageName.replace('.', '/'));
		}
		IPath projectRelativeJavaPath = javaPath.removeFirstSegments(
				project.getLocation().segmentCount());
		IFolder javaSourceFolder = project.getFolder(projectRelativeJavaPath);
		prepareFolder(javaSourceFolder, monitor);

		// Create the .java file in the source folder
		String javaContent = generateJavaSource(componentName, packageName, superclassName);
		createFile(javaSourceFolder.getFile(componentName + ".java"),
				javaContent, monitor);

		IFile htmlFile;

		if (isNG) {
			// Standalone template: just a .html file alongside other templates
			htmlFile = ((IFolder) targetFolder).getFile(componentName + ".html");
			createFile(htmlFile, templateHtml, monitor);
		}
		else {
			// Bundle template: .wo folder with .html, .wod, .woo
			IFolder componentFolder = ((IFolder) targetFolder).getFolder(componentName + ".wo");
			prepareFolder(componentFolder, monitor);

			htmlFile = componentFolder.getFile(componentName + ".html");
			createFile(htmlFile, templateHtml, monitor);

			createFile(componentFolder.getFile(componentName + ".wod"),
					"\n", monitor);

			String wooContent = "{\n\t\"WebObjects Release\" = \"WebObjects 5.0\";\n\tencoding = \"UTF-8\";\n}\n";
			createFile(componentFolder.getFile(componentName + ".woo"),
					wooContent, monitor);
		}

		// Refresh to make Eclipse aware of the new files
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);

		return htmlFile;
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
	 * Finds the first Java source folder in the project.
	 */
	private IPath findJavaSourcePath(IJavaProject javaProject) throws Exception {
		IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
		for (IPackageFragmentRoot root : roots) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				return root.getCorrespondingResource().getLocation();
			}
		}
		return javaProject.getProject().getLocation();
	}

	/**
	 * Recursively creates folders if they don't exist.
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
	 * Returns the active shell.
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

			if (!Character.isJavaIdentifierStart(name.charAt(0))) {
				return "Component name must start with a letter or underscore.";
			}

			for (int i = 1; i < name.length(); i++) {
				if (!Character.isJavaIdentifierPart(name.charAt(i))) {
					return "Component name contains invalid character: '" + name.charAt(i) + "'";
				}
			}

			if (RenameComponentProcessor.componentExists(_project, name)) {
				return "A component named '" + name + "' already exists in this project.";
			}

			return null;
		}
	}
}

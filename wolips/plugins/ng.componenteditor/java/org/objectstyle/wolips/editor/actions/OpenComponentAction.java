package org.objectstyle.wolips.editor.actions;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate2;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.editor.component.ComponentEditor;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.locate.result.ElementDescriptor;

public class OpenComponentAction extends Action implements IWorkbenchWindowActionDelegate, IActionDelegate2 {
	private Object _selectedObject;

	public OpenComponentAction() {
		setText("Open Component");
		setDescription("Open a Component");
		setToolTipText("Open a Component");
	}

	public void run() {
		runWithEvent(null);
	}

	public void runWithEvent(Event e) {
		IJavaProject javaProject = null;
		if (_selectedObject instanceof IJavaElement) {
			IJavaElement javaElement = (IJavaElement) _selectedObject;
			javaProject = javaElement.getJavaProject();
		} else if (_selectedObject instanceof IResource) {
			IProject project = ((IResource) _selectedObject).getProject();
			javaProject = JavaCore.create(project);
		} else {
			IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			if (editorPart != null) {
				IEditorInput editorInput = editorPart.getEditorInput();
				if (editorInput instanceof IFileEditorInput) {
					IFile file = ((IFileEditorInput) editorInput).getFile();
					javaProject = JavaCore.create(file.getProject());
				}
			}
		}

		Shell parent = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		if (javaProject == null) {
			ErrorDialog.openError(parent, "Select a Project", "You must have selected an object within a project before using Open Component.", Status.OK_STATUS);
		}
		else {
			SelectionDialog dialog = new WOElementSelectionDialog(parent, javaProject, PlatformUI.getWorkbench().getProgressService());
			dialog.setTitle("Open Component");
			dialog.setMessage("Select a Component to Open");

			int result = dialog.open();
			if (result != IDialogConstants.OK_ID) {
				return;
			}
			Object[] typeNames = dialog.getResult();
			if (typeNames != null && typeNames.length > 0) {
				for (int i = 0; i < typeNames.length; i++) {
					String typeName = (String) typeNames[i];
					OpenComponentAction.openComponentWithTypeNamed(javaProject, typeName);
				}
			}
		}
	}

	public void run(IAction action) {
		run();
	}

	public void dispose() {
		// DO NOTHING
	}

	public void init(IWorkbenchWindow window) {
		// DO NOTHING
	}

	public void selectionChanged(IAction action, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			_selectedObject = ((IStructuredSelection) selection).getFirstElement();
		}
	}

	public void runWithEvent(IAction action, Event event) {
		runWithEvent(event);
	}

	public void init(IAction action) {
		// DO NOTHING
	}
	
	public static void openComponentWithTypeNamed(IJavaProject javaProject, String typeName) {
		openComponentWithTypeNamed(javaProject, typeName, -1);
	}

	/**
	 * Resolves a component type name to an {@link IType}, accepting either a
	 * fully-qualified name or a bare simple name.
	 *
	 * <p>{@link IJavaProject#findType(String)} only resolves fully-qualified
	 * names (or types in the default package). Callers within Eclipse (the
	 * selection dialog) always pass fully-qualified names, but the browser
	 * exception page sends a bare component name (e.g. {@code Main}), which
	 * {@code findType} can't resolve — it silently returns null, so the open
	 * appears to do nothing. So when the direct lookup fails on a simple name,
	 * fall back to an exact-match type search (the same machinery the selection
	 * dialog uses), which finds the type regardless of package.
	 *
	 * @return the resolved type, or null if none was found
	 */
	private static IType resolveType(IJavaProject javaProject, String typeName) throws Exception {
		// Direct lookup: works for fully-qualified names and default-package types.
		IType type = javaProject.findType(typeName);
		if (type != null) {
			return type;
		}

		// A dotted name that findType couldn't resolve isn't going to be found
		// by a simple-name search either.
		if (typeName.indexOf('.') != -1) {
			return null;
		}

		// Simple name: search the project for an exact match. The collector
		// resolves matches to ITypes for us.
		final TypeNameCollector typeNameCollector = new TypeNameCollector(javaProject, false);
		BindingReflectionUtils.findMatchingElementClassNames(typeName, SearchPattern.R_EXACT_MATCH, typeNameCollector, new NullProgressMonitor());

		if (typeNameCollector.isEmpty()) {
			return null;
		}

		return typeNameCollector.getTypeForClassName(typeNameCollector.firstTypeName());
	}

	/**
	 * Opens the named component in the Parsley component editor, optionally
	 * revealing a specific line in its HTML template.
	 *
	 * <p>The source position the exception page sends us is always an offset
	 * into the <em>HTML</em> template — that's the source the template parser
	 * walks — regardless of whether the component is a standalone {@code .html}
	 * or a {@code .wo} bundle (HTML + WOD). So when a line is requested we open
	 * the HTML file and reveal the line in the HTML tab.
	 *
	 * <p>When <em>no</em> line is requested we keep the plain "Open Component"
	 * behaviour: open the WOD file for a bundle template (the bindings are
	 * usually what you want to see), or the HTML file for a standalone one.
	 *
	 * @param javaProject the project to resolve the component type in
	 * @param typeName    the component (type) name
	 * @param lineNumber  the 1-based line to reveal in the HTML template, or
	 *                    {@code <= 0} to just open the component without navigating
	 */
	public static void openComponentWithTypeNamed(IJavaProject javaProject, String typeName, int lineNumber) {
		try {
			IType type = resolveType(javaProject, typeName);
			if (type != null) {
				JavaUI.openInEditor(type, true, true);
				IResource underlyingResource = type.getUnderlyingResource();
				if (underlyingResource instanceof IFile) {
					ElementDescriptor descriptor = ElementDescriptor.forFile((IFile) underlyingResource);
					if (descriptor != null) {
						final IFile wodFile = descriptor.getWodFile();
						final IFile htmlFile = descriptor.getHtmlFile();
						final boolean revealLine = lineNumber > 0 && htmlFile != null;

						// With a line to reveal, open the HTML (the source the
						// line refers to). Otherwise keep "Open Component"
						// behaviour: WOD for a bundle, HTML for a standalone.
						final IFile templateFile;
						if (revealLine) {
							templateFile = htmlFile;
						}
						else {
							templateFile = wodFile != null ? wodFile : htmlFile;
						}

						if (templateFile != null) {
							IEditorPart editorPart = WorkbenchUtilities.open(templateFile, EditorsPlugin.ComponentEditorID);

							if (revealLine && editorPart instanceof ComponentEditor) {
								revealHtmlLine((ComponentEditor) editorPart, lineNumber);
							}
						}
					}
				}
			}
		} catch (Throwable e1) {
			ComponenteditorPlugin.getDefault().log(e1);
		}
	}

	/**
	 * Switches the component editor to its HTML view and reveals the given
	 * 1-based line in the HTML template.
	 *
	 * <p><b>Why we target {@link ComponentEditor#getTemplateEditor()} directly
	 * rather than {@code getActiveEditor()}.</b> In the component editor the
	 * HTML and WOD editors are <em>not</em> separate workbench pages — they sit
	 * side by side in a SashForm on a single page ({@code HtmlWodTab}), and
	 * {@code switchToHtml()}/{@code switchToWod()} merely flip a boolean flag.
	 * So {@code getActiveEditor()} (which is keyed on page index) can't tell us
	 * which of the two is "active" and tends to hand back the WOD editor. We
	 * therefore reach straight for the {@link TemplateEditor} — the HTML
	 * {@link ITextEditor} — via the editor's accessor.
	 *
	 * <p>The reveal is <em>deferred</em> onto the UI event queue with
	 * {@link Display#asyncExec}. When the component was just opened, its inner
	 * editors are created during this same event but their controls and
	 * documents aren't realized until layout completes — calling
	 * {@code selectAndReveal} immediately would no-op against an editor that
	 * isn't ready yet. Deferring lets the open finish first.
	 *
	 * <p>Best-effort throughout: any failure leaves the editor open on the
	 * template without navigating.
	 */
	private static void revealHtmlLine(ComponentEditor editor, int lineNumber) {
		// Defer until after the just-opened editor has laid out its inner parts.
		Display.getDefault().asyncExec(() -> {
			try {
				// Flip the composite to show the HTML side (the WOD usually has
				// focus on open, especially for the empty .wod files most
				// inline-syntax components carry).
				editor.switchToHtml();

				// The HTML editor specifically — not getActiveEditor(), which
				// can't distinguish the side-by-side HTML/WOD editors.
				ITextEditor templateEditor = editor.getTemplateEditor();
				if (templateEditor == null) {
					ComponenteditorPlugin.getDefault().log(new IllegalStateException(
							"No template (HTML) editor available; cannot reveal line " + lineNumber));
					return;
				}

				IDocumentProvider provider = templateEditor.getDocumentProvider();
				if (provider == null) {
					return;
				}
				IDocument document = provider.getDocument(templateEditor.getEditorInput());
				if (document == null) {
					return;
				}

				// 1-based line numbers from the browser; IDocument lines are 0-based.
				int lineStart = document.getLineOffset(lineNumber - 1);
				templateEditor.selectAndReveal(lineStart, 0);
			}
			catch (BadLocationException x) {
				// Line doesn't exist (template out of sync with what threw) —
				// the file is open, which is still useful.
			}
			catch (Throwable t) {
				ComponenteditorPlugin.getDefault().log(t);
			}
		});
	}
}

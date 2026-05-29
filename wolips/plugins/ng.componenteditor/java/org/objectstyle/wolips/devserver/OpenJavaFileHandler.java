package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Opens a Java source file in Eclipse at a given line number — the feature
 * that lets you click a stack-trace line on a browser exception page and land
 * on the offending source.
 *
 * <p>Request parameters (from Wonder's {@code ERXExceptionPage}):
 * <ul>
 *   <li>{@code className} — fully-qualified class name (required)</li>
 *   <li>{@code lineNumber} — 1-based line number (required)</li>
 *   <li>{@code app} — application/project name (optional; see below)</li>
 * </ul>
 *
 * <p>The {@code app} parameter scopes the type lookup to a single project when
 * present. Unlike the original WOLips handler — which <em>required</em>
 * {@code app} and gave up if the project name didn't match — we treat it as a
 * hint: if {@code app} names a real Java project, we search there; otherwise
 * (or if the type isn't found there) we fall back to searching every open Java
 * project in the workspace. This is more forgiving when the browser's notion
 * of the app name doesn't match the Eclipse project name.
 */
class OpenJavaFileHandler implements DevServerHandler {

	@Override
	public void handle(Map<String, String> params) {
		final String className = params.get("className");
		final String lineNumberStr = params.get("lineNumber");
		final String appName = params.get("app");

		if (className == null || lineNumberStr == null) {
			return; // Required parameters missing — nothing to open.
		}

		final int lineNumber;
		try {
			lineNumber = Integer.parseInt(lineNumberStr);
		}
		catch (NumberFormatException e) {
			return;
		}

		Display.getDefault().asyncExec(() -> openType(className, lineNumber, appName));
	}

	private static void openType(String className, int lineNumber, String appName) {
		try {
			IType type = findType(className, appName);
			if (type == null) {
				return;
			}

			IEditorPart editorPart = JavaUI.openInEditor(type, true, true);
			if (!(editorPart instanceof ITextEditor)) {
				return;
			}

			ITextEditor editor = (ITextEditor) editorPart;
			IDocumentProvider provider = editor.getDocumentProvider();
			IDocument document = provider.getDocument(editor.getEditorInput());
			try {
				// Line numbers from stack traces are 1-based; IDocument is 0-based.
				int lineStart = document.getLineOffset(lineNumber - 1);
				editor.selectAndReveal(lineStart, 0);
			}
			catch (BadLocationException x) {
				// The line doesn't exist (e.g. source out of sync with the
				// deployed bytecode). We've at least opened the file.
			}
		}
		catch (Throwable t) {
			ComponenteditorPlugin.getDefault().log(t);
		}
	}

	/**
	 * Finds a type by fully-qualified name, preferring the named project but
	 * falling back to a workspace-wide search.
	 */
	private static IType findType(String className, String appName) throws Exception {
		// 1. Try the named project first, if given.
		if (appName != null && !appName.isEmpty()) {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(appName);
			if (project != null && project.isOpen()) {
				IJavaProject javaProject = JavaCore.create(project);
				if (javaProject != null && javaProject.exists()) {
					IType type = javaProject.findType(className);
					if (type != null) {
						return type;
					}
				}
			}
		}

		// 2. Fall back to every open Java project in the workspace.
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (!project.isOpen()) {
				continue;
			}
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) {
				continue;
			}
			IType type = javaProject.findType(className);
			if (type != null) {
				return type;
			}
		}

		return null;
	}
}

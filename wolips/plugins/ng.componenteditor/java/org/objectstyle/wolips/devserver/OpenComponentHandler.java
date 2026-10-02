package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.editor.actions.OpenComponentAction;

/**
 * Opens a component by name in the Parsley component editor.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code component} — the component (type) name (required)</li>
 *   <li>{@code lineNumber} — 1-based line in the component's HTML template to
 *       reveal (optional)</li>
 *   <li>{@code offset} — 0-based character offset into the HTML template to place
 *       the caret on precisely; takes precedence over {@code lineNumber} when given
 *       (optional). The render heat map's inspect mode sends this.</li>
 *   <li>{@code length} — characters to select from {@code offset} (optional, default
 *       0 = caret only); used to select the element's source span</li>
 *   <li>{@code app} — application/project name (optional hint)</li>
 * </ul>
 *
 * <p>As with {@link OpenJavaFileHandler}, the {@code app} parameter is treated
 * as a hint: if it names an open Java project we use it, otherwise we fall
 * back to searching every open Java project for a matching component.
 *
 * <p>The optional {@code lineNumber} lets the browser exception page deep-link
 * straight to the template line where a component render failed — the template
 * counterpart of {@link OpenJavaFileHandler}'s line navigation.
 */
class OpenComponentHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return "{\"error\":\"missing required parameter 'component'\"}";
		}
		final String appName = params.get("app");
		final int lineNumber = parseInt(params.get("lineNumber"), -1);
		// Optional precise position: a character offset into the HTML template (and an
		// optional selection length). When present it lands the caret exactly on the
		// element rather than just its line; the render heat map's inspect mode sends it.
		final int offset = parseInt(params.get("offset"), -1);
		final int length = parseInt(params.get("length"), 0);

		// syncExec, not asyncExec: the caller deserves to hear whether the component was
		// found, and only the open itself can tell. Opening an editor is quick, and the UI
		// thread keeps dispatching runnables even while a modal dialog's event loop runs,
		// so this doesn't hang behind a dialog. (The exception page's links ignore the
		// body; an agent reads it.)
		final boolean[] opened = { false };
		Display.getDefault().syncExec(() -> {
			IJavaProject javaProject = resolveProject(appName);
			if (javaProject != null && OpenComponentAction.openComponentWithTypeNamed(javaProject, componentName, lineNumber, offset, length)) {
				opened[0] = true;
				return;
			}
			// No such project, or the component isn't in it (app is only a hint): search
			// every open project, stopping at the first that has it - one editor, not one
			// per project that happens to share the name.
			for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (!project.isOpen()) {
					continue;
				}
				IJavaProject jp = JavaCore.create(project);
				if (jp != null && jp.exists() && OpenComponentAction.openComponentWithTypeNamed(jp, componentName, lineNumber, offset, length)) {
					opened[0] = true;
					return;
				}
			}
		});

		if (!opened[0]) {
			return "{\"opened\":false,\"reason\":\"no component named '" + DevServerJson.escape(componentName)
					+ "' in any open project; its project may be closed (see /status) or the name misspelled\"}";
		}
		// Success stays the plain "ok" existing callers expect.
		return null;
	}

	/**
	 * Parses an optional integer parameter, returning {@code fallback} when it's
	 * absent or not a number.
	 */
	private static int parseInt(String value, int fallback) {
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return fallback;
		}
	}

	/**
	 * @return the named project as an {@link IJavaProject} if it exists and is
	 *         open, or {@code null} to signal a workspace-wide search
	 */
	private static IJavaProject resolveProject(String appName) {
		if (appName == null || appName.isEmpty()) {
			return null;
		}
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(appName);
		if (project != null && project.isOpen()) {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null && javaProject.exists()) {
				return javaProject;
			}
		}
		return null;
	}
}

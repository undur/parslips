package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.componenteditor.actions.OpenComponentAction;

/**
 * Opens a component by name in the Parsley component editor.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code component} — the component (type) name (required)</li>
 *   <li>{@code app} — application/project name (optional hint)</li>
 * </ul>
 *
 * <p>As with {@link OpenJavaFileHandler}, the {@code app} parameter is treated
 * as a hint: if it names an open Java project we use it, otherwise we fall
 * back to searching every open Java project for a matching component.
 */
class OpenComponentHandler implements DevServerHandler {

	@Override
	public void handle(Map<String, String> params) {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return;
		}
		final String appName = params.get("app");

		Display.getDefault().asyncExec(() -> {
			IJavaProject javaProject = resolveProject(appName);
			if (javaProject != null) {
				OpenComponentAction.openComponentWithTypeNamed(javaProject, componentName);
			}
			else {
				// No specific project — try each open one until the action
				// succeeds in opening something. openComponentWithTypeNamed
				// is a no-op when the component isn't found in the project,
				// so calling it across projects is safe.
				for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
					if (!project.isOpen()) {
						continue;
					}
					IJavaProject jp = JavaCore.create(project);
					if (jp != null && jp.exists()) {
						OpenComponentAction.openComponentWithTypeNamed(jp, componentName);
					}
				}
			}
		});
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

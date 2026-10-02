package org.objectstyle.wolips.devserver;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.editor.actions.OpenComponentAction;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;

/**
 * Finding projects and components by name, shared by the component-scoped endpoints
 * ({@code /validate}, {@code /componentApi}, {@code /keypath}, {@code /find}, {@code /callers},
 * {@code /rename}, {@code /quickfix}, {@code /context}) so they all resolve a name the same way
 * and refuse with the same reasons.
 *
 * <p>The {@code project} parameter is a hint, as it has always been for {@code /validate}: the
 * named project is searched first, then every open project. A name the hinted project doesn't
 * have is therefore still found elsewhere, which is what an agent that guessed the project wrong
 * wants.
 */
final class DevServerComponents {

	private DevServerComponents() {
	}

	/** A component and the project it was found in. */
	record Found(ElementDescriptor descriptor, IJavaProject javaProject) {

		String projectName() {
			return javaProject.getProject().getName();
		}
	}

	/** The {@code project} parameter, accepting {@code app} as its older spelling. */
	static String projectParam(java.util.Map<String, String> params) {
		final String project = params.get("project");
		return project != null && !project.isEmpty() ? project : params.get("app");
	}

	/** The named project as an open Java project, or null (unknown, closed or not Java). */
	static IJavaProject javaProject(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!project.isOpen()) {
			return null;
		}
		final IJavaProject javaProject = JavaCore.create(project);
		return javaProject != null && javaProject.exists() ? javaProject : null;
	}

	/**
	 * Why a named project can't be used, as a refusal ({@code reason}, plus a {@code hint} for a
	 * closed project), or null when it's an open Java project. {@code outcome} is the response's
	 * usual payload at its empty value, so the refusal keeps the endpoint's shape.
	 */
	static String projectRefusal(String name, JsonObject outcome) {
		if (javaProject(name) != null) {
			return null;
		}
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!project.exists()) {
			return outcome.put("reason", "no project named '" + name + "' in the workspace").toString();
		}
		if (!project.isOpen()) {
			return outcome.put("reason", "project '" + name + "' is closed").put("hint", "/openProject?project=" + name).toString();
		}
		return outcome.put("reason", "project '" + name + "' is not a Java project").toString();
	}

	/**
	 * Finds a component by name: in the hinted project first, then in every open project.
	 *
	 * @return the component, or null when no open project has it
	 */
	static Found find(String componentName, String projectHint) throws Exception {
		final IJavaProject hinted = javaProject(projectHint);
		if (hinted != null) {
			final ElementDescriptor descriptor = findIn(hinted, componentName);
			if (descriptor != null) {
				return new Found(descriptor, hinted);
			}
		}
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			final IJavaProject javaProject = javaProject(project.getName());
			if (javaProject == null || javaProject.equals(hinted)) {
				continue;
			}
			final ElementDescriptor descriptor = findIn(javaProject, componentName);
			if (descriptor != null) {
				return new Found(descriptor, javaProject);
			}
		}
		return null;
	}

	/**
	 * A component's files in one project. Through its Java class first (what "Open Component"
	 * does: it resolves a simple or qualified name), then through the locate machinery, which
	 * also finds a template-only component — one with no class of its own.
	 */
	private static ElementDescriptor findIn(IJavaProject javaProject, String componentName) throws Exception {
		final ElementDescriptor viaType = OpenComponentAction.descriptorForComponent(javaProject, componentName);
		if (viaType != null && viaType.hasTemplate()) {
			return viaType;
		}
		final String simpleName = componentName.substring(componentName.lastIndexOf('.') + 1);
		final LocalizedComponentsLocateResult located = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(javaProject.getProject(), simpleName);
		if (located != null && located.getFirstHtmlFile() != null) {
			return ElementDescriptor.fromLocateResult(located, javaProject.getProject());
		}
		return viaType;
	}

	/**
	 * Why a component wasn't found, as a sentence. The cases need different fixes (open the
	 * project, fix the project name, fix the component name), so they are told apart.
	 */
	static String notFoundReason(String componentName, String projectHint) {
		if (projectHint != null && !projectHint.isEmpty()) {
			final IProject hinted = ResourcesPlugin.getWorkspace().getRoot().getProject(projectHint);
			if (!hinted.exists()) {
				return "no project named '" + projectHint + "' in the workspace, and no open project has a component named '" + componentName + "'";
			}
			if (!hinted.isOpen()) {
				return "project '" + projectHint + "' is closed (open it with /openProject?project=" + projectHint + "), and no open project has a component named '" + componentName + "'";
			}
			return "no component named '" + componentName + "' in project '" + projectHint + "' or any other open project";
		}
		return "no component named '" + componentName + "' in any open project; its project may be closed (see /status) or the name misspelled";
	}

	/** A component's files as JSON: workspace paths, absent ones omitted. */
	static JsonObject filesJson(ElementDescriptor descriptor) {
		final JsonObject files = new JsonObject();
		putPath(files, "html", descriptor.getHtmlFile());
		putPath(files, "wod", descriptor.getWodFile());
		putPath(files, "woo", descriptor.getWooFile());
		putPath(files, "api", descriptor.getApiFile());
		putPath(files, "java", descriptor.getJavaFile());
		return files;
	}

	private static void putPath(JsonObject files, String key, org.eclipse.core.resources.IFile file) {
		if (file != null && file.exists()) {
			files.put(key, file.getFullPath().toString());
		}
	}

	/** {@code bundle} (a .wo folder of html + wod) or {@code standalone} (one .html). */
	static String formatName(ElementDescriptor descriptor) {
		return descriptor.isBundle() ? "bundle" : descriptor.isStandalone() ? "standalone" : "none";
	}
}

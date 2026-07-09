package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Opens a closed workspace project — together with the workspace projects it depends on
 * (transitively, resolved by pom-walk; see {@link ProjectOpener}), because dependency
 * resolution here is Maven-workspace-level and only sees open projects. Also the fix for
 * "/refreshProject silently skips closed projects": open first, then refresh.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code project} — the project to open (required; {@code all} opens every closed
 *       project in the workspace).</li>
 *   <li>{@code related} — {@code false} to open only the named project, without its
 *       workspace dependencies (default true).</li>
 * </ul>
 */
class OpenProjectHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String name = params.get("project");
		if (name == null || name.isEmpty()) {
			return "{\"error\":\"missing required parameter 'project' (a project name, or 'all')\"}";
		}

		if ("all".equalsIgnoreCase(name)) {
			final ProjectOpener.Result result = new ProjectOpener.Result();
			for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (project.exists() && !project.isOpen()) {
					// Reuse the walker per closed project so building/settling happens once each;
					// visiting an already-opened project is a no-op.
					result.opened.addAll(ProjectOpener.openWithRelated(project).opened);
				}
			}
			return "{\"opened\":" + DevServerJson.stringArray(result.opened) + "}";
		}

		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (project == null || !project.exists()) {
			return "{\"error\":\"no project named \\\"" + DevServerJson.escape(name) + "\\\" in the workspace\"}";
		}

		if ("false".equalsIgnoreCase(params.get("related"))) {
			if (!project.isOpen()) {
				project.open(new org.eclipse.core.runtime.NullProgressMonitor());
				return "{\"opened\":[\"" + DevServerJson.escape(name) + "\"]}";
			}
			return "{\"opened\":[]}";
		}

		final ProjectOpener.Result result = ProjectOpener.openWithRelated(project);
		return "{\"opened\":" + DevServerJson.stringArray(result.opened) + "}";
	}
}
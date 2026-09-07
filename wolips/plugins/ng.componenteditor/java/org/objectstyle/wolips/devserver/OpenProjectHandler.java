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
			return "{\"error\":\"missing required parameter 'project'\"}";
		}

		// 'all' used to open every closed project in the workspace. Removed: an agent that
		// found its project closed reached for it, opened 30+ unrelated projects, and the
		// developer closed them all again - project included - later that day. Opening a
		// project with its dependency closure (below, or /launch?open=true) is the tool.
		if ("all".equalsIgnoreCase(name)) {
			return "{\"error\":\"'all' is not supported - open the project you need (its workspace dependencies come along), or use /launch?config=NAME&open=true\"}";
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
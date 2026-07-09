package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Reports a project's (or every open project's) problem markers — the programmatic
 * equivalent of glancing at Eclipse's Problems view, which external tools otherwise
 * can't do. The primary use is checking for compile errors after editing source
 * outside Eclipse, before wondering why a launch or hot-swap "didn't take".
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code project} — project name; omit for all open projects.</li>
 *   <li>{@code severity} — {@code error} (default) or {@code warning} (= warnings and up).</li>
 *   <li>{@code limit} — maximum markers per project (default 25).</li>
 * </ul>
 */
class ProblemsHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String projectName = params.get("project");
		final int severity = "warning".equalsIgnoreCase(params.get("severity")) ? IMarker.SEVERITY_WARNING : IMarker.SEVERITY_ERROR;

		int limit = 25;
		try {
			if (params.get("limit") != null) {
				limit = Math.max(1, Integer.parseInt(params.get("limit")));
			}
		}
		catch (NumberFormatException e) {
			// keep the default
		}

		final StringBuilder b = new StringBuilder();
		b.append("{\"projects\":[");
		boolean first = true;
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (projectName != null && !projectName.isEmpty() && !projectName.equalsIgnoreCase(project.getName())) {
				continue;
			}
			if (!project.isOpen()) {
				continue;
			}
			final List<WorkspaceProblems.Problem> problems = WorkspaceProblems.problems(project, severity, limit);
			if (problems.isEmpty() && projectName == null) {
				continue; // in the all-projects listing, only show projects that have something to report
			}
			if (!first) {
				b.append(',');
			}
			first = false;
			b.append("{\"project\":\"").append(DevServerJson.escape(project.getName()))
					.append("\",\"count\":").append(problems.size())
					.append(",\"problems\":").append(WorkspaceProblems.toJsonArray(problems))
					.append('}');
		}
		b.append("]}");
		return b.toString();
	}
}
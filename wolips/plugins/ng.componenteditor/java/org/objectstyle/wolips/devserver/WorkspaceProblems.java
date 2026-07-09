package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Reads a project's problem markers (the contents of Eclipse's Problems view) for the dev
 * server. Shared by {@code /problems}, the launch preflight and the refresh build report,
 * so "does this project have errors, and which?" is answered the same way everywhere.
 */
final class WorkspaceProblems {

	/** One problem marker, reduced to what an external tool needs to act on it. */
	static final class Problem {
		final String resource; // workspace-relative path
		final int line; // -1 when the marker has no line
		final String message;

		Problem(String resource, int line, String message) {
			this.resource = resource;
			this.line = line;
			this.message = message;
		}
	}

	private WorkspaceProblems() {
	}

	/** The number of ERROR-severity problem markers on the project (0 on any failure). */
	static int errorCount(IProject project) {
		return problems(project, IMarker.SEVERITY_ERROR, Integer.MAX_VALUE).size();
	}

	/**
	 * The project's problem markers at or above the given severity, up to {@code limit}.
	 * Returns an empty list on any failure — the callers degrade to "no report" rather
	 * than breaking their actual operation.
	 */
	static List<Problem> problems(IProject project, int minimumSeverity, int limit) {
		final List<Problem> result = new ArrayList<>();
		if (project == null || !project.isOpen()) {
			return result;
		}
		try {
			for (final IMarker marker : project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
				if (marker.getAttribute(IMarker.SEVERITY, -1) < minimumSeverity) {
					continue;
				}
				result.add(new Problem(
						marker.getResource() == null ? "" : marker.getResource().getProjectRelativePath().toString(),
						marker.getAttribute(IMarker.LINE_NUMBER, -1),
						String.valueOf(marker.getAttribute(IMarker.MESSAGE, ""))));
				if (result.size() >= limit) {
					break;
				}
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return result;
	}

	/** Renders problems as a JSON array of {@code {resource, line, message}} objects. */
	static String toJsonArray(List<Problem> problems) {
		final StringBuilder b = new StringBuilder(problems.size() * 96 + 2);
		b.append('[');
		for (int i = 0; i < problems.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			final Problem p = problems.get(i);
			b.append("{\"resource\":\"").append(DevServerJson.escape(p.resource))
					.append("\",\"line\":").append(p.line)
					.append(",\"message\":\"").append(DevServerJson.escape(p.message))
					.append("\"}");
		}
		b.append(']');
		return b.toString();
	}
}
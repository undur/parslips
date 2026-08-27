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
		final String source; // which tool family wrote it: parsley | java | stock | other

		Problem(String resource, int line, String message, String source) {
			this.resource = resource;
			this.line = line;
			this.message = message;
			this.source = source;
		}
	}

	/**
	 * Classifies a marker by its type into the tool family that wrote it, so a report reader can
	 * tell template findings from Java errors from legacy leftovers without guessing by message
	 * text: {@code parsley} (this plugin's typed template/apiext markers), {@code java} (JDT),
	 * {@code stock} (the untyped {@code IMarker.PROBLEM} — no living tool writes these; they're
	 * legacy-validator leftovers, purgeable via /purgeMarkers), {@code other} (anything else,
	 * e.g. WTP).
	 */
	private static String sourceOf(IMarker marker) {
		try {
			final String type = marker.getType();
			if ("ng.componenteditor.problem".equals(type)) {
				return "parsley";
			}
			if (type.startsWith("org.eclipse.jdt.")) {
				return "java";
			}
			if (IMarker.PROBLEM.equals(type)) {
				return "stock";
			}
			return "other";
		}
		catch (Exception e) {
			return "other";
		}
	}

	private WorkspaceProblems() {
	}

	/** The number of ERROR-severity problem markers on the project (0 on any failure). */
	static int errorCount(IProject project) {
		return problems(project, IMarker.SEVERITY_ERROR, Integer.MAX_VALUE).size();
	}

	/**
	 * The project's JAVA compile and build-path errors only — the ones that actually make a
	 * launch pointless. Template-validation and other tool markers are deliberately excluded:
	 * a WO app with a stale template binding launches fine, and blocking on those would make
	 * every marker a launch blocker.
	 */
	static List<Problem> javaErrors(IProject project, int limit) {
		final List<Problem> result = new ArrayList<>();
		if (project == null || !project.isOpen()) {
			return result;
		}
		try {
			final String[] types = {
					org.eclipse.jdt.core.IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
					org.eclipse.jdt.core.IJavaModelMarker.BUILDPATH_PROBLEM_MARKER,
			};
			for (final String type : types) {
				for (final IMarker marker : project.findMarkers(type, true, IResource.DEPTH_INFINITE)) {
					if (marker.getAttribute(IMarker.SEVERITY, -1) < IMarker.SEVERITY_ERROR) {
						continue;
					}
					result.add(new Problem(
							marker.getResource() == null ? "" : marker.getResource().getProjectRelativePath().toString(),
							marker.getAttribute(IMarker.LINE_NUMBER, -1),
							String.valueOf(marker.getAttribute(IMarker.MESSAGE, "")),
							"java"));
					if (result.size() >= limit) {
						return result;
					}
				}
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return result;
	}

	/**
	 * The TRUE number of problem markers at or above the given severity — independent of any
	 * listing limit, so a report's "count" can be honest while its problem list stays capped.
	 * Returns 0 on any failure, matching {@link #problems}' degrade-to-empty behavior.
	 */
	static int count(IProject project, int minimumSeverity) {
		if (project == null || !project.isOpen()) {
			return 0;
		}
		try {
			int count = 0;
			for (final IMarker marker : project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
				if (marker.getAttribute(IMarker.SEVERITY, -1) >= minimumSeverity) {
					count++;
				}
			}
			return count;
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
			return 0;
		}
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
						String.valueOf(marker.getAttribute(IMarker.MESSAGE, "")),
						sourceOf(marker)));
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
					.append(",\"source\":\"").append(DevServerJson.escape(p.source))
					.append("\",\"message\":\"").append(DevServerJson.escape(p.message))
					.append("\"}");
		}
		b.append(']');
		return b.toString();
	}
}
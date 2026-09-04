package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.editor.actions.OpenComponentAction;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.wodclipse.core.builder.WodBuilder;

/**
 * Validates a named component's template and returns the resulting problems as
 * JSON, so an external tool (or AI agent) editing templates on disk can read the
 * validation messages without opening the component in the Eclipse editor.
 *
 * <p>This fills a real gap: template validation in Parsley is editor- and
 * Java-change-driven (see {@code WodBuilder} / {@code WodParserCache}), <em>not</em>
 * build-driven. So {@code /refreshProject} recompiles Java but never validates
 * templates — a template error stays invisible until a human opens the file. This
 * endpoint triggers the same validator headlessly and reports what it found.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code component} — the component (type) name (required), e.g.
 *       {@code ASISearchPage}. Resolved the same way "Open Component" resolves it.</li>
 *   <li>{@code project} — project/app name (optional hint). If it names an open
 *       project we look there; otherwise we search every open project for a match.</li>
 * </ul>
 *
 * <p>The handler refreshes the component's files from disk first, so it reflects
 * on-disk edits even if {@code /refreshProject} wasn't called, then runs the
 * validator synchronously and reads the problem markers back. Validation touches
 * shared parser/model state, so it runs under {@link WodBuilder} on the calling
 * (request) thread — the workspace operations take their own locks.
 *
 * <h2>Response</h2>
 * <pre>
 * {
 *   "component": "ASISearchPage",
 *   "found": true,
 *   "files": ["/MyApp/.../ASISearchPage.wo/ASISearchPage.html"],
 *   "problems": [
 *     { "severity":"error", "line":17, "charStart":420, "charEnd":448,
 *       "message":"…", "file":"/MyApp/.../ASISearchPage.html" }
 *   ]
 * }
 * </pre>
 * An empty {@code problems} array means the template validated clean.
 * {@code "found": false} means the component name didn't resolve to a component in
 * any (matching) open project.
 */
class ValidateComponentHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return "{\"error\":\"missing required parameter 'component'\"}";
		}
		final String appName = params.get("app") != null ? params.get("app") : params.get("project");

		final ElementDescriptor descriptor = resolveDescriptor(componentName, appName);
		if (descriptor == null || descriptor.getHtmlFile() == null) {
			return notFoundJson(componentName, appName);
		}

		final IFile htmlFile = descriptor.getHtmlFile();
		final IFile wodFile = descriptor.getWodFile();

		// The validator keys off the .wo bundle folder for bundle templates, or the
		// HTML file itself for a standalone template (mirrors WodParserCache.validate,
		// which uses the parent .wo folder when present).
		final IContainer parent = htmlFile.getParent();
		final boolean isBundle = parent != null && "wo".equals(parent.getFileExtension());
		final IResource validationResource = isBundle ? parent : htmlFile;

		// Refresh the component's files from disk so on-disk edits are seen even if the
		// caller didn't hit /refreshProject first — this endpoint is self-contained.
		refreshFromDisk(validationResource);

		// Run the validator synchronously (not threaded) so the markers exist by the
		// time we read them back below.
		WodBuilder.validateComponent(validationResource, false, new NullProgressMonitor());

		// Collect problems from the files the validator marks (HTML always; WOD for a
		// bundle template). Markers are the same ones shown in the Problems view.
		final List<String> problems = new ArrayList<>();
		final List<String> files = new ArrayList<>();
		appendFileProblems(htmlFile, problems, files);
		if (wodFile != null && wodFile.exists()) {
			appendFileProblems(wodFile, problems, files);
		}

		return buildJson(componentName, files, problems);
	}

	/**
	 * Resolves the component name to its {@link ElementDescriptor}, honouring the
	 * optional project hint: try the named project first, then fall back to every
	 * open project until one resolves (matching the open-component semantics).
	 */
	private static ElementDescriptor resolveDescriptor(String componentName, String appName) throws Exception {
		final IJavaProject hinted = resolveProject(appName);
		if (hinted != null) {
			return OpenComponentAction.descriptorForComponent(hinted, componentName);
		}
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (!project.isOpen()) {
				continue;
			}
			final IJavaProject jp = JavaCore.create(project);
			if (jp != null && jp.exists()) {
				final ElementDescriptor descriptor = OpenComponentAction.descriptorForComponent(jp, componentName);
				if (descriptor != null && descriptor.getHtmlFile() != null) {
					return descriptor;
				}
			}
		}
		return null;
	}

	private static IJavaProject resolveProject(String appName) {
		if (appName == null || appName.isEmpty()) {
			return null;
		}
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(appName);
		if (project != null && project.isOpen()) {
			final IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null && javaProject.exists()) {
				return javaProject;
			}
		}
		return null;
	}

	private static void refreshFromDisk(IResource resource) {
		try {
			if (resource != null && resource.exists()) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			}
		}
		catch (Exception e) {
			// A failed refresh isn't fatal: validate whatever the workspace currently has.
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

	/**
	 * Reads problem markers off a file and appends each as a JSON object string;
	 * records the file path once if it carried any markers (so the response lists the
	 * files that actually had problems). We read all {@link IMarker#PROBLEM} markers
	 * (and subtypes), which covers both the template problem marker and stock HTML
	 * problem markers.
	 */
	private static void appendFileProblems(IFile file, List<String> problemsOut, List<String> filesOut) throws Exception {
		if (file == null || !file.exists()) {
			return;
		}
		final String path = file.getFullPath().toString();
		final IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
		boolean any = false;
		for (final IMarker marker : markers) {
			final int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
			final int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
			final int charStart = marker.getAttribute(IMarker.CHAR_START, -1);
			final int charEnd = marker.getAttribute(IMarker.CHAR_END, -1);
			final String message = marker.getAttribute(IMarker.MESSAGE, "");

			final StringBuilder p = new StringBuilder(160);
			p.append('{')
					.append("\"severity\":\"").append(severityName(severity)).append('"')
					.append(",\"line\":").append(line)
					.append(",\"charStart\":").append(charStart)
					.append(",\"charEnd\":").append(charEnd)
					.append(",\"message\":\"").append(DevServerJson.escape(message)).append('"')
					.append(",\"file\":\"").append(DevServerJson.escape(path)).append('"')
					.append('}');
			problemsOut.add(p.toString());
			any = true;
		}
		if (any && !filesOut.contains(path)) {
			filesOut.add(path);
		}
	}

	private static String severityName(int severity) {
		switch (severity) {
		case IMarker.SEVERITY_ERROR:
			return "error";
		case IMarker.SEVERITY_WARNING:
			return "warning";
		default:
			return "info";
		}
	}

	private static String buildJson(String componentName, List<String> files, List<String> problems) {
		final StringBuilder b = new StringBuilder(256);
		b.append('{')
				.append("\"component\":\"").append(DevServerJson.escape(componentName)).append('"')
				.append(",\"found\":true")
				.append(",\"files\":").append(DevServerJson.stringArray(files))
				.append(",\"problems\":[").append(String.join(",", problems)).append(']')
				.append('}');
		return b.toString();
	}

	/**
	 * {@code found:false} with the reason a caller can act on. Three things produce it, and
	 * they need different fixes: the hinted project is closed (open it), the hinted project
	 * doesn't exist (a typo'd name), or no open project has the component (a typo'd component
	 * name, or its project isn't in the workspace). A bare {@code found:false} sent agents
	 * chasing the wrong one.
	 */
	private static String notFoundJson(String componentName, String appName) {
		String reason;
		if (appName != null && !appName.isEmpty()) {
			final IProject hinted = ResourcesPlugin.getWorkspace().getRoot().getProject(appName);
			if (hinted == null || !hinted.exists()) {
				reason = "no project named '" + appName + "' in the workspace; searched every open project instead";
			}
			else if (!hinted.isOpen()) {
				reason = "project '" + appName + "' is CLOSED in the workspace (see projectOpen in /status); open it with /openProject?project=" + appName;
			}
			else {
				reason = "no component named '" + componentName + "' in project '" + appName + "' (or any open project)";
			}
		}
		else {
			reason = "no component named '" + componentName + "' in any open project; its project may be closed (see /status) or the name misspelled";
		}
		return "{\"component\":\"" + DevServerJson.escape(componentName) + "\",\"found\":false,\"reason\":\""
				+ DevServerJson.escape(reason) + "\",\"problems\":[]}";
	}
}

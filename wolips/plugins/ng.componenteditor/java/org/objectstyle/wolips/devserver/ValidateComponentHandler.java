package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
			return notFoundJson(componentName);
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
					.append(",\"message\":\"").append(jsonEscape(message)).append('"')
					.append(",\"file\":\"").append(jsonEscape(path)).append('"')
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
				.append("\"component\":\"").append(jsonEscape(componentName)).append('"')
				.append(",\"found\":true")
				.append(",\"files\":").append(jsonStringArray(files))
				.append(",\"problems\":[").append(String.join(",", problems)).append(']')
				.append('}');
		return b.toString();
	}

	private static String notFoundJson(String componentName) {
		return "{\"component\":\"" + jsonEscape(componentName) + "\",\"found\":false,\"problems\":[]}";
	}

	private static String jsonStringArray(List<String> values) {
		final StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append('"').append(jsonEscape(values.get(i))).append('"');
		}
		b.append(']');
		return b.toString();
	}

	/** Minimal JSON string escaping for the fields we emit (messages, paths). */
	private static String jsonEscape(String s) {
		if (s == null) {
			return "";
		}
		final StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			switch (c) {
			case '"':
				b.append("\\\"");
				break;
			case '\\':
				b.append("\\\\");
				break;
			case '\n':
				b.append("\\n");
				break;
			case '\r':
				b.append("\\r");
				break;
			case '\t':
				b.append("\\t");
				break;
			default:
				if (c < 0x20) {
					b.append(String.format("\\u%04x", (int) c));
				}
				else {
					b.append(c);
				}
			}
		}
		return b.toString();
	}
}

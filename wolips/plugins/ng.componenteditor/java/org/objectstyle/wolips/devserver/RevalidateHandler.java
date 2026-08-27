package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.objectstyle.wolips.wodclipse.core.builder.TemplateRevalidator;

/**
 * Revalidates every component template in a project — or the whole workspace — replacing all
 * existing template problem markers with freshly computed ones.
 *
 * <p>Why this exists: template validation is per-file and event-driven, so problem markers written
 * under old circumstances (an earlier plugin's severity mapping, validation racing type resolution
 * at startup) survive Java clean/rebuilds indefinitely, as phantoms. This endpoint is the bulk
 * cure: what {@code /validate} does for one component, for everything. It's also the honest way to
 * make the whole workspace's markers reflect current validation preferences.
 *
 * <p>Request parameters:
 * <ul>
 *   <li><b>project</b> — optional project name. Omit to revalidate every open project the
 *       component editor handles.</li>
 * </ul>
 *
 * <p>Runs synchronously on the request thread (the same rationale as {@code /validate}: workspace
 * operations take their own locks) and can take a while for a large workspace — minutes, not
 * seconds; size your client timeout accordingly. The response reports what was done:
 *
 * <pre>
 *   {"projects":26,"components":412,"canceled":false}
 * </pre>
 *
 * Enumeration mirrors the editor's own file-association rule: {@code .wo} bundles plus standalone
 * {@code .html} files, in projects the editor claims (see {@link TemplateRevalidator}).
 */
class RevalidateHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String projectName = params.get("project");

		final List<IProject> projects;
		if (projectName != null && !projectName.isEmpty()) {
			final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			if (project == null || !project.isOpen()) {
				return "{\"error\":\"no open project named '" + DevServerJson.escape(projectName) + "'\"}";
			}
			projects = List.of(project);
		}
		else {
			projects = TemplateRevalidator.allHandledProjects();
		}

		final TemplateRevalidator.Summary summary = TemplateRevalidator.revalidate(projects, new NullProgressMonitor());

		return "{\"projects\":" + summary.projectCount
				+ ",\"components\":" + summary.componentCount
				+ ",\"canceled\":" + summary.canceled + "}";
	}
}

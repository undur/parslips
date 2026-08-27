package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.objectstyle.wolips.wodclipse.core.builder.StaleMarkerPurge;

/**
 * Bring out your dead: deletes orphaned problem markers no tool will ever clear — the leavings of
 * the removed legacy validators (Rhino/JTidy, issue #4), which wrote markers of the exact stock
 * {@code IMarker.PROBLEM} type onto js/css/html/xml files. Nothing revalidates those files, so the
 * markers are permanently stale. Every living tool (this plugin's template validation, JDT, WTP)
 * uses a typed subtype, so the purge — which matches the stock type exactly, subtypes excluded —
 * cannot touch anything alive.
 *
 * <p>Request parameters:
 * <ul>
 *   <li><b>project</b> — optional project name; omit to purge every open project.</li>
 * </ul>
 *
 * <p>Response: {@code {"deleted":82,"files":9}}.
 */
class PurgeMarkersHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String projectName = params.get("project");

		final StaleMarkerPurge.Summary summary;
		if (projectName != null && !projectName.isEmpty()) {
			final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			if (project == null || !project.isOpen()) {
				return "{\"error\":\"no open project named '" + DevServerJson.escape(projectName) + "'\"}";
			}
			summary = StaleMarkerPurge.purge(project);
		}
		else {
			summary = StaleMarkerPurge.purgeWorkspace();
		}

		return "{\"deleted\":" + summary.deletedMarkers + ",\"files\":" + summary.affectedFiles + "}";
	}
}

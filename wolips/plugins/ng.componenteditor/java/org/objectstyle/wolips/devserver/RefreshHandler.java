package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * Refreshes a workspace resource from the file system. Used by the runtime to
 * tell Eclipse "I just generated/changed a file on disk, pick it up."
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code path} — either an absolute file-system path or a
 *       workspace-relative path (required)</li>
 * </ul>
 *
 * <p>Ported essentially unchanged from the original WOLips
 * {@code RefreshRequestHandler}. Runs on a server request thread; resource
 * refresh is thread-safe (it acquires the workspace lock itself), so no
 * SWT-thread dispatch is needed.
 */
class RefreshHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String pathStr = params.get("path");
		if (pathStr == null || pathStr.isEmpty()) {
			return "{\"error\":\"missing required parameter 'path'\"}";
		}

		Path path = new Path(pathStr);
		int refreshed = 0;
		if (path.isAbsolute()) {
			// Absolute file-system path — map to workspace resource(s).
			IResource[] resources = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocation(path);
			if (resources.length == 0) {
				resources = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(path);
			}
			for (IResource resource : resources) {
				// A file created on disk isn't in the workspace yet, so exists() is false
				// for it - but a refresh of it is exactly what's being asked for. Require
				// only that its project is open.
				if (resource.getProject() != null && resource.getProject().isOpen()) {
					resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
					refreshed++;
				}
			}
		}
		else {
			// Workspace-relative path.
			IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (resource != null && resource.exists()) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				refreshed++;
			}
		}

		if (refreshed == 0) {
			// Nothing to refresh is not success: the caller expected Eclipse to pick a file
			// up, and it won't.
			return "{\"refreshed\":false,\"reason\":\"" + DevServerJson.escape(pathStr)
					+ " is not inside any open workspace project (an absolute path is matched against project locations, a relative one is a workspace path)\"}";
		}
		// Success stays the plain "ok" existing callers expect.
		return null;
	}
}

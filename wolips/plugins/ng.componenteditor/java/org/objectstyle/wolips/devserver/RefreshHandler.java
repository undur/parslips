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
	public void handle(Map<String, String> params) throws Exception {
		final String pathStr = params.get("path");
		if (pathStr == null || pathStr.isEmpty()) {
			return;
		}

		Path path = new Path(pathStr);
		if (path.isAbsolute()) {
			// Absolute file-system path — map to workspace resource(s).
			IResource[] resources = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocation(path);
			if (resources.length == 0) {
				resources = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(path);
			}
			for (IResource resource : resources) {
				if (resource.exists()) {
					resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				}
			}
		}
		else {
			// Workspace-relative path.
			IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (resource != null && resource.exists()) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			}
		}
	}
}

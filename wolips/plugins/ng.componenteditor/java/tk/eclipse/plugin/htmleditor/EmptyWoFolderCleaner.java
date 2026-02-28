package tk.eclipse.plugin.htmleditor;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;

/**
 * Deletes empty {@code .wo} folders left behind when their contents are removed.
 *
 * <p>Git tracks files, not directories — so when a branch switch or pull
 * removes all files inside a {@code .wo} folder, the empty directory remains
 * on disk. Eclipse picks it up as a valid component folder, which blocks
 * creating a new component with the same name and confuses the editor.
 *
 * <p>This listener watches for changes to {@code .wo} folders and deletes
 * them if they become empty. The actual deletion is posted to the UI thread
 * because the workspace is locked during resource change notifications.
 *
 * <p>Registered at plugin startup in {@link HTMLPlugin#start} so it's always
 * active, unlike the lazily-initialized {@code WodParserCacheInvalidator}.
 */
public class EmptyWoFolderCleaner implements IResourceChangeListener, IResourceDeltaVisitor {

	private static EmptyWoFolderCleaner _instance;

	/**
	 * Registers the listener on the workspace. Called from
	 * {@link HTMLPlugin#start}.
	 */
	public static synchronized void install() {
		if (_instance == null) {
			_instance = new EmptyWoFolderCleaner();
			ResourcesPlugin.getWorkspace().addResourceChangeListener(_instance);
		}
	}

	/**
	 * Removes the listener from the workspace. Called from
	 * {@link HTMLPlugin#stop}.
	 */
	public static synchronized void uninstall() {
		if (_instance != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(_instance);
			_instance = null;
		}
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta != null) {
			try {
				delta.accept(this);
			}
			catch (CoreException e) {
				// Not critical — just skip this event
			}
		}
	}

	@Override
	public boolean visit(IResourceDelta delta) {
		IResource resource = delta.getResource();

		if (resource.isDerived()) {
			return false;
		}

		// Look for .wo folders that have been modified (children added/removed).
		// When the last file is removed, delete the empty folder.
		if (resource instanceof IFolder) {
			IFolder folder = (IFolder) resource;
			if (folder.getName().endsWith(".wo") && delta.getKind() == IResourceDelta.CHANGED) {
				deleteIfEmpty(folder);
				// No need to visit children of a .wo folder for this listener
				return false;
			}
		}

		return true;
	}

	/**
	 * Posts an async deletion of the folder if it's empty. By the time the
	 * UI thread runs, the workspace has finished processing the resource
	 * change, so {@code members()} returns the correct (updated) contents.
	 */
	private void deleteIfEmpty(final IFolder woFolder) {
		Display.getDefault().asyncExec(() -> {
			try {
				if (woFolder.exists() && woFolder.members().length == 0) {
					woFolder.delete(false, null);
				}
			}
			catch (CoreException e) {
				// Not critical — the folder will just remain until manually cleaned up
			}
		});
	}
}

package ng.componenteditor.explorer;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * Watches the workspace for resource changes that affect Parsley Explorer's
 * pulled-up source folders, and refreshes the corresponding project node in
 * the tree viewer when one is added, removed, or moved.
 *
 * <p>The base JDT content provider reacts to resource deltas on existing
 * folders, but it doesn't know that folders like {@code src/main/woresources}
 * or any {@code components} folder under {@code src/main/resources/} should
 * cause the project node itself to be refreshed (so they appear/disappear
 * at the project root level). Without this listener, creating a new
 * pulled-up folder leaves the tree showing a stale view until the user
 * presses F5.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Created from {@code inputChanged()} once a viewer is available.</li>
 *   <li>Disposed from {@code dispose()} — must be disposed to avoid leaking
 *       a workspace listener.</li>
 * </ul>
 *
 * <p>The listener uses {@code POST_CHANGE} so it fires after the workspace
 * commits the change. Refreshes are dispatched to the UI thread via
 * {@code Display.asyncExec} (resource change events arrive on a worker thread).
 */
class PulledUpFolderRefresher implements IResourceChangeListener {

	private final AbstractTreeViewer _viewer;

	PulledUpFolderRefresher(AbstractTreeViewer viewer) {
		_viewer = viewer;
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}

		// Walk the delta and collect projects whose pulled-up folder set
		// may have changed. We refresh each project's node in the viewer
		// so getChildren(IJavaProject) is re-invoked.
		Set<IProject> affected = new HashSet<>();
		try {
			delta.accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(IResourceDelta d) throws CoreException {
					IResource resource = d.getResource();

					// Stop descending into projects that are closed or absent
					if (resource instanceof IProject) {
						IProject p = (IProject) resource;
						if (!p.isOpen() || !p.isAccessible()) {
							return false;
						}
						return true;
					}

					// Only interested in folder additions/removals/moves
					if (!(resource instanceof IFolder)) {
						// Continue descending into the workspace root and projects
						return true;
					}

					int kind = d.getKind();
					if (kind != IResourceDelta.ADDED && kind != IResourceDelta.REMOVED) {
						// CHANGED on folder content doesn't affect the pulled-up set;
						// keep descending in case a child was added or removed
						return true;
					}

					IFolder folder = (IFolder) resource;
					if (isPulledUpCandidate(folder)) {
						affected.add(folder.getProject());
						// No need to descend further into this subtree
						return false;
					}

					// Keep descending — a new folder might contain a pulled-up
					// folder as a child (e.g. someone pasted in a whole tree)
					return true;
				}
			});
		}
		catch (CoreException e) {
			// Visitor cannot fail in our implementation; ignore
			return;
		}

		if (affected.isEmpty()) {
			return;
		}

		// Refresh on the UI thread. The viewer's control may be disposed
		// by the time we get there (view closed), so check before refreshing.
		Display.getDefault().asyncExec(() -> {
			Control control = _viewer.getControl();
			if (control == null || control.isDisposed()) {
				return;
			}
			for (IProject project : affected) {
				IJavaProject javaProject = JavaCore.create(project);
				if (javaProject != null) {
					_viewer.refresh(javaProject);
				}
			}
		});
	}

	/**
	 * Returns true if this folder, by its path, would be pulled up to the
	 * project root by the Parsley Explorer content provider.
	 *
	 * <p>We can't use {@link NGPackageExplorerContentProvider#isSourceFolder}
	 * here directly because it relies on a live IFolder; on a REMOVED
	 * delta, the folder no longer exists on disk but the {@link IFolder}
	 * handle and its path are still meaningful — and the path-based check
	 * still works correctly for both added and removed folders.
	 */
	private static boolean isPulledUpCandidate(IFolder folder) {
		return NGPackageExplorerContentProvider.isSourceFolder(folder);
	}
}

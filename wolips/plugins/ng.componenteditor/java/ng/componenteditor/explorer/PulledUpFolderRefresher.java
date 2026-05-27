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
 * pulled-up source folders, and refreshes the appropriate tree-viewer node
 * when one is added, removed, moved, or has its contents change.
 *
 * <p>Two kinds of changes need handling:
 *
 * <ul>
 *   <li><b>The set of pulled-up folders changes</b> — e.g. someone creates
 *       a new {@code src/main/woresources} or any {@code components} folder
 *       under {@code src/main/resources/}, or deletes one. The project node
 *       in the tree needs to be refreshed so the new folder appears (or the
 *       deleted one disappears) at the project root level.</li>
 *   <li><b>Contents of an existing pulled-up folder change</b> — e.g. the
 *       user drops files into {@code src/main/components}. JDT's base
 *       content provider reacts to the resource delta on the
 *       <em>physical</em> location ({@code src/main} &gt; {@code components}),
 *       which is not the same TreeItem as the pulled-up node at the project
 *       root. So the pulled-up node's children never get refreshed unless
 *       we do it ourselves.</li>
 * </ul>
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

		// Two disjoint sets of refresh targets:
		//   - projectsToRefresh: project root nodes whose pulled-up folder
		//     list may have changed (folder added/removed).
		//   - foldersToRefresh: pulled-up folder nodes whose contents have
		//     changed (descendants added/removed).
		// We never need both for the same project at the same time, but
		// processing them separately keeps the refresh as targeted as
		// possible — refreshing the project root would also work for the
		// folder-contents case, but it's a much heavier operation.
		Set<IProject> projectsToRefresh = new HashSet<>();
		Set<IFolder> foldersToRefresh = new HashSet<>();

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

					if (!(resource instanceof IFolder)) {
						// Continue descending into the workspace root and projects
						return true;
					}

					IFolder folder = (IFolder) resource;
					int kind = d.getKind();

					// Case 1: the pulled-up folder itself was added or removed.
					// We need to refresh the project root so the pulled-up
					// folder list is recomputed.
					if (kind == IResourceDelta.ADDED || kind == IResourceDelta.REMOVED) {
						if (isPulledUpCandidate(folder)) {
							projectsToRefresh.add(folder.getProject());
							return false; // children handled by the refresh
						}
						// Not a pulled-up folder — keep descending in case
						// a pulled-up folder is nested deeper (e.g. an
						// ng-style components/ folder under src/main/resources/).
						return true;
					}

					// Case 2: the folder itself changed (its contents). If
					// this folder is one of our pulled-up ones, refresh it
					// directly so its visible children update.
					if (kind == IResourceDelta.CHANGED && isPulledUpCandidate(folder)) {
						foldersToRefresh.add(folder);
						// Keep descending — JDT will handle deeper resource
						// deltas on its own once the parent is refreshed, but
						// we want to keep visiting for any nested pulled-up
						// folders too.
						return true;
					}

					return true;
				}
			});
		}
		catch (CoreException e) {
			// Visitor cannot fail in our implementation; ignore
			return;
		}

		if (projectsToRefresh.isEmpty() && foldersToRefresh.isEmpty()) {
			return;
		}

		// Refresh on the UI thread. The viewer's control may be disposed
		// by the time we get there (view closed), so check before refreshing.
		Display.getDefault().asyncExec(() -> {
			Control control = _viewer.getControl();
			if (control == null || control.isDisposed()) {
				return;
			}

			// Refresh project roots first (covers any pulled-up folder
			// add/remove). If a folder is inside a project whose root we're
			// refreshing anyway, no need to also refresh the folder.
			for (IProject project : projectsToRefresh) {
				IJavaProject javaProject = JavaCore.create(project);
				if (javaProject != null) {
					_viewer.refresh(javaProject);
				}
			}

			for (IFolder folder : foldersToRefresh) {
				if (projectsToRefresh.contains(folder.getProject())) {
					continue; // already refreshed via project root
				}
				_viewer.refresh(folder);
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

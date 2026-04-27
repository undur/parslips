package ng.componenteditor.explorer;

import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.packageview.WorkingSetAwareContentProvider;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetModel;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.Viewer;

/**
 * Working-set-aware variant of the content provider for the Parsley Explorer.
 * Applies the same source folder pull-up as {@link NGPackageExplorerContentProvider}.
 */
public class NGWorkingSetAwareContentProvider extends WorkingSetAwareContentProvider {

	/**
	 * Watches the workspace for additions/removals of pulled-up folders and
	 * refreshes the project node in the viewer. See
	 * {@link NGPackageExplorerContentProvider} for full rationale.
	 */
	private PulledUpFolderRefresher _refresher;

	public NGWorkingSetAwareContentProvider(boolean provideMembers, WorkingSetModel model) {
		super(provideMembers, model);
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);
		if (_refresher == null && viewer instanceof AbstractTreeViewer) {
			_refresher = new PulledUpFolderRefresher((AbstractTreeViewer) viewer);
		}
	}

	@Override
	public void dispose() {
		if (_refresher != null) {
			_refresher.dispose();
			_refresher = null;
		}
		super.dispose();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		Object[] children = super.getChildren(parentElement);

		if (parentElement instanceof IJavaProject) {
			IJavaProject javaProject = (IJavaProject) parentElement;
			IProject project = javaProject.getProject();
			List<IFolder> extraFolders = NGPackageExplorerContentProvider.getSourceFolders(project);
			if (!extraFolders.isEmpty()) {
				Object[] newChildren = new Object[children.length + extraFolders.size()];
				System.arraycopy(children, 0, newChildren, 0, children.length);
				for (int i = 0; i < extraFolders.size(); i++) {
					newChildren[children.length + i] = extraFolders.get(i);
				}
				return newChildren;
			}
		}

		return children;
	}

	@Override
	protected Object[] getFolderContent(IFolder folder) throws CoreException {
		if ("src/main".equals(folder.getProjectRelativePath().toString())) {
			Object[] contents = super.getFolderContent(folder);
			// Reuse the filtering from the base content provider
			List<Object> filtered = new java.util.ArrayList<>(contents.length);
			for (Object child : contents) {
				if (child instanceof IFolder && NGPackageExplorerContentProvider.isSourceFolder((IFolder) child)) {
					continue;
				}
				filtered.add(child);
			}
			return filtered.size() < contents.length ? filtered.toArray() : contents;
		}
		return super.getFolderContent(folder);
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof IFolder && NGPackageExplorerContentProvider.isSourceFolder((IFolder) element)) {
			return ((IFolder) element).getProject();
		}
		return super.getParent(element);
	}
}

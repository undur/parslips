package ng.componenteditor.explorer;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.packageview.WorkingSetAwareContentProvider;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetModel;

/**
 * Working-set-aware variant of the content provider that also collapses
 * .wo bundle folders into single leaf nodes.
 */
public class NGWorkingSetAwareContentProvider extends WorkingSetAwareContentProvider {

	public NGWorkingSetAwareContentProvider(boolean provideMembers, WorkingSetModel model) {
		super(provideMembers, model);
	}

	@Override
	protected Object[] getFolderContent(IFolder folder) throws CoreException {
		if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
			return NO_CHILDREN;
		}
		return super.getFolderContent(folder);
	}
}

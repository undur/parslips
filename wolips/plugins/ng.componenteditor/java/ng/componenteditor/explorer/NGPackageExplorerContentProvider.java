package ng.componenteditor.explorer;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;

/**
 * Content provider that collapses .wo bundle folders into single leaf nodes
 * (no children shown). This makes component bundles appear as a single item
 * in the Package Explorer tree.
 */
public class NGPackageExplorerContentProvider extends PackageExplorerContentProvider {

	public NGPackageExplorerContentProvider(boolean provideMembers) {
		super(provideMembers);
	}

	@Override
	protected Object[] getFolderContent(IFolder folder) throws CoreException {
		if (isComponentBundle(folder)) {
			return NO_CHILDREN;
		}
		return super.getFolderContent(folder);
	}

	/**
	 * Returns true if the folder is a .wo component bundle that should be
	 * collapsed into a single tree node.
	 */
	public static boolean isComponentBundle(IFolder folder) {
		return folder.getName().endsWith(".wo");
	}
}

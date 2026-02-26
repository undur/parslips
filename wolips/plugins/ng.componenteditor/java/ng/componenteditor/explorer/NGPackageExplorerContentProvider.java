package ng.componenteditor.explorer;

import org.eclipse.core.resources.IFolder;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;

/**
 * Content provider for the NG Explorer. Currently delegates fully to the
 * standard JDT content provider. The .wo bundle folders are shown with
 * their normal children (html, wod, woo files) so users can expand them
 * if needed. A custom decorator provides the component icon, and
 * double-click / Enter opens the component editor.
 */
public class NGPackageExplorerContentProvider extends PackageExplorerContentProvider {

	public NGPackageExplorerContentProvider(boolean provideMembers) {
		super(provideMembers);
	}

	/**
	 * Returns true if the folder is a .wo component bundle.
	 */
	public static boolean isComponentBundle(IFolder folder) {
		return folder.getName().endsWith(".wo");
	}
}

package ng.componenteditor.explorer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;

/**
 * Content provider for the NG Explorer. Extends the standard JDT content
 * provider with two customizations:
 * <ul>
 *   <li>Pulls up WebObjects/ng-objects source folders ({@code src/main/components},
 *       {@code src/main/woresources}, {@code src/main/webserver-resources}) to the
 *       project root level — similar to how Eclipse pulls up {@code src/main/java}
 *       as a top-level source root. The folders are removed from their physical
 *       location so they only appear once.</li>
 *   <li>The {@code .wo} bundle folders are shown with their normal children
 *       (html, wod, woo files) so users can expand them if needed.</li>
 * </ul>
 * A custom decorator provides the component icon, and double-click / Enter
 * opens the component editor.
 */
public class NGPackageExplorerContentProvider extends PackageExplorerContentProvider {

	/**
	 * Folder paths (relative to the project root) that should be pulled up
	 * to the top level of the project tree. Only folders that actually exist
	 * in the project are shown.
	 */
	static final String[] SOURCE_FOLDER_PATHS = {
		"src/main/components",
		"src/main/woresources",
		"src/main/webserver-resources",
	};

	/**
	 * The simple names of pulled-up folders. Used to filter them out from
	 * their physical parent ({@code src/main/}).
	 */
	private static final String[] SOURCE_FOLDER_NAMES = {
		"components",
		"woresources",
		"webserver-resources",
	};

	public NGPackageExplorerContentProvider(boolean provideMembers) {
		super(provideMembers);
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		Object[] children = super.getChildren(parentElement);

		// When showing a Java project's children, add the source folders
		// at the top level
		if (parentElement instanceof IJavaProject) {
			IJavaProject javaProject = (IJavaProject) parentElement;
			IProject project = javaProject.getProject();
			List<IFolder> extraFolders = getSourceFolders(project);
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
		// If this is the src/main/ folder, filter out the pulled-up children
		if (isSrcMainFolder(folder)) {
			Object[] contents = super.getFolderContent(folder);
			return filterPulledUpFolders(contents);
		}
		return super.getFolderContent(folder);
	}

	@Override
	public Object getParent(Object element) {
		// If this is one of our pulled-up folders, its logical parent
		// in the tree is the Java project (not the physical parent folder)
		if (element instanceof IFolder) {
			IFolder folder = (IFolder) element;
			if (isSourceFolder(folder)) {
				return folder.getProject();
			}
		}
		return super.getParent(element);
	}

	/**
	 * Returns the source folders that exist in the given project and should
	 * be shown at the project root level.
	 */
	static List<IFolder> getSourceFolders(IProject project) {
		List<IFolder> folders = new ArrayList<>();
		for (String path : SOURCE_FOLDER_PATHS) {
			IFolder folder = project.getFolder(path);
			if (folder.exists()) {
				folders.add(folder);
			}
		}
		return folders;
	}

	/**
	 * Returns true if the folder is one of the pulled-up source folders.
	 */
	static boolean isSourceFolder(IFolder folder) {
		String path = folder.getProjectRelativePath().toString();
		for (String sourcePath : SOURCE_FOLDER_PATHS) {
			if (path.equals(sourcePath)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the folder is {@code src/main/} (the physical parent
	 * of the pulled-up source folders).
	 */
	private static boolean isSrcMainFolder(IFolder folder) {
		return "src/main".equals(folder.getProjectRelativePath().toString());
	}

	/**
	 * Removes pulled-up source folders from the given children array.
	 */
	private static Object[] filterPulledUpFolders(Object[] children) {
		List<Object> filtered = null;
		for (Object child : children) {
			if (child instanceof IFolder) {
				String name = ((IFolder) child).getName();
				for (String sourceName : SOURCE_FOLDER_NAMES) {
					if (name.equals(sourceName)) {
						// Need to filter — create list lazily
						if (filtered == null) {
							filtered = new ArrayList<>(children.length);
							for (Object prev : children) {
								if (prev == child) break;
								filtered.add(prev);
							}
						}
						child = null;
						break;
					}
				}
			}
			if (filtered != null && child != null) {
				filtered.add(child);
			}
		}
		return filtered != null ? filtered.toArray() : children;
	}

	/**
	 * Returns true if the folder is a .wo component bundle.
	 */
	public static boolean isComponentBundle(IFolder folder) {
		return folder.getName().endsWith(".wo");
	}
}

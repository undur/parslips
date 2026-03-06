package ng.componenteditor.explorer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;

/**
 * Content provider for the Parsley Explorer. Extends the standard JDT content
 * provider with two customizations:
 * <ul>
 *   <li>Pulls up resource folders to the project root level — similar to how
 *       Eclipse pulls up {@code src/main/java} as a top-level source root.
 *       Two kinds of folders are pulled up:
 *       <ul>
 *         <li><b>WO-style:</b> hardcoded paths directly under {@code src/main/}
 *             ({@code components}, {@code woresources}, {@code webserver-resources})</li>
 *         <li><b>ng-style:</b> dynamically discovered folders under {@code src/main/resources/}
 *             with well-known names ({@code components}, {@code app-resources},
 *             {@code webserver-resources}). These can be nested at any depth,
 *             e.g. {@code src/main/resources/ng/namespace/components}.</li>
 *       </ul>
 *       Pulled-up folders are removed from their physical location so they
 *       appear only once.</li>
 *   <li>The {@code .wo} bundle folders are shown with their normal children
 *       (html, wod, woo files) so users can expand them if needed.</li>
 * </ul>
 * A custom decorator provides the component icon, and double-click / Enter
 * opens the component editor.
 */
public class NGPackageExplorerContentProvider extends PackageExplorerContentProvider {

	/**
	 * WO-style folder paths (relative to the project root) that are pulled up
	 * directly. Only folders that actually exist are shown.
	 */
	private static final String[] WO_SOURCE_FOLDER_PATHS = {
		"src/main/components",
		"src/main/woresources",
		"src/main/webserver-resources",
	};

	/**
	 * Folder names recognized as ng-objects resource folders when found
	 * anywhere under {@link #NG_RESOURCES_ROOT}.
	 */
	private static final Set<String> NG_RESOURCE_FOLDER_NAMES = Set.of(
		"components",
		"app-resources",
		"webserver-resources"
	);

	/**
	 * Root path under which ng-style resource folders are discovered.
	 */
	private static final String NG_RESOURCES_ROOT = "src/main/resources";

	public NGPackageExplorerContentProvider(boolean provideMembers) {
		super(provideMembers);
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		Object[] children = super.getChildren(parentElement);

		// When showing a Java project's children, add the pulled-up folders
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
		Object[] contents = super.getFolderContent(folder);

		// Filter pulled-up folders from their physical parent so they
		// don't appear twice. For WO folders, the parent is src/main/.
		// For ng folders, the parent could be any ancestor under
		// src/main/resources/.
		String path = folder.getProjectRelativePath().toString();

		if ("src/main".equals(path)) {
			return filterPulledUpChildren(contents, folder.getProject());
		}
		if (path.equals(NG_RESOURCES_ROOT) || path.startsWith(NG_RESOURCES_ROOT + "/")) {
			return filterPulledUpChildren(contents, folder.getProject());
		}

		return contents;
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
	 * Returns all source folders that exist in the given project and should
	 * be shown at the project root level. Includes both WO-style hardcoded
	 * paths and dynamically discovered ng-style resource folders.
	 */
	static List<IFolder> getSourceFolders(IProject project) {
		List<IFolder> folders = new ArrayList<>();

		// WO-style: direct path lookup
		for (String path : WO_SOURCE_FOLDER_PATHS) {
			IFolder folder = project.getFolder(path);
			if (folder.exists()) {
				folders.add(folder);
			}
		}

		// ng-style: scan src/main/resources/ recursively
		IFolder resourcesRoot = project.getFolder(NG_RESOURCES_ROOT);
		if (resourcesRoot.exists()) {
			findNgResourceFolders(resourcesRoot, folders);
		}

		return folders;
	}

	/**
	 * Returns true if the folder is one of the pulled-up source folders.
	 * Checks both WO-style hardcoded paths and ng-style resource folders.
	 */
	static boolean isSourceFolder(IFolder folder) {
		String path = folder.getProjectRelativePath().toString();

		// WO-style: exact path match
		for (String sourcePath : WO_SOURCE_FOLDER_PATHS) {
			if (path.equals(sourcePath)) {
				return true;
			}
		}

		// ng-style: folder with a recognized name under src/main/resources/
		if (path.startsWith(NG_RESOURCES_ROOT + "/") && NG_RESOURCE_FOLDER_NAMES.contains(folder.getName())) {
			return true;
		}

		return false;
	}

	/**
	 * Recursively scans a folder for ng-style resource folders (those
	 * with names in {@link #NG_RESOURCE_FOLDER_NAMES}). When a matching
	 * folder is found, it's added to the result and its children are not
	 * scanned further (resource folders don't nest inside each other).
	 */
	private static void findNgResourceFolders(IFolder parent, List<IFolder> result) {
		IResource[] members;
		try {
			members = parent.members();
		}
		catch (CoreException e) {
			return;
		}

		for (IResource member : members) {
			if (member instanceof IFolder subfolder) {
				if (NG_RESOURCE_FOLDER_NAMES.contains(subfolder.getName())) {
					result.add(subfolder);
					// Don't recurse into a resource folder — they don't nest
				}
				else {
					findNgResourceFolders(subfolder, result);
				}
			}
		}
	}

	/**
	 * Removes pulled-up source folders from the given children array.
	 * Works for both WO-style folders (under {@code src/main/}) and
	 * ng-style folders (anywhere under {@code src/main/resources/}).
	 */
	private static Object[] filterPulledUpChildren(Object[] children, IProject project) {
		// Build a set of paths that are pulled up for this project
		List<IFolder> pulledUp = getSourceFolders(project);
		if (pulledUp.isEmpty()) {
			return children;
		}

		Set<String> pulledUpPaths = new HashSet<>();
		for (IFolder f : pulledUp) {
			pulledUpPaths.add(f.getProjectRelativePath().toString());
		}

		List<Object> filtered = null;
		for (Object child : children) {
			boolean shouldFilter = false;
			if (child instanceof IFolder childFolder) {
				shouldFilter = pulledUpPaths.contains(
						childFolder.getProjectRelativePath().toString());
			}
			if (shouldFilter) {
				// Need to filter — create list lazily
				if (filtered == null) {
					filtered = new ArrayList<>(children.length);
					for (Object prev : children) {
						if (prev == child) break;
						filtered.add(prev);
					}
				}
			}
			else if (filtered != null) {
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

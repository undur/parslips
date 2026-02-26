package ng.componenteditor.explorer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jface.viewers.Viewer;

/**
 * Comparator for the Parsley Explorer that handles two special cases:
 * <ul>
 *   <li><b>Pulled-up source folders</b> ({@code components}, {@code woresources},
 *       {@code webserver-resources}) — positioned right after Java source roots
 *       ({@code src/main/java} etc.) and before classpath containers (JRE, Maven
 *       Dependencies). Sorted by name within the group.</li>
 *   <li><b>.wo bundles</b> — sorted alphabetically alongside regular files,
 *       after regular folders.</li>
 * </ul>
 *
 * Ordering at the project level:
 * <ol>
 *   <li>Java source roots (IPackageFragmentRoot with K_SOURCE) — classpath order</li>
 *   <li>Pulled-up source folders — alphabetical</li>
 *   <li>Classpath containers (JRE, Maven Dependencies, Referenced Libraries)</li>
 *   <li>Regular folders, .wo bundles, files — see rules above</li>
 * </ol>
 */
public class NGJavaElementComparator extends JavaElementComparator {

	/**
	 * Sort category for pulled-up source folders. Placed between source
	 * roots (which use classpath ordering via super) and classpath
	 * containers.
	 */
	private static final int CATEGORY_PULLED_UP_SOURCE_FOLDER = 1;
	private static final int CATEGORY_CLASSPATH_CONTAINER = 2;
	private static final int CATEGORY_OTHER = 3;

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		boolean isSource1 = isSourceFolder(e1);
		boolean isSource2 = isSourceFolder(e2);

		// If neither is a pulled-up source folder, fall through to
		// the standard comparator (with .wo bundle handling)
		if (!isSource1 && !isSource2) {
			return compareStandard(viewer, e1, e2);
		}

		// At least one is a pulled-up source folder — use custom ordering
		int cat1 = isSource1 ? CATEGORY_PULLED_UP_SOURCE_FOLDER : topLevelCategory(e1);
		int cat2 = isSource2 ? CATEGORY_PULLED_UP_SOURCE_FOLDER : topLevelCategory(e2);

		if (cat1 != cat2) {
			return cat1 - cat2;
		}

		// Both are pulled-up source folders — sort by name
		if (isSource1 && isSource2) {
			return ((IFolder) e1).getName().compareToIgnoreCase(((IFolder) e2).getName());
		}

		// Shouldn't reach here, but fall back to name comparison
		return 0;
	}

	/**
	 * Returns a category for non-source-folder top-level items that
	 * determines their position relative to pulled-up source folders.
	 * <p>
	 * <b>Before</b> pulled-up source folders:
	 * Source package fragment roots under {@code src/main/} (e.g.,
	 * {@code src/main/java}, {@code src/main/resources}).
	 * <p>
	 * <b>After</b> pulled-up source folders:
	 * Source roots under {@code src/test/}, classpath containers
	 * (JRE, Maven Dependencies), and binary roots (JARs).
	 */
	private int topLevelCategory(Object element) {
		if (element instanceof IPackageFragmentRoot) {
			try {
				IPackageFragmentRoot root = (IPackageFragmentRoot) element;
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					// Check if this source root is under src/main/ or src/test/
					IPath path = root.getResource() != null
							? root.getResource().getProjectRelativePath()
							: null;
					if (path != null && path.segmentCount() >= 2
							&& "src".equals(path.segment(0))
							&& "main".equals(path.segment(1))) {
						return 0; // src/main/* source roots before pulled-up folders
					}
					// src/test/* and other source roots after pulled-up folders
					return CATEGORY_PULLED_UP_SOURCE_FOLDER + 1;
				}
			} catch (Exception e) {
				// fall through
			}
			return CATEGORY_CLASSPATH_CONTAINER; // binary roots after
		}
		if (element instanceof PackageFragmentRootContainer) {
			return CATEGORY_CLASSPATH_CONTAINER;
		}
		// Regular resources (folders, files) come after everything
		return CATEGORY_OTHER;
	}

	/**
	 * Standard comparison for non-source-folder elements — handles .wo
	 * bundles and delegates everything else to the JDT comparator.
	 */
	private int compareStandard(Viewer viewer, Object e1, Object e2) {
		if (e1 instanceof IResource && e2 instanceof IResource) {
			String name1 = ((IResource) e1).getName();
			String ext1 = getExtension(name1);

			String name2 = ((IResource) e2).getName();
			String ext2 = getExtension(name2);

			boolean isBundle1 = "wo".equals(ext1);
			boolean isBundle2 = "wo".equals(ext2);

			if (isBundle1) {
				if (e2 instanceof IFile || isBundle2) {
					return name1.compareTo(name2);
				} else if (e2 instanceof IContainer) {
					return 1;
				}
			} else if (isBundle2) {
				if (e1 instanceof IFile) {
					return name1.compareTo(name2);
				} else if (e1 instanceof IContainer) {
					return -1;
				}
			}
		}

		return super.compare(viewer, e1, e2);
	}

	private static boolean isSourceFolder(Object element) {
		return element instanceof IFolder
				&& NGPackageExplorerContentProvider.isSourceFolder((IFolder) element);
	}

	private static String getExtension(String name) {
		int dot = name.lastIndexOf('.');
		return (dot != -1) ? name.substring(dot + 1) : null;
	}
}

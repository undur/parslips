package ng.componenteditor.explorer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jface.viewers.Viewer;

/**
 * Comparator for the Parsley Explorer that handles two special cases:
 * <ul>
 *   <li><b>Pulled-up source folders</b> ({@code components}, {@code woresources},
 *       {@code webserver-resources}) — slotted into the classpath/source-root
 *       group at the project root level, immediately after the {@code src/main/*}
 *       source roots and before {@code src/test/*} and classpath containers.</li>
 *   <li><b>.wo bundles</b> — sorted alphabetically alongside regular files,
 *       after regular folders.</li>
 * </ul>
 *
 * <p>Implementation note: pulled-up folders are given the same {@link #category}
 * as Java source roots, package containers, etc. (the JDT "category 2" group).
 * This ensures we don't violate the {@link java.util.Comparator} contract by
 * disagreeing with JDT's category ordering — earlier versions tried to slot
 * pulled-up folders between categories using a separate ordering for cases
 * involving a pulled-up folder, but that meant
 * {@code compare(JRE, java) < 0 < compare(java, components)} via our logic
 * while {@code compare(components, JRE) < 0} via the same logic, producing a
 * transitivity cycle that TimSort rejects with
 * <em>"Comparison method violates its general contract!"</em>
 *
 * <p>With everything in one category, we override {@link #compare} only to
 * decide the order <em>within</em> that category, using a fine-grained
 * sub-category for slot ordering.
 */
public class NGJavaElementComparator extends JavaElementComparator {

	/**
	 * JDT's category number for source roots, classpath containers, and other
	 * top-level classpath items. We slot pulled-up folders into this group so
	 * the overall ordering is consistent with JDT's notion of categories.
	 */
	private static final int CATEGORY_CLASSPATH = 2;

	// ------------------------------------------------------------------------
	// Sub-categories used for ordering within CATEGORY_CLASSPATH. Lower comes
	// first. Items with equal sub-categories fall through to JDT's super
	// comparator (which uses classpath order for IPackageFragmentRoot items).
	// ------------------------------------------------------------------------

	/** {@code src/main/*} source roots — first within the classpath group. */
	private static final int SUB_SRC_MAIN = 10;

	/** Pulled-up folders ({@code components}, {@code woresources}, etc.). */
	private static final int SUB_PULLED_UP = 20;

	/** Any other classpath-group item handled by JDT (containers, {@code src/test/*}, etc.). */
	private static final int SUB_OTHER = 30;

	@Override
	public int category(Object element) {
		// Pulled-up folders join the classpath group — same category as
		// source roots / containers. Sub-category ordering happens in compare().
		if (isPulledUpSourceFolder(element)) {
			return CATEGORY_CLASSPATH;
		}
		return super.category(element);
	}

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		int cat1 = category(e1);
		int cat2 = category(e2);

		// Cross-category: JDT's category ordering is the source of truth.
		if (cat1 != cat2) {
			return cat1 - cat2;
		}

		// Same category. If we're in the classpath group, apply our
		// sub-category logic to slot pulled-up folders into the right place.
		if (cat1 == CATEGORY_CLASSPATH) {
			int sub1 = classpathSubCategory(e1);
			int sub2 = classpathSubCategory(e2);
			if (sub1 != sub2) {
				return sub1 - sub2;
			}
			// Within a sub-category:
			//   - pulled-up vs pulled-up: name compare
			//   - everything else: defer to JDT (handles classpath order)
			if (sub1 == SUB_PULLED_UP) {
				return ((IFolder) e1).getName().compareToIgnoreCase(((IFolder) e2).getName());
			}
			return super.compare(viewer, e1, e2);
		}

		// Other categories (folders, files, etc.) — handle .wo bundles first,
		// then defer to JDT.
		return compareNonClasspath(viewer, e1, e2);
	}

	/**
	 * Sub-category for an element in the classpath group (category 2).
	 * Used to slot pulled-up folders into the right position relative to
	 * the {@code src/main/*} source roots and other classpath items.
	 */
	private static int classpathSubCategory(Object element) {
		if (isPulledUpSourceFolder(element)) {
			return SUB_PULLED_UP;
		}
		if (element instanceof IPackageFragmentRoot) {
			IPackageFragmentRoot root = (IPackageFragmentRoot) element;
			try {
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					org.eclipse.core.runtime.IPath path = root.getResource() != null
							? root.getResource().getProjectRelativePath()
							: null;
					if (path != null
							&& path.segmentCount() >= 2
							&& "src".equals(path.segment(0))
							&& "main".equals(path.segment(1))) {
						return SUB_SRC_MAIN;
					}
				}
			}
			catch (Exception e) {
				// Fall through to SUB_OTHER
			}
		}
		return SUB_OTHER;
	}

	/**
	 * Comparison for non-classpath-category elements. Handles .wo bundles,
	 * then delegates to JDT.
	 */
	private int compareNonClasspath(Viewer viewer, Object e1, Object e2) {
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
				}
				else if (e2 instanceof IContainer) {
					return 1;
				}
			}
			else if (isBundle2) {
				if (e1 instanceof IFile) {
					return name1.compareTo(name2);
				}
				else if (e1 instanceof IContainer) {
					return -1;
				}
			}
		}

		return super.compare(viewer, e1, e2);
	}

	private static boolean isPulledUpSourceFolder(Object element) {
		return element instanceof IFolder
				&& NGPackageExplorerContentProvider.isSourceFolder((IFolder) element);
	}

	private static String getExtension(String name) {
		int dot = name.lastIndexOf('.');
		return (dot != -1) ? name.substring(dot + 1) : null;
	}
}

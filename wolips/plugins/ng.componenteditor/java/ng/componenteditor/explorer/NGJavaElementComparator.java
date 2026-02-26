package ng.componenteditor.explorer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jface.viewers.Viewer;

/**
 * Comparator that sorts .wo bundle folders alongside regular files
 * (alphabetically by name) rather than lumping them with other folders.
 * This makes component bundles appear in a natural position in the tree.
 */
public class NGJavaElementComparator extends JavaElementComparator {

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
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

	private static String getExtension(String name) {
		int dot = name.lastIndexOf('.');
		return (dot != -1) ? name.substring(dot + 1) : null;
	}
}

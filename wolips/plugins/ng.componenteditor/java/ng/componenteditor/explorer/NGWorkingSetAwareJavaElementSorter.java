package ng.componenteditor.explorer;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.IWorkingSet;

/**
 * Working-set-aware variant of the comparator. When comparing working sets,
 * preserves their original order; otherwise delegates to the .wo-aware sorting.
 */
public class NGWorkingSetAwareJavaElementSorter extends NGJavaElementComparator {

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		if (e1 instanceof IWorkingSet || e2 instanceof IWorkingSet) {
			return 0;
		}
		return super.compare(viewer, e1, e2);
	}
}

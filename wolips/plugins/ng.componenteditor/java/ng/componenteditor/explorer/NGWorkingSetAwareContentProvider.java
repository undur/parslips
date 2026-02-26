package ng.componenteditor.explorer;

import org.eclipse.jdt.internal.ui.packageview.WorkingSetAwareContentProvider;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetModel;

/**
 * Working-set-aware variant of the content provider for the NG Explorer.
 * Delegates fully to the standard JDT working set content provider.
 */
public class NGWorkingSetAwareContentProvider extends WorkingSetAwareContentProvider {

	public NGWorkingSetAwareContentProvider(boolean provideMembers, WorkingSetModel model) {
		super(provideMembers, model);
	}
}

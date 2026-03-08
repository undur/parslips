package org.objectstyle.wolips.componenteditor.part;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

/**
 * Tab showing which other components in the workspace reference this component
 * as an element — a reverse dependency ("who uses me?") view.
 *
 * <p>Unlike other tabs, UsagesTab does not wrap an {@link IEditorPart} — it is
 * a pure read-only view. It has no dirty state and does not participate in save
 * operations.
 *
 * <p>Delegates all UI and scanning to {@link UsagesComposite}, which is also
 * reused by the standalone API editor's Usages page.
 */
public class UsagesTab extends ComponentEditorTab {

	private final String _componentName;
	private final IProject _project;
	private UsagesComposite _usagesComposite;

	/**
	 * @param componentEditorPart the parent multi-tab editor
	 * @param tabIndex            the tab position
	 * @param componentName       the name of the component being edited
	 * @param project             the project the component belongs to — the
	 *                            scan covers this project and all dependents
	 */
	public UsagesTab(ComponentEditorPart componentEditorPart, int tabIndex,
			String componentName, IProject project) {
		super(componentEditorPart, tabIndex);
		_componentName = componentName;
		_project = project;
	}

	/**
	 * Builds the tab UI by embedding a {@link UsagesComposite} in the
	 * tab's sash form.
	 */
	public void createTab() {
		Composite parent = getParentSashForm();
		_usagesComposite = new UsagesComposite(parent, SWT.NONE, _componentName, _project);
	}

	/**
	 * Triggers a scan on first tab activation. Subsequent activations do not
	 * re-scan — use the Refresh button for that.
	 */
	@Override
	public void editorSelected() {
		_usagesComposite.scanIfNeeded();
	}

	// -- ComponentEditorTab contract (read-only tab, no editor) --

	@Override
	public IEditorPart getActiveEmbeddedEditor() {
		return null;
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		// Read-only tab — nothing to save
	}

	@Override
	public void close(boolean save) {
		// Read-only tab — nothing to close
	}

	@Override
	public IEditorInput getActiveEditorInput() {
		return null;
	}
}

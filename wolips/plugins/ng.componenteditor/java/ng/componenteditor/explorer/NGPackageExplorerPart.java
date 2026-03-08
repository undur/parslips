package ng.componenteditor.explorer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Parsley Explorer — a Package Explorer variant with component-aware behavior.
 * <p>
 * .wo component bundle folders are shown with their normal children (the
 * expansion triangle is visible), but double-clicking or pressing Enter
 * on a .wo folder opens the Component Editor. The expansion state of
 * the .wo folder is preserved (not toggled) on double-click.
 * <p>
 * A registered decorator ({@code WOComponentDecorator}) provides the
 * custom component icon in all views.
 * <p>
 * Uses unique IDs so it can coexist with WOLips' WO Explorer.
 */
public class NGPackageExplorerPart extends PackageExplorerPart {
	private static final int SHOW_PROJECTS = PackageExplorerPart.PROJECTS_AS_ROOTS;
	private static final int SHOW_WORKING_SETS = PackageExplorerPart.WORKING_SETS_AS_ROOTS;

	private IDoubleClickListener _componentDoubleClickListener;
	private IOpenListener _componentOpenListener;

	public NGPackageExplorerPart() {
		super();
	}

	@Override
	public PackageExplorerContentProvider createContentProvider() {
		IPreferenceStore store = PreferenceConstants.getPreferenceStore();
		boolean showCUChildren = store.getBoolean(PreferenceConstants.SHOW_CU_CHILDREN);
		if (getRootMode() == SHOW_PROJECTS) {
			return new NGPackageExplorerContentProvider(showCUChildren);
		}
		return new NGWorkingSetAwareContentProvider(showCUChildren, getWorkingSetModel());
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		switchToNGSorter();
		installComponentOpenListeners();
		SourceFolderDecorator.install(getTreeViewer());
	}

	@Override
	public void rootModeChanged(int newMode) {
		super.rootModeChanged(newMode);
		switchToNGSorter();
	}

	@Override
	public void dispose() {
		TreeViewer viewer = getTreeViewer();
		if (viewer != null) {
			if (_componentDoubleClickListener != null) {
				viewer.removeDoubleClickListener(_componentDoubleClickListener);
			}
			if (_componentOpenListener != null) {
				viewer.removeOpenListener(_componentOpenListener);
			}
		}
		super.dispose();
	}

	protected void switchToNGSorter() {
		TreeViewer viewer = getTreeViewer();
		boolean showWorkingSets = (getRootMode() == SHOW_WORKING_SETS);
		if (showWorkingSets) {
			viewer.setComparator(new NGWorkingSetAwareJavaElementSorter());
		} else {
			viewer.setComparator(new NGJavaElementComparator());
		}
	}

	/**
	 * Installs listeners for double-click and Enter/Return on .wo
	 * component bundles.
	 * <p>
	 * <b>The expansion flash problem:</b> when the user double-clicks a
	 * .wo folder, the TreeViewer's built-in handler toggles the expansion
	 * state before our {@code IDoubleClickListener} fires. Additionally,
	 * opening a file inside the .wo folder (via {@code WorkbenchUtilities.open})
	 * can trigger an editor "reveal" that re-expands the parent node.
	 * <p>
	 * <b>The fix:</b> in the {@code IDoubleClickListener}, compute the
	 * desired expansion state (the inverse of what the TreeViewer toggled
	 * to), then open the component, then restore the expansion state.
	 * This order ensures both the TreeViewer's toggle and any reveal-triggered
	 * expansion are undone in a single event dispatch cycle — no
	 * intermediate state is ever painted.
	 * <p>
	 * <b>Important:</b> the expansion undo must <i>only</i> happen in the
	 * {@code IDoubleClickListener}, not in the {@code IOpenListener}. A
	 * double-click fires both listeners, and if both try to toggle the
	 * expansion state they cancel each other out.
	 */
	private void installComponentOpenListeners() {
		TreeViewer viewer = getTreeViewer();

		// Double-click: open the component and immediately undo the expansion toggle
		_componentDoubleClickListener = event -> {
			if (!(event.getSelection() instanceof IStructuredSelection)) {
				return;
			}
			IStructuredSelection sel = (IStructuredSelection) event.getSelection();
			if (sel.size() == 1 && sel.getFirstElement() instanceof IFolder) {
				IFolder folder = (IFolder) sel.getFirstElement();
				if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
					// The TreeViewer toggles expansion before this listener
					// fires. We need to undo it, but opening the component
					// first — because WorkbenchUtilities.open() can trigger
					// a "reveal" that re-expands the parent .wo folder.
					// By computing the desired state before the open and
					// restoring it after, both the toggle and the reveal
					// are undone in a single event dispatch cycle.
					boolean wasExpanded = !viewer.getExpandedState(folder);
					openComponentBundle(folder);
					viewer.setExpandedState(folder, wasExpanded);
					// Opening the component's HTML file can cause "Link with
					// Editor" to move the selection to the (now hidden) child
					// file. Re-select the .wo folder so it stays highlighted.
					viewer.setSelection(new StructuredSelection(folder));
				}
			}
		};
		viewer.addDoubleClickListener(_componentDoubleClickListener);

		// Enter/Return: just open the component (no expansion undo needed —
		// the TreeViewer only toggles expansion on double-click, not on Enter)
		_componentOpenListener = new IOpenListener() {
			@Override
			public void open(OpenEvent event) {
				if (event.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection sel = (IStructuredSelection) event.getSelection();
					if (sel.size() == 1 && sel.getFirstElement() instanceof IFolder) {
						IFolder folder = (IFolder) sel.getFirstElement();
						if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
							openComponentBundle(folder);
							// Re-select the .wo folder — same reason as in the
							// double-click listener above.
							viewer.setSelection(new StructuredSelection(folder));
						}
					}
				}
			}
		};
		viewer.addOpenListener(_componentOpenListener);
	}

	/**
	 * WOLips' component editor ID — used to delegate .wo bundle opens to
	 * WOLips when the project isn't a Parsley project. We can't use Eclipse's
	 * editor association here because WOLips doesn't register an
	 * {@code IEditorAssociationOverride} — it hardcodes its editor ID in its
	 * own explorer, just like we do. So we have to hardcode theirs too.
	 */
	private static final String WOLIPS_COMPONENT_EDITOR_ID =
			"org.objectstyle.wolips.componenteditor.ComponentEditor";

	/**
	 * Opens a .wo component bundle by finding the HTML template file inside
	 * and opening it with an appropriate component editor.
	 *
	 * <p>For projects Parsley should handle, uses the Parsley Template Editor.
	 * For other projects when WOLips is installed, delegates to WOLips'
	 * component editor. If neither applies, falls back to our editor —
	 * still better than Eclipse's web browser for {@code .html}.
	 */
	private void openComponentBundle(IFolder woFolder) {
		String folderName = woFolder.getName();
		// Strip the .wo extension to get the component name
		String componentName = folderName.substring(0, folderName.length() - ".wo".length());

		// Pick the right component editor for this project
		String editorId = EditorsPlugin.ComponentEditorID;
		if (!ParsleyProject.shouldHandleProject(woFolder.getProject()) && ParsleyProject.isWOLipsInstalled()) {
			editorId = WOLIPS_COMPONENT_EDITOR_ID;
		}

		// Look for the HTML file inside the .wo folder
		List<IResource> htmlFiles = new ArrayList<>();
		WorkbenchUtilities.findFilesInResourceByName(htmlFiles, woFolder, componentName + ".html");

		if (!htmlFiles.isEmpty()) {
			WorkbenchUtilities.open((IFile) htmlFiles.get(0), editorId);
		} else {
			// Fall back to .wod if no HTML found
			List<IResource> wodFiles = new ArrayList<>();
			WorkbenchUtilities.findFilesInResourceByName(wodFiles, woFolder, componentName + ".wod");
			if (!wodFiles.isEmpty()) {
				WorkbenchUtilities.open((IFile) wodFiles.get(0), editorId);
			}
		}
	}
}

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
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.editors.EditorsPlugin;

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
	 * The double-click listener snapshots the expansion state of the .wo
	 * folder before the default handler runs, then restores it via
	 * {@code asyncExec} so the tree doesn't visually toggle.
	 */
	private void installComponentOpenListeners() {
		TreeViewer viewer = getTreeViewer();

		// Double-click: open the component and undo any expand/collapse
		_componentDoubleClickListener = event -> {
			if (!(event.getSelection() instanceof IStructuredSelection)) {
				return;
			}
			IStructuredSelection sel = (IStructuredSelection) event.getSelection();
			if (sel.size() == 1 && sel.getFirstElement() instanceof IFolder) {
				IFolder folder = (IFolder) sel.getFirstElement();
				if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
					// Snapshot current expansion state — the tree will have
					// already toggled it by the time this listener fires
					boolean isNowExpanded = viewer.getExpandedState(folder);
					openComponentBundle(folder);
					// Restore: undo the toggle on the next event loop tick
					viewer.getTree().getDisplay().asyncExec(() -> {
						if (!viewer.getTree().isDisposed()) {
							viewer.setExpandedState(folder, !isNowExpanded);
						}
					});
				}
			}
		};
		viewer.addDoubleClickListener(_componentDoubleClickListener);

		// Enter/Return key handler
		_componentOpenListener = new IOpenListener() {
			@Override
			public void open(OpenEvent event) {
				if (event.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection sel = (IStructuredSelection) event.getSelection();
					if (sel.size() == 1 && sel.getFirstElement() instanceof IFolder) {
						IFolder folder = (IFolder) sel.getFirstElement();
						if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
							openComponentBundle(folder);
						}
					}
				}
			}
		};
		viewer.addOpenListener(_componentOpenListener);
	}

	/**
	 * Opens a .wo component bundle by finding the HTML template file inside
	 * and opening it with the NG Component Editor.
	 */
	private void openComponentBundle(IFolder woFolder) {
		String folderName = woFolder.getName();
		// Strip the .wo extension to get the component name
		String componentName = folderName.substring(0, folderName.length() - ".wo".length());

		// Look for the HTML file inside the .wo folder
		List<IResource> htmlFiles = new ArrayList<>();
		WorkbenchUtilities.findFilesInResourceByName(htmlFiles, woFolder, componentName + ".html");

		if (!htmlFiles.isEmpty()) {
			WorkbenchUtilities.open((IFile) htmlFiles.get(0), EditorsPlugin.ComponentEditorID);
		} else {
			// Fall back to .wod if no HTML found
			List<IResource> wodFiles = new ArrayList<>();
			WorkbenchUtilities.findFilesInResourceByName(wodFiles, woFolder, componentName + ".wod");
			if (!wodFiles.isEmpty()) {
				WorkbenchUtilities.open((IFile) wodFiles.get(0), EditorsPlugin.ComponentEditorID);
			}
		}
	}
}

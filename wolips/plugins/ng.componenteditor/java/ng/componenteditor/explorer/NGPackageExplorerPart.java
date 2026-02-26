package ng.componenteditor.explorer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.editors.EditorsPlugin;

/**
 * NG Explorer — a Package Explorer variant that collapses .wo bundles
 * into single nodes and sorts them alongside regular files.
 * <p>
 * Double-clicking a .wo component folder opens it in the NG Component Editor.
 * <p>
 * Based on the WOLips WO Explorer but stripped of Tagged Components,
 * .eomodeld handling, and the WO-specific component rename action.
 * Uses unique IDs so it can coexist with WOLips' WO Explorer.
 */
public class NGPackageExplorerPart extends PackageExplorerPart {
	private static final int SHOW_PROJECTS = PackageExplorerPart.PROJECTS_AS_ROOTS;
	private static final int SHOW_WORKING_SETS = PackageExplorerPart.WORKING_SETS_AS_ROOTS;

	private IDoubleClickListener _componentDoubleClickListener;

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
		installComponentDoubleClickListener();
	}

	@Override
	public void rootModeChanged(int newMode) {
		super.rootModeChanged(newMode);
		switchToNGSorter();
	}

	@Override
	public void dispose() {
		if (_componentDoubleClickListener != null) {
			getTreeViewer().removeDoubleClickListener(_componentDoubleClickListener);
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
	 * Installs a double-click listener that opens .wo component bundles
	 * in the NG Component Editor. When a .wo folder is double-clicked,
	 * the HTML template file inside it is located and opened.
	 */
	private void installComponentDoubleClickListener() {
		_componentDoubleClickListener = new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				if (!(event.getSelection() instanceof IStructuredSelection)) {
					return;
				}
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Iterator<?> iter = selection.iterator();
				while (iter.hasNext()) {
					Object selected = iter.next();
					if (selected instanceof IFolder) {
						IFolder folder = (IFolder) selected;
						if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
							openComponentBundle(folder);
						}
					}
				}
			}
		};
		getTreeViewer().addDoubleClickListener(_componentDoubleClickListener);
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

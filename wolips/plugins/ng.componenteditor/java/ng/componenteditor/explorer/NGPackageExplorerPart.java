package ng.componenteditor.explorer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
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
	private IPartListener2 _linkCorrectionListener;

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
		installLinkCorrectionListener();
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
		if (_linkCorrectionListener != null && getSite() != null && getSite().getPage() != null) {
			getSite().getPage().removePartListener(_linkCorrectionListener);
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
	 * Corrects Link-with-Editor for {@code .wo} components.
	 *
	 * <p>JDT's link-with-editor reveal ({@code editorActivated}/{@code showInput}) is
	 * package-private, so we can't override it. For a file inside a {@code .wo} bundle —
	 * which isn't part of the Java model — JDT can't resolve a tree node and falls back to
	 * selecting the file's parent, landing on the (pulled-up, empty-looking) source folder
	 * instead of the component. Our public {@code tryToReveal}/{@code selectReveal}
	 * overrides don't catch it because {@code showInput} doesn't route through them.
	 *
	 * <p>So we post-correct: listen for editor activation and, when linking is on and the
	 * active editor shows a {@code .wo}-internal file, re-select the {@code .wo} bundle.
	 * We defer via {@code asyncExec} so this runs <em>after</em> JDT's own activation
	 * handler (which fires synchronously on the same event) — ours lands last and wins.
	 */
	private void installLinkCorrectionListener() {
		_linkCorrectionListener = new IPartListener2() {
			@Override
			public void partActivated(IWorkbenchPartReference ref) {
				correct(ref);
			}

			@Override
			public void partBroughtToTop(IWorkbenchPartReference ref) {
				correct(ref);
			}

			private void correct(IWorkbenchPartReference ref) {
				if (!isLinkingEnabled()) {
					return;
				}
				final IWorkbenchPart part = ref.getPart(false);
				if (!(part instanceof IEditorPart)) {
					return;
				}
				final IEditorPart editor = (IEditorPart) part;
				if (!(editor.getEditorInput() instanceof IFileEditorInput)) {
					return;
				}
				final IFolder bundle = enclosingComponentBundle(((IFileEditorInput) editor.getEditorInput()).getFile());
				if (bundle == null) {
					return; // not a .wo-internal file — let JDT's normal reveal stand
				}

				final TreeViewer viewer = getTreeViewer();
				if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed()) {
					return;
				}
				// Run after JDT's synchronous editorActivated handler so our selection wins.
				viewer.getControl().getDisplay().asyncExec(() -> {
					if (viewer.getControl().isDisposed() || !isLinkingEnabled()) {
						return;
					}
					// Skip if we're already on the bundle (avoids redundant work / flicker).
					if (viewer.getStructuredSelection().size() == 1
							&& bundle.equals(viewer.getStructuredSelection().getFirstElement())) {
						return;
					}
					revealBundle(bundle);
				});
			}
		};
		getSite().getPage().addPartListener(_linkCorrectionListener);
	}

	/**
	 * Link with Editor reveal — redirect reveals of files <em>inside</em> a {@code .wo}
	 * component bundle to the {@code .wo} folder itself.
	 *
	 * <p>A {@code .wo} bundle is a folder presented as a single component, and its
	 * contents (the {@code .html}, {@code .wod}, …) aren't part of the Java model. When
	 * the active editor is such a file, JDT's link-with-editor resolves the element to
	 * reveal through the Java model and can't place the non-Java leaf — it stops at an
	 * enclosing node (the pulled-up {@code components} source folder), selecting it and
	 * showing nothing. That's the intermittent "jumps to components and reveals nothing"
	 * behavior. By mapping any file under a {@code .wo} to that {@code .wo} folder — the
	 * real tree node for the component, and the same node our open listeners select — the
	 * reveal lands on the component the editor is showing.
	 */
	@Override
	public int tryToReveal(Object element) {
		IFolder bundle = enclosingComponentBundle(element);
		if (bundle != null) {
			revealBundle(bundle);
			return REVEAL_OK;
		}
		return super.tryToReveal(element);
	}

	// PackageExplorerPart.tryToReveal returns an int status; "revealed" is 0 (IStatus.OK).
	// We avoid importing IStatus just for the constant.
	private static final int REVEAL_OK = 0;

	@Override
	public void selectReveal(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sel = (IStructuredSelection) selection;
			if (sel.size() == 1) {
				IFolder bundle = enclosingComponentBundle(sel.getFirstElement());
				if (bundle != null) {
					revealBundle(bundle);
					return;
				}
			}
		}
		super.selectReveal(selection);
	}

	/**
	 * Selects and reveals a {@code .wo} bundle, expanding its visible ancestor chain
	 * first so it works on a cold (collapsed) tree.
	 *
	 * <p>Why the explicit expansion: a {@code .wo} bundle lives inside a <em>pulled-up</em>
	 * source folder (e.g. {@code src/main/components} shown directly under the project,
	 * filtered out of its physical {@code src/main} parent). On a freshly collapsed tree
	 * those intermediate nodes haven't been materialized, and JDT's reveal can't lazily
	 * resolve the pulled-up path — it gives up at the nearest materialized ancestor
	 * (expanding the now-empty-looking physical {@code src/main}) on the first attempt,
	 * only succeeding once a prior attempt populated the tree. That's the "works on the
	 * second try" flakiness. By force-expanding project &rarr; source folder &rarr; bundle
	 * top-down ourselves, the path is materialized before we select, so it lands on the
	 * component the first time.
	 */
	private void revealBundle(IFolder bundle) {
		TreeViewer viewer = getTreeViewer();
		if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed()) {
			return;
		}

		// Expand the visible ancestors top-down. The bundle's parent is the pulled-up
		// source folder (our content provider maps its parent to the project), so
		// expanding it under the project materializes the path down to the bundle.
		IFolder sourceFolder = (bundle.getParent() instanceof IFolder) ? (IFolder) bundle.getParent() : null;
		if (sourceFolder != null) {
			viewer.setExpandedState(sourceFolder, true);
		}
		viewer.setSelection(new StructuredSelection(bundle), true);
	}

	/**
	 * If {@code element} is (or adapts to) a file inside a {@code .wo} component bundle,
	 * returns that {@code .wo} folder; otherwise null. Handles the forms the reveal path
	 * hands us: a raw {@link IResource}, or an {@link IAdaptable} (e.g. an
	 * {@code IJavaElement} or editor input) that adapts to one.
	 */
	private static IFolder enclosingComponentBundle(Object element) {
		IResource resource = null;
		if (element instanceof IResource) {
			resource = (IResource) element;
		}
		else if (element instanceof IAdaptable) {
			resource = ((IAdaptable) element).getAdapter(IResource.class);
		}
		if (resource == null) {
			return null;
		}

		// Walk up from the resource; the first .wo ancestor is the component bundle.
		for (IResource r = resource; r != null; r = r.getParent()) {
			if (r instanceof IFolder && NGPackageExplorerContentProvider.isComponentBundle((IFolder) r)) {
				return (IFolder) r;
			}
		}
		return null;
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

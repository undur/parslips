package org.objectstyle.wolips.editor.help;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.jface.text.Region;
import org.objectstyle.wolips.bindings.api.ApiextHtmlRenderer;
import org.objectstyle.wolips.bindings.api.ElementCatalog;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.wodclipse.core.document.WodElementTypeHyperlink;

/**
 * A help view that lists every element available to the active component editor's project
 * and shows each element's API documentation rendered in the same card format as the
 * editor hover.
 *
 * <p>It doubles as a migration dashboard for the {@code .api} &rarr; {@code .apiext}
 * effort: a per-row state chip shows whether an element has a rich {@code .apiext}
 * definition (blue), a legacy {@code .api}/global one (grey), or none (the row is greyed
 * out). Each row also shows the element's originating framework, which aids navigation.
 *
 * <p>Layout: a {@link SashForm} with a filterable {@link Table} (Element / Origin / State)
 * on the left and a {@link Browser} showing the selected element's card on the right.
 * The view follows the frontmost component editor — switching editors re-populates the
 * list for that editor's project.
 */
public class ElementHelpView extends ViewPart {

	public static final String ID = "ng.componenteditor.help.ElementHelpView";

	private Text _filter;
	private Table _table;
	private Browser _browser;

	/** The project whose elements are currently listed (tracks the active editor). */
	private IJavaProject _project;

	/** The full, unfiltered catalog for {@link #_project}; the table shows a filtered view. */
	private List<ElementCatalog.Entry> _entries = new ArrayList<>();

	/** Current sort column (0=Element, 1=Origin, 2=API state) and direction. */
	private int _sortColumn = 0;
	private boolean _sortAscending = true;

	// State-chip colors (kept close to the card's source-badge palette).
	private Color _apiextBg;
	private Color _apiextFg;
	private Color _apiBg;
	private Color _apiFg;
	private Color _noneFg;

	/** Listens for editor activation so the list follows the frontmost editor. */
	private IPartListener2 _partListener;

	@Override
	public void createPartControl(Composite parent) {
		allocateColors(parent);

		final SashForm sash = new SashForm(parent, SWT.HORIZONTAL);

		// ---- Left: filter + element table ----
		final Composite left = new Composite(sash, SWT.NONE);
		final GridLayout leftLayout = new GridLayout(1, false);
		leftLayout.marginWidth = 0;
		leftLayout.marginHeight = 0;
		leftLayout.verticalSpacing = 0;
		left.setLayout(leftLayout);

		_filter = new Text(left, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		_filter.setMessage("Filter elements");
		_filter.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		_filter.addModifyListener(e -> populateTable());

		_table = new Table(left, SWT.FULL_SELECTION | SWT.BORDER);
		_table.setHeaderVisible(true);
		_table.setLinesVisible(false);
		_table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		final TableColumn nameCol = new TableColumn(_table, SWT.LEFT);
		nameCol.setText("Element");
		nameCol.setWidth(180);
		final TableColumn originCol = new TableColumn(_table, SWT.LEFT);
		originCol.setText("Origin");
		originCol.setWidth(150);
		final TableColumn stateCol = new TableColumn(_table, SWT.CENTER);
		stateCol.setText("API");
		stateCol.setWidth(70);

		// Click a header to sort by that column; click again to reverse.
		nameCol.addListener(SWT.Selection, e -> sortBy(0, nameCol));
		originCol.addListener(SWT.Selection, e -> sortBy(1, originCol));
		stateCol.addListener(SWT.Selection, e -> sortBy(2, stateCol));

		_table.addListener(SWT.Selection, e -> showSelectedCard());
		// Double-click opens the element — the same action as cmd-clicking it in a template.
		_table.addListener(SWT.MouseDoubleClick, e -> openSelectedElement());

		// ---- Right: detail card ----
		_browser = new Browser(sash, SWT.NONE);
		_browser.setJavascriptEnabled(false);

		sash.setWeights(new int[] { 40, 60 });

		// Follow the active editor.
		_partListener = new EditorActivationListener();
		getSite().getWorkbenchWindow().getPartService().addPartListener(_partListener);

		// Prime from whatever editor is already active.
		final IEditorPart active = getSite().getWorkbenchWindow().getActivePage() != null
				? getSite().getWorkbenchWindow().getActivePage().getActiveEditor() : null;
		trackEditor(active);
	}

	private void allocateColors(Composite parent) {
		final Display d = parent.getDisplay();
		_apiextBg = new Color(d, 0xEE, 0xF6, 0xFF);
		_apiextFg = new Color(d, 0x1C, 0x4F, 0x8A);
		_apiBg = new Color(d, 0xF1, 0xF3, 0xF5);
		_apiFg = new Color(d, 0x6C, 0x75, 0x7D);
		_noneFg = new Color(d, 0x9A, 0xA0, 0xA8);
	}

	/**
	 * Points the view at the given editor's project (if it is a component editor with a
	 * resolvable Java project), rebuilding the element catalog. No-op if the project is
	 * unchanged, so simply clicking around the same editor doesn't re-enumerate.
	 */
	private void trackEditor(IEditorPart editor) {
		final IJavaProject project = projectForEditor(editor);
		if (project == null || project.equals(_project)) {
			return;
		}
		_project = project;
		setContentDescription(project.getElementName());
		reload();
	}

	/** Resolves the Java project backing an editor, or null if it has none. */
	private IJavaProject projectForEditor(IEditorPart editor) {
		if (editor == null) {
			return null;
		}
		final IEditorInput input = editor.getEditorInput();
		final IFile file = input != null ? input.getAdapter(IFile.class) : null;
		if (file == null || file.getProject() == null) {
			return null;
		}
		final IJavaProject jp = JavaCore.create(file.getProject());
		return (jp != null && jp.exists()) ? jp : null;
	}

	/** Rebuilds the catalog for the current project and repopulates the table. */
	private void reload() {
		_entries = ElementCatalog.forProject(_project);
		sortEntries();
		populateTable();
	}

	/**
	 * Sorts by the clicked column, toggling direction if it's already the sort column.
	 * Updates the table's sort indicator (the little arrow in the header) and repopulates.
	 */
	private void sortBy(int column, TableColumn col) {
		if (_sortColumn == column) {
			_sortAscending = !_sortAscending;
		} else {
			_sortColumn = column;
			_sortAscending = true;
		}
		_table.setSortColumn(col);
		_table.setSortDirection(_sortAscending ? SWT.UP : SWT.DOWN);
		sortEntries();
		populateTable();
	}

	/** Re-sorts {@link #_entries} by the current sort column/direction. */
	private void sortEntries() {
		java.util.Comparator<ElementCatalog.Entry> cmp;
		switch (_sortColumn) {
		case 1:
			// Origin (nulls last), then name as a tiebreaker.
			cmp = java.util.Comparator
					.comparing((ElementCatalog.Entry e) -> e.getOrigin() == null ? "￿" : e.getOrigin().toLowerCase())
					.thenComparing(e -> e.getSimpleName().toLowerCase());
			break;
		case 2:
			// API state: .apiext first, then .api, then none — with name as a tiebreaker so
			// each state group is itself alphabetical (handy when scanning migration coverage).
			cmp = java.util.Comparator
					.comparingInt((ElementCatalog.Entry e) -> e.getDefinitionKind().ordinal())
					.thenComparing(e -> e.getSimpleName().toLowerCase());
			break;
		case 0:
		default:
			cmp = java.util.Comparator.comparing(e -> e.getSimpleName().toLowerCase());
			break;
		}
		if (!_sortAscending) {
			cmp = cmp.reversed();
		}
		_entries.sort(cmp);
	}

	/** Repopulates the table from {@link #_entries}, honoring the current filter text. */
	private void populateTable() {
		if (_table == null || _table.isDisposed()) {
			return;
		}
		final String filter = _filter.getText().trim().toLowerCase();
		_table.removeAll();
		for (final ElementCatalog.Entry entry : _entries) {
			if (!filter.isEmpty() && !entry.getSimpleName().toLowerCase().contains(filter)
					&& (entry.getOrigin() == null || !entry.getOrigin().toLowerCase().contains(filter))) {
				continue;
			}
			final TableItem item = new TableItem(_table, SWT.NONE);
			item.setText(0, entry.getSimpleName());
			item.setText(1, entry.getOrigin() != null ? entry.getOrigin() : "");
			item.setData(entry);
			styleStateCell(item, entry.getDefinitionKind());
		}
	}

	/** Applies the state-chip text/colors and greys out rows with no definition. */
	private void styleStateCell(TableItem item, ElementCatalog.DefinitionKind kind) {
		switch (kind) {
		case APIEXT:
			item.setText(2, ".apiext");
			item.setBackground(2, _apiextBg);
			item.setForeground(2, _apiextFg);
			break;
		case API:
			item.setText(2, ".api");
			item.setBackground(2, _apiBg);
			item.setForeground(2, _apiFg);
			break;
		case NONE:
		default:
			item.setText(2, "");
			// Grey the whole row so undocumented elements visibly recede.
			item.setForeground(0, _noneFg);
			item.setForeground(1, _noneFg);
			break;
		}
	}

	/** Renders the selected entry's card into the browser. */
	private void showSelectedCard() {
		if (_browser == null || _browser.isDisposed()) {
			return;
		}
		final TableItem[] selection = _table.getSelection();
		if (selection.length == 0) {
			_browser.setText("");
			return;
		}
		final ElementCatalog.Entry entry = (ElementCatalog.Entry) selection[0].getData();
		final String body = ElementCatalog.renderCardBody(entry, _project);
		_browser.setText("<html><head><style>" + ApiextHtmlRenderer.css() + "</style></head><body>" + body + "</body></html>");
	}

	/**
	 * Opens the selected element — the same action as cmd-clicking the element in a
	 * template. Reuses {@link WodElementTypeHyperlink#open()} so the open/reveal behaviour
	 * (the type's source, or the component's template in the component editor) is identical.
	 * Does nothing for elements that can't be resolved to a type.
	 */
	private void openSelectedElement() {
		final TableItem[] selection = _table.getSelection();
		if (selection.length == 0 || _project == null) {
			return;
		}
		final ElementCatalog.Entry entry = (ElementCatalog.Entry) selection[0].getData();
		if (entry.getType() == null) {
			return;
		}
		// Region is only used for the in-editor link UI; opening doesn't need a real one.
		new WodElementTypeHyperlink(new Region(0, 0), entry.getSimpleName(), _project, new TypeCache()).open();
	}

	@Override
	public void setFocus() {
		if (_filter != null && !_filter.isDisposed()) {
			_filter.setFocus();
		}
	}

	@Override
	public void dispose() {
		if (_partListener != null && getSite() != null && getSite().getWorkbenchWindow() != null) {
			getSite().getWorkbenchWindow().getPartService().removePartListener(_partListener);
		}
		disposeColor(_apiextBg);
		disposeColor(_apiextFg);
		disposeColor(_apiBg);
		disposeColor(_apiFg);
		disposeColor(_noneFg);
		super.dispose();
	}

	private static void disposeColor(Color c) {
		if (c != null && !c.isDisposed()) {
			c.dispose();
		}
	}

	/** Re-points the view whenever a (component) editor is activated or brought forward. */
	private final class EditorActivationListener implements IPartListener2 {
		@Override
		public void partActivated(IWorkbenchPartReference ref) {
			final IWorkbenchPart part = ref.getPart(false);
			if (part instanceof IEditorPart) {
				trackEditor((IEditorPart) part);
			}
		}

		@Override
		public void partBroughtToTop(IWorkbenchPartReference ref) {
			partActivated(ref);
		}

		@Override public void partClosed(IWorkbenchPartReference ref) { }
		@Override public void partDeactivated(IWorkbenchPartReference ref) { }
		@Override public void partOpened(IWorkbenchPartReference ref) { }
		@Override public void partHidden(IWorkbenchPartReference ref) { }
		@Override public void partVisible(IWorkbenchPartReference ref) { }
		@Override public void partInputChanged(IWorkbenchPartReference ref) { }
	}
}

package org.objectstyle.wolips.editor.help;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.objectstyle.wolips.bindings.api.ElementCatalog;
import org.objectstyle.wolips.editor.component.ComponentEditor;
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

	/**
	 * The project the most recent {@link #reload()} was launched for. The catalog is built
	 * on a background {@link Job} (the build does JAR I/O and must not block the UI thread);
	 * when a job finishes it compares against this field and discards its result if the view
	 * has since been pointed at a different project, so a slow stale build can't clobber a
	 * newer one. Only touched on the UI thread.
	 */
	private IJavaProject _reloadTarget;

	/** Current sort column (0=Element, 1=Origin, 2=API state) and direction. */
	private int _sortColumn = 0;
	private boolean _sortAscending = true;

	// State-chip colors (kept close to the card's source-badge palette).
	private Color _apiextBg;
	private Color _apiextFg;
	private Color _apiBg;
	private Color _apiFg;
	private Color _noneFg;
	private Color _deprecatedStrike; // light red, drawn semi-transparently through deprecated rows
	/** Orange, used to mark an element that is overridden/replaced in the current project. */
	private Color _overriddenFg;

	/** Listens for editor activation so the list follows the frontmost editor. */
	private IPartListener2 _partListener;

	/** Reloads the catalog when a parsley-tag-aliases.properties changes in the workspace. */
	private org.eclipse.core.resources.IResourceChangeListener _aliasFileListener;

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
		// The search field's cancel (X) icon doesn't clear the text by itself — clicking it
		// fires SWT.Selection with detail == SWT.CANCEL. Clear the field ourselves so the icon
		// works on a single click. (The search icon fires SWT.Selection with detail == SWT.ICON_SEARCH.)
		// The native search-field cancel (X) icon should fire SWT.Selection with
		// detail == SWT.CANCEL, but on this Eclipse/Cocoa build it doesn't fire at all
		// (an Eclipse-wide bug — even the Error Log's own search box has it). We still want
		// single-click-to-clear, so detect a click in the cancel icon's region ourselves:
		// it sits at the right edge of the field and is only present when there's text.
		_filter.addListener(SWT.Selection, e -> {
			if (e.detail == SWT.CANCEL) {
				_filter.setText(""); // honour the native event too, on platforms where it works
			}
		});
		_filter.addListener(SWT.MouseDown, e -> {
			if (_filter.getText().isEmpty()) {
				return; // no cancel icon shown when the field is empty
			}
			final int cancelZone = 22; // approx. width of the cancel icon at the right edge
			if (e.x >= _filter.getClientArea().width - cancelZone) {
				_filter.setText("");
			}
		});

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
		final TableColumn tagsCol = new TableColumn(_table, SWT.LEFT);
		tagsCol.setText("Tags");
		tagsCol.setWidth(160);
		final TableColumn overriddenCol = new TableColumn(_table, SWT.LEFT);
		overriddenCol.setText("Overridden by");
		overriddenCol.setWidth(160);

		// Click a header to sort by that column; click again to reverse.
		nameCol.addListener(SWT.Selection, e -> sortBy(0, nameCol));
		originCol.addListener(SWT.Selection, e -> sortBy(1, originCol));
		stateCol.addListener(SWT.Selection, e -> sortBy(2, stateCol));
		tagsCol.addListener(SWT.Selection, e -> sortBy(3, tagsCol));
		overriddenCol.addListener(SWT.Selection, e -> sortBy(4, overriddenCol));

		_table.addListener(SWT.Selection, e -> showSelectedCard());
		// Double-click opens the element — the same action as cmd-clicking it in a template.
		_table.addListener(SWT.MouseDoubleClick, e -> openSelectedElement());

		// Strike deprecated elements through. A native SWT Table can't style cell text (no per-cell
		// StyleRange), so we owner-draw the line ourselves: after the cell's text is painted, draw a
		// light-red, semi-transparent rule across the name column. Only the name column (index 0) is
		// struck — enough to read "don't use this" without turning the whole row into noise. Alpha keeps
		// the thin line from swallowing the small text; it's restored so we don't leak GC state.
		_table.addListener(SWT.PaintItem, e -> {
			if (e.index != 0) {
				return; // strike the element name only
			}
			final ElementCatalog.Entry entry = (ElementCatalog.Entry) ((TableItem) e.item).getData();
			if (entry == null || !entry.isDeprecated()) {
				return;
			}
			final GC gc = e.gc;
			final int savedAlpha = gc.getAlpha();
			final Color savedFg = gc.getForeground();
			gc.setForeground(_deprecatedStrike);
			gc.setAlpha(150); // ~60% — visible over grey text, but the glyphs still read through it
			final int y = e.y + e.height / 2;
			// e.x is the cell's left edge; strike only across the drawn text, not the full column width.
			final String text = ((TableItem) e.item).getText(e.index);
			final int textWidth = gc.textExtent(text).x;
			gc.drawLine(e.x + 2, y, e.x + 2 + textWidth, y);
			gc.setAlpha(savedAlpha);
			gc.setForeground(savedFg);
		});

		// ---- Right: detail card ----
		_browser = new Browser(sash, SWT.NONE);
		_browser.setJavascriptEnabled(false);

		sash.setWeights(new int[] { 40, 60 });

		// Follow the active editor.
		_partListener = new EditorActivationListener();
		getSite().getWorkbenchWindow().getPartService().addPartListener(_partListener);

		// Reload when a parsley-tag-aliases.properties changes, so the catalog (including the
		// override column) reflects edited aliases without an Eclipse restart — mirroring the
		// alias-cache invalidation that the hover/completion already get.
		_aliasFileListener = event -> {
			if (_project != null && aliasFileChanged(event)) {
				// Clear the resolver cache here too, so we don't depend on this listener firing
				// after the one that normally invalidates it — otherwise reload() could rebuild
				// from the stale alias map.
				ParsleyTagAliasResolver.clearCache();
				final Table t = _table;
				if (t != null && !t.isDisposed()) {
					t.getDisplay().asyncExec(() -> {
						if (!t.isDisposed()) {
							reload();
						}
					});
				}
			}
		};
		ResourcesPlugin.getWorkspace().addResourceChangeListener(_aliasFileListener, IResourceChangeEvent.POST_CHANGE);

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
		_overriddenFg = new Color(d, 0xC0, 0x5A, 0x12); // orange — "replaced in this project"
		_deprecatedStrike = new Color(d, 0xE0, 0x40, 0x40); // light red — strikethrough on deprecated rows (drawn at reduced alpha)
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

	/** True if the resource delta includes a change to a parsley-tag-aliases.properties file. */
	private static boolean aliasFileChanged(IResourceChangeEvent event) {
		final org.eclipse.core.resources.IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return false;
		}
		final boolean[] hit = { false };
		try {
			delta.accept(d -> {
				if (hit[0]) {
					return false;
				}
				final IResource res = d.getResource();
				if (res != null
						&& ParsleyTagAliasResolver.ALIASES_RESOURCE.equals(res.getName())) {
					hit[0] = true;
					return false;
				}
				return true; // keep descending (incl. into derived output folders)
			});
		} catch (Exception e) {
			// best-effort
		}
		return hit[0];
	}

	/**
	 * Rebuilds the catalog for the current project and repopulates the table.
	 * <p>
	 * The build ({@link ElementCatalog#forProject}) enumerates every element type on the
	 * classpath and reads {@code .api}/{@code .apiext} bytes out of JARs — seconds of
	 * blocking I/O in a large project — so it runs on a background {@link Job}, never on the
	 * UI thread. When the job finishes it re-enters the UI thread to populate the table, but
	 * only if the view still exists and is still pointed at the same project it was launched
	 * for (guards against a stale build clobbering a newer one — see {@link #_reloadTarget}).
	 */
	private void reload() {
		final IJavaProject target = _project;
		_reloadTarget = target;
		if (target == null) {
			_entries = new ArrayList<>();
			sortEntries();
			populateTable();
			return;
		}
		final Job job = new Job("Loading element reference for " + target.getElementName()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final List<ElementCatalog.Entry> built = ElementCatalog.forProject(target);
				final Table t = _table;
				if (t == null || t.isDisposed()) {
					return Status.OK_STATUS;
				}
				t.getDisplay().asyncExec(() -> {
					// Drop the result if the view was disposed or re-pointed while we built.
					if (t.isDisposed() || _reloadTarget != target) {
						return;
					}
					_entries = built;
					sortEntries();
					populateTable();
				});
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true); // housekeeping work — don't show it in the Progress view
		job.schedule();
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
		case 4:
			// Overridden by: overridden elements first (by the override target), others last;
			// name tiebreaker. Groups all the replaced elements together for a quick scan.
			cmp = java.util.Comparator
					.comparing((ElementCatalog.Entry e) -> e.getOverriddenBy() == null ? "￿" : e.getOverriddenBy().toLowerCase())
					.thenComparing(e -> e.getSimpleName().toLowerCase());
			break;
		case 3:
			// Tags: elements with shortcuts first (by their first tag), tagless last; name tiebreaker.
			cmp = java.util.Comparator
					.comparing((ElementCatalog.Entry e) -> e.getTags().isEmpty() ? "￿" : e.getTags().get(0).toLowerCase())
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
			final String tags = String.join(", ", entry.getTags());
			if (!filter.isEmpty()
					&& !entry.getSimpleName().toLowerCase().contains(filter)
					&& (entry.getOrigin() == null || !entry.getOrigin().toLowerCase().contains(filter))
					&& !tags.toLowerCase().contains(filter)
					&& (entry.getOverriddenBy() == null || !entry.getOverriddenBy().toLowerCase().contains(filter))) {
				continue;
			}
			final TableItem item = new TableItem(_table, SWT.NONE);
			item.setText(0, entry.getSimpleName());
			item.setText(1, entry.getOrigin() != null ? entry.getOrigin() : "");
			item.setText(3, tags);
			item.setData(entry);
			styleStateCell(item, entry.getDefinitionKind());
			// "Overridden by" column: what replaces this element in the project being viewed
			// (e.g. ERXWOString for WOString). An overridden element's whole row is tinted
			// orange so it reads as replaced at a glance.
			if (entry.isOverridden()) {
				item.setText(4, entry.getOverriddenBy());
				for (int col = 0; col < _table.getColumnCount(); col++) {
					item.setForeground(col, _overriddenFg);
				}
			}
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
			item.setForeground(3, _noneFg);
			break;
		}
	}

	/**
	 * Selects and shows the element with the given simple name in the list (clearing any
	 * active filter so it's visible), and renders its card. Used by the "Show in Element
	 * Reference" command to reveal the element under the template editor's cursor.
	 *
	 * @param elementName the element's simple name (e.g. "WOString")
	 */
	public void revealElement(String elementName) {
		if (elementName == null || _table == null || _table.isDisposed()) {
			return;
		}
		// Clear the filter so the target row isn't hidden (setText repopulates the table).
		if (!_filter.getText().isEmpty()) {
			_filter.setText("");
		}
		for (final TableItem item : _table.getItems()) {
			final ElementCatalog.Entry entry = (ElementCatalog.Entry) item.getData();
			if (entry != null && elementName.equalsIgnoreCase(entry.getSimpleName())) {
				_table.setSelection(item);
				_table.showSelection();
				showSelectedCard();
				return;
			}
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
		if (_aliasFileListener != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(_aliasFileListener);
		}
		disposeColor(_apiextBg);
		disposeColor(_apiextFg);
		disposeColor(_apiBg);
		disposeColor(_apiFg);
		disposeColor(_noneFg);
		disposeColor(_overriddenFg);
		disposeColor(_deprecatedStrike);
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
			// Only react to component editors. Activating any OTHER editor (a pom, a
			// plain .java/.xml, …) must NOT rebuild the catalog: the rebuild enumerates
			// every element type on the classpath and reads .api/.apiext bytes out of
			// JARs, which is costly in a large project and was beachballing the UI when
			// the user simply opened/closed an unrelated file. A non-component editor
			// tells us nothing about which element library to show, so we ignore it and
			// leave the view pointed at whatever component project it last tracked.
			if (part instanceof ComponentEditor) {
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

package org.objectstyle.wolips.editor.apiext;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.objectstyle.wolips.bindings.api.ApiextModel.UnknownAttributes;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableType;

/**
 * The main form page: the element header (class, wrapsContent, unknownAttributes, doc, deprecation)
 * plus a bindings master/detail — a table of bindings and, below it, an editor for the selected one
 * (name, pull/push types, interpretation, required, default, doc, deprecation). Every edit mutates the
 * {@link MutableApiextModel} POJOs directly and marks the editor dirty.
 */
public class ElementBindingsPage extends FormPage {

	static final String ID = "org.objectstyle.wolips.editor.apiext.main";

	private final ApiextEditor _editor;
	private FormToolkit _toolkit;

	private TableViewer _bindingTable;
	private BindingDetail _detail;
	/** Suppresses dirty-marking while widgets are being populated programmatically. */
	private boolean _updating;

	public ElementBindingsPage(ApiextEditor editor) {
		super(editor, ID, "Element & Bindings");
		_editor = editor;
	}

	private MutableApiextModel model() {
		return _editor.getModel();
	}

	private void dirty() {
		if (_updating) {
			return;
		}
		final MutableApiextModel m = model();
		if (m != null) {
			m.markAsDirty();
			_editor.editorDirtyStateChanged();
		}
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		_toolkit = managedForm.getToolkit();
		final ScrolledForm form = managedForm.getForm();
		final MutableApiextModel m = model();
		form.setText(m != null && m.className != null && !m.className.isEmpty() ? m.className : "Element API");
		final Composite body = form.getBody();
		body.setLayout(new GridLayout());

		if (m == null) {
			_toolkit.createLabel(body, "This .apiext file could not be loaded.");
			return;
		}

		_updating = true;
		try {
			createElementSection(body, m);
			createBindingsSection(body, m);
		}
		finally {
			_updating = false;
		}
	}

	// ---- element header ----

	private void createElementSection(Composite parent, MutableApiextModel m) {
		final Section section = _toolkit.createSection(parent, Section.TITLE_BAR);
		section.setText("Element");
		section.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Composite c = _toolkit.createComposite(section, SWT.WRAP);
		c.setLayout(new GridLayout(2, false));
		section.setClient(c);

		_toolkit.createLabel(c, "Class:");
		final Text classText = _toolkit.createText(c, m.className, SWT.SINGLE);
		classText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		classText.addModifyListener(e -> { m.className = classText.getText(); dirty(); });

		_toolkit.createLabel(c, "Wraps content:");
		final Button wraps = _toolkit.createButton(c, "", SWT.CHECK);
		wraps.setSelection(m.wrapsContent);
		wraps.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) { m.wrapsContent = wraps.getSelection(); dirty(); }
		});

		_toolkit.createLabel(c, "Unknown attributes:");
		final Combo policy = new Combo(c, SWT.READ_ONLY);
		policy.setItems("(no policy)", "forbidden", "allowed", "passthrough");
		policy.select(policyIndex(m.unknownAttributes));
		_toolkit.adapt(policy);
		policy.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				m.unknownAttributes = policyFromIndex(policy.getSelectionIndex());
				dirty();
			}
		});

		_toolkit.createLabel(c, "Documentation:");
		final Text doc = _toolkit.createText(c, m.doc != null ? m.doc : "", SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		final GridData docData = new GridData(GridData.FILL_HORIZONTAL);
		docData.heightHint = 80;
		doc.setLayoutData(docData);
		doc.addModifyListener(e -> { m.doc = doc.getText(); dirty(); });

		createDeprecationControls(c, "Element deprecated:", m.deprecationNote,
				note -> { m.deprecationNote = note; dirty(); });
	}

	// ---- bindings master/detail ----

	private void createBindingsSection(Composite parent, MutableApiextModel m) {
		final Section section = _toolkit.createSection(parent, Section.TITLE_BAR);
		section.setText("Bindings");
		section.setLayoutData(new GridData(GridData.FILL_BOTH));
		final Composite c = _toolkit.createComposite(section);
		c.setLayout(new GridLayout(2, false));
		section.setClient(c);

		// Master: table + add/remove.
		final Composite masterCol = _toolkit.createComposite(c);
		masterCol.setLayout(new GridLayout(1, false));
		final GridData masterData = new GridData(GridData.FILL_VERTICAL);
		masterData.widthHint = 220;
		masterCol.setLayoutData(masterData);

		final Table table = _toolkit.createTable(masterCol, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		final GridData tableData = new GridData(GridData.FILL_BOTH);
		table.setLayoutData(tableData);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		_bindingTable = new TableViewer(table);
		_bindingTable.setContentProvider((org.eclipse.jface.viewers.IStructuredContentProvider) input -> m.bindings.toArray());

		// Column 1: a narrow direction glyph (↓ pull-only, ↑ push-only, ↕ two-way, ! none — likely
		// an error), so a missing pull/push is spottable at a glance.
		final org.eclipse.jface.viewers.TableViewerColumn dirCol = new org.eclipse.jface.viewers.TableViewerColumn(_bindingTable, SWT.CENTER);
		dirCol.getColumn().setText("Dir");
		dirCol.getColumn().setWidth(44);
		dirCol.setLabelProvider(new org.eclipse.jface.viewers.ColumnLabelProvider() {
			@Override public String getText(Object element) { return directionGlyph((MutableBinding) element); }
			@Override public String getToolTipText(Object element) { return directionTooltip((MutableBinding) element); }
			@Override public org.eclipse.swt.graphics.Color getForeground(Object element) {
				// Red-tint the "no direction" error marker so it stands out from the plain arrows.
				// A shared system color (never disposed — no resource leak).
				final MutableBinding b = (MutableBinding) element;
				if (b.pull.isEmpty() && b.push.isEmpty()) {
					return org.eclipse.swt.widgets.Display.getCurrent().getSystemColor(SWT.COLOR_RED);
				}
				return null;
			}
		});

		// Column 2: the binding name (required dot, struck-through-ish "(deprecated)" hint).
		final org.eclipse.jface.viewers.TableViewerColumn nameCol = new org.eclipse.jface.viewers.TableViewerColumn(_bindingTable, SWT.LEFT);
		nameCol.getColumn().setText("Binding");
		nameCol.getColumn().setWidth(170);
		nameCol.setLabelProvider(new org.eclipse.jface.viewers.ColumnLabelProvider() {
			@Override public String getText(Object element) {
				final MutableBinding b = (MutableBinding) element;
				return (b.required ? "• " : "") + b.name + (b.deprecationNote != null ? "  (deprecated)" : "");
			}
		});
		org.eclipse.jface.viewers.ColumnViewerToolTipSupport.enableFor(_bindingTable);
		_bindingTable.setInput(m);

		final Composite buttons = _toolkit.createComposite(masterCol);
		buttons.setLayout(new GridLayout(2, true));
		buttons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button add = _toolkit.createButton(buttons, "Add", SWT.PUSH);
		add.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button remove = _toolkit.createButton(buttons, "Remove", SWT.PUSH);
		remove.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		add.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final MutableBinding nb = new MutableBinding();
				nb.name = uniqueName(m, "newBinding");
				m.bindings.add(nb);
				_bindingTable.refresh();
				_bindingTable.setSelection(new StructuredSelection(nb));
				dirty();
			}
		});
		remove.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final MutableBinding sel = selectedBinding();
				if (sel != null) {
					m.bindings.remove(sel);
					_bindingTable.refresh();
					_detail.show(null);
					dirty();
				}
			}
		});

		// Detail: swaps to the selected binding.
		final Composite detailCol = _toolkit.createComposite(c);
		detailCol.setLayout(new GridLayout(1, false));
		detailCol.setLayoutData(new GridData(GridData.FILL_BOTH));
		_detail = new BindingDetail(detailCol);

		_bindingTable.addSelectionChangedListener(ev -> _detail.show(selectedBinding()));
	}

	private MutableBinding selectedBinding() {
		final IStructuredSelection sel = _bindingTable.getStructuredSelection();
		return sel.isEmpty() ? null : (MutableBinding) sel.getFirstElement();
	}

	/** The per-binding detail editor, rebuilt (shown/hidden fields) for the selected binding. */
	private final class BindingDetail {
		private final Composite _root;
		private MutableBinding _binding;
		private Text _name;
		private Text _pull;
		private Text _push;
		private Combo _interp;
		private Button _required;
		private Text _default;
		private Text _doc;

		BindingDetail(Composite parent) {
			_root = _toolkit.createComposite(parent);
			_root.setLayout(new GridLayout(1, false));
			_root.setLayoutData(new GridData(GridData.FILL_BOTH));
			build();
			show(null);
		}

		private void build() {
			// --- Identity (binding-level) ---
			final Composite identity = group("Binding", 2);
			_toolkit.createLabel(identity, "Name:");
			_name = _toolkit.createText(identity, "", SWT.SINGLE);
			_name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_name.addModifyListener(e -> { if (_binding != null && !_updating) { _binding.name = _name.getText(); _bindingTable.refresh(_binding); dirty(); } });

			_toolkit.createLabel(identity, "Required:");
			_required = _toolkit.createButton(identity, "", SWT.CHECK);
			_required.addSelectionListener(new SelectionAdapter() {
				@Override public void widgetSelected(SelectionEvent e) {
					if (_binding != null && !_updating) { _binding.required = _required.getSelection(); _bindingTable.refresh(_binding); dirty(); }
				}
			});

			// --- ↓ Pull: the read contract (types + interpretation) ---
			final Composite pull = group("↓ Pull", 2);
			_toolkit.createLabel(pull, "Types (comma-sep FQN):");
			_pull = _toolkit.createText(pull, "", SWT.SINGLE);
			_pull.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_pull.addModifyListener(e -> { if (_binding != null && !_updating) { setTypes(_binding.pull, _pull.getText()); _bindingTable.refresh(_binding); dirty(); } });

			_toolkit.createLabel(pull, "Interpretation:");
			_interp = new Combo(pull, SWT.READ_ONLY);
			_interp.setItems("(none)", "truthy");
			_toolkit.adapt(_interp);
			_interp.addSelectionListener(new SelectionAdapter() {
				@Override public void widgetSelected(SelectionEvent e) {
					if (_binding != null && !_updating && !_binding.pull.isEmpty()) {
						_binding.pull.get(0).interpretation = _interp.getSelectionIndex() == 1 ? "truthy" : null;
						dirty();
					}
				}
			});

			// --- ↑ Push: the write-back contract (types) ---
			final Composite push = group("↑ Push", 2);
			_toolkit.createLabel(push, "Types (comma-sep FQN):");
			_push = _toolkit.createText(push, "", SWT.SINGLE);
			_push.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_push.addModifyListener(e -> { if (_binding != null && !_updating) { setTypes(_binding.push, _push.getText()); _bindingTable.refresh(_binding); dirty(); } });

			// --- Documentation (binding-level metadata) ---
			final Composite meta = group("Documentation", 2);
			_toolkit.createLabel(meta, "Default value:");
			_default = _toolkit.createText(meta, "", SWT.SINGLE);
			_default.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_default.addModifyListener(e -> { if (_binding != null && !_updating) { _binding.defaultValue = emptyToNull(_default.getText()); dirty(); } });

			_toolkit.createLabel(meta, "Documentation:");
			_doc = _toolkit.createText(meta, "", SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
			final GridData docData = new GridData(GridData.FILL_HORIZONTAL);
			docData.heightHint = 80;
			_doc.setLayoutData(docData);
			_doc.addModifyListener(e -> { if (_binding != null && !_updating) { _binding.doc = emptyToNull(_doc.getText()); dirty(); } });

			createDeprecationControls(meta, "Binding deprecated:", null, note -> {
				if (_binding != null && !_updating) { _binding.deprecationNote = note; _bindingTable.refresh(_binding); dirty(); }
			});
		}

		/** A titled group box under the detail root, laid out with the given column count. */
		private Composite group(String title, int columns) {
			final org.eclipse.swt.widgets.Group g = new org.eclipse.swt.widgets.Group(_root, SWT.NONE);
			g.setText(title);
			g.setLayout(new GridLayout(columns, false));
			g.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_toolkit.adapt(g);
			return g;
		}

		void show(MutableBinding b) {
			_binding = b;
			_updating = true;
			try {
				final boolean has = b != null;
				_name.setEnabled(has);
				_pull.setEnabled(has);
				_push.setEnabled(has);
				_interp.setEnabled(has);
				_required.setEnabled(has);
				_default.setEnabled(has);
				_doc.setEnabled(has);
				_name.setText(has && b.name != null ? b.name : "");
				_pull.setText(has ? typeList(b.pull) : "");
				_push.setText(has ? typeList(b.push) : "");
				_interp.select(has && !b.pull.isEmpty() && "truthy".equals(b.pull.get(0).interpretation) ? 1 : 0);
				_required.setSelection(has && b.required);
				_default.setText(has && b.defaultValue != null ? b.defaultValue : "");
				_doc.setText(has && b.doc != null ? b.doc : "");
			}
			finally {
				_updating = false;
			}
		}
	}

	// ---- shared helpers ----

	/** A "deprecated" checkbox + note field pair. `onChange` receives null (not deprecated) or the note. */
	private void createDeprecationControls(Composite parent, String label, String initialNote,
			java.util.function.Consumer<String> onChange) {
		_toolkit.createLabel(parent, label);
		final Composite row = _toolkit.createComposite(parent);
		row.setLayout(new GridLayout(2, false));
		row.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button check = _toolkit.createButton(row, "Deprecated", SWT.CHECK);
		check.setSelection(initialNote != null);
		final Text note = _toolkit.createText(row, initialNote != null ? initialNote : "", SWT.SINGLE);
		note.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		note.setEnabled(initialNote != null);
		note.setMessage("migration note");
		check.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final boolean on = check.getSelection();
				note.setEnabled(on);
				onChange.accept(on ? note.getText() : null);
			}
		});
		note.addModifyListener(e -> { if (check.getSelection()) { onChange.accept(note.getText()); } });
	}

	/** ↓ pull-only, ↑ push-only, ↓↑ two-way, ! neither (a binding with no direction — likely an error). */
	private static String directionGlyph(MutableBinding b) {
		final boolean pull = !b.pull.isEmpty();
		final boolean push = !b.push.isEmpty();
		if (pull && push) return "↓↑"; // two arrows — clearer than a single small ↕
		if (pull) return "↓";
		if (push) return "↑";
		return "!";
	}

	private static String directionTooltip(MutableBinding b) {
		final boolean pull = !b.pull.isEmpty();
		final boolean push = !b.push.isEmpty();
		if (pull && push) return "Two-way: pulled and pushed";
		if (pull) return "Pull only (read)";
		if (push) return "Push only (written back)";
		return "No pull or push type declared";
	}

	private static int policyIndex(UnknownAttributes p) {
		if (p == UnknownAttributes.FORBIDDEN) return 1;
		if (p == UnknownAttributes.ALLOWED) return 2;
		if (p == UnknownAttributes.PASSTHROUGH) return 3;
		return 0;
	}

	private static UnknownAttributes policyFromIndex(int i) {
		switch (i) {
		case 1: return UnknownAttributes.FORBIDDEN;
		case 2: return UnknownAttributes.ALLOWED;
		case 3: return UnknownAttributes.PASSTHROUGH;
		default: return null;
		}
	}

	private static String typeList(List<MutableType> types) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < types.size(); i++) {
			if (i > 0) b.append(", ");
			b.append(types.get(i).name);
		}
		return b.toString();
	}

	/** Rewrites a direction's type list from a comma-separated string, preserving the first interpretation. */
	private static void setTypes(List<MutableType> target, String csv) {
		final String interp = target.isEmpty() ? null : target.get(0).interpretation;
		target.clear();
		boolean first = true;
		for (final String raw : csv.split(",")) {
			final String name = raw.trim();
			if (!name.isEmpty()) {
				target.add(new MutableType(name, first ? interp : null));
				first = false;
			}
		}
	}

	private static String uniqueName(MutableApiextModel m, String base) {
		String name = base;
		int n = 1;
		while (hasBinding(m, name)) {
			name = base + (++n);
		}
		return name;
	}

	private static boolean hasBinding(MutableApiextModel m, String name) {
		for (final MutableBinding b : m.bindings) {
			if (name.equals(b.name)) {
				return true;
			}
		}
		return false;
	}

	private static String emptyToNull(String s) {
		return (s == null || s.isEmpty()) ? null : s;
	}
}

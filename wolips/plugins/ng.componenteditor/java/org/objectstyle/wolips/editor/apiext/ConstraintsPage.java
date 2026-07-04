package org.objectstyle.wolips.editor.apiext;

import java.util.ArrayList;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.objectstyle.wolips.bindings.api.ApiextConstraintMessages;
import org.objectstyle.wolips.bindings.api.ApiextModel;
import org.objectstyle.wolips.bindings.api.ApiextModel.Obligation;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableChoose;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableConstraint;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableRequires;

/**
 * The constraint builder — the novel, high-value page. Constraints are listed by their generated
 * message (the same sentence the hover shows, via {@link ApiextConstraintMessages}). Adding/editing a
 * constraint picks bindings from <b>checkboxes of the declared binding names</b>, so a constraint can
 * only ever reference a real binding (reference integrity by construction), with a live preview of the
 * generated message.
 */
public class ConstraintsPage extends FormPage {

	static final String ID = "org.objectstyle.wolips.editor.apiext.constraints";

	private final ApiextEditor _editor;
	private FormToolkit _toolkit;
	private TableViewer _list;
	private ConstraintDetail _detail;
	private boolean _updating;

	public ConstraintsPage(ApiextEditor editor) {
		super(editor, ID, "Constraints");
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
		refreshList();
	}

	private void refreshList() {
		if (_list != null && !_list.getControl().isDisposed()) {
			_list.refresh();
		}
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		_toolkit = managedForm.getToolkit();
		final ScrolledForm form = managedForm.getForm();
		form.setText("Constraints");
		final MutableApiextModel m = model();
		final Composite body = form.getBody();
		body.setLayout(new GridLayout());
		if (m == null) {
			_toolkit.createLabel(body, "This .apiext file could not be loaded.");
			return;
		}

		final Section section = _toolkit.createSection(body, Section.TITLE_BAR);
		section.setText("Cross-binding constraints");
		section.setLayoutData(new GridData(GridData.FILL_BOTH));
		final Composite c = _toolkit.createComposite(section);
		c.setLayout(new GridLayout(2, false));
		section.setClient(c);

		// Master: list of constraints (shown as generated messages) + add-choose / add-requires / remove.
		final Composite masterCol = _toolkit.createComposite(c);
		masterCol.setLayout(new GridLayout(1, false));
		final GridData masterData = new GridData(GridData.FILL_BOTH);
		masterData.widthHint = 320;
		masterCol.setLayoutData(masterData);

		final Table table = _toolkit.createTable(masterCol, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		_list = new TableViewer(table);
		_list.setContentProvider((org.eclipse.jface.viewers.IStructuredContentProvider) in -> m.constraints.toArray());
		_list.setLabelProvider(new org.eclipse.jface.viewers.LabelProvider() {
			@Override public String getText(Object element) {
				return describe((MutableConstraint) element);
			}
		});
		_list.setInput(m);

		final Composite buttons = _toolkit.createComposite(masterCol);
		buttons.setLayout(new GridLayout(3, true));
		buttons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button addChoose = _toolkit.createButton(buttons, "Add choose", SWT.PUSH);
		addChoose.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button addRequires = _toolkit.createButton(buttons, "Add requires", SWT.PUSH);
		addRequires.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button remove = _toolkit.createButton(buttons, "Remove", SWT.PUSH);
		remove.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		addChoose.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final MutableChoose ch = new MutableChoose();
				ch.min = 1; ch.max = 1;
				m.constraints.add(ch);
				dirty();
				_list.setSelection(new StructuredSelection(ch));
				_detail.show(ch);
			}
		});
		addRequires.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final MutableRequires r = new MutableRequires();
				m.constraints.add(r);
				dirty();
				_list.setSelection(new StructuredSelection(r));
				_detail.show(r);
			}
		});
		remove.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final MutableConstraint sel = selected();
				if (sel != null) { m.constraints.remove(sel); dirty(); _detail.show(null); }
			}
		});

		// Detail: the builder for the selected constraint.
		final Composite detailCol = _toolkit.createComposite(c);
		detailCol.setLayout(new GridLayout(1, false));
		detailCol.setLayoutData(new GridData(GridData.FILL_BOTH));
		_detail = new ConstraintDetail(detailCol);

		_list.addSelectionChangedListener(ev -> _detail.show(selected()));
	}

	private MutableConstraint selected() {
		final IStructuredSelection sel = _list.getStructuredSelection();
		return sel.isEmpty() ? null : (MutableConstraint) sel.getFirstElement();
	}

	/** Generated message for a mutable constraint, via a throwaway immutable snapshot of just that rule. */
	private String describe(MutableConstraint c) {
		if (c.message != null && !c.message.isEmpty()) {
			return c.message;
		}
		try {
			final ApiextModel snapshot = model().toImmutable();
			// Match by position: the immutable constraints are in the same order as the mutable ones.
			final int idx = model().constraints.indexOf(c);
			if (idx >= 0 && idx < snapshot.getConstraints().size()) {
				return ApiextConstraintMessages.describe(snapshot.getConstraints().get(idx));
			}
		}
		catch (Exception ignore) {
			// fall through
		}
		return "(incomplete constraint)";
	}

	private List<String> bindingNames() {
		final List<String> names = new ArrayList<>();
		final MutableApiextModel m = model();
		if (m != null) {
			for (final MutableBinding b : m.bindings) {
				if (b.name != null && !b.name.isEmpty()) {
					names.add(b.name);
				}
			}
		}
		return names;
	}

	/** The builder for the selected constraint — a choose (min/max + alternative checkboxes) or a
	 *  requires (consequent + obligation + antecedent checkboxes), with a live message preview. */
	private final class ConstraintDetail {
		private final Composite _root;
		private Composite _body;
		private Label _preview;

		ConstraintDetail(Composite parent) {
			_root = _toolkit.createComposite(parent);
			_root.setLayout(new GridLayout(1, false));
			_root.setLayoutData(new GridData(GridData.FILL_BOTH));
			show(null);
		}

		void show(MutableConstraint c) {
			if (_body != null && !_body.isDisposed()) {
				_body.dispose();
			}
			_body = _toolkit.createComposite(_root);
			_body.setLayout(new GridLayout(1, false));
			_body.setLayoutData(new GridData(GridData.FILL_BOTH));
			if (c instanceof MutableChoose) {
				buildChoose((MutableChoose) c);
			}
			else if (c instanceof MutableRequires) {
				buildRequires((MutableRequires) c);
			}
			else {
				_toolkit.createLabel(_body, "Select or add a constraint.");
			}
			_root.layout(true, true);
			updatePreview(c);
		}

		private void buildChoose(MutableChoose ch) {
			final Composite minMax = _toolkit.createComposite(_body);
			minMax.setLayout(new GridLayout(4, false));
			_toolkit.createLabel(minMax, "min:");
			final Spinner min = new Spinner(minMax, SWT.BORDER);
			min.setMinimum(0); min.setMaximum(99);
			min.setSelection(ch.min != null ? ch.min : 0);
			_toolkit.adapt(min);
			_toolkit.createLabel(minMax, "max:");
			final Spinner max = new Spinner(minMax, SWT.BORDER);
			max.setMinimum(0); max.setMaximum(99);
			max.setSelection(ch.max != null ? ch.max : 99);
			_toolkit.adapt(max);
			min.addModifyListener(e -> { ch.min = min.getSelection(); dirty(); updatePreview(ch); });
			max.addModifyListener(e -> { ch.max = max.getSelection() >= 99 ? null : max.getSelection(); dirty(); updatePreview(ch); });

			_toolkit.createLabel(_body, "Alternatives — single bindings (pick declared bindings):");
			for (final String name : bindingNames()) {
				final Button cb = _toolkit.createButton(_body, name, SWT.CHECK);
				cb.setSelection(containsSingle(ch.alternatives, name));
				cb.addSelectionListener(new SelectionAdapter() {
					@Override public void widgetSelected(SelectionEvent e) {
						if (cb.getSelection()) {
							ch.alternatives.add(new ArrayList<>(List.of(name)));
						}
						else {
							ch.alternatives.removeIf(alt -> alt.size() == 1 && alt.get(0).equals(name));
						}
						dirty(); updatePreview(ch);
					}
				});
			}

			// Any-of alternatives — each counts as ONE alternative, satisfied if any member is bound.
			_toolkit.createLabel(_body, "Alternatives — any-of groups (each counts as one):");
			for (int i = 0; i < ch.alternatives.size(); i++) {
				final List<String> alt = ch.alternatives.get(i);
				if (alt.size() < 2) {
					continue; // single-binding alternatives are shown as checkboxes above
				}
				final Composite groupRow = _toolkit.createComposite(_body);
				groupRow.setLayout(new GridLayout(2, false));
				groupRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				_toolkit.createLabel(groupRow, "(" + String.join(" or ", alt) + ")");
				final Button del = _toolkit.createButton(groupRow, "Remove group", SWT.PUSH);
				del.addSelectionListener(new SelectionAdapter() {
					@Override public void widgetSelected(SelectionEvent e) { ch.alternatives.remove(alt); rebuild(ch); dirty(); }
				});
			}
			final Button addGroup = _toolkit.createButton(_body, "+ Add any-of group…", SWT.PUSH);
			addGroup.addSelectionListener(new SelectionAdapter() {
				@Override public void widgetSelected(SelectionEvent e) {
					final List<String> chosen = pickBindings("Any-of group", "Pick two or more bindings; the group is satisfied if any is bound.");
					if (chosen.size() >= 2) {
						ch.alternatives.add(new ArrayList<>(chosen));
						rebuild(ch);
						dirty();
					}
				}
			});

			addMessageOverride(ch);
			addPreview();
		}

		/** Re-render the detail for the given constraint (after a structural change like adding a group). */
		private void rebuild(MutableConstraint c) {
			show(c);
			updatePreview(c);
		}

		private void buildRequires(MutableRequires r) {
			final Composite row = _toolkit.createComposite(_body);
			row.setLayout(new GridLayout(4, false));
			_toolkit.createLabel(row, "binding:");
			final Combo consequent = new Combo(row, SWT.READ_ONLY);
			final List<String> names = bindingNames();
			consequent.setItems(names.toArray(new String[0]));
			final int cidx = names.indexOf(r.binding);
			if (cidx >= 0) consequent.select(cidx);
			_toolkit.adapt(consequent);
			consequent.addSelectionListener(new SelectionAdapter() {
				@Override public void widgetSelected(SelectionEvent e) { r.binding = consequent.getText(); dirty(); updatePreview(r); }
			});
			_toolkit.createLabel(row, "must be:");
			final Combo must = new Combo(row, SWT.READ_ONLY);
			must.setItems("bound", "settable", "gettable");
			must.select(r.must == Obligation.SETTABLE ? 1 : r.must == Obligation.GETTABLE ? 2 : 0);
			_toolkit.adapt(must);
			must.addSelectionListener(new SelectionAdapter() {
				@Override public void widgetSelected(SelectionEvent e) {
					r.must = must.getSelectionIndex() == 1 ? Obligation.SETTABLE : must.getSelectionIndex() == 2 ? Obligation.GETTABLE : Obligation.BOUND;
					dirty(); updatePreview(r);
				}
			});

			_toolkit.createLabel(_body, "when any of (empty = unconditional):");
			for (final String name : names) {
				final Button cb = _toolkit.createButton(_body, name, SWT.CHECK);
				cb.setSelection(r.when.contains(name));
				cb.addSelectionListener(new SelectionAdapter() {
					@Override public void widgetSelected(SelectionEvent e) {
						if (cb.getSelection()) { if (!r.when.contains(name)) r.when.add(name); }
						else { r.when.remove(name); }
						dirty(); updatePreview(r);
					}
				});
			}

			addMessageOverride(r);
			addPreview();
		}

		private void addMessageOverride(MutableConstraint c) {
			_toolkit.createLabel(_body, "Message override (optional):");
			final org.eclipse.swt.widgets.Text msg = _toolkit.createText(_body, c.message != null ? c.message : "", SWT.SINGLE);
			msg.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			msg.addModifyListener(e -> { c.message = msg.getText().isEmpty() ? null : msg.getText(); dirty(); updatePreview(c); });
		}

		private void addPreview() {
			final Label sep = _toolkit.createLabel(_body, "Preview:");
			sep.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			_preview = _toolkit.createLabel(_body, "", SWT.WRAP);
			_preview.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		}

		private void updatePreview(MutableConstraint c) {
			if (_preview != null && !_preview.isDisposed() && c != null) {
				_preview.setText(describe(c));
				_body.layout(true, true);
			}
		}
	}

	private static boolean containsSingle(List<List<String>> alts, String name) {
		for (final List<String> alt : alts) {
			if (alt.size() == 1 && alt.get(0).equals(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Prompts for a subset of the declared binding names (a checkbox per binding). Returns the chosen
	 * names, or an empty list if cancelled — reference integrity by construction: only declared
	 * bindings can be picked.
	 */
	private List<String> pickBindings(String title, String message) {
		final List<String> names = bindingNames();
		final List<String> chosen = new ArrayList<>();
		final org.eclipse.jface.dialogs.Dialog dialog =
				new org.eclipse.jface.dialogs.Dialog(getSite().getShell()) {
			private final List<Button> _checks = new ArrayList<>();

			@Override
			protected org.eclipse.swt.widgets.Control createDialogArea(Composite parent) {
				final Composite area = (Composite) super.createDialogArea(parent);
				new Label(area, SWT.WRAP).setText(message);
				for (final String name : names) {
					final Button cb = new Button(area, SWT.CHECK);
					cb.setText(name);
					_checks.add(cb);
				}
				return area;
			}

			@Override
			protected void configureShell(org.eclipse.swt.widgets.Shell shell) {
				super.configureShell(shell);
				shell.setText(title);
			}

			@Override
			protected void okPressed() {
				for (int i = 0; i < _checks.size(); i++) {
					if (_checks.get(i).getSelection()) {
						chosen.add(names.get(i));
					}
				}
				super.okPressed();
			}
		};
		dialog.open();
		return chosen;
	}
}

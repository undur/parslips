/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2005 The ObjectStyle Group,
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */
package org.objectstyle.wolips.editor.api;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IDetailsPage;
import org.eclipse.ui.forms.IFormPart;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiValidation;
import org.objectstyle.wolips.bindings.api.IApiBinding;
import org.objectstyle.wolips.bindings.api.SimpleApiBinding;

/**
 * Details page for editing the properties of a selected binding in the
 * {@code .api} editor.
 *
 * <p>Displays and edits:
 * <ul>
 *   <li>Binding name (text field)
 *   <li>Value set / defaults (dropdown)
 *   <li>Required flag (checkbox)
 *   <li>Will Set flag (checkbox)
 * </ul>
 *
 * <p>Mutations go directly to the {@link SimpleApiBinding} POJO. The model
 * is marked as dirty so the editor knows to enable Save.
 */
public class BindingDetailsPage implements IDetailsPage {
	IManagedForm _managedForm;

	/** The currently-selected binding, or null if nothing is selected. */
	SimpleApiBinding _binding;

	/**
	 * Guard flag to suppress listener-driven dirty marking during programmatic
	 * widget updates. Without this, {@link #update()} → {@code _name.setText()}
	 * fires the {@code ModifyListener}, which calls {@link #markDirty()},
	 * re-dirtying the model immediately after save.
	 */
	boolean _updating;

	Text _name;
	Button _requiredFlag;
	Button _willSetFlag;
	Combo _valueSetCombo;

	public BindingDetailsPage() {
		super();
	}

	public void initialize(IManagedForm form) {
		this._managedForm = form;
	}

	public void createContents(Composite parent) {
		TableWrapLayout layout = new TableWrapLayout();
		layout.topMargin = 5;
		layout.leftMargin = 5;
		layout.rightMargin = 2;
		layout.bottomMargin = 2;
		parent.setLayout(layout);

		FormToolkit toolkit = _managedForm.getToolkit();
		Section s1 = toolkit.createSection(parent, ExpandableComposite.TITLE_BAR);
		s1.marginWidth = 10;
		s1.setText("Binding Details");
		s1.setDescription("Set the properties of the selected binding.");
		TableWrapData td = new TableWrapData(TableWrapData.FILL, TableWrapData.TOP);
		td.grabHorizontal = true;
		s1.setLayoutData(td);
		Composite client = toolkit.createComposite(s1);
		GridLayout glayout = new GridLayout();
		glayout.marginWidth = glayout.marginHeight = 0;
		glayout.numColumns = 2;
		client.setLayout(glayout);

		SelectionListener choiceListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (_binding != null && !_updating) {
					int selectedIndex = ((Combo)e.widget).getSelectionIndex();
					_binding.setDefaults(selectedIndex);
					markDirty();
				}
			}
		};

		GridData gd;
		toolkit.createLabel(client, "Name:");
		_name = toolkit.createText(client, "", SWT.SINGLE);
		_name.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (_binding != null && !_updating) {
					try {
						_binding.setName(_name.getText());
					}
					catch (Throwable t) {
						// Name conflict — append timestamp to make unique
						_binding.setName(_name.getText() + System.currentTimeMillis());
					}
					// Notify snapshot that the name map needs rebuilding
					try {
						getApiEditor().getModel().getSnapshot().bindingNameChanged();
					} catch (ApiModelException ex) {
						// Best effort
					}
					markDirty();
				}
			}
		});
		gd = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
		gd.widthHint = 10;
		_name.setLayoutData(gd);

		toolkit.createLabel(client, "Value Set:");
		this._valueSetCombo = new Combo(client, SWT.READ_ONLY);
		_valueSetCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		for (int i = 0; i < IApiBinding.ALL_DEFAULTS.length; i++) {
			_valueSetCombo.add(IApiBinding.ALL_DEFAULTS[i]);
		}
		_valueSetCombo.addSelectionListener(choiceListener);

		createSpacer(toolkit, client, 2);

		_requiredFlag = toolkit.createButton(client, "Required", SWT.CHECK);
		_requiredFlag.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (_binding != null) {
					boolean isRequired = _requiredFlag.getSelection();
					_binding.setRequired(isRequired);
					_binding.setExplicitlyRequired(isRequired);
					// When unchecking required, also remove any implicit
					// <unbound> validation rule for this binding
					if (!isRequired) {
						try {
							getApiEditor().getModel().getSnapshot()
								.removeImplicitValidation(_binding.getName(), ApiValidation.Kind.UNBOUND);
						} catch (ApiModelException ex) {
							// Best effort
						}
					}
					markDirty();
				}
			}
		});
		gd = new GridData();
		gd.horizontalSpan = 2;
		_requiredFlag.setLayoutData(gd);

		_willSetFlag = toolkit.createButton(client, "Will Set", SWT.CHECK);
		_willSetFlag.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (_binding != null) {
					boolean isWillSet = _willSetFlag.getSelection();
					_binding.setWillSet(isWillSet);
					_binding.setExplicitlySettable(isWillSet);
					// When unchecking willSet, also remove any implicit
					// <unsettable> validation rule for this binding
					if (!isWillSet) {
						try {
							getApiEditor().getModel().getSnapshot()
								.removeImplicitValidation(_binding.getName(), ApiValidation.Kind.UNSETTABLE);
						} catch (ApiModelException ex) {
							// Best effort
						}
					}
					markDirty();
				}
			}
		});
		gd = new GridData();
		gd.horizontalSpan = 2;
		_willSetFlag.setLayoutData(gd);

		createSpacer(toolkit, client, 2);

		toolkit.paintBordersFor(s1);
		s1.setClient(client);
	}

	private void createSpacer(FormToolkit toolkit, Composite parent, int span) {
		Label spacer = toolkit.createLabel(parent, "");
		GridData gd = new GridData();
		gd.horizontalSpan = span;
		spacer.setLayoutData(gd);
	}

	/**
	 * Populates the detail widgets from the currently-selected binding.
	 * Sets {@link #_updating} to suppress listener-driven dirty marking
	 * during programmatic widget updates.
	 */
	private void update() {
		_updating = true;
		try {
			int selectedDefaults = _binding.getSelectedDefaults();
			this._valueSetCombo.select(selectedDefaults);

			_requiredFlag.setSelection(_binding != null && _binding.isRequired());
			_willSetFlag.setSelection(_binding != null && _binding.isWillSet());
			_name.setText(_binding != null && _binding.getName() != null ? _binding.getName() : "");
		} finally {
			_updating = false;
		}
	}

	/**
	 * Marks the model as dirty, refreshes the master binding list, and
	 * notifies both the managed form and the enclosing editor.
	 *
	 * <p>The master list refresh ensures that name edits and required/will-set
	 * toggles are immediately visible in the binding list (bold formatting for
	 * required bindings, updated names) without waiting for a save.
	 *
	 * <p>{@code dirtyStateChanged()} tells the managed form its parts are dirty,
	 * but doesn't fire {@code PROP_DIRTY} to the workbench. We must also call
	 * {@code editorDirtyStateChanged()} so that the {@code ComponentEditorPart}
	 * (which wraps the {@code ApiEditor}) picks up the change and enables Save.
	 */
	private void markDirty() {
		try {
			getApiEditor().getModel().markAsDirty();
		} catch (ApiModelException e) {
			// Best effort
		}

		// Refresh the master binding list so it reflects the change
		// immediately (updated name text, bold/normal for required flag)
		BindingsPage bindingsPage = (BindingsPage) _managedForm.getContainer();
		bindingsPage.block.refreshBindingList();

		_managedForm.dirtyStateChanged();
		getApiEditor().editorDirtyStateChanged();
	}

	/**
	 * Returns the enclosing {@link ApiEditor}.
	 *
	 * <p>The managed form's container is the {@link BindingsPage} (a FormPage),
	 * not the editor itself. We must call {@code getEditor()} on the page to
	 * reach the {@link ApiEditor}.
	 */
	private ApiEditor getApiEditor() {
		FormPage page = (FormPage) _managedForm.getContainer();
		return (ApiEditor) page.getEditor();
	}

	public void selectionChanged(IFormPart part, ISelection selection) {
		IStructuredSelection ssel = (IStructuredSelection) selection;
		if (ssel.size() == 1) {
			_binding = (SimpleApiBinding) ssel.getFirstElement();
		} else {
			_binding = null;
		}
		update();
	}

	public void commit(boolean onSave) {
		// nothing to do — mutations go directly to the POJO
	}

	public void setFocus() {
		_name.setFocus();
	}

	public void dispose() {
		// nothing to dispose
	}

	public boolean isDirty() {
		try {
			return getApiEditor().getModel().isDirty();
		} catch (ApiModelException e) {
			return false;
		}
	}

	public boolean isStale() {
		return false;
	}

	public void refresh() {
		update();
	}

	public boolean setFormInput(Object input) {
		return false;
	}
}

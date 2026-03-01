package tk.eclipse.plugin.htmleditor;


import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.preferences.PreferenceConstants;

/**
 * Preference page for template formatting settings.
 * <p>
 * Controls indentation style (tabs vs. spaces), indent size, and
 * spaces-around-equals in formatted output. These preferences are read by
 * {@code FormatRefactoring} and {@code XMLEditor} when formatting templates.
 * <p>
 * The indentation preferences ({@code INDENT_TABS}, {@code INDENT_SIZE}) have
 * existed in {@link PreferenceConstants} since the WOLips era but were never
 * exposed in the UI — this page now surfaces them.
 *
 * @author Naoki Takezoe
 * @since 2.0.3
 */
public class XMLPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private IWorkbench _workbench;

	/** Checkbox: use tabs instead of spaces for indentation. */
	private Button _indentTabs;

	/** Spinner: number of spaces per indent level (disabled when using tabs). */
	private Spinner _indentSize;

	/** Label for the indent size spinner (dimmed when using tabs). */
	private Label _indentSizeLabel;

	private Button _spacesAroundEquals;
	private Button _enableClassName;
	private List _classNameAttrs;
	private Button _addClassName;
	private Button _removeClassName;

	public XMLPreferencePage() {
		super(HTMLPlugin.getResourceString("HTMLEditorPreferencePage.XML"));
		setPreferenceStore(HTMLPlugin.getDefault().getPreferenceStore());
	}

	/**
	 * Creates contents of the preference page.
	 *
	 * @param parent the parent {@code Composite}
	 * @return the created {@code Control} which contains contents.
	 */
	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		composite.setLayout(new GridLayout(2, false));

		IPreferenceStore formattingPrefs = Activator.getDefault().getPreferenceStore();

		// --- Indentation section ---

		// "Use tabs" checkbox
		_indentTabs = new Button(composite, SWT.CHECK);
		_indentTabs.setText("Indent with tabs");
		_indentTabs.setSelection(formattingPrefs.getBoolean(PreferenceConstants.INDENT_TABS));
		_indentTabs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateIndentControls();
			}
		});
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		_indentTabs.setLayoutData(gd);

		// Indent size: label + spinner on the same row
		_indentSizeLabel = new Label(composite, SWT.NONE);
		_indentSizeLabel.setText("Indent size (spaces):");
		_indentSizeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		_indentSize = new Spinner(composite, SWT.BORDER);
		_indentSize.setMinimum(1);
		_indentSize.setMaximum(8);
		_indentSize.setSelection(formattingPrefs.getInt(PreferenceConstants.INDENT_SIZE));
		_indentSize.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		// Disable the spinner when tabs are selected
		updateIndentControls();

		// --- Formatting section ---

		// Spaces around equals checkbox
		_spacesAroundEquals = new Button(composite, SWT.CHECK);
		_spacesAroundEquals.setText(HTMLPlugin.getResourceString("HTMLEditorPreferencePage.SpacesAroundEquals"));
		_spacesAroundEquals.setSelection(formattingPrefs.getBoolean(PreferenceConstants.SPACES_AROUND_EQUALS));
		gd = new GridData();
		gd.horizontalSpan = 2;
		_spacesAroundEquals.setLayoutData(gd);

		// --- Class name attributes section ---

		// checkbox to toggle the classname support
		_enableClassName = new Button(composite, SWT.CHECK);
		_enableClassName.setText(HTMLPlugin.getResourceString("HTMLEditorPreferencePage.EnableClassName"));
		_enableClassName.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e){
				updateControls();
			}
		});
		gd = new GridData();
		gd.horizontalSpan = 2;
		_enableClassName.setLayoutData(gd);


		// listbox
		_classNameAttrs = new List(composite, SWT.BORDER|SWT.MULTI|SWT.V_SCROLL);
		_classNameAttrs.setLayoutData(new GridData(GridData.FILL_BOTH));
		_classNameAttrs.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e){
				updateControls();
			}
		});

		Composite buttons = new Composite(composite, SWT.NULL);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		buttons.setLayout(layout);
		buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));

		_addClassName = new Button(buttons, SWT.PUSH);
		_addClassName.setText(HTMLPlugin.getResourceString("HTMLEditorPreferencePage.AddAttribute"));
		_addClassName.setLayoutData(createButtonGridData());
		_addClassName.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e){
				InputDialog dialog = new InputDialog(
						_workbench.getActiveWorkbenchWindow().getShell(),
						HTMLPlugin.getResourceString("HTMLEditorPreferencePage.Dialog.Title"),
						HTMLPlugin.getResourceString("HTMLEditorPreferencePage.Dialog.Message"),
						"",
						new IInputValidator(){
							public String isValid(String newText) {
								return newText.length()==0 ?
										HTMLPlugin.getResourceString("HTMLEditorPreferencePage.Dialog.Error") : null;
							}
				});
				if(dialog.open()==InputDialog.OK){
					_classNameAttrs.add(dialog.getValue());
				}
			}
		});

		_removeClassName = new Button(buttons, SWT.PUSH);
		_removeClassName.setText(HTMLPlugin.getResourceString("HTMLEditorPreferencePage.RemoveAttribute"));
		_removeClassName.setLayoutData(createButtonGridData());
		_removeClassName.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e){
				_classNameAttrs.remove(_classNameAttrs.getSelectionIndices());
			}
		});

		// fill initial values
		IPreferenceStore store = getPreferenceStore();
		_enableClassName.setSelection(
				store.getBoolean(HTMLPlugin.PREF_ENABLE_CLASSNAME));
		String[] values = StringConverter.asArray(
				store.getString(HTMLPlugin.PREF_CLASSNAME_ATTRS));
		for(int i=0;i<values.length;i++){
			_classNameAttrs.add(values[i]);
		}

		updateControls();
		return composite;
	}

	/**
	 * Enables/disables the indent size spinner based on the tabs checkbox.
	 * When using tabs, the number of spaces is irrelevant.
	 */
	private void updateIndentControls() {
		boolean useTabs = _indentTabs.getSelection();
		_indentSize.setEnabled(!useTabs);
		_indentSizeLabel.setEnabled(!useTabs);
	}

	/**
	 * Updates controls status for the class name attributes section.
	 */
	private void updateControls(){
		boolean enableClassName = this._enableClassName.getSelection();
		_classNameAttrs.setEnabled(enableClassName);
		_addClassName.setEnabled(enableClassName);
		_removeClassName.setEnabled(enableClassName);
		if(enableClassName){
			_removeClassName.setEnabled(_classNameAttrs.getSelectionCount()>0);
		}
	}

	/**
	 * Creates the {@code GridData} for buttons.
	 *
	 * @return the {@code GridData} which is configured for buttons
	 */
	private static GridData createButtonGridData(){
		return new GridData(SWT.FILL, SWT.DEFAULT, true, false);
	}

	/**
	 * Initializes the preference page.
	 *
	 * @param workbench the {@code IWorkbench} instance
	 */
	public void init(IWorkbench workbench) {
		this._workbench = workbench;
	}

	@Override
	protected void performDefaults() {
		IPreferenceStore store = getPreferenceStore();

		// Restore formatting defaults from the Activator's preference store.
		IPreferenceStore formattingPrefs = Activator.getDefault().getPreferenceStore();
		_indentTabs.setSelection(formattingPrefs.getDefaultBoolean(PreferenceConstants.INDENT_TABS));
		_indentSize.setSelection(formattingPrefs.getDefaultInt(PreferenceConstants.INDENT_SIZE));
		_spacesAroundEquals.setSelection(formattingPrefs.getDefaultBoolean(PreferenceConstants.SPACES_AROUND_EQUALS));
		updateIndentControls();

		_enableClassName.setSelection(
				store.getDefaultBoolean(HTMLPlugin.PREF_ENABLE_CLASSNAME));
		String[] values = StringConverter.asArray(
				store.getDefaultString(HTMLPlugin.PREF_CLASSNAME_ATTRS));
		_classNameAttrs.removeAll();
		for(int i=0;i<values.length;i++){
			_classNameAttrs.add(values[i]);
		}
	}

	@Override
	public boolean performOk() {
		// Save formatting preferences to the Activator's store (where
		// FormatRefactoring and XMLEditor read them from).
		IPreferenceStore formattingPrefs = Activator.getDefault().getPreferenceStore();
		formattingPrefs.setValue(PreferenceConstants.INDENT_TABS, _indentTabs.getSelection());
		formattingPrefs.setValue(PreferenceConstants.INDENT_SIZE, _indentSize.getSelection());
		formattingPrefs.setValue(PreferenceConstants.SPACES_AROUND_EQUALS, _spacesAroundEquals.getSelection());

		// Save class name preferences to this page's store.
		IPreferenceStore store = getPreferenceStore();
		store.setValue(HTMLPlugin.PREF_ENABLE_CLASSNAME, _enableClassName.getSelection());

		String[] items = _classNameAttrs.getItems();
		StringBuffer sb = new StringBuffer();
		for(int i=0;i<items.length;i++){
			if(i!=0){
				sb.append(" ");
			}
			sb.append(items[i]);
		}
		store.setValue(HTMLPlugin.PREF_CLASSNAME_ATTRS, sb.toString());

		return true;
	}

}

package org.objectstyle.wolips.editor.apiext;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;

/**
 * A read-only view of the {@code .apiext} XML the form currently represents — regenerated from the
 * model each time the page is shown. The form is where you edit; this is where you verify. Read-only
 * by design (form-primary editor: no form↔source sync).
 */
public class SourcePage extends FormPage {

	static final String ID = "org.objectstyle.wolips.editor.apiext.source";

	private final ApiextEditor _editor;
	private Text _text;

	public SourcePage(ApiextEditor editor) {
		super(editor, ID, "Source");
		_editor = editor;
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		final ScrolledForm form = managedForm.getForm();
		form.setText("Source (read-only)");
		final FormToolkit toolkit = managedForm.getToolkit();
		final Composite body = form.getBody();
		body.setLayout(new GridLayout());

		// A monospaced, read-only, scrollable text control showing the serialized XML.
		_text = new Text(body, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		_text.setLayoutData(new GridData(GridData.FILL_BOTH));
		toolkit.adapt(_text, true, true);
	}

	/** Regenerate the shown XML from the current model whenever this page becomes active. */
	@Override
	public void setActive(boolean active) {
		super.setActive(active);
		if (active && _text != null && !_text.isDisposed()) {
			final MutableApiextModel model = _editor.getModel();
			_text.setText(model != null ? model.toXml() : "");
		}
	}
}

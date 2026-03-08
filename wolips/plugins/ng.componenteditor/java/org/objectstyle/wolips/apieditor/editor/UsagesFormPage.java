package org.objectstyle.wolips.apieditor.editor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.objectstyle.wolips.componenteditor.part.UsagesComposite;

/**
 * A {@link FormPage} that embeds a {@link UsagesComposite} to show
 * "who uses this element?" results in the standalone API editor.
 *
 * <p>This page is added to the {@link ApiEditor} alongside the Bindings
 * page, providing element-level reverse dependency information for both
 * components and non-component elements (e.g. WODynamicElement subclasses,
 * NGElement implementations).
 *
 * <p>The page is read-only and does not contribute to the editor's dirty
 * state. The scan runs lazily on first page activation.
 */
public class UsagesFormPage extends FormPage {

	public static final String PAGE_ID = "ng.componenteditor.api.UsagesPage";

	private final String _elementName;
	private UsagesComposite _usagesComposite;

	/**
	 * @param editor      the parent API editor
	 * @param elementName the name of the element whose usages to find
	 */
	public UsagesFormPage(ApiEditor editor, String elementName) {
		super(editor, PAGE_ID, "Usages");
		_elementName = elementName;
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		ScrolledForm form = managedForm.getForm();

		// Use a FillLayout on the form body so the UsagesComposite fills
		// the entire page area. The form toolkit's default GridLayout
		// would add unwanted margins.
		Composite body = form.getBody();
		body.setLayout(new FillLayout());

		_usagesComposite = new UsagesComposite(body, SWT.NONE, _elementName);
	}

	/**
	 * Called when this page becomes active in the FormEditor.
	 * Triggers the lazy scan on first activation.
	 */
	@Override
	public void setActive(boolean active) {
		super.setActive(active);
		if (active && _usagesComposite != null) {
			_usagesComposite.scanIfNeeded();
		}
	}

	/**
	 * This page is always clean — it's a read-only view.
	 */
	@Override
	public boolean isDirty() {
		return false;
	}
}

package org.objectstyle.wolips.editor.apiext;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;

/**
 * A form-based editor for {@code .apiext} element-API files — the authoring counterpart to the
 * {@code .apiext} validation the editor already performs. Deliberately <b>separate</b> from the legacy
 * {@code .api} editor (no shared code): {@code .apiext} is a successor format, and its editor is
 * greenfield.
 * <p>
 * Form-primary: a {@link MutableApiextModel} is the source of truth for the session, edited via the
 * form pages and serialized to XML on save. Structure mirrors the house {@code FormEditor} idiom —
 * one mutable model held for the session, plain-POJO mutations, a single dirty flag, one-shot
 * serialize on save. Pages: the main element/bindings form, the constraint builder, and a read-only
 * source view.
 */
public class ApiextEditor extends FormEditor {

	private MutableApiextModel _model;

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		super.init(site, input);
		if (input instanceof FileEditorInput) {
			setPartName(((FileEditorInput) input).getFile().getName());
		}
	}

	/**
	 * The session model, lazily loaded from the editor input's file. Returns null if the input isn't a
	 * workspace file or the model can't be loaded (the {@link #addPages()} fallback handles that).
	 */
	public MutableApiextModel getModel() {
		if (_model == null && getEditorInput() instanceof FileEditorInput) {
			try {
				_model = new MutableApiextModel(((FileEditorInput) getEditorInput()).getFile());
			}
			catch (ApiModelException e) {
				// Leave null — addPages() shows the error/empty state.
				_model = null;
			}
		}
		return _model;
	}

	@Override
	protected void addPages() {
		try {
			addPage(new ElementBindingsPage(this));
			addPage(new ConstraintsPage(this));
			addPage(new SourcePage(this));
		}
		catch (PartInitException e) {
			// Best effort — a failed page shouldn't break the editor entirely.
		}
		if (getContainer() instanceof CTabFolder) {
			final CTabFolder ctf = (CTabFolder) getContainer();
			ctf.setTabPosition(SWT.TOP);
			ctf.setBorderVisible(false);
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		final MutableApiextModel model = getModel();
		if (model == null) {
			return;
		}
		// Let each page flush any in-progress widget state into the model before serializing.
		commitPages(true);
		try {
			model.saveChanges();
		}
		catch (ApiModelException e) {
			throw new RuntimeException("Failed to save .apiext file.", e);
		}
		editorDirtyStateChanged();
	}

	@Override
	public void doSaveAs() {
		// Save As is not supported for .apiext element files (they are named after the element).
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public boolean isDirty() {
		final MutableApiextModel model = _model;
		return (model != null && model.isDirty()) || super.isDirty();
	}
}

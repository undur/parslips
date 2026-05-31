package org.objectstyle.wolips.preferences;

import org.objectstyle.wolips.devserver.DevServerManager;
import org.objectstyle.wolips.devserver.DevServerPreferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Preference page for the Parsley dev server — the loopback HTTP server that
 * lets a running application drive Eclipse (most usefully, clicking a
 * stack-trace line on a browser exception page to open the source in Eclipse).
 *
 * <p>Changing any setting and applying restarts the server so the new state
 * takes effect immediately, without an Eclipse restart.
 */
public class DevServerPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public DevServerPreferencePage() {
		super(GRID);
		setPreferenceStore(ComponenteditorPlugin.getDefault().getPreferenceStore());
		setDescription("A local HTTP server that lets a running WebObjects / ng-objects "
				+ "application open source files and components in Eclipse.");
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing to initialize
	}

	@Override
	protected void createFieldEditors() {
		Composite parent = getFieldEditorParent();

		Label explanation = new Label(parent, SWT.WRAP);
		explanation.setText(
				"When enabled, your application's exception pages (Wonder's "
				+ "ERXExceptionPage, or ng-objects' equivalent) can link directly "
				+ "to the offending source line — clicking a line number in the "
				+ "browser opens that file in Eclipse at that line.\n\n"
				+ "The server listens only on the loopback interface (localhost), "
				+ "which is its security boundary — there is no password to "
				+ "configure on either side.");
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		gd.horizontalSpan = 2;
		gd.widthHint = 420;
		explanation.setLayoutData(gd);

		Label spacer = new Label(parent, SWT.NONE);
		GridData spacerGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		spacerGd.horizontalSpan = 2;
		spacerGd.heightHint = 10;
		spacer.setLayoutData(spacerGd);

		addField(new BooleanFieldEditor(
				DevServerPreferences.SERVER_ENABLED,
				"Enable the dev server",
				parent));

		IntegerFieldEditor portEditor = new IntegerFieldEditor(
				DevServerPreferences.SERVER_PORT,
				"Port:",
				parent);
		portEditor.setValidRange(1, 65535);
		addField(portEditor);
	}

	@Override
	public boolean performOk() {
		boolean result = super.performOk();
		if (!result) {
			return false;
		}
		// Apply the new settings live: stop and (if still enabled) restart
		// with the new port and enabled state.
		DevServerManager.getDefault().restart();
		return true;
	}
}

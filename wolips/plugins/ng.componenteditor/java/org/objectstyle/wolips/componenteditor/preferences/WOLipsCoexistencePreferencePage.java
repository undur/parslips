package org.objectstyle.wolips.componenteditor.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Preference page for managing WOLips coexistence settings.
 *
 * <p>When WOLips and Parsley are both installed, their keybindings conflict
 * because both register the same key sequences (Cmd+Alt+1/2/3/5, Cmd+Shift+X,
 * etc.) for different commands. Eclipse shows a disambiguation popup every
 * time the user presses one of these keys.
 *
 * <p>This page provides a single checkbox that shadows WOLips' keybindings
 * using Eclipse's USER-level binding mechanism, letting Parsley's bindings
 * take over cleanly.
 *
 * <p>WORKAROUND: WOLips coexistence.
 *
 * @see WOLipsBindingShadow
 */
public class WOLipsCoexistencePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private BooleanFieldEditor _shadowBindings;

	public WOLipsCoexistencePreferencePage() {
		super(GRID);
		setPreferenceStore(HTMLPlugin.getDefault().getPreferenceStore());
		setDescription("Settings for running Parsley alongside WOLips.");
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing to initialize
	}

	@Override
	protected void createFieldEditors() {
		Composite parent = getFieldEditorParent();

		// Explanatory text above the checkbox
		Label explanation = new Label(parent, SWT.WRAP);
		explanation.setText(
			"When both Parsley and WOLips are installed, their keyboard shortcuts "
			+ "conflict and Eclipse shows a disambiguation popup for every shared "
			+ "shortcut.\n\n"
			+ "Enable the option below to disable WOLips' shortcuts, allowing "
			+ "Parsley's shortcuts to work without interruption.");
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		gd.horizontalSpan = 2;
		gd.widthHint = 400;
		explanation.setLayoutData(gd);

		// Spacer
		Label spacer = new Label(parent, SWT.NONE);
		GridData spacerGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		spacerGd.horizontalSpan = 2;
		spacerGd.heightHint = 10;
		spacer.setLayoutData(spacerGd);

		_shadowBindings = new BooleanFieldEditor(
			HTMLPlugin.PREF_SHADOW_WOLIPS_BINDINGS,
			"Take over component shortcuts from WOLips",
			parent);
		addField(_shadowBindings);
	}

	@Override
	public boolean performOk() {
		// Read the old value before the field editor stores the new one
		boolean wasShadowed = getPreferenceStore().getBoolean(HTMLPlugin.PREF_SHADOW_WOLIPS_BINDINGS);

		boolean result = super.performOk();
		if (!result) {
			return false;
		}

		boolean shouldShadow = getPreferenceStore().getBoolean(HTMLPlugin.PREF_SHADOW_WOLIPS_BINDINGS);

		if (shouldShadow && !wasShadowed) {
			WOLipsBindingShadow.applyShadows();
		}
		else if (!shouldShadow && wasShadowed) {
			WOLipsBindingShadow.removeShadows();
		}

		return true;
	}
}

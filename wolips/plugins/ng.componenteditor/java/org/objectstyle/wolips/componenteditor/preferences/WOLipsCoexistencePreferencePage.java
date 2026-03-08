package org.objectstyle.wolips.componenteditor.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Preference page for managing WOLips coexistence settings.
 *
 * <p>This page provides two categories of settings:
 * <ul>
 *   <li><b>Element handling:</b> Controls whether Parsley handles all
 *       recognized WO/NG projects (editors, decorators, refactoring) or
 *       only those with an explicit {@code project.base} in
 *       {@code build.properties}.</li>
 *   <li><b>Keyboard shortcuts:</b> Shadows WOLips' keybindings using
 *       Eclipse's USER-level binding mechanism, letting Parsley's bindings
 *       take over cleanly. Also provides a "Restore WOLips keybindings"
 *       button for clean uninstallation.</li>
 * </ul>
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

		// ---- Element handling section ----

		Label elementExplanation = new Label(parent, SWT.WRAP);
		elementExplanation.setText(
			"By default, Parsley only handles projects that have project.base "
			+ "set in their build.properties. Enable the option below to let "
			+ "Parsley handle all WebObjects and ng-objects projects, even "
			+ "those without project.base.");
		GridData elementGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		elementGd.horizontalSpan = 2;
		elementGd.widthHint = 400;
		elementExplanation.setLayoutData(elementGd);

		// Spacer
		Label elementSpacer = new Label(parent, SWT.NONE);
		GridData elementSpacerGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		elementSpacerGd.horizontalSpan = 2;
		elementSpacerGd.heightHint = 10;
		elementSpacer.setLayoutData(elementSpacerGd);

		addField(new BooleanFieldEditor(
			HTMLPlugin.PREF_PARSLEY_HANDLES_ALL,
			"Let Parsley handle all elements by default",
			parent));

		// Spacer before keyboard shortcuts section
		Label sectionSpacer = new Label(parent, SWT.NONE);
		GridData sectionSpacerGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		sectionSpacerGd.horizontalSpan = 2;
		sectionSpacerGd.heightHint = 20;
		sectionSpacer.setLayoutData(sectionSpacerGd);

		// Separator between sections
		Label sectionSep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData sectionSepGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		sectionSepGd.horizontalSpan = 2;
		sectionSep.setLayoutData(sectionSepGd);

		// Spacer after separator
		Label sectionSpacer2 = new Label(parent, SWT.NONE);
		GridData sectionSpacer2Gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		sectionSpacer2Gd.horizontalSpan = 2;
		sectionSpacer2Gd.heightHint = 10;
		sectionSpacer2.setLayoutData(sectionSpacer2Gd);

		// ---- Keyboard shortcuts section ----

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

		// Spacer before restore section
		Label spacer2 = new Label(parent, SWT.NONE);
		GridData spacer2Gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		spacer2Gd.horizontalSpan = 2;
		spacer2Gd.heightHint = 20;
		spacer2.setLayoutData(spacer2Gd);

		// Restore section — horizontal rule effect via separator
		Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData sepGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		sepGd.horizontalSpan = 2;
		separator.setLayoutData(sepGd);

		// Spacer after separator
		Label spacer3 = new Label(parent, SWT.NONE);
		GridData spacer3Gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		spacer3Gd.horizontalSpan = 2;
		spacer3Gd.heightHint = 10;
		spacer3.setLayoutData(spacer3Gd);

		// Explanatory text for the restore button
		Label restoreExplanation = new Label(parent, SWT.WRAP);
		restoreExplanation.setText(
			"If you are about to uninstall Parsley, click the button below first "
			+ "to restore WOLips' original keyboard shortcuts. This removes all "
			+ "keybinding overrides that Parsley has applied.");
		GridData restoreGd = new GridData(SWT.FILL, SWT.TOP, true, false);
		restoreGd.horizontalSpan = 2;
		restoreGd.widthHint = 400;
		restoreExplanation.setLayoutData(restoreGd);

		// Spacer
		Label spacer4 = new Label(parent, SWT.NONE);
		GridData spacer4Gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		spacer4Gd.horizontalSpan = 2;
		spacer4Gd.heightHint = 5;
		spacer4.setLayoutData(spacer4Gd);

		// Restore button
		Button restoreButton = new Button(parent, SWT.PUSH);
		restoreButton.setText("Restore WOLips Keybindings");
		GridData buttonGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		buttonGd.horizontalSpan = 2;
		restoreButton.setLayoutData(buttonGd);
		restoreButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				WOLipsBindingShadow.removeShadows();
				// Also uncheck the shadow preference so it doesn't
				// get re-applied on next startup
				getPreferenceStore().setValue(
					HTMLPlugin.PREF_SHADOW_WOLIPS_BINDINGS, false);
				_shadowBindings.load();
				setMessage("WOLips keybindings have been restored. "
					+ "You can now safely uninstall Parsley.",
					INFORMATION);
			}
		});
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

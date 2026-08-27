/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2005 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 1.
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. The end-user documentation
 * included with the redistribution, if any, must include the following
 * acknowlegement: "This product includes software developed by the ObjectStyle
 * Group (http://objectstyle.org/)." Alternately, this acknowlegement may
 * appear in the software itself, if and wherever such third-party
 * acknowlegements normally appear. 4. The names "ObjectStyle Group" and
 * "Cayenne" must not be used to endorse or promote products derived from this
 * software without prior written permission. For written permission, please
 * contact andrus@objectstyle.org. 5. Products derived from this software may
 * not be called "ObjectStyle" nor may "ObjectStyle" appear in their names
 * without prior written permission of the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/> .
 *  
 */
package org.objectstyle.wolips.preferences;

import java.util.HashMap;
import java.util.Map;

import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.wodclipse.core.builder.StaleMarkerPurge;
import org.objectstyle.wolips.wodclipse.core.builder.TemplateRevalidator;

/**
 * @author mike
 */
public class BindingValidationPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	/**
	 * The preference keys this page edits. Used to detect whether Apply/OK actually changed
	 * anything, so we can offer a full revalidation — existing problem markers were written under
	 * the OLD settings and won't reflect the new ones until their files are revalidated (template
	 * validation is per-file and event-driven; nothing else re-runs it). This is the same pattern
	 * as JDT's compiler preference pages prompting for a full rebuild.
	 */
	private static final String[] VALIDATION_KEYS = {
			PreferenceConstants.VALIDATE_TEMPLATES_KEY,
			PreferenceConstants.USE_INLINE_BINDINGS_KEY,
			PreferenceConstants.VALIDATE_BINDING_VALUES,
			PreferenceConstants.VALIDATE_WOO_ENCODINGS_KEY,
			PreferenceConstants.HTML_ERRORS_SEVERITY_KEY,
			PreferenceConstants.WOD_MISSING_COMPONENT_SEVERITY_KEY,
			PreferenceConstants.WOD_API_PROBLEMS_SEVERITY_KEY,
			PreferenceConstants.UNUSED_WOD_ELEMENT_SEVERITY_KEY,
			PreferenceConstants.WOD_ERRORS_IN_HTML_SEVERITY_KEY,
			PreferenceConstants.DEPRECATED_BINDING_SEVERITY_KEY,
			PreferenceConstants.MISSING_COLLECTION_SEVERITY_KEY,
			PreferenceConstants.MISSING_COMPONENT_SEVERITY_KEY,
			PreferenceConstants.MISSING_NSKVC_SEVERITY_KEY,
			PreferenceConstants.AMBIGUOUS_SEVERITY_KEY,
			PreferenceConstants.AT_OPERATOR_SEVERITY_KEY,
			PreferenceConstants.HELPER_FUNCTION_SEVERITY_KEY,
			PreferenceConstants.INVALID_OGNL_SEVERITY_KEY,
			PreferenceConstants.WELL_FORMED_TEMPLATE_KEY };

	public BindingValidationPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("The following settings control various aspects of the component validation system.");
	}

	@Override
	public void createFieldEditors() {
		addField(new BooleanFieldEditor(PreferenceConstants.VALIDATE_TEMPLATES_KEY, "Component Validation", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceConstants.USE_INLINE_BINDINGS_KEY, "Inline Bindings Allowed", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceConstants.VALIDATE_BINDING_VALUES, "Binding Value Validation (Slow)", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceConstants.VALIDATE_WOO_ENCODINGS_KEY, "WOO Encoding Validation", getFieldEditorParent()));

		addField(new ComboFieldEditor(PreferenceConstants.HTML_ERRORS_SEVERITY_KEY, "Invalid HTML", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.WOD_MISSING_COMPONENT_SEVERITY_KEY, "Missing Component", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.WOD_API_PROBLEMS_SEVERITY_KEY, "WOD API Problems", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.UNUSED_WOD_ELEMENT_SEVERITY_KEY, "Unused WOD Elements", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.WOD_ERRORS_IN_HTML_SEVERITY_KEY, "WOD Errors in Template", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.DEPRECATED_BINDING_SEVERITY_KEY, "Deprecated API in Template", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.MISSING_COLLECTION_SEVERITY_KEY, "Missing Key on NSDictionary/NSArray", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.MISSING_COMPONENT_SEVERITY_KEY, "Missing Key on 'extends WOComponent'", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.MISSING_NSKVC_SEVERITY_KEY, "Missing Key on 'implements NSKeyValueCoding'", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.AMBIGUOUS_SEVERITY_KEY, "Ambiguous Key Paths", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.AT_OPERATOR_SEVERITY_KEY, "@Operator KVC Paths", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.HELPER_FUNCTION_SEVERITY_KEY, "Helper Functions", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));
		addField(new ComboFieldEditor(PreferenceConstants.INVALID_OGNL_SEVERITY_KEY, "Invalid OGNL", PreferenceConstants.IGNORE_WARNING_ERROR, getFieldEditorParent()));

		addField(new ComboFieldEditor(PreferenceConstants.WELL_FORMED_TEMPLATE_KEY, "Require well-formed HTML Template", PreferenceConstants.DEFAULT_YES_NO, getFieldEditorParent()));
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
	}

	/**
	 * Adds a "Revalidate All Templates" button next to the standard page buttons — the on-demand
	 * trigger for a full-workspace revalidation (all templates in every project the component
	 * editor handles). Useful when markers look stale for reasons other than a settings change
	 * (e.g. validation ran while the workspace was still resolving types).
	 */
	@Override
	protected void contributeButtons(Composite parent) {
		((GridLayout) parent.getLayout()).numColumns += 2;
		final Button revalidate = new Button(parent, SWT.PUSH);
		revalidate.setText("Revalidate All Templates");
		revalidate.setToolTipText("Re-run template validation on every component in the workspace, replacing all existing template problem markers.");
		revalidate.addListener(SWT.Selection, event -> TemplateRevalidator.scheduleRevalidation(TemplateRevalidator.allHandledProjects()));

		final Button purge = new Button(parent, SWT.PUSH);
		purge.setText("Purge Stale Markers");
		purge.setToolTipText("Delete orphaned untyped problem markers on js/css/html/xml files — leftovers of removed legacy validators that nothing will ever revalidate. Typed markers (template validation, Java, WTP) are untouched.");
		purge.addListener(SWT.Selection, event -> {
			final StaleMarkerPurge.Summary summary = StaleMarkerPurge.purgeWorkspace();
			MessageDialog.openInformation(getShell(), "Purge Stale Markers",
					"Deleted " + summary.deletedMarkers + " stale marker(s) across " + summary.affectedFiles + " file(s).");
		});
	}

	/**
	 * After applying, offer a full revalidation when any validation setting actually changed —
	 * existing markers were produced under the old settings and stay stale until their files are
	 * revalidated (the JDT "settings changed, full rebuild?" pattern).
	 */
	@Override
	public boolean performOk() {
		final Map<String, String> before = snapshot();
		final boolean ok = super.performOk();
		if (ok && !snapshot().equals(before)) {
			if (MessageDialog.openQuestion(getShell(), "Revalidate Templates",
					"Validation settings changed. Existing problem markers still reflect the old settings until their templates are revalidated.\n\nRevalidate all templates now?")) {
				TemplateRevalidator.scheduleRevalidation(TemplateRevalidator.allHandledProjects());
			}
		}
		return ok;
	}

	private Map<String, String> snapshot() {
		final IPreferenceStore store = getPreferenceStore();
		final Map<String, String> values = new HashMap<>();
		for (final String key : VALIDATION_KEYS) {
			values.put(key, store.getString(key));
		}
		return values;
	}

	public void init(IWorkbench workbench) {
		// DO NOTHING
	}
}
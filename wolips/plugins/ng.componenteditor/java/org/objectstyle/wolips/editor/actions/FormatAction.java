package org.objectstyle.wolips.editor.actions;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.FormatRefactoring;

/**
 * Editor action that formats the HTML template using FuzzyXML's renderer.
 *
 * <p>The formatter operates purely on the HTML template — it does not
 * require or modify the WOD file. This means it works for both bundle
 * templates ({@code .wo} folders) and standalone {@code .html} templates.
 */
public class FormatAction extends AbstractTemplateAction {
	@Override
	public void run(IAction action) {
		try {
			TemplateEditor templateEditor = getTemplateEditor();
			if (templateEditor != null) {
				WodParserCache cache = templateEditor.getSourceEditor().getParserCache();
				FormatRefactoring.run(cache, new NullProgressMonitor());
			}
		} catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

}

package org.objectstyle.wolips.componenteditor.actions;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditorPart;
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.ConvertInlineToWodRefactoring;
import org.objectstyle.wolips.editor.wod.WodEditor;

public class ConvertInlineToWodAction extends AbstractTemplateAction {
	@Override
	public void run(IAction action) {
		try {
			ComponentEditorPart componentEditorPart = getComponentEditorPart();
			if (componentEditorPart != null) {
				IEditorPart activeEditorPart = componentEditorPart.getActiveEditor();
				TemplateEditor templateEditor = getTemplateEditor();
				WodEditor wodEditor = getWodEditor();
				if (templateEditor != null && wodEditor != null && activeEditorPart == templateEditor) {
					// Force a fresh parse of the HTML document so that offsets
					// in the FuzzyXML tree match the current editor content.
					templateEditor.getSourceEditor().getHtmlXmlDocument(true);

					ITextSelection templateSelection = (ITextSelection) templateEditor.getSourceEditor().getSelectionProvider().getSelection();
					int offset = templateSelection.getOffset();
					WodParserCache cache = templateEditor.getSourceEditor().getParserCache();
					ParsleyProject parsleyProject = (ParsleyProject)cache.getProject().getAdapter(ParsleyProject.class);
					ConvertInlineToWodRefactoring.run(cache, offset, parsleyProject, new NullProgressMonitor());
				}
			}
		} catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}
}

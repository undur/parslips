package org.objectstyle.wolips.componenteditor.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IEditorPart;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditorPart;
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.editor.wod.WodEditor;

public class DeleteTagAction extends AbstractTemplateAction {
	@Override
	public void run(IAction action) {
		try {
			ComponentEditorPart componentEditorPart = getComponentEditorPart();
			if (componentEditorPart != null) {
				IEditorPart activeEditorPart = componentEditorPart.getActiveEditor();
				TemplateEditor templateEditor = getTemplateEditor();
				WodEditor wodEditor = getWodEditor();
				if (templateEditor != null && wodEditor != null) {
					if (activeEditorPart == templateEditor) {
						templateEditor.getSourceEditor().new DeleteTagAction().run();
					}
				}
			}
		} catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

}

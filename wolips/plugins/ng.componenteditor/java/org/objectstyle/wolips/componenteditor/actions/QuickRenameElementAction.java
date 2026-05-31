package org.objectstyle.wolips.componenteditor.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.ui.IEditorPart;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditorPart;
import org.objectstyle.wolips.templateeditor.TemplateEditor;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.QuickRenameRefactoring;
import org.objectstyle.wolips.editor.wod.WodEditor;

/**
 * Cmd+2,R action: renames the tag or WOD element reference at the cursor
 * using Eclipse linked mode (edit one occurrence and all others update live).
 *
 * <p>In the HTML editor:
 * <ul>
 *   <li>Cursor on a tag name → renames the open + close tag pair</li>
 *   <li>Cursor on a {@code <webobject name="X">} name attribute → renames
 *       the element name across both HTML and WOD (bundle templates only)</li>
 * </ul>
 *
 * <p>In the WOD editor: renames the element name across WOD + HTML.
 *
 * <p>Works for both standalone and bundle templates. For standalone templates,
 * the WOD editor is null — only HTML tag rename is available, which doesn't
 * need the WOD viewer.
 */
public class QuickRenameElementAction extends AbstractTemplateAction {
	@Override
	public void run(IAction action) {
		try {
			ComponentEditorPart componentEditorPart = getComponentEditorPart();
			if (componentEditorPart != null) {
				IEditorPart activeEditorPart = componentEditorPart.getActiveEditor();
				TemplateEditor templateEditor = getTemplateEditor();
				WodEditor wodEditor = getWodEditor();
				if (activeEditorPart == templateEditor && templateEditor != null) {
					ITextSelection templateSelection = (ITextSelection) templateEditor.getSourceEditor().getSelectionProvider().getSelection();
					int offset = templateSelection.getOffset();
					WodParserCache cache = templateEditor.getSourceEditor().getParserCache();
					// wodEditor may be null for standalone templates — renameHtmlSelection
					// handles this: renameHtmlTag() doesn't need the WOD viewer, and
					// renameElement() (WOD name references) is guarded against null.
					ITextViewer wodViewer = (wodEditor != null) ? wodEditor.getViewer() : null;
					QuickRenameRefactoring.renameHtmlSelection(offset, templateEditor.getSourceEditor().getViewer(), wodViewer, cache);
				} else if (activeEditorPart == wodEditor && wodEditor != null) {
					// WOD rename — only possible in bundle templates where wodEditor exists
					ITextSelection wodSelection = (ITextSelection) wodEditor.getSelectionProvider().getSelection();
					int offset = wodSelection.getOffset();
					WodParserCache cache = wodEditor.getParserCache();
					QuickRenameRefactoring.renameWodSelection(offset, templateEditor.getSourceEditor().getViewer(), wodEditor.getViewer(), cache);
				}
			}
		} catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}
}

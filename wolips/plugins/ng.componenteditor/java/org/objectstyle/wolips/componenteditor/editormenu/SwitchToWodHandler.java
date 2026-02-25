package org.objectstyle.wolips.componenteditor.editormenu;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditor;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;

/**
 * Command handler for switching to the WOD editor from any editor context.
 * If the active editor is the ComponentEditor, switches to the WOD tab.
 * Otherwise, locates the WOD file and opens it in the ComponentEditor.
 */
public class SwitchToWodHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) {
			return null;
		}

		// If we're already in the ComponentEditor, just switch tabs
		if (editor instanceof ComponentEditor) {
			((ComponentEditor) editor).switchToWod();
			return null;
		}

		IEditorInput editorInput = editor.getEditorInput();
		if (!(editorInput instanceof FileEditorInput)) {
			return null;
		}

		IFile file = ((FileEditorInput) editorInput).getFile();
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(file);
			if (result != null) {
				IFile wodFile = result.getFirstWodFile();
				if (wodFile != null) {
					WorkbenchUtilities.open(wodFile, EditorsPlugin.ComponentEditorID);
				}
			}
		} catch (CoreException | LocateException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return null;
	}
}

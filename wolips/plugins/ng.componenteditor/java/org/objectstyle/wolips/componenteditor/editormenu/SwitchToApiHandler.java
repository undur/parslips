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
 * Command handler for switching to the API editor from any editor context.
 * If the active editor is the ComponentEditor, switches to the API tab.
 * Otherwise, locates the API file and opens it — in the ComponentEditor if
 * a template exists (so the user gets the full multi-tab component editor
 * with the API tab active), or in the standalone ApiEditor if only a .api
 * file exists (common for non-component WOElements that have no template).
 */
public class SwitchToApiHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) {
			return null;
		}

		// If we're already in the ComponentEditor, just switch to the API tab
		if (editor instanceof ComponentEditor) {
			((ComponentEditor) editor).switchToApi();
			return null;
		}

		IEditorInput editorInput = editor.getEditorInput();
		if (!(editorInput instanceof FileEditorInput)) {
			return null;
		}

		IFile file = ((FileEditorInput) editorInput).getFile();
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(file);
			if (result == null) {
				return null;
			}

			// If a template exists, open the ComponentEditor with the API tab revealed.
			// Opening via the .api file path triggers ComponentEditorInput.createWithDotApi(),
			// which sets displayApiPartOnReveal=true to auto-switch to the API tab.
			IFile htmlFile = result.getFirstHtmlFile();
			if (htmlFile != null) {
				IFile apiFile = result.getDotApi(true);
				if (apiFile != null) {
					WorkbenchUtilities.open(apiFile, EditorsPlugin.ComponentEditorID);
				}
				return null;
			}

			// No template — this is likely a non-component WOElement.
			// Open the standalone ApiEditor if a .api file exists.
			IFile apiFile = result.getDotApi();
			if (apiFile != null && apiFile.exists()) {
				WorkbenchUtilities.open(apiFile, EditorsPlugin.ApiEditorID);
			}
		} catch (CoreException | LocateException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return null;
	}
}

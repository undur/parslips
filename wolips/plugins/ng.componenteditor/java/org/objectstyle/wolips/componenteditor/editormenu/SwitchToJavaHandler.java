package org.objectstyle.wolips.componenteditor.editormenu;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;

/**
 * Command handler for switching to the Java editor from any editor context.
 * Works from both the ComponentEditor and standalone editors (e.g., JDT Java editor).
 */
public class SwitchToJavaHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) {
			return null;
		}

		IEditorInput editorInput = editor.getEditorInput();
		if (!(editorInput instanceof FileEditorInput)) {
			return null;
		}

		IFile file = ((FileEditorInput) editorInput).getFile();
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(file);
			if (result != null && result.getDotJava() != null) {
				WorkbenchUtilities.open(result.getDotJava());
			}
		} catch (CoreException | LocateException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return null;
	}
}

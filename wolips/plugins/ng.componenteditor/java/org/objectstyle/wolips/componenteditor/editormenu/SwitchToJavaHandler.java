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
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Command handler for switching to the Java editor from any editor context.
 * Works from both the ComponentEditor and standalone editors (e.g., JDT Java editor).
 *
 * <p>For non-Parsley projects (when WOLips is installed), delegates to
 * WOLips' equivalent command so WOLips behavior is preserved.
 *
 * <p>WORKAROUND: WOLips coexistence — the project check and delegation.
 */
public class SwitchToJavaHandler extends AbstractHandler {

	/** WOLips' equivalent command ID. */
	private static final String WOLIPS_COMMAND_ID =
		"org.objectstyle.wolips.componenteditor.editors.tojava";

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

		// WORKAROUND: WOLips coexistence.
		// For non-Parsley projects, delegate to WOLips' command so
		// WOLips behavior is preserved.
		if (!ParsleyProject.isParsleyProject(file.getProject())) {
			return WOLipsCommandDelegate.execute(WOLIPS_COMMAND_ID, event);
		}

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

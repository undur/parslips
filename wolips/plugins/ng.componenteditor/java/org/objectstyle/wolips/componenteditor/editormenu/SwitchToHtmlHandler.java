package org.objectstyle.wolips.componenteditor.editormenu;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.componenteditor.part.ComponentEditor;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Command handler for switching to the HTML editor from any editor context.
 * If the active editor is the ComponentEditor, switches to the HTML tab.
 * Otherwise, locates the HTML file and opens it in the ComponentEditor.
 *
 * <p>For non-Parsley projects (when WOLips is installed), delegates to
 * WOLips' equivalent command so WOLips behavior is preserved.
 *
 * <p>WORKAROUND: WOLips coexistence — the project check and delegation.
 */
public class SwitchToHtmlHandler extends AbstractHandler {

	/** WOLips' equivalent command ID. */
	private static final String WOLIPS_COMMAND_ID =
		"org.objectstyle.wolips.componenteditor.editors.tohtml";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) {
			return null;
		}

		// If we're already in the ComponentEditor, just switch tabs
		if (editor instanceof ComponentEditor) {
			((ComponentEditor) editor).switchToHtml();
			return null;
		}

		IEditorInput editorInput = editor.getEditorInput();
		if (!(editorInput instanceof FileEditorInput)) {
			return null;
		}

		IFile file = ((FileEditorInput) editorInput).getFile();

		// WORKAROUND: WOLips coexistence.
		// For projects Parsley shouldn't handle, delegate to WOLips'
		// command so WOLips behavior is preserved.
		if (!ParsleyProject.shouldHandleProject(file.getProject())) {
			return WOLipsCommandDelegate.execute(WOLIPS_COMMAND_ID, event);
		}

		ElementDescriptor descriptor = ElementDescriptor.forFile(file);
		if (descriptor != null && descriptor.getHtmlFile() != null) {
			WorkbenchUtilities.open(descriptor.getHtmlFile(), EditorsPlugin.ComponentEditorID);
		}
		return null;
	}
}

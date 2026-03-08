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
import org.objectstyle.wolips.components.input.ComponentEditorInput;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Command handler for switching to the Java editor from any editor context.
 * Works from both the ComponentEditor and standalone editors (e.g., JDT Java editor).
 *
 * <p>When invoked from a {@link ComponentEditor}, the Java file is resolved from
 * the already-located {@link LocalizedComponentsLocateResult} stored in the
 * editor input. This avoids the project-type check that would otherwise
 * delegate to WOLips for projects without {@code project.base} set — important
 * because the user may F3 into a component from a different project, and that
 * component's project might not be explicitly marked as a Parsley project.
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

		// If we're in the ComponentEditor, use the already-resolved locate
		// result to find the Java file. This bypasses the project-type check,
		// which matters when the component was opened via F3 from a different
		// project — the component's own project might not have project.base set.
		if (editor instanceof ComponentEditor) {
			ComponentEditor componentEditor = (ComponentEditor) editor;
			// Use getComponentEditorInput() — not getEditorInput(), which
			// returns the active tab's FileEditorInput, not the ComponentEditorInput.
			ComponentEditorInput componentInput = componentEditor.getComponentEditorInput();
			if (componentInput != null) {
				LocalizedComponentsLocateResult result =
					componentInput.getLocalizedComponentsLocateResult();
				if (result != null && result.getDotJava() != null) {
					WorkbenchUtilities.open(result.getDotJava());
					return null;
				}
			}
			// Fallback: try to locate from the active tab's file
			// (handles standalone templates opened via createStandaloneHtml
			// which don't have a LocalizedComponentsLocateResult)
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

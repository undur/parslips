package org.objectstyle.wolips.editor.menu;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.bindings.api.MutableApiModel;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.componenteditor.part.ComponentEditor;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Command handler for switching to the API editor from any editor context.
 * If the active editor is the ComponentEditor, switches to the API tab.
 * Otherwise, locates the API file and opens it — in the ComponentEditor if
 * a template exists (so the user gets the full multi-tab component editor
 * with the API tab active), or in the standalone ApiEditor if only a .api
 * file exists (common for non-component WOElements that have no template).
 *
 * <p>If no .api file exists at all, one is created automatically: in the
 * project's {@code components} folder if one exists under {@code src/main/},
 * otherwise next to the Java file.
 *
 * <p>For non-Parsley projects (when WOLips is installed), delegates to
 * WOLips' equivalent command so WOLips behavior is preserved.
 *
 * <p>WORKAROUND: WOLips coexistence — the project check and delegation.
 */
public class SwitchToApiHandler extends AbstractHandler {

	/** WOLips' equivalent command ID. */
	private static final String WOLIPS_COMMAND_ID =
		"org.objectstyle.wolips.componenteditor.editors.toapi";

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

		// WORKAROUND: WOLips coexistence.
		// For projects Parsley shouldn't handle, delegate to WOLips'
		// command so WOLips behavior is preserved.
		if (!ParsleyProject.shouldHandleProject(file.getProject())) {
			return WOLipsCommandDelegate.execute(WOLIPS_COMMAND_ID, event);
		}

		ElementDescriptor descriptor = ElementDescriptor.forFile(file);
		if (descriptor == null) {
			return null;
		}

		try {
			// If a template exists, open the ComponentEditor with the API tab revealed.
			// Opening via the .api file path triggers ComponentEditorInput.createWithDotApi(),
			// which sets displayApiPartOnReveal=true to auto-switch to the API tab.
			if (descriptor.hasTemplate()) {
				IFile apiFile = descriptor.getApiFile();
				if (apiFile != null) {
					WorkbenchUtilities.open(apiFile, EditorsPlugin.ComponentEditorID);
				}
				return null;
			}

			// No template — this is likely a non-component WOElement.
			// Open the standalone ApiEditor, creating the .api file if needed.
			IFile apiFile = descriptor.getApiFile();

			if (apiFile == null) {
				// No .api file found or guessed — derive a path.
				// Prefer the project's "components" folder (same heuristic as
				// the New Component wizard); fall back to placing it next to
				// the Java file if no components folder exists.
				String baseName = LocatePlugin.getDefault().fileNameWithoutExtension(file);
				IContainer apiFolder = findComponentsFolder(file.getProject());
				if (apiFolder == null) {
					apiFolder = file.getParent();
				}
				apiFile = apiFolder.getFile(new Path(baseName + ".api"));
			}

			if (!apiFile.exists()) {
				String name = LocatePlugin.getDefault().fileNameWithoutExtension(apiFile);
				String content = MutableApiModel.blankContent(name);
				apiFile.create(
					new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
					true, null);
			}

			WorkbenchUtilities.open(apiFile, EditorsPlugin.ApiEditorID);
		} catch (CoreException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return null;
	}

	/**
	 * Searches for a "components" folder in the given project.
	 *
	 * @see ParsleyProject#findComponentsFolder(IProject)
	 */
	private static IContainer findComponentsFolder(IProject project) {
		return ParsleyProject.findComponentsFolder(project);
	}
}

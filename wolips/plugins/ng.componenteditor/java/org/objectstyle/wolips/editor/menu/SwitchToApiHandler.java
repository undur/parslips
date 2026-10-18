package org.objectstyle.wolips.editor.menu;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.jface.dialogs.MessageDialog;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiParser;
import org.objectstyle.wolips.bindings.api.ApiToApiext;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.editor.component.ComponentEditor;
import org.objectstyle.wolips.editors.EditorsPlugin;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Command handler for switching to the API editor from any editor context. The API is the
 * {@code .apiext}: in the ComponentEditor this switches to the API tab; elsewhere it opens the
 * component's ComponentEditor on that tab, or, for an element without a template (a dynamic element
 * class), its {@code .apiext} in the {@link org.objectstyle.wolips.editor.apiext.ApiextEditor},
 * written first when there's none yet: from the element's {@code .api}, when it has one.
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

		// A component: its ComponentEditor, on the API tab.
		if (descriptor.hasTemplate()) {
			final IEditorPart opened = WorkbenchUtilities.open(descriptor.getHtmlFile(), EditorsPlugin.ComponentEditorID);
			if (opened instanceof ComponentEditor) {
				((ComponentEditor) opened).switchToApi();
			}
			return null;
		}

		// An element without a template: its .apiext, beside its .api if it has one, else in the
		// project's components folder (or next to the Java file).
		final IFile apiFile = descriptor.getApiFile();
		final String baseName = LocatePlugin.getDefault().fileNameWithoutExtension(file);
		IContainer folder = apiFile != null && apiFile.exists() ? apiFile.getParent() : findComponentsFolder(file.getProject());
		if (folder == null) {
			folder = file.getParent();
		}
		final IFile apiextFile = folder.getFile(new Path(baseName + ".apiext"));
		if (!apiextFile.exists() && !createApiext(apiextFile, apiFile, HandlerUtil.getActiveShell(event))) {
			return null;
		}
		WorkbenchUtilities.open(apiextFile, EditorsPlugin.ApiextEditorID);
		return null;
	}

	/**
	 * Writes an element's first {@code .apiext}, starting from its hand-written {@code .api} when it
	 * has one: saving an {@code .apiext} rewrites the {@code .api} beside it, so an empty one would
	 * erase what was written there. When a rule there has no {@code .apiext} form, nothing is written.
	 *
	 * @return whether the file now exists
	 */
	private static boolean createApiext(IFile apiextFile, IFile apiFile, org.eclipse.swt.widgets.Shell shell) {
		try {
			final MutableApiextModel model = new MutableApiextModel(apiextFile);
			if (apiFile != null && apiFile.exists() && apiFile.getLocation() != null) {
				final List<String> untranslatable = ApiToApiext.seed(model, ApiParser.parseFile(apiFile.getLocation().toFile()), List.of());
				if (!untranslatable.isEmpty()) {
					MessageDialog.openInformation(shell, "API", apiFile.getName() + " has rules with no .apiext form, which would be lost: \"" + String.join("\", \"", untranslatable)
							+ "\". Write " + apiextFile.getName() + " by hand, carrying them over as you see fit.");
					return false;
				}
			}
			model.saveChanges();
			return apiextFile.exists();
		}
		catch (ApiModelException e) {
			ComponenteditorPlugin.getDefault().log(e);
			return false;
		}
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

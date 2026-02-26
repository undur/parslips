package org.objectstyle.wolips.componenteditor.part;

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.IEditorAssociationOverride;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.core.runtime.content.IContentType;
import org.objectstyle.wolips.variables.BuildProperties;

/**
 * Overrides Eclipse's default editor selection so that component files open
 * in the NG Component Editor instead of any other editor.
 *
 * Activates for:
 * <ul>
 *   <li>Any file inside a {@code .wo} folder (unambiguously a WO component)</li>
 *   <li>Component files ({@code .html}, {@code .wod}, {@code .woo}, {@code .api})
 *       in ng-objects projects (identified by {@code base=ng} in {@code build.properties})</li>
 * </ul>
 */
public class NGEditorAssociationOverride implements IEditorAssociationOverride {

	private static final String NG_EDITOR_ID = "ng.componenteditor.ComponentEditor";
	private static final Set<String> COMPONENT_EXTENSIONS = Set.of("html", "wod", "woo", "api");

	@Override
	public IEditorDescriptor overrideDefaultEditor(IEditorInput editorInput, IContentType contentType, IEditorDescriptor editorDescriptor) {
		if (editorDescriptor == null) {
			return null;
		}

		if (NG_EDITOR_ID.equals(editorDescriptor.getId())) {
			return editorDescriptor;
		}

		IFile file = ResourceUtil.getFile(editorInput);
		if (file == null) {
			return editorDescriptor;
		}

		if (!shouldUseComponentEditor(file)) {
			return editorDescriptor;
		}

		IEditorDescriptor ngEditor = findEditor(NG_EDITOR_ID);
		if (ngEditor != null) {
			return ngEditor;
		}

		return editorDescriptor;
	}

	@Override
	public IEditorDescriptor overrideDefaultEditor(String fileName, IContentType contentType, IEditorDescriptor editorDescriptor) {
		return editorDescriptor;
	}

	@Override
	public IEditorDescriptor[] overrideEditors(IEditorInput editorInput, IContentType contentType, IEditorDescriptor[] editorDescriptors) {
		IFile file = ResourceUtil.getFile(editorInput);
		if (file == null || !shouldUseComponentEditor(file)) {
			return editorDescriptors;
		}

		return reorderEditors(editorDescriptors);
	}

	@Override
	public IEditorDescriptor[] overrideEditors(String fileName, IContentType contentType, IEditorDescriptor[] editorDescriptors) {
		return editorDescriptors;
	}

	/**
	 * Returns true if this file should be opened with the component editor.
	 * A file qualifies if it's inside a .wo folder, or if it's a component
	 * file extension in an ng-objects project.
	 */
	private boolean shouldUseComponentEditor(IFile file) {
		if (isInsideWoFolder(file)) {
			return true;
		}
		return isComponentFile(file) && isNGProject(file.getProject());
	}

	private boolean isInsideWoFolder(IFile file) {
		return file.getParent() != null && file.getParent().getName().endsWith(".wo");
	}

	private boolean isComponentFile(IFile file) {
		String ext = file.getFileExtension();
		return ext != null && COMPONENT_EXTENSIONS.contains(ext.toLowerCase());
	}

	private boolean isNGProject(IProject project) {
		if (project == null || !project.isOpen()) {
			return false;
		}
		try {
			BuildProperties buildProps = (BuildProperties) project.getAdapter(BuildProperties.class);
			if (buildProps != null) {
				return buildProps.isNGProject();
			}
		} catch (Exception e) {
			// Ignore — fall through to false
		}
		return false;
	}

	/**
	 * Reorder the editor list so the NG editor comes first.
	 */
	private IEditorDescriptor[] reorderEditors(IEditorDescriptor[] editors) {
		int ngIndex = -1;
		for (int i = 0; i < editors.length; i++) {
			if (NG_EDITOR_ID.equals(editors[i].getId())) {
				ngIndex = i;
				break;
			}
		}
		if (ngIndex <= 0) {
			return editors;
		}
		IEditorDescriptor[] reordered = new IEditorDescriptor[editors.length];
		reordered[0] = editors[ngIndex];
		int dest = 1;
		for (int i = 0; i < editors.length; i++) {
			if (i != ngIndex) {
				reordered[dest++] = editors[i];
			}
		}
		return reordered;
	}

	private IEditorDescriptor findEditor(String editorId) {
		try {
			return org.eclipse.ui.PlatformUI.getWorkbench().getEditorRegistry().findEditor(editorId);
		} catch (Exception e) {
			return null;
		}
	}
}

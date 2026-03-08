package org.objectstyle.wolips.componenteditor.part;

import java.util.Arrays;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.IEditorAssociationOverride;
import org.eclipse.ui.ide.ResourceUtil;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Overrides Eclipse's default editor selection so that component files open
 * in the Parsley Template Editor instead of any other editor.
 *
 * Activates for:
 * <ul>
 *   <li>Any file inside a {@code .wo} folder (unambiguously a WO component)</li>
 *   <li>Component files ({@code .html}, {@code .wod}, {@code .woo}, {@code .api})
 *       in ng-objects projects (identified by {@code project.base=ng} in {@code build.properties})</li>
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

		IFile file = ResourceUtil.getFile(editorInput);
		if (file == null) {
			return editorDescriptor;
		}

		if (shouldUseComponentEditor(file)) {
			// Parsley project — force our editor
			if (NG_EDITOR_ID.equals(editorDescriptor.getId())) {
				return editorDescriptor;
			}
			IEditorDescriptor ngEditor = findEditor(NG_EDITOR_ID);
			return ngEditor != null ? ngEditor : editorDescriptor;
		}

		// Not a Parsley project — if Eclipse chose our editor (because of
		// plugin.xml default="true"), reject it so the next editor is used
		if (isOurEditor(editorDescriptor)) {
			return null;
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
		if (file == null) {
			return editorDescriptors;
		}

		if (shouldUseComponentEditor(file)) {
			return reorderEditors(editorDescriptors);
		}

		// Not a Parsley project — remove our editors from the list
		return removeOurEditors(editorDescriptors);
	}

	@Override
	public IEditorDescriptor[] overrideEditors(String fileName, IContentType contentType, IEditorDescriptor[] editorDescriptors) {
		return editorDescriptors;
	}

	/**
	 * Returns true if this file should be opened with the component editor.
	 *
	 * <p>A file qualifies if it's inside a .wo folder or has a component file
	 * extension — but only if the project is a Parsley project. When WOLips
	 * is installed alongside Parsley, this check ensures we only claim files
	 * in projects that have explicitly opted in via {@code project.base}.
	 */
	private boolean shouldUseComponentEditor(IFile file) {
		if (!ParsleyProject.shouldHandleProject(file.getProject())) {
			return false;
		}
		if (isInsideWoFolder(file)) {
			return true;
		}
		return isComponentFile(file);
	}

	private boolean isInsideWoFolder(IFile file) {
		return file.getParent() != null && file.getParent().getName().endsWith(".wo");
	}

	private boolean isComponentFile(IFile file) {
		String ext = file.getFileExtension();
		return ext != null && COMPONENT_EXTENSIONS.contains(ext.toLowerCase());
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

	/**
	 * Returns true if the given editor descriptor is one of ours.
	 */
	private boolean isOurEditor(IEditorDescriptor descriptor) {
		String id = descriptor.getId();
		return id != null && id.startsWith("ng.componenteditor.");
	}

	/**
	 * Remove all Parsley editors from the list. Used for non-Parsley projects
	 * to prevent our editors from appearing in "Open With" menus.
	 */
	private IEditorDescriptor[] removeOurEditors(IEditorDescriptor[] editors) {
		IEditorDescriptor[] filtered = Arrays.stream(editors)
			.filter(e -> !isOurEditor(e))
			.toArray(IEditorDescriptor[]::new);
		// If we filtered nothing, return original array to avoid unnecessary allocation
		return filtered.length == editors.length ? editors : filtered;
	}

	private IEditorDescriptor findEditor(String editorId) {
		try {
			return org.eclipse.ui.PlatformUI.getWorkbench().getEditorRegistry().findEditor(editorId);
		} catch (Exception e) {
			return null;
		}
	}
}

package org.objectstyle.wolips.bindings.api;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;

/**
 * Where a component's API files are: its {@code .apiext}, and for a WebObjects component the
 * {@code .api} generated beside it ({@link ApiCompanion}).
 *
 * <p>The {@code .apiext} goes beside the component's {@code .api} when it has one, wherever that
 * is, so the two travel together and the generated {@code .api} replaces that file. Otherwise it
 * goes beside the template, or beside the {@code .lproj} folder of a localized one: API files
 * describe the component, not one localization of it, and WebObjects projects keep them in the
 * components folder itself.
 */
public final class ComponentApiFiles {

	private ComponentApiFiles() {
	}

	/** The template a resource stands for: a bundle template's {@code .html} stands for its {@code .wo} folder. */
	public static IResource templateOf(IResource resource) {
		if (resource.getType() == IResource.FILE && "wo".equals(resource.getParent().getFileExtension())) {
			return resource.getParent();
		}
		return resource;
	}

	/** The component's name: its {@code .wo} folder's or {@code .html} file's, without the extension. */
	public static String componentName(IResource template) {
		final String name = template.getName();
		final int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/**
	 * The component's {@code .apiext}, existing or not.
	 *
	 * @param template the component's {@code .wo} folder or standalone {@code .html}
	 * @param api the component's {@code .api} as located, or null when it has none
	 */
	public static IFile apiextFile(IResource template, IFile api) {
		final String fileName = componentName(template) + ".apiext";
		if (api != null && api.exists()) {
			return api.getParent().getFile(new Path(fileName));
		}
		final IContainer parent = template.getParent();
		final IContainer folder = "lproj".equals(parent.getFileExtension()) ? parent.getParent() : parent;
		return folder.getFile(new Path(fileName));
	}
}

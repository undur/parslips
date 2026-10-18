package org.objectstyle.wolips.bindings.api;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.variables.TemplateRuntime;

/**
 * Keeps a WebObjects {@code .api} file beside an {@code .apiext}: the part of it {@code .api} can
 * carry ({@link ApiextToApi}), for the tools that read only {@code .api}. The {@code .apiext} is the
 * source; the {@code .api} is regenerated whole on every save, and says so in a header comment.
 *
 * <p>Only for WebObjects elements and components: ng-objects never reads {@code .api}. An element
 * is WebObjects when the class its {@code .apiext} names extends {@code WOElement}, and ng when it
 * extends {@code NGElement}; a class the project can't resolve falls back to where the file lives
 * ({@link TemplateRuntime#of}: an ng project, or an ng components folder, is ng).
 */
public final class ApiCompanion {

	private ApiCompanion() {
	}

	/** The {@code .api} beside the given {@code .apiext}: same folder, same base name. */
	public static IFile apiFileFor(IFile apiextFile) {
		final String name = apiextFile.getName();
		final String base = name.endsWith(".apiext") ? name.substring(0, name.length() - ".apiext".length()) : name;
		return apiextFile.getParent().getFile(new org.eclipse.core.runtime.Path(base + ".api"));
	}

	/**
	 * Writes the {@code .api} for the given {@code .apiext} when it describes a WebObjects element.
	 *
	 * @return the file written, or null when none was (an ng element)
	 */
	public static IFile writeIfWebObjects(IFile apiextFile, ApiextModel apiext) throws IOException {
		if (!isWebObjects(apiextFile, apiext.getClassName())) {
			return null;
		}
		final IFile apiFile = apiFileFor(apiextFile);
		final String comment = "Generated from " + apiextFile.getName() + " by Parslips. Edit that file instead: this one is rewritten on every save.";
		try (Writer writer = new FileWriter(apiFile.getLocation().toFile())) {
			ApiSerializer.serialize(ApiextToApi.convert(apiext), comment, writer);
		}
		try {
			apiFile.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
		}
		catch (CoreException e) {
			// The file is written; Eclipse notices it on its next refresh.
		}
		return apiFile;
	}

	/** Whether the {@code .apiext} describes a WebObjects element or component, so it gets an {@code .api}. */
	public static boolean isWebObjects(IFile apiextFile, String className) {
		return runtimeOf(apiextFile, className) == TemplateRuntime.WO;
	}

	/** The runtime of the element the {@code .apiext} describes: its class's root, else its location's. */
	static TemplateRuntime runtimeOf(IFile apiextFile, String className) {
		final TemplateRuntime byClass = runtimeOfClass(JavaCore.create(apiextFile.getProject()), className);
		if (byClass != null) {
			return byClass;
		}
		final ParsleyProject parsleyProject = (ParsleyProject) apiextFile.getProject().getAdapter(ParsleyProject.class);
		return TemplateRuntime.of(parsleyProject, apiextFile);
	}

	/** {@code WO} or {@code NG} by the class's element root, or null when the class can't be resolved. */
	private static TemplateRuntime runtimeOfClass(IJavaProject javaProject, String className) {
		if (javaProject == null || !javaProject.exists() || className == null || className.isEmpty()) {
			return null;
		}
		try {
			final IType type = javaProject.findType(className);
			if (type == null) {
				return null;
			}
			final ITypeHierarchy hierarchy = type.newSupertypeHierarchy(new NullProgressMonitor());
			for (final IType supertype : hierarchy.getAllSuperclasses(type)) {
				final String name = supertype.getFullyQualifiedName();
				if (ParsleyProject.WO_ELEMENT_CLASS.equals(name)) {
					return TemplateRuntime.WO;
				}
				if (ParsleyProject.NG_ELEMENT_CLASS.equals(name)) {
					return TemplateRuntime.NG;
				}
			}
		}
		catch (CoreException e) {
			// Unresolvable here; the caller falls back to the file's location.
		}
		return null;
	}
}

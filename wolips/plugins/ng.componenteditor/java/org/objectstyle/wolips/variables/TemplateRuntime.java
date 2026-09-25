package org.objectstyle.wolips.variables;

import org.eclipse.core.resources.IResource;

/**
 * Which runtime renders a template: ng-objects or WebObjects. The runtime decides a template's
 * tag vocabulary ({@code ng-tag-aliases.properties} vs {@code parsley-tag-aliases.properties}),
 * which element classes its tags can name ({@code NGElement} vs {@code WOElement} subclasses),
 * and so what it validates and completes against.
 *
 * <p>It is a property of the <b>template</b>, not the project: a hybrid project — a WebObjects
 * app that also serves ng-objects components — holds templates of both kinds side by side, and
 * each must be checked against the runtime that will render it. The rule mirrors how ng-objects
 * finds component templates: as classpath resources under {@code ng/<namespace>/components/}.
 * So a template is {@link #NG} when its project is an ng project, or when it lives under such a
 * path; otherwise it is {@link #WO}.
 */
public enum TemplateRuntime {

	NG(ParsleyProject.NG_ELEMENT_CLASS),
	WO(ParsleyProject.WO_ELEMENT_CLASS);

	private final String _elementClass;

	TemplateRuntime(String elementClass) {
		_elementClass = elementClass;
	}

	/** The root class every element of this runtime extends. */
	public String elementClass() {
		return _elementClass;
	}

	/**
	 * The runtime of a template (a standalone {@code .html} file, or a {@code .wo} folder) in the
	 * given project.
	 */
	public static TemplateRuntime of(ParsleyProject parsleyProject, IResource template) {
		if (parsleyProject != null && parsleyProject.ownProjectType() == ParsleyProject.ProjectType.NG) {
			return NG;
		}
		if (template != null && isNGTemplatePath(template.getProjectRelativePath().toString())) {
			return NG;
		}
		return WO;
	}

	/**
	 * Whether a project-relative path lies under an ng-objects component location,
	 * {@code …/ng/<namespace>/components/…} — where ng-objects' resource loader looks for component
	 * templates (e.g. {@code src/main/resources/ng/app/components/Main.html}).
	 */
	static boolean isNGTemplatePath(String projectRelativePath) {
		if (projectRelativePath == null) {
			return false;
		}
		final String[] segments = projectRelativePath.split("/");
		for (int i = 0; i + 2 < segments.length; i++) {
			if ("ng".equals(segments[i]) && !segments[i + 1].isEmpty() && "components".equals(segments[i + 2])) {
				return true;
			}
		}
		return false;
	}
}

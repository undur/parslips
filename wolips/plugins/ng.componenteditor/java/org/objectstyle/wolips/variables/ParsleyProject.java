package org.objectstyle.wolips.variables;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * The project model for a WO or ng-objects project in the workspace.
 * Provides framework detection, element/component class resolution, and
 * access to the underlying {@link BuildProperties}.
 *
 * <p>Obtained via the Eclipse adapter mechanism:
 * <pre>
 * ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
 * </pre>
 *
 * <p>Framework detection priority:
 * <ol>
 *   <li>{@code base=ng} in build.properties → ng-objects</li>
 *   <li>{@code base=wo} in build.properties → WebObjects</li>
 *   <li>Classpath probe: NGElement present and WOElement absent → ng-objects; otherwise WebObjects</li>
 * </ol>
 *
 * @see BuildProperties
 * @see ParsleyProjectAdapterFactory
 */
public class ParsleyProject {

	/** Fully-qualified root element type for ng-objects projects. */
	public static final String NG_ELEMENT_CLASS = "ng.appserver.templating.NGElement";
	/** Fully-qualified root component type for ng-objects projects. */
	public static final String NG_COMPONENT_CLASS = "ng.appserver.templating.NGComponent";

	/** Fully-qualified root element type for WebObjects projects. */
	public static final String WO_ELEMENT_CLASS = "com.webobjects.appserver.WOElement";
	/** Fully-qualified root component type for WebObjects projects. */
	public static final String WO_COMPONENT_CLASS = "com.webobjects.appserver.WOComponent";

	private final IProject _project;
	private final BuildProperties _buildProperties;

	public ParsleyProject(IProject project, BuildProperties buildProperties) {
		_project = project;
		_buildProperties = buildProperties;
	}

	public IProject getProject() {
		return _project;
	}

	public BuildProperties getBuildProperties() {
		return _buildProperties;
	}

	// ---- Framework detection ----

	/**
	 * Returns {@code true} if this project uses ng-objects (as opposed to WebObjects).
	 */
	public boolean isNGProject() {
		String base = _buildProperties.get(BuildProperties.Key.BASE);
		if ("ng".equals(base)) {
			return true;
		}
		if ("wo".equals(base)) {
			return false;
		}
		// No explicit override — probe the classpath.
		// Only claim ng if NGElement is present and WOElement is not;
		// if both are on the classpath (mixed workspace) default to WO.
		return classpathContains(NG_ELEMENT_CLASS) && !classpathContains(WO_ELEMENT_CLASS);
	}

	/**
	 * Returns the root element class name for this project.
	 *
	 * @see #isNGProject()
	 */
	private String getElementClass() {
		return resolveFrameworkClass(NG_ELEMENT_CLASS, WO_ELEMENT_CLASS);
	}

	/**
	 * Returns the root component class name for this project.
	 *
	 * @see #isNGProject()
	 */
	private String getComponentClass() {
		return resolveFrameworkClass(NG_COMPONENT_CLASS, WO_COMPONENT_CLASS);
	}

	private String resolveFrameworkClass(String ngClass, String woClass) {
		String base = _buildProperties.get(BuildProperties.Key.BASE);
		if ("ng".equals(base)) {
			return ngClass;
		}
		if ("wo".equals(base)) {
			return woClass;
		}
		// No explicit override — probe the classpath.
		if (classpathContains(ngClass) && !classpathContains(woClass)) {
			return ngClass;
		}
		return woClass;
	}

	private boolean classpathContains(String fullyQualifiedClassName) {
		try {
			IJavaProject javaProject = JavaCore.create(_project);
			if (javaProject != null && javaProject.exists()) {
				return javaProject.findType(fullyQualifiedClassName) != null;
			}
		} catch (Exception e) {
			// ignore — fall through to false
		}
		return false;
	}

	// ---- Static convenience methods ----

	/**
	 * Resolves the element class for the given project.
	 * Falls back to {@value #WO_ELEMENT_CLASS} if no project or adapter can be obtained.
	 */
	public static String getElementClass(IProject project) {
		ParsleyProject pp = forProject(project);
		return pp != null ? pp.getElementClass() : WO_ELEMENT_CLASS;
	}

	/**
	 * Resolves the component class for the given project.
	 * Falls back to {@value #WO_COMPONENT_CLASS} if no project or adapter can be obtained.
	 */
	public static String getComponentClass(IProject project) {
		ParsleyProject pp = forProject(project);
		return pp != null ? pp.getComponentClass() : WO_COMPONENT_CLASS;
	}

	/**
	 * Returns the {@link ParsleyProject} for the given project, or {@code null}
	 * if the adapter is not available.
	 */
	private static ParsleyProject forProject(IProject project) {
		if (project == null) {
			return null;
		}
		return (ParsleyProject) project.getAdapter(ParsleyProject.class);
	}
}

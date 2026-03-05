package org.objectstyle.wolips.variables;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
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
 * <p>Framework detection priority (see {@link #getProjectType()}):
 * <ol>
 *   <li>{@code project.base=ng} in build.properties → {@link ProjectType#NG}</li>
 *   <li>{@code project.base=wo} in build.properties → {@link ProjectType#WO}</li>
 *   <li>{@code project.name} in build.properties (without explicit project.base) → {@link ProjectType#WO}</li>
 *   <li>Classpath probe: NGElement present and WOElement absent → {@link ProjectType#NG}</li>
 *   <li>Classpath probe: WOElement present → {@link ProjectType#WO}</li>
 *   <li>Neither marker class on classpath → {@link ProjectType#UNKNOWN}</li>
 * </ol>
 *
 * @see BuildProperties
 * @see ParsleyProjectAdapterFactory
 */
public class ParsleyProject {

	/** The detected framework type for a project. */
	public enum ProjectType {
		/** An ng-objects project. */
		NG,
		/** A WebObjects project. */
		WO,
		/** Not recognized as either framework. */
		UNKNOWN
	}

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
	 * Detects the framework type for this project.
	 *
	 * <p>Detection priority:
	 * <ol>
	 *   <li>{@code project.base=ng} in build.properties → {@link ProjectType#NG}</li>
	 *   <li>{@code project.base=wo} in build.properties → {@link ProjectType#WO}</li>
	 *   <li>{@code project.name} in build.properties (without explicit project.base) → {@link ProjectType#WO}</li>
	 *   <li>Classpath probe: NGElement present and WOElement absent → {@link ProjectType#NG}</li>
	 *   <li>Classpath probe: WOElement present → {@link ProjectType#WO}</li>
	 *   <li>Neither marker class on classpath → {@link ProjectType#UNKNOWN}</li>
	 * </ol>
	 */
	public ProjectType getProjectType() {
		// 1. Explicit base in build.properties
		String base = _buildProperties.get(BuildProperties.Key.BASE);
		if ("ng".equals(base)) {
			return ProjectType.NG;
		}
		if ("wo".equals(base)) {
			return ProjectType.WO;
		}

		// 2. project.name without explicit base → legacy WO project
		if (_buildProperties.get(BuildProperties.Key.PROJECT_NAME) != null) {
			return ProjectType.WO;
		}

		// 3. Classpath probing
		boolean hasNG = classpathContains(NG_ELEMENT_CLASS);
		boolean hasWO = classpathContains(WO_ELEMENT_CLASS);

		if (hasNG && !hasWO) {
			return ProjectType.NG;
		}
		if (hasWO) {
			return ProjectType.WO;
		}

		return ProjectType.UNKNOWN;
	}

	/**
	 * Returns {@code true} if this project uses ng-objects (as opposed to WebObjects).
	 *
	 * @see #getProjectType()
	 */
	public boolean isNGProject() {
		return getProjectType() == ProjectType.NG;
	}

	/**
	 * Returns the root element class name for this project.
	 *
	 * @see #getProjectType()
	 */
	private String getElementClass() {
		return isNGProject() ? NG_ELEMENT_CLASS : WO_ELEMENT_CLASS;
	}

	/**
	 * Returns the root component class name for this project.
	 *
	 * @see #getProjectType()
	 */
	private String getComponentClass() {
		return isNGProject() ? NG_COMPONENT_CLASS : WO_COMPONENT_CLASS;
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

	// ---- WOLips coexistence ----

	/**
	 * The WOLips component editor bundle ID, used to detect whether WOLips
	 * is installed alongside Parsley.
	 */
	private static final String WOLIPS_BUNDLE_ID = "org.objectstyle.wolips.componenteditor";

	/** Cached result of WOLips bundle detection (null = not yet checked). */
	private static Boolean _wolipsInstalled;

	/**
	 * Returns {@code true} if Parsley should be active for this project.
	 *
	 * <p>When WOLips is <b>not</b> installed, Parsley handles all recognized
	 * projects (NG or WO) — the same as it always has.
	 *
	 * <p>When WOLips <b>is</b> installed, Parsley only activates for projects
	 * that explicitly set {@code project.base} in {@code build.properties}.
	 * Projects without this property are left to WOLips. This allows the user
	 * to opt in per-project by adding {@code project.base=wo} (or {@code =ng}).
	 */
	public boolean isParsleyProject() {
		if (!isWOLipsInstalled()) {
			// No WOLips — activate for any recognized project type
			return getProjectType() != ProjectType.UNKNOWN;
		}
		// WOLips is present — only activate if project.base is explicitly set
		return _buildProperties.get(BuildProperties.Key.BASE) != null;
	}

	/**
	 * Returns whether the WOLips component editor bundle is installed.
	 * Cached after first check since bundles don't change during a session.
	 */
	private static boolean isWOLipsInstalled() {
		if (_wolipsInstalled == null) {
			_wolipsInstalled = Platform.getBundle(WOLIPS_BUNDLE_ID) != null;
		}
		return _wolipsInstalled;
	}

	/**
	 * Convenience method: checks if the given project is a Parsley project.
	 *
	 * @return {@code true} if Parsley should be active for this project
	 * @see #isParsleyProject()
	 */
	public static boolean isParsleyProject(IProject project) {
		if (project == null || !project.isOpen()) {
			return false;
		}
		try {
			ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
			return pp != null && pp.isParsleyProject();
		}
		catch (Exception e) {
			return false;
		}
	}

	// ---- Static convenience methods ----

	/**
	 * Resolves the element class for the given project.
	 */
	public static String getElementClass(IProject project) {
		return forProject(project).getElementClass();
	}

	/**
	 * Resolves the component class for the given project.
	 */
	public static String getComponentClass(IProject project) {
		return forProject(project).getComponentClass();
	}

	/**
	 * Returns the {@link ParsleyProject} for the given project.
	 */
	private static ParsleyProject forProject(IProject project) {
		return (ParsleyProject) project.getAdapter(ParsleyProject.class);
	}
}

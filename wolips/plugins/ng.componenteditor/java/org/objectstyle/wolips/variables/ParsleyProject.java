package org.objectstyle.wolips.variables;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

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
	 * Returns {@code true} if this is a recognized WO or NG project.
	 *
	 * <p>This is a pure project type check — it does <b>not</b> consider
	 * WOLips coexistence policy. Use {@link #shouldHandleProject()} to
	 * determine whether Parsley should actually activate for this project.
	 */
	public boolean isParsleyProject() {
		return getProjectType() != ProjectType.UNKNOWN;
	}

	/**
	 * Returns {@code true} if Parsley should activate for this project.
	 *
	 * <p>When WOLips is <b>not</b> installed, Parsley handles all recognized
	 * projects (NG or WO).
	 *
	 * <p>When WOLips <b>is</b> installed, behavior depends on the global
	 * "Let Parsley handle all elements" preference:
	 * <ul>
	 *   <li><b>Off</b> (default): only projects with explicit
	 *       {@code project.base} in {@code build.properties}</li>
	 *   <li><b>On</b>: all recognized projects, regardless of
	 *       {@code project.base}</li>
	 * </ul>
	 *
	 * @see #isParsleyProject()
	 */
	public boolean shouldHandleProject() {
		if (!isParsleyProject()) {
			return false;
		}
		if (!isWOLipsInstalled()) {
			return true;
		}
		// Global preference: user wants Parsley for all recognized projects
		if (HTMLPlugin.getDefault() != null
				&& HTMLPlugin.getDefault().getPreferenceStore()
					.getBoolean(HTMLPlugin.PREF_PARSLEY_HANDLES_ALL)) {
			return true;
		}
		// Default: only activate if project.base is explicitly set
		return _buildProperties.get(BuildProperties.Key.BASE) != null;
	}

	/**
	 * Returns {@code true} if Parsley's refactoring participants should
	 * activate for this project.
	 *
	 * <p>This is more conservative than {@link #shouldHandleProject()} because
	 * WOLips has its own refactoring participants that skip only when
	 * {@code project.base} is set. If we activated based on the global
	 * "handle all elements" preference, both Parsley and WOLips would fire
	 * for the same rename — causing duplicate or conflicting changes.
	 *
	 * <p>Therefore, refactoring only activates when {@code project.base} is
	 * explicitly set (guaranteeing WOLips' participant skips), or when
	 * WOLips is not installed at all.
	 */
	public boolean shouldRefactor() {
		if (!isParsleyProject()) {
			return false;
		}
		if (!isWOLipsInstalled()) {
			return true;
		}
		// Only refactor when project.base is set — this is the signal
		// that makes WOLips' participant skip, avoiding double-fire.
		return _buildProperties.get(BuildProperties.Key.BASE) != null;
	}

	/**
	 * Convenience method: checks if Parsley's refactoring participants should
	 * activate for the given project.
	 *
	 * @return {@code true} if Parsley should refactor in this project
	 * @see #shouldRefactor()
	 */
	public static boolean shouldRefactor(IProject project) {
		if (project == null || !project.isOpen()) {
			return false;
		}
		try {
			ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
			return pp != null && pp.shouldRefactor();
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Returns whether the WOLips component editor bundle is installed.
	 * Cached after first check since bundles don't change during a session.
	 */
	public static boolean isWOLipsInstalled() {
		if (_wolipsInstalled == null) {
			_wolipsInstalled = Platform.getBundle(WOLIPS_BUNDLE_ID) != null;
		}
		return _wolipsInstalled;
	}

	/**
	 * Convenience method: checks if the given project is a recognized
	 * WO or NG project.
	 *
	 * @return {@code true} if this is a recognized Parsley project type
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

	/**
	 * Convenience method: checks if Parsley should activate for the given
	 * project, considering WOLips coexistence and the global preference.
	 *
	 * @return {@code true} if Parsley should handle this project
	 * @see #shouldHandleProject()
	 */
	public static boolean shouldHandleProject(IProject project) {
		if (project == null || !project.isOpen()) {
			return false;
		}
		try {
			ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
			return pp != null && pp.shouldHandleProject();
		}
		catch (Exception e) {
			return false;
		}
	}

	// ---- Project layout ----

	/**
	 * Finds the "components" folder in this project.
	 *
	 * <p>Searches in order:
	 * <ol>
	 *   <li>Under {@code src/main/} — the standard Maven layout
	 *       (handles both {@code src/main/components} and deeper nesting
	 *       like {@code src/main/resources/components})
	 *   <li>{@code Components/} at the project root — the "Fluffy Bunny"
	 *       layout used by some WebObjects projects
	 * </ol>
	 *
	 * @return the components folder, or {@code null} if not found
	 */
	public IFolder findComponentsFolder() {
		return findComponentsFolder(_project);
	}

	/**
	 * Static variant of {@link #findComponentsFolder()} for callers that
	 * don't have a {@link ParsleyProject} instance handy.
	 *
	 * <p>For ng-objects projects, the components folder is under
	 * {@code src/main/resources/} (e.g. {@code src/main/resources/ng/app/components/}).
	 * We must NOT search all of {@code src/main/} because that would also
	 * match the Java source folder ({@code src/main/java/.../components/})
	 * which contains {@code .java} files, not templates.
	 *
	 * <p>For WebObjects projects, the components folder is directly under
	 * {@code src/main/} (e.g. {@code src/main/components/}), so the broader
	 * search is correct.
	 *
	 * @param project the project to search in
	 * @return the components folder, or {@code null} if not found
	 */
	public static IFolder findComponentsFolder(IProject project) {
		try {
			ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
			boolean isNG = pp != null && pp.isNGProject();

			if (isNG) {
				// ng-objects: search only under src/main/resources/ to avoid
				// matching the Java source "components" package
				IFolder srcMainResources = project.getFolder("src/main/resources");
				if (srcMainResources.exists()) {
					IFolder found = findComponentsFolderIn(srcMainResources);
					if (found != null) {
						return found;
					}
				}
			}
			else {
				// WebObjects: search under all of src/main/ (components are in
				// src/main/components/ which is not under resources/)
				IFolder srcMain = project.getFolder("src/main");
				if (srcMain.exists()) {
					IFolder found = findComponentsFolderIn(srcMain);
					if (found != null) {
						return found;
					}
				}

				// Fluffy Bunny layout: Components/ at the project root
				IFolder fluffyBunny = project.getFolder("Components");
				if (fluffyBunny.exists()) {
					return fluffyBunny;
				}
			}
		}
		catch (CoreException e) {
			// fall through
		}
		return null;
	}

	/**
	 * Recursively searches for a folder named "components" (case-insensitive)
	 * under the given container.
	 */
	private static IFolder findComponentsFolderIn(IContainer container) throws CoreException {
		for (IResource member : container.members()) {
			if (member.getType() == IResource.FOLDER) {
				if ("components".equalsIgnoreCase(member.getName())) {
					return (IFolder) member;
				}
				IFolder found = findComponentsFolderIn((IFolder) member);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
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

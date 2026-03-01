package org.objectstyle.wolips.variables;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

public class BuildProperties {
	private IProject _project;

	private Properties _properties;

	private boolean _dirty;

	private long _version;

	public BuildProperties(IProject project) {
		_project = project;
		_version = -1;
		load();
	}

	public boolean isDirty() {
		return _dirty;
	}

	public IProject getProject() {
		return _project;
	}

	public IFile getBuildPropertiesEclipseFile() {
		IFile file = _project.getFile("build.properties");
		return file;
	}

	public File getBuildPropertiesFile() {
		File file = getBuildPropertiesEclipseFile().getLocation().toFile();
		return file;
	}

	public long getModificationStamp() {
		File file = getBuildPropertiesFile();
		if (_version == -1 && file.exists()) {
			_version = file.lastModified();
		}
		return _version;
	}

	public synchronized boolean getBoolean(String key, boolean defaultValue) {
		String strValue = get(key);
		boolean value;
		if (strValue == null) {
			value = defaultValue;
		}
		else {
			value = "true".equalsIgnoreCase(strValue);
		}
		return value;
	}

	public synchronized String get(String key) {
		return get(key, null);
	}

	public synchronized String get(String key, String defaultValue) {
		String value = _properties.getProperty(key, defaultValue);
		return value;
	}

	public synchronized void remove(String key) {
		put(key, null);
	}

	public synchronized void put(String key, boolean value) {
		put(key, Boolean.valueOf(value).toString());
	}

	public synchronized void put(String key, String value) {
		if (value == null) {
			if (_properties.containsKey(key)) {
				_properties.remove(key);
				_dirty = true;
			}
		}
		else {
			String oldValue = get(key);
			if (!value.equals(oldValue)) {
				_properties.setProperty(key, value);
				_dirty = true;
			}
		}
	}

	protected void load() {
		try {
			boolean dirty;
			
			Properties properties = new Properties();
			File file = getBuildPropertiesFile();
			if (file.exists()) {
				InputStream inputStream = new FileInputStream(file);
				try {
					properties.load(inputStream);
				}
				finally {
					inputStream.close();
				}
				dirty = false;
			}
			else {
				dirty = true;
			}
			
			synchronized (this) {
				_dirty = dirty;
				_properties = properties;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to load the build properties for the project '" + _project + "'.", e);
		}
	}

	public synchronized void save() throws CoreException, IOException {
		if (!_dirty) {
			return;
		}

		// Use a sorted Properties subclass so the output is deterministic.
		// This replaces the old ToHellWithProperties from woenvironment.jar.
		Properties sorted = new Properties() {
			@Override
			public java.util.Enumeration<Object> keys() {
				return java.util.Collections.enumeration(new java.util.TreeSet<>(super.keySet()));
			}
		};
		sorted.putAll(_properties);

		File file = getBuildPropertiesFile();
		FileOutputStream fos = new FileOutputStream(file);
		try {
			sorted.store(fos, null);
		}
		finally {
			fos.close();
		}

		ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				getBuildPropertiesEclipseFile().refreshLocal(IResource.DEPTH_ONE, monitor);
			}
		}, null);

		_dirty = false;
	}

	public String getName() {
		String projectName = get("project.name");
		// MS: compatibility with old build.properties
		if (projectName == null || projectName.length() == 0) {
			projectName = get("framework.name");
		}
		if (projectName == null || projectName.length() == 0) {
			projectName = _project.getName();
		}
		return projectName;
	}

	public void setName(String name) {
		put("project.name", name);
		put("project.name.lowercase", name.toLowerCase());
	}

	public boolean isFramework() {
		boolean isFramework = false;
		String projectType = get("project.type");
		if (projectType != null) {
			isFramework = "framework".equals(projectType);
		}
		else {
			// MS: compatibility with old build.properties
			String frameworkName = get("framework.name");
			if (frameworkName != null) {
				isFramework = true;
			}
		}
		return isFramework;
	}

	// ---- Defaults machinery ----
	// Workspace-level defaults for inline binding syntax and template strictness.
	// Initialized lazily via ensureDefaultsInitialized(), and copied between
	// BuildProperties instances by _copyDefaultsFrom() to avoid redundant I/O.

	private boolean _defaultsInitialized;

	private String _inlineBindingPrefixDefault;

	private String _inlineBindingSuffixDefault;

	private boolean _wellFormedTemplateRequiredDefault;

	/**
	 * Copies cached defaults from another BuildProperties instance to avoid
	 * re-reading preferences. Called from {@link BuildPropertiesAdapterFactory}
	 * when creating a new BuildProperties for a project that shares the same workspace.
	 */
	public void _copyDefaultsFrom(BuildProperties props) {
		if (props._defaultsInitialized) {
			_inlineBindingPrefixDefault = props._inlineBindingPrefixDefault;
			_inlineBindingSuffixDefault = props._inlineBindingSuffixDefault;
			_wellFormedTemplateRequiredDefault = props._wellFormedTemplateRequiredDefault;
			_defaultsInitialized = true;
		}
	}

	protected synchronized void ensureDefaultsInitialized() {
		if (!_defaultsInitialized) {
			_defaultsInitialized = true;
			_inlineBindingPrefixDefault = "$";
			_inlineBindingSuffixDefault = "";
			_wellFormedTemplateRequiredDefault = "yes".equals(Platform.getPreferencesService().getString("ng.componenteditor", "WellFormedTemplate", null, null));
		}
	}

	/**
	 * Returns the configured inline binding prefix for this project (e.g. "$"),
	 * falling back to the workspace default.
	 */
	public String getInlineBindingPrefix() {
		ensureDefaultsInitialized();
		return get("component.inlineBindingPrefix", _inlineBindingPrefixDefault);
	}

	/**
	 * Returns the configured inline binding suffix for this project (e.g. ""),
	 * falling back to the workspace default.
	 */
	public String getInlineBindingSuffix() {
		ensureDefaultsInitialized();
		return get("component.inlineBindingSuffix", _inlineBindingSuffixDefault);
	}

	/**
	 * Returns whether this project requires well-formed (XHTML-style) templates,
	 * falling back to the workspace default.
	 */
	public boolean isWellFormedTemplateRequired() {
		ensureDefaultsInitialized();
		return getBoolean("component.wellFormedTemplateRequired", _wellFormedTemplateRequiredDefault);
	}

	// ---- Framework detection (ng-objects vs WebObjects) ----

	/** Fully-qualified root element type for ng-objects projects. */
	public static final String NG_ELEMENT_CLASS = "ng.appserver.templating.NGElement";
	/** Fully-qualified root component type for ng-objects projects. */
	public static final String NG_COMPONENT_CLASS = "ng.appserver.templating.NGComponent";
	/** Package prefix for ng-objects built-in ("private") elements. */
	public static final String NG_PRIVATE_ELEMENT_PACKAGE = "ng.appserver.templating._private.";

	/** Fully-qualified root element type for WebObjects projects. */
	public static final String WO_ELEMENT_CLASS = "com.webobjects.appserver.WOElement";
	/** Fully-qualified root component type for WebObjects projects. */
	public static final String WO_COMPONENT_CLASS = "com.webobjects.appserver.WOComponent";
	/** Package prefix for WebObjects built-in ("private") elements. */
	public static final String WO_PRIVATE_ELEMENT_PACKAGE = "com.webobjects.appserver._private.";

	/**
	 * Returns the root element class name for this project, using the following priority:
	 * <ol>
	 *   <li>{@code base=ng} in build.properties &rarr; {@value #NG_ELEMENT_CLASS}</li>
	 *   <li>{@code base=wo} in build.properties &rarr; {@value #WO_ELEMENT_CLASS}</li>
	 *   <li>Classpath probe: if NGElement is on the classpath, use it; otherwise fall back to WOElement</li>
	 * </ol>
	 */
	public String getElementClass() {
		return resolveFrameworkClass(NG_ELEMENT_CLASS, WO_ELEMENT_CLASS);
	}

	/**
	 * Returns the root component class name for this project (same priority as {@link #getElementClass()}).
	 */
	public String getComponentClass() {
		return resolveFrameworkClass(NG_COMPONENT_CLASS, WO_COMPONENT_CLASS);
	}

	/**
	 * Returns the package prefix for built-in ("private") elements for this project.
	 */
	public String getPrivateElementPackage() {
		return resolveFrameworkClass(NG_PRIVATE_ELEMENT_PACKAGE, WO_PRIVATE_ELEMENT_PACKAGE);
	}

	/**
	 * Returns {@code true} if this project uses ng-objects (as opposed to WebObjects).
	 */
	public boolean isNGProject() {
		String base = get("base");
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

	private String resolveFrameworkClass(String ngClass, String woClass) {
		String base = get("base");
		if ("ng".equals(base)) {
			return ngClass;
		}
		if ("wo".equals(base)) {
			return woClass;
		}
		// No explicit override — probe the classpath.
		// Only use ng classes if NGElement is present and WOElement is not;
		// if both are on the classpath (mixed workspace) default to WO.
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

	/**
	 * Convenience: resolves the element class for the given project.
	 * Falls back to {@value #WO_ELEMENT_CLASS} if no project or build.properties can be obtained.
	 */
	public static String getElementClass(IProject project) {
		if (project != null) {
			try {
				BuildProperties bp = (BuildProperties) project.getAdapter(BuildProperties.class);
				if (bp != null) {
					return bp.getElementClass();
				}
			} catch (Exception e) {
				// ignore
			}
		}
		return WO_ELEMENT_CLASS;
	}

	/**
	 * Convenience: resolves the component class for the given project.
	 * Falls back to {@value #WO_COMPONENT_CLASS} if no project or build.properties can be obtained.
	 */
	public static String getComponentClass(IProject project) {
		if (project != null) {
			try {
				BuildProperties bp = (BuildProperties) project.getAdapter(BuildProperties.class);
				if (bp != null) {
					return bp.getComponentClass();
				}
			} catch (Exception e) {
				// ignore
			}
		}
		return WO_COMPONENT_CLASS;
	}

	/**
	 * Convenience: resolves the element class for the given Java project.
	 */
	public static String getElementClass(IJavaProject javaProject) {
		if (javaProject != null) {
			return getElementClass(javaProject.getProject());
		}
		return WO_ELEMENT_CLASS;
	}

	/**
	 * Convenience: resolves the component class for the given Java project.
	 */
	public static String getComponentClass(IJavaProject javaProject) {
		if (javaProject != null) {
			return getComponentClass(javaProject.getProject());
		}
		return WO_COMPONENT_CLASS;
	}

	/**
	 * Convenience: resolves the private element package prefix for the given project.
	 */
	public static String getPrivateElementPackage(IProject project) {
		if (project != null) {
			try {
				BuildProperties bp = (BuildProperties) project.getAdapter(BuildProperties.class);
				if (bp != null) {
					return bp.getPrivateElementPackage();
				}
			} catch (Exception e) {
				// ignore
			}
		}
		return NG_PRIVATE_ELEMENT_PACKAGE;
	}
}

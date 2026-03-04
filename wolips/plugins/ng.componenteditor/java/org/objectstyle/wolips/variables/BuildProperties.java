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

/**
 * Wrapper around a project's {@code build.properties} file. Provides access
 * to build-time configuration: inline binding syntax, template strictness,
 * project name, and framework/application type.
 *
 * <p>For project-level concerns like framework detection (ng-objects vs WebObjects)
 * and element/component class resolution, use {@link ParsleyProject} instead.
 *
 * @see ParsleyProject
 */
public class BuildProperties {

	/**
	 * Known property keys in {@code build.properties}.
	 * Each constant carries the property key string and a human-readable description.
	 */
	public enum Key {
		/** Framework type: "ng" for ng-objects, "wo" for WebObjects, or absent for classpath probing. */
		BASE("base", "Framework type (ng or wo)"),

		/** The project name. Falls back to {@link #FRAMEWORK_NAME} for legacy compatibility. */
		PROJECT_NAME("project.name", "Project name"),

		/** Auto-generated lowercase variant of the project name. */
		PROJECT_NAME_LOWERCASE("project.name.lowercase", "Lowercase project name"),

		/** Legacy property: framework name (pre-dates project.name). */
		FRAMEWORK_NAME("framework.name", "Legacy framework name"),

		/** Project type: "framework" for framework projects, absent for applications. */
		PROJECT_TYPE("project.type", "Project type (framework or application)"),

		/** Inline binding prefix (e.g. "$"). */
		INLINE_BINDING_PREFIX("component.inlineBindingPrefix", "Inline binding prefix"),

		/** Inline binding suffix (e.g. ""). */
		INLINE_BINDING_SUFFIX("component.inlineBindingSuffix", "Inline binding suffix"),

		/** Whether templates must be well-formed (XHTML-style). */
		WELL_FORMED_TEMPLATE_REQUIRED("component.wellFormedTemplateRequired", "Require well-formed templates"),

		/** Whether template validation is enabled. */
		VALIDATE_TEMPLATES("component.validateTemplates", "Enable template validation"),

		/** Whether template validation runs during incremental builds. */
		VALIDATE_TEMPLATES_ON_BUILD("component.validateTemplatesOnBuild", "Validate templates on build"),

		/** Whether validation runs in a thread pool. */
		THREADED_VALIDATION("component.threadedValidation", "Use threaded validation");

		private final String _key;
		private final String _description;

		Key(String key, String description) {
			_key = key;
			_description = description;
		}

		/** The property key string as it appears in {@code build.properties}. */
		public String key() {
			return _key;
		}

		/** Human-readable description of this property's purpose. */
		public String description() {
			return _description;
		}
	}

	private IProject _project;

	private Properties _properties;

	private boolean _dirty;

	private long _version;

	public BuildProperties(IProject project) {
		_project = project;
		_version = -1;
		load();
	}

	private boolean isDirty() {
		return _dirty;
	}

	private IProject getProject() {
		return _project;
	}

	private IFile getBuildPropertiesEclipseFile() {
		IFile file = _project.getFile("build.properties");
		return file;
	}

	private File getBuildPropertiesFile() {
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

	/** Returns the boolean value for the given key, or {@code defaultValue} if absent. */
	public synchronized boolean getBoolean(Key key, boolean defaultValue) {
		return getBoolean(key.key(), defaultValue);
	}

	/** Returns the value for the given key, or {@code null} if absent. */
	public synchronized String get(Key key) {
		return get(key.key(), null);
	}

	/** Returns the value for the given key, or {@code defaultValue} if absent. */
	public synchronized String get(Key key, String defaultValue) {
		return get(key.key(), defaultValue);
	}

	private synchronized boolean getBoolean(String key, boolean defaultValue) {
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

	private synchronized String get(String key) {
		return get(key, null);
	}

	private synchronized String get(String key, String defaultValue) {
		String value = _properties.getProperty(key, defaultValue);
		return value;
	}

	private synchronized void remove(String key) {
		put(key, null);
	}

	private synchronized void put(String key, boolean value) {
		put(key, Boolean.valueOf(value).toString());
	}

	private synchronized void put(String key, String value) {
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

	private void load() {
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

	private synchronized void save() throws CoreException, IOException {
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

	private String getName() {
		String projectName = get(Key.PROJECT_NAME);
		// MS: compatibility with old build.properties
		if (projectName == null || projectName.length() == 0) {
			projectName = get(Key.FRAMEWORK_NAME);
		}
		if (projectName == null || projectName.length() == 0) {
			projectName = _project.getName();
		}
		return projectName;
	}

	private void setName(String name) {
		put(Key.PROJECT_NAME.key(), name);
		put(Key.PROJECT_NAME_LOWERCASE.key(), name.toLowerCase());
	}

	private boolean isFramework() {
		boolean isFramework = false;
		String projectType = get(Key.PROJECT_TYPE);
		if (projectType != null) {
			isFramework = "framework".equals(projectType);
		}
		else {
			// MS: compatibility with old build.properties
			String frameworkName = get(Key.FRAMEWORK_NAME);
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
	 * re-reading preferences. Called from {@link ParsleyProjectAdapterFactory}
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

	private synchronized void ensureDefaultsInitialized() {
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
		return get(Key.INLINE_BINDING_PREFIX, _inlineBindingPrefixDefault);
	}

	/**
	 * Returns the configured inline binding suffix for this project (e.g. ""),
	 * falling back to the workspace default.
	 */
	public String getInlineBindingSuffix() {
		ensureDefaultsInitialized();
		return get(Key.INLINE_BINDING_SUFFIX, _inlineBindingSuffixDefault);
	}

	/**
	 * Returns whether this project requires well-formed (XHTML-style) templates,
	 * falling back to the workspace default.
	 */
	public boolean isWellFormedTemplateRequired() {
		ensureDefaultsInitialized();
		return getBoolean(Key.WELL_FORMED_TEMPLATE_REQUIRED, _wellFormedTemplateRequiredDefault);
	}

}

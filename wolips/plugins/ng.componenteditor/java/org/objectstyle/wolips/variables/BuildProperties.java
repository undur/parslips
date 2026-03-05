package org.objectstyle.wolips.variables;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;

/**
 * Read-only wrapper around a project's {@code build.properties} file.
 * Provides access to build-time configuration: inline binding syntax,
 * template strictness, and project name.
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
		BASE("project.base", "Framework type (ng or wo)"),

		/** The project name. */
		PROJECT_NAME("project.name", "Project name"),

		/** Project type: "framework" or "application". Used by the Vermilingua build plugin. */
		PROJECT_TYPE("project.type", "Project type (framework or application)"),

		/** Inline binding prefix (e.g. "$"). */
		INLINE_BINDING_PREFIX("component.inlineBindingPrefix", "Inline binding prefix"),

		/** Inline binding suffix (e.g. ""). */
		INLINE_BINDING_SUFFIX("component.inlineBindingSuffix", "Inline binding suffix"),

		/** Whether templates must be well-formed (XHTML-style). */
		WELL_FORMED_TEMPLATE_REQUIRED("component.wellFormedTemplateRequired", "Require well-formed templates");

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

	private final IProject _project;

	private Properties _properties;

	private long _version;

	public BuildProperties(IProject project) {
		_project = project;
		_version = -1;
		load();
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

	private void load() {
		try {
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
			}

			synchronized (this) {
				_properties = properties;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to load the build properties for the project '" + _project + "'.", e);
		}
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

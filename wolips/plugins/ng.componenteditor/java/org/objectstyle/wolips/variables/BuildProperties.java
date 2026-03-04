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

}

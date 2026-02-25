package org.objectstyle.wolips.templateengine;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.objectstyle.wolips.baseforplugins.logging.PluginLogger;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Lazy-initialized singleton for the template engine.
 * Not an OSGi activator — delegates to HTMLPlugin for bundle access.
 */
public class TemplateEnginePlugin {
	private final static String PLUGIN_ID = "ng.componenteditor";

	private static TemplateEnginePlugin plugin;

	private PluginLogger pluginLogger = null;

	public static final String WOComponent = "WOComponent";

	private TemplateEnginePlugin() {
		this.pluginLogger = new PluginLogger(PLUGIN_ID, false);
	}

	public static synchronized TemplateEnginePlugin getDefault() {
		if (plugin == null) {
			plugin = new TemplateEnginePlugin();
		}
		return plugin;
	}

	public static String getPluginId() {
		return PLUGIN_ID;
	}

	public static IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	public PluginLogger getPluginLogger() {
		return this.pluginLogger;
	}

	/**
	 * Returns the OSGi bundle — delegates to the main HTMLPlugin activator.
	 */
	public org.osgi.framework.Bundle getBundle() {
		return HTMLPlugin.getDefault().getBundle();
	}
}

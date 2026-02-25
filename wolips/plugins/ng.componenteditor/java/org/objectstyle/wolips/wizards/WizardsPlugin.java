package org.objectstyle.wolips.wizards;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;

/**
 * Lazy-initialized singleton for the wizard functionality.
 * Not an OSGi activator — delegates to the main HTMLPlugin for bundle access.
 */
public class WizardsPlugin {
	private static WizardsPlugin plugin;

	private static ImageDescriptor WOCOMPONENT_WIZARD_BANNER;

	private IDialogSettings dialogSettings;

	private WizardsPlugin() {
		dialogSettings = new DialogSettings("ng.componenteditor.wizards");
	}

	public static synchronized WizardsPlugin getDefault() {
		if (plugin == null) {
			plugin = new WizardsPlugin();
		}
		return plugin;
	}

	public IDialogSettings getDialogSettings() {
		return dialogSettings;
	}

	public void publicSaveDialogSettings() {
		// Dialog settings are in-memory only; nothing to persist for now
	}

	public void log(Throwable t) {
		if (t != null) {
			t.printStackTrace();
		}
	}

	public static ImageDescriptor getImageDescriptor(String path) {
		return AbstractUIPlugin.imageDescriptorFromPlugin("ng.componenteditor", path);
	}

	public static ImageDescriptor WOCOMPONENT_WIZARD_BANNER() {
		if (WOCOMPONENT_WIZARD_BANNER == null)
			WOCOMPONENT_WIZARD_BANNER = getImageDescriptor("icons/wizban/webobjects_wiz.gif");
		return WOCOMPONENT_WIZARD_BANNER;
	}

	public static void selectAndReveal(IResource[] resources) {
		if (resources != null) {
			for (int i = 0; i < resources.length; i++) {
				selectAndReveal(resources[i]);
			}
		}
	}

	public static void selectAndReveal(IResource newResource) {
		if (newResource != null) {
			BasicNewResourceWizard.selectAndReveal(newResource, WorkbenchUtilities.getActiveWorkbenchWindow());
			if (newResource.getType() == IResource.FILE)
				WorkbenchUtilities.open((IFile) newResource);
		}
	}
}

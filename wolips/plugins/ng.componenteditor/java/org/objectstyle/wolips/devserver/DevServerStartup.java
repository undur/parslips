package org.objectstyle.wolips.devserver;

import org.eclipse.ui.IStartup;

/**
 * Workbench startup hook — starts the dev server on launch if it's enabled in
 * preferences. Registered via the {@code org.eclipse.ui.startup} extension
 * point in plugin.xml.
 */
public class DevServerStartup implements IStartup {

	@Override
	public void earlyStartup() {
		DevServerManager.getDefault().startIfEnabled();
	}
}

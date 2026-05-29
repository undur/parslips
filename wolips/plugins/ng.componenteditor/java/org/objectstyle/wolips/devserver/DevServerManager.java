package org.objectstyle.wolips.devserver;

import java.net.BindException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Owns the lifecycle of the single {@link DevServer} instance and keeps it in
 * sync with the user's preferences.
 *
 * <p>Both the workbench startup hook ({@link DevServerStartup}) and the
 * preference page ({@code DevServerPreferencePage}) drive the server through
 * this manager, so toggling the "enabled" preference or changing the port can
 * restart the server live without an Eclipse restart.
 *
 * <p>All methods are synchronized on the manager instance — start/stop can be
 * triggered from the UI thread (preference apply) and the startup thread.
 */
public final class DevServerManager {

	private static final DevServerManager INSTANCE = new DevServerManager();

	public static DevServerManager getDefault() {
		return INSTANCE;
	}

	private DevServer _server;

	private DevServerManager() {
	}

	private static IPreferenceStore prefs() {
		return ComponenteditorPlugin.getDefault().getPreferenceStore();
	}

	/**
	 * Starts the server if the "enabled" preference is set and it isn't
	 * already running. Safe to call when disabled (no-op) or already
	 * running (no-op).
	 */
	public synchronized void startIfEnabled() {
		if (!prefs().getBoolean(DevServerPreferences.SERVER_ENABLED)) {
			return;
		}
		if (_server != null && _server.isRunning()) {
			return;
		}
		startServer();
	}

	/**
	 * Stops the server if running, then starts it again if currently enabled.
	 * Used by the preference page when settings change so the new port /
	 * enabled state takes effect immediately.
	 */
	public synchronized void restart() {
		stop();
		startIfEnabled();
	}

	public synchronized void stop() {
		if (_server != null) {
			_server.stop();
			_server = null;
		}
	}

	public synchronized boolean isRunning() {
		return _server != null && _server.isRunning();
	}

	private void startServer() {
		int port = prefs().getInt(DevServerPreferences.SERVER_PORT);
		if (port <= 0) {
			port = DevServerPreferences.DEFAULT_PORT;
		}

		DevServer server = new DevServer(port);
		try {
			server.start();
			_server = server;
		}
		catch (BindException e) {
			// The port is already in use — almost always a second Eclipse
			// with Parsley already running (the dev server is on by default,
			// so this is a routine, expected situation for anyone who keeps
			// more than one window open). The first instance owns the port
			// and is the one the runtime talks to anyway, so there's nothing
			// to fix here. Log a calm INFO note rather than an alarming error
			// with a stack trace, and carry on — the feature is simply
			// unavailable in this instance.
			ComponenteditorPlugin.getDefault().log(new Status(
					IStatus.INFO,
					ComponenteditorPlugin.PLUGIN_ID,
					"Parsley dev server: port " + port + " is already in use "
							+ "(another Eclipse instance likely owns it). The dev "
							+ "server will not run in this instance."));
			_server = null;
		}
		catch (Exception e) {
			// Anything other than a port conflict is genuinely unexpected —
			// log it as an error. We deliberately don't pop a dialog; startup
			// shouldn't be interrupted by a modal.
			ComponenteditorPlugin.getDefault().log(e);
			_server = null;
		}
	}
}

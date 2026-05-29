package org.objectstyle.wolips.devserver;

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
	 * password / enabled state takes effect immediately.
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
		String password = prefs().getString(DevServerPreferences.SERVER_PASSWORD);

		DevServer server = new DevServer(port, password);
		try {
			server.start();
			_server = server;
		}
		catch (Exception e) {
			// Most likely the port is already in use (another Eclipse, or a
			// stale process). Log it; the user can pick a different port in
			// preferences. We deliberately don't pop a dialog — startup
			// shouldn't be interrupted by a modal error.
			ComponenteditorPlugin.getDefault().log(e);
			_server = null;
		}
	}
}

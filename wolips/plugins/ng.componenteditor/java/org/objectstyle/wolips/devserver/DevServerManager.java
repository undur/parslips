package org.objectstyle.wolips.devserver;

import java.net.BindException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
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

	/** How often, and for how long, a port held by someone else is tried again. */
	static final long PORT_RETRY_INTERVAL_MILLIS = 2000;
	static final long PORT_RETRY_WINDOW_MILLIS = 60_000;

	private DevServer _server;

	/** When the current run of port retries gives up; 0 when not retrying. */
	private long _retryDeadline;

	/**
	 * Tries the port again while it's held. An Eclipse that was quit just before this one started
	 * may still be shutting down, and releases the port within seconds; giving up at the first
	 * refusal left the new Eclipse without a dev server for its whole session.
	 */
	private final Job _retryJob = new Job("Parsley dev server: waiting for its port") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			startIfEnabled();
			return Status.OK_STATUS;
		}
	};

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
			_retryDeadline = 0;
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

	/**
	 * Stops the server and any pending retries. Called when the plug-in stops, which on shutdown
	 * comes before the launcher exits or restarts: on macOS the launcher restarts Eclipse by
	 * fork + exec from the same process, so a listening socket still open at that point is
	 * inherited by the new Eclipse, which then holds the port with nothing accepting on it.
	 */
	public synchronized void stop() {
		_retryDeadline = 0;
		_retryJob.cancel();
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
			_retryDeadline = 0;
		}
		catch (BindException e) {
			_server = null;
			final long now = System.currentTimeMillis();
			if (_retryDeadline == 0) {
				_retryDeadline = now + PORT_RETRY_WINDOW_MILLIS;
			}
			if (now < _retryDeadline) {
				_retryJob.setSystem(true);
				_retryJob.schedule(PORT_RETRY_INTERVAL_MILLIS);
				return;
			}
			_retryDeadline = 0;
			// Still held after the retries: usually a second Eclipse with Parslips running, which
			// owns the port and is the one apps talk to. A routine situation, so a calm INFO note
			// rather than an error with a stack trace; the feature is simply unavailable here.
			ComponenteditorPlugin.getDefault().log(new Status(
					IStatus.INFO,
					ComponenteditorPlugin.PLUGIN_ID,
					"Parsley dev server: port " + port + " is held by another process (usually another Eclipse "
							+ "running Parslips), so the dev server won't run in this instance. Once the port is free, "
							+ "apply the Dev Server preferences to start it."));
		}
		catch (Throwable t) {
			// Anything other than a port conflict is genuinely unexpected —
			// log it as an error, Errors included (a LinkageError from a handler
			// class is as fatal here as any exception). We deliberately don't pop
			// a dialog; startup shouldn't be interrupted by a modal.
			ComponenteditorPlugin.getDefault().log(new Status(
					IStatus.ERROR,
					ComponenteditorPlugin.PLUGIN_ID,
					"Parsley dev server failed to start on port " + port,
					t));
			_server = null;
		}
	}
}

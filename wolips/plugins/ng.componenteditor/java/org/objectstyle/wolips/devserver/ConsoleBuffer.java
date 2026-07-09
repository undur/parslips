package org.objectstyle.wolips.devserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Keeps an in-memory tail of every launched application's console output, so the
 * {@code /console} endpoint can serve it to external tools. This closes the dev loop's
 * biggest blind spot for agents: when a launch fails <em>before</em> the app's own HTTP
 * is up (bad config, port conflict, startup exception), the only evidence is in the
 * Eclipse console — which an external tool can't see. With this buffer, it can.
 *
 * <p>One buffer per launch configuration name, <b>latest launch wins</b> — and the buffer
 * survives the launch's termination on purpose, because reading the console of an app
 * that just died on startup is the whole point. A new launch of the same config replaces
 * the old buffer.
 *
 * <p>Wiring: {@link #install()} registers a launch listener (idempotent; called from
 * {@link DevServer#start()}) and also attaches to launches already running. Stream
 * listeners are attached per {@link IProcess}, guarded by a process attribute so the
 * repeated {@code launchChanged} notifications don't double-attach. Attaching also grabs
 * {@link IStreamMonitor#getContents()} so output produced before the listener existed is
 * not lost.
 *
 * <p>The buffer is capped (tail-trimmed) so a chatty app can't grow memory unboundedly.
 */
final class ConsoleBuffer {

	/** Keep at most this many characters per buffer; when exceeded, trim to half (keeping the tail). */
	private static final int MAX_CHARS = 400_000;

	private static final String ATTACHED_MARKER = "org.objectstyle.wolips.devserver.consolebuffer.attached";

	private static final Map<String, Buffer> _byConfigName = new ConcurrentHashMap<>();
	private static volatile boolean _installed;

	private ConsoleBuffer() {
	}

	/** The console tail of one launch (the most recent one for its config). */
	static final class Buffer {
		final String configName;
		final String projectName;
		final long startedEpochMillis;
		private final ILaunch _launch;
		private final StringBuilder _text = new StringBuilder();

		Buffer(String configName, String projectName, ILaunch launch, long startedEpochMillis) {
			this.configName = configName;
			this.projectName = projectName;
			_launch = launch;
			this.startedEpochMillis = startedEpochMillis;
		}

		synchronized void append(String s) {
			_text.append(s);
			if (_text.length() > MAX_CHARS) {
				_text.delete(0, _text.length() - MAX_CHARS / 2);
			}
		}

		synchronized String text() {
			return _text.toString();
		}

		boolean isTerminated() {
			return _launch.isTerminated();
		}

		/** The process exit value, or null while running / when unavailable. */
		Integer exitValue() {
			try {
				final IProcess[] processes = _launch.getProcesses();
				if (processes.length > 0 && processes[0].isTerminated()) {
					return Integer.valueOf(processes[0].getExitValue());
				}
			}
			catch (Exception e) {
				// Exit value genuinely unavailable — report null rather than fail the read.
			}
			return null;
		}
	}

	/** Registers the launch listener and attaches to already-running launches. Idempotent. */
	static synchronized void install() {
		if (_installed) {
			return;
		}
		_installed = true;

		DebugPlugin.getDefault().getLaunchManager().addLaunchListener(new ILaunchListener() {
			@Override
			public void launchAdded(ILaunch launch) {
				attach(launch);
			}

			@Override
			public void launchChanged(ILaunch launch) {
				// Processes can appear after launchAdded; attach is double-attach-safe.
				attach(launch);
			}

			@Override
			public void launchRemoved(ILaunch launch) {
				// Keep the buffer: post-mortem reads of removed launches are still useful.
			}
		});

		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			attach(launch);
		}
	}

	/**
	 * Finds the buffer for an app/config/project name (case-insensitive, matching the same
	 * way {@code /stop} matches), or null when nothing has launched under that name since
	 * Eclipse started.
	 */
	static Buffer find(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		for (final Buffer buffer : _byConfigName.values()) {
			if (name.equalsIgnoreCase(buffer.configName) || name.equalsIgnoreCase(buffer.projectName)) {
				return buffer;
			}
		}
		return null;
	}

	private static void attach(ILaunch launch) {
		try {
			final ILaunchConfiguration config = launch.getLaunchConfiguration();
			if (config == null || !LaunchConfigs.isJavaApplication(config)) {
				return;
			}
			for (final IProcess process : launch.getProcesses()) {
				if (process.getAttribute(ATTACHED_MARKER) != null) {
					continue;
				}
				process.setAttribute(ATTACHED_MARKER, "true");

				final Buffer buffer = _byConfigName.compute(config.getName(), (k, existing) ->
						existing != null && existing._launch == launch
								? existing
								: new Buffer(config.getName(), LaunchConfigs.projectNameOf(config), launch, System.currentTimeMillis()));

				final IStreamsProxy proxy = process.getStreamsProxy();
				if (proxy == null) {
					continue;
				}
				attachMonitor(buffer, proxy.getOutputStreamMonitor());
				attachMonitor(buffer, proxy.getErrorStreamMonitor());
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

	private static void attachMonitor(Buffer buffer, IStreamMonitor monitor) {
		if (monitor == null) {
			return;
		}
		// Grab whatever the stream produced before we attached, then follow along.
		final String existing = monitor.getContents();
		if (existing != null && !existing.isEmpty()) {
			buffer.append(existing);
		}
		monitor.addListener(new IStreamListener() {
			@Override
			public void streamAppended(String text, IStreamMonitor source) {
				buffer.append(text);
			}
		});
	}
}
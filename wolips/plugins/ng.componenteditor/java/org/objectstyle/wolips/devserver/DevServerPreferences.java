package org.objectstyle.wolips.devserver;

/**
 * Preference keys and defaults for the Parsley dev server.
 *
 * <p>The default port ({@value #DEFAULT_PORT}) matches {@code wolips.port}'s
 * default on the runtime side, so existing runtime clients (Wonder's
 * {@code ERXExceptionPage}, the {@code WOLips} framework) reach it without
 * configuration. There is intentionally no password preference — the server
 * is loopback-only, which is the security boundary; see {@link DevServer}.
 */
public final class DevServerPreferences {

	private DevServerPreferences() {
	}

	/** Boolean — whether the dev server runs. */
	public static final String SERVER_ENABLED = "org.objectstyle.wolips.devserver.enabled";

	/** Int — the loopback port to listen on. */
	public static final String SERVER_PORT = "org.objectstyle.wolips.devserver.port";

	/** Default port; matches {@code wolips.port}'s default on the runtime side. */
	public static final int DEFAULT_PORT = DevServer.DEFAULT_PORT;

	/**
	 * Default enabled state. On by default — the server is loopback-only and
	 * password-free, so it's safe to run for everyone, and having it always
	 * available is what makes the "click an exception line to open the source"
	 * loop just work without setup. If the port is already taken (e.g. a second
	 * Eclipse is open), startup fails gracefully and the feature is simply
	 * unavailable in that instance; see {@link DevServerManager}.
	 */
	public static final boolean DEFAULT_ENABLED = true;
}

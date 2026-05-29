package org.objectstyle.wolips.devserver;

/**
 * Preference keys and defaults for the Parsley dev server.
 *
 * <p>The defaults match the wire contract used by existing runtime clients
 * (Wonder's {@code ERXExceptionPage}, the {@code WOLips} framework): port
 * {@value #DEFAULT_PORT}, and a password that the runtime sends as the
 * {@code pw} query parameter (set on the runtime side via the
 * {@code wolips.password} system property).
 */
public final class DevServerPreferences {

	private DevServerPreferences() {
	}

	/** Boolean — whether the dev server runs. */
	public static final String SERVER_ENABLED = "org.objectstyle.wolips.devserver.enabled";

	/** Int — the loopback port to listen on. */
	public static final String SERVER_PORT = "org.objectstyle.wolips.devserver.port";

	/** String — the required password (matched against the request's {@code pw} param). */
	public static final String SERVER_PASSWORD = "org.objectstyle.wolips.devserver.password";

	/** Default port; matches {@code wolips.port}'s default on the runtime side. */
	public static final int DEFAULT_PORT = DevServer.DEFAULT_PORT;

	/** Default enabled state. Off by default — opt in via preferences. */
	public static final boolean DEFAULT_ENABLED = false;
}

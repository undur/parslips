package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

/**
 * The port side of launching: which port a launch configuration will listen on, who is
 * holding a port right now, and how to launch a configuration on a different port without
 * touching the saved configuration.
 *
 * <p>Both runtimes take their port the WebObjects way — a {@code -WOPort N} program argument
 * (ng-objects reads the same key through its properties) — and both default to
 * {@link #DEFAULT_PORT} when it's absent, which is why two apps launched with their plain
 * configs collide: development ports are shared by convention. The frameworks resolve the
 * collision on their own (the earlier instance is stopped, or evicted) — silently, which
 * is exactly what an external caller can't see. So {@code /launch} decides it explicitly:
 * refuse and name the holder, stop the holder first ({@code stopOthers=true}), or run
 * alongside on another port ({@code port=N}).
 *
 * <p>Running on another port injects the argument into an <b>unsaved working copy</b> of the
 * configuration ({@link #withExtraArguments}): Eclipse launches a working copy exactly like
 * the saved config, and the saved config — the developer's — stays as it was.
 */
final class LaunchPorts {

	/** What both runtimes listen on when no {@code -WOPort} is given. */
	static final int DEFAULT_PORT = 1200;

	private static final Pattern PROGRAM_ARG = Pattern.compile("(?:^|\\s)-WOPort\\s+(\\d+)");
	private static final Pattern VM_ARG = Pattern.compile("(?:^|\\s)-DWOPort=(\\d+)");

	private LaunchPorts() {
	}

	/** The port the configuration will listen on: its {@code -WOPort} argument, or the default. */
	static int portOf(ILaunchConfiguration config) {
		try {
			final Integer explicit = portFromArguments(
					config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, ""),
					config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, ""));
			return explicit != null ? explicit.intValue() : DEFAULT_PORT;
		}
		catch (Exception e) {
			return DEFAULT_PORT;
		}
	}

	/** {@code -WOPort N} in the program arguments, else {@code -DWOPort=N} in the VM arguments, else null. */
	static Integer portFromArguments(String programArguments, String vmArguments) {
		Matcher m = PROGRAM_ARG.matcher(programArguments == null ? "" : programArguments);
		if (m.find()) {
			return Integer.valueOf(m.group(1));
		}
		m = VM_ARG.matcher(vmArguments == null ? "" : vmArguments);
		if (m.find()) {
			return Integer.valueOf(m.group(1));
		}
		return null;
	}

	/** Appends extra program arguments to an existing argument string, with sane spacing. */
	static String appendArguments(String existing, String extra) {
		final String base = existing == null ? "" : existing.trim();
		final String more = extra == null ? "" : extra.trim();
		if (more.isEmpty()) {
			return base;
		}
		return base.isEmpty() ? more : base + " " + more;
	}

	/**
	 * A launchable, UNSAVED copy of the configuration with extra program arguments appended.
	 * The saved configuration is not modified; the launch reports the copy (same name) as its
	 * configuration, so everything keyed by config name — console buffers, {@code /status},
	 * {@code /stop} — keeps working.
	 */
	static ILaunchConfigurationWorkingCopy withExtraArguments(ILaunchConfiguration config, String extraProgramArguments) throws Exception {
		final ILaunchConfigurationWorkingCopy copy = config.getWorkingCopy();
		final String existing = config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, "");
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, appendArguments(existing, extraProgramArguments));
		return copy;
	}

	/** Whether something is listening on the port right now (a TCP connect on loopback). */
	static boolean isHeld(int port) {
		try (java.net.Socket socket = new java.net.Socket()) {
			socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 250);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * The names the dev server can stop that plausibly hold the port: registered apps that
	 * announced this port and still answer, and running Java launches whose configuration
	 * resolves to this port. Empty when the holder is nothing we know (a process started
	 * outside Eclipse, say).
	 */
	static List<String> holdersOf(int port) {
		final List<String> holders = new ArrayList<>();
		for (final AppRegistry.Entry entry : AppRegistry.all()) {
			if (entry.port == port && AppRegistry.isReachable(entry) && !holders.contains(entry.name)) {
				holders.add(entry.name);
			}
		}
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			final ILaunchConfiguration config = launch.getLaunchConfiguration();
			if (launch.isTerminated() || config == null || !LaunchConfigs.isJavaApplication(config)) {
				continue;
			}
			if (portOf(config) == port) {
				final String name = config.getName();
				final String project = LaunchConfigs.projectNameOf(config);
				// A registered app usually announces itself under its project name; don't
				// list the same instance twice under both names.
				if (!holders.contains(name) && !holders.contains(project)) {
					holders.add(name);
				}
			}
		}
		return holders;
	}
}

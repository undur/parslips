package org.objectstyle.wolips.devserver;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

/**
 * Reports what is actually running — the ground truth an external tool otherwise has to
 * reconstruct from port probes and process listings. For each launch configuration (or
 * a single one, via {@code app}): is a launch of it alive, in which mode, since when,
 * is its project open, does it have compile errors, and what the app itself registered
 * (port/pid, and whether that port currently answers).
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code app} (or {@code config}) — an app/config/project name; omit for all.</li>
 * </ul>
 */
class StatusHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String name = params.get("app") != null ? params.get("app") : params.get("config");

		final StringBuilder b = new StringBuilder();
		b.append("{\"apps\":[");
		boolean first = true;
		for (final ILaunchConfiguration config : LaunchConfigs.all()) {
			final String projectName = LaunchConfigs.projectNameOf(config);
			if (name != null && !name.isEmpty()
					&& !name.equalsIgnoreCase(config.getName())
					&& !name.equalsIgnoreCase(projectName)) {
				continue;
			}
			if (!first) {
				b.append(',');
			}
			first = false;
			b.append(statusJson(config, projectName));
		}
		b.append("]}");
		return b.toString();
	}

	private static String statusJson(ILaunchConfiguration config, String projectName) {
		final StringBuilder b = new StringBuilder(256);
		b.append("{\"config\":\"").append(DevServerJson.escape(config.getName()))
				.append("\",\"project\":\"").append(DevServerJson.escape(projectName)).append('"');

		final IProject project = projectName.isEmpty() ? null : ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project != null && project.exists()) {
			b.append(",\"projectOpen\":").append(project.isOpen());
			if (project.isOpen()) {
				// Java errors only — "compileErrors" must not count template-validation markers.
				b.append(",\"compileErrors\":").append(WorkspaceProblems.javaErrors(project, Integer.MAX_VALUE).size());
			}
		}

		final ILaunch launch = LaunchHandler.findRunningLaunch(config.getName());
		b.append(",\"running\":").append(launch != null);
		if (launch != null) {
			b.append(",\"mode\":\"").append(DevServerJson.escape(launch.getLaunchMode())).append('"');
			final ConsoleBuffer.Buffer console = ConsoleBuffer.find(config.getName());
			if (console != null) {
				b.append(",\"uptimeSeconds\":").append((System.currentTimeMillis() - console.startedEpochMillis) / 1000);
			}
		}

		// What the app itself announced (see /registerApp), plus whether that port answers now.
		final AppRegistry.Entry registered = firstRegistered(config.getName(), projectName);
		if (registered != null) {
			b.append(",\"registered\":{\"port\":").append(registered.port);
			if (registered.pid != null && !registered.pid.isEmpty()) {
				b.append(",\"pid\":\"").append(DevServerJson.escape(registered.pid)).append('"');
			}
			if (registered.runtime != null && !registered.runtime.isEmpty()) {
				b.append(",\"runtime\":\"").append(DevServerJson.escape(registered.runtime)).append('"');
			}
			b.append(",\"reachable\":").append(AppRegistry.isReachable(registered)).append('}');
		}

		b.append('}');
		return b.toString();
	}

	private static AppRegistry.Entry firstRegistered(String configName, String projectName) {
		final List<AppRegistry.Entry> all = AppRegistry.all();
		for (final AppRegistry.Entry entry : all) {
			if (entry.name.equalsIgnoreCase(configName) || entry.name.equalsIgnoreCase(projectName)) {
				return entry;
			}
		}
		// Config names commonly decorate the app name ("JavaNBServer - Local"); accept a
		// registered name that the config name contains, so the two conventions meet.
		for (final AppRegistry.Entry entry : all) {
			if (configName.toLowerCase().contains(entry.name.toLowerCase())) {
				return entry;
			}
		}
		return null;
	}
}
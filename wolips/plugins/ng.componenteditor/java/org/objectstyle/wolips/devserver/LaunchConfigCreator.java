package org.objectstyle.wolips.devserver;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

/**
 * Makes sure an application project has a launch configuration, so a project that was just
 * created or imported can be started through {@code /launch} without anyone opening the
 * Run Configurations dialog.
 *
 * <p>The configuration is deliberately minimal and portable — project, main class, and m2e's
 * classpath/source-path providers (what the Run dialog sets for a Maven project). Nothing
 * machine-specific belongs in it: the JRE is the project's, and hot-reload agent flags live on
 * the installed JRE's default VM arguments, not per launch.
 */
final class LaunchConfigCreator {

	private static final String M2E_CLASSPATH_PROVIDER = "org.eclipse.m2e.launchconfig.classpathProvider";
	private static final String M2E_SOURCEPATH_PROVIDER = "org.eclipse.m2e.launchconfig.sourcepathProvider";

	private LaunchConfigCreator() {
	}

	/**
	 * The name of a Java-application launch configuration that runs {@code mainClass} in the
	 * project: an existing one when there is one, otherwise a newly saved one named after the
	 * project (suffixed if that name is taken by something else).
	 */
	static String ensure(String projectName, String mainClass) throws Exception {
		for (final ILaunchConfiguration existing : LaunchConfigs.all()) {
			if (projectName.equals(LaunchConfigs.projectNameOf(existing))
					&& mainClass.equals(existing.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, ""))) {
				return existing.getName();
			}
		}

		final ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		final ILaunchConfigurationType type = manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
		final String name = manager.isExistingLaunchConfigurationName(projectName)
				? manager.generateLaunchConfigurationName(projectName)
				: projectName;

		// container null = stored in the workspace metadata, like configs made in the Run dialog.
		final ILaunchConfigurationWorkingCopy copy = type.newInstance(null, name);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, mainClass);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, M2E_CLASSPATH_PROVIDER);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, M2E_SOURCEPATH_PROVIDER);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE, true);
		return copy.doSave().getName();
	}
}

package org.objectstyle.wolips.wizards;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;

/**
 * Imports a directory containing a {@code pom.xml} into the workspace as a Maven project,
 * through m2e — the equivalent of File → Import → Existing Maven Projects. m2e does all the
 * Eclipse-side configuration: natures, builders, classpath, source folders, dependency
 * resolution.
 *
 * <p>Shared by the New Project wizard and the dev server's {@code /createProject} and
 * {@code /importProject}, so a project created by hand, by the wizard, or by an external
 * agent ends up configured identically.
 */
public final class MavenProjectImporter {

	private MavenProjectImporter() {
	}

	/**
	 * @param projectDir the directory containing the pom.xml
	 * @return the imported project, or null when the directory holds no importable Maven project
	 */
	public static IProject importProject(File projectDir) throws CoreException, InterruptedException {
		final IProjectConfigurationManager configManager = MavenPlugin.getProjectConfigurationManager();

		// Scan for the pom in the project directory (not recursing into nested modules'
		// directories ourselves — the scanner reports modules as children, flattened below).
		final LocalProjectScanner scanner = new LocalProjectScanner(
				Collections.singletonList(projectDir.getAbsolutePath()),
				false,
				MavenPlugin.getMavenModelManager());
		scanner.run(new NullProgressMonitor());

		final Collection<MavenProjectInfo> projects = flatten(scanner.getProjects());
		if (projects.isEmpty()) {
			return null;
		}

		final List<IMavenProjectImportResult> results = configManager.importProjects(
				projects,
				new ProjectImportConfiguration(),
				new NullProgressMonitor());

		for (final IMavenProjectImportResult result : results) {
			final IProject project = result.getProject();
			if (project != null && project.exists()) {
				return project;
			}
		}
		return null;
	}

	private static Collection<MavenProjectInfo> flatten(Collection<MavenProjectInfo> input) {
		final List<MavenProjectInfo> result = new ArrayList<>();
		for (final MavenProjectInfo info : input) {
			result.add(info);
			result.addAll(flatten(info.getProjects()));
		}
		return result;
	}
}

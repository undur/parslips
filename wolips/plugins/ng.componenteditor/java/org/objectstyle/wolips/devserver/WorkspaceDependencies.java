package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Resolves which of an application's dependencies are <em>open in the workspace with
 * source</em>, versus the jar-only dependencies an external tool can't read.
 *
 * <p>Why this matters for an agent: when the source of a dependency is open in the
 * workspace, the agent can read, navigate, and edit it directly; when it's only a jar
 * on the classpath, it's flying blind. The developer's convention is that every
 * dependency they have sources for is open as a workspace project — so "the app's
 * classpath entries that resolve to open Java projects" is exactly the set of
 * source-available dependencies. This computes that set live from the workspace
 * (which is the source of truth and can change between queries), keyed off the app's
 * resolved classpath.
 */
final class WorkspaceDependencies {

	/** One source-available dependency: an open workspace project on the app's classpath. */
	static final class Dependency {
		final String name;
		final String path; // absolute on-disk location of the project
		final List<String> sourceFolders; // absolute on-disk paths of its source roots

		Dependency(String name, String path, List<String> sourceFolders) {
			this.name = name;
			this.path = path;
			this.sourceFolders = sourceFolders;
		}
	}

	private WorkspaceDependencies() {
	}

	/**
	 * @return the source-available dependencies for the named app — its classpath
	 *         entries that resolve to open Java projects in the workspace, each with
	 *         its location and source folders. Empty if the app isn't an open Java
	 *         project or has no such dependencies.
	 */
	static List<Dependency> forApp(String appName) {
		final List<Dependency> result = new ArrayList<>();
		if (appName == null || appName.isEmpty()) {
			return result;
		}

		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(appName);
		if (project == null || !project.isOpen()) {
			return result;
		}
		final IJavaProject javaProject = JavaCore.create(project);
		if (javaProject == null || !javaProject.exists()) {
			return result;
		}

		try {
			// Resolved classpath expands containers (Maven, JRE, etc.) to concrete
			// entries; project entries are the ones whose source is open in the workspace.
			for (final IClasspathEntry entry : javaProject.getResolvedClasspath(true)) {
				if (entry.getEntryKind() != IClasspathEntry.CPE_PROJECT) {
					continue;
				}
				// The path of a CPE_PROJECT entry is the dependency project's workspace path.
				final IProject dep = ResourcesPlugin.getWorkspace().getRoot().getProject(entry.getPath().lastSegment());
				if (dep == null || !dep.isOpen()) {
					// A closed (or missing) project has no readable source — skip it, since
					// the whole point is "dependencies we can actually read."
					continue;
				}
				result.add(describe(dep));
			}
		}
		catch (Exception e) {
			// A classpath that won't resolve shouldn't fail the whole /apps response;
			// return whatever we gathered.
			ComponenteditorPlugin.getDefault().log(e);
		}

		return result;
	}

	/** Builds the {@link Dependency} record for an open project, including source folders. */
	private static Dependency describe(IProject project) {
		final String name = project.getName();
		final IPath location = project.getLocation();
		final String path = location != null ? location.toOSString() : null;

		final List<String> sourceFolders = new ArrayList<>();
		final IJavaProject depJava = JavaCore.create(project);
		if (depJava != null && depJava.exists()) {
			try {
				for (final IClasspathEntry entry : depJava.getRawClasspath()) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						// Source-folder paths are workspace-relative; map to an on-disk path.
						final IResource folder = ResourcesPlugin.getWorkspace().getRoot().findMember(entry.getPath());
						if (folder != null && folder.getLocation() != null) {
							sourceFolders.add(folder.getLocation().toOSString());
						}
					}
				}
			}
			catch (Exception e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		return new Dependency(name, path, sourceFolders);
	}
}

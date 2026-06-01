package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * Refreshes — and optionally rebuilds — whole workspace project(s) from disk.
 *
 * <p>This is the programmatic equivalent of manually closing and reopening a
 * project in Eclipse: it tells Eclipse "files in this project changed underneath
 * you, re-read them and recompile." It exists because external tools (and AI
 * agents) that edit project source files outside the Eclipse editor leave the
 * workspace unaware of the change — Eclipse only auto-detects edits it mediated
 * itself. Without a refresh, the running app keeps using stale {@code .class}
 * files and the edit appears to have no effect.
 *
 * <p>Why a whole project rather than a single {@code path} (which
 * {@link RefreshHandler} already does)? When you change several files — or don't
 * want to enumerate exactly which ones changed — refreshing the project root with
 * {@code DEPTH_INFINITE} picks up every change in one call. That matches the
 * close/reopen gesture it replaces.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code project} — the name of the project to refresh. If omitted or
 *       empty, <em>all</em> open projects are refreshed (the "pick up everything
 *       I just changed" case).</li>
 *   <li>{@code build} — {@code "false"} to skip the incremental build and only
 *       refresh resources from disk. Defaults to {@code true}: after refreshing
 *       we request an incremental build so regenerated {@code .class} files are
 *       available for the running app's hot-code-replace. (Only meaningful when
 *       Eclipse auto-build is off; with auto-build on, the refresh alone already
 *       schedules a build, and the explicit build is a harmless no-op.)</li>
 * </ul>
 *
 * <h2>Hot-code-replace caveat</h2>
 * Refreshing + building makes new {@code .class} files exist, and a JVM launched
 * in debug mode will hot-swap method bodies. The JVM cannot hot-swap changes that
 * alter a class's shape — new/removed methods or fields, changed signatures, new
 * classes. Those still require an app restart. This handler reports success once
 * the refresh/build completes; whether the running app actually picks the change
 * up depends on that JVM limitation, not on this handler.
 *
 * <p>Runs on a dev-server request thread; the workspace operations acquire the
 * workspace lock themselves, so no UI-thread dispatch is needed.
 */
class RefreshProjectHandler implements DevServerHandler {

	@Override
	public void handle(Map<String, String> params) throws Exception {
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final String projectName = params.get("project");

		// "build" defaults to true; only the literal string "false" disables it.
		final boolean build = !"false".equalsIgnoreCase(params.get("build"));

		if (projectName == null || projectName.isEmpty()) {
			// No name → refresh every open project (the "pick up everything" case).
			for (IProject project : workspace.getRoot().getProjects()) {
				refreshAndBuild(project, build);
			}
			return;
		}

		final IProject project = workspace.getRoot().getProject(projectName);
		if (project != null && project.exists()) {
			refreshAndBuild(project, build);
		}
	}

	/**
	 * Refreshes the project tree from disk and, if requested and the project is
	 * open, runs an incremental build so regenerated class files are available.
	 */
	private static void refreshAndBuild(final IProject project, final boolean build) throws Exception {
		if (!project.isOpen()) {
			// A closed project can't be refreshed/built meaningfully; skip it
			// rather than forcing it open (the user closed it deliberately).
			return;
		}

		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());

		if (build) {
			project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
		}
	}
}

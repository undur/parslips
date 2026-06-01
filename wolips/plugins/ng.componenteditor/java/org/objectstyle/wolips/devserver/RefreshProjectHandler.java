package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

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

		// "clean" defaults to FALSE: a normal incremental build, which produces the
		// per-type class delta that hot-swap (HotswapAgent / IDE HCR) reacts to — the
		// same thing an in-editor save produces, and the whole point of this endpoint
		// in the edit→refresh→see-it loop. Pass clean=true only to force a CLEAN+FULL
		// rebuild for recovering a project from a bad build state (it won't hot-swap).
		final boolean clean = "true".equalsIgnoreCase(params.get("clean"));

		// NOTE: refresh and build are deliberately NOT wrapped in a single atomic
		// workspace operation. The refresh must fully settle (its resource deltas
		// integrated by JDT) before the build runs, or the build can compile against a
		// half-integrated delta and leave a stub class. refreshAndBuild() sequences
		// them per project; refreshLocal/build each take the workspace lock as needed.
		if (projectName == null || projectName.isEmpty()) {
			for (IProject project : workspace.getRoot().getProjects()) {
				refreshAndBuild(project, build, clean);
			}
		}
		else {
			final IProject project = workspace.getRoot().getProject(projectName);
			if (project != null && project.exists()) {
				refreshAndBuild(project, build, clean);
			}
		}

		// Crucial for the edit→refresh→observe loop: don't return "ok" until Eclipse
		// has actually FINISHED refreshing and building. project.build(...) above
		// triggers the build, but auto-build/auto-refresh jobs and the JVM's class
		// reload may still be settling — a client that fetches a page the instant it
		// sees "ok" can hit the app mid-rebuild and catch a transient half-built state
		// (the source of the "it only works some of the time" flakiness). Joining the
		// build/refresh job families blocks until the workspace is quiescent, so by the
		// time the caller gets "ok" the new classes are genuinely in place.
		waitForBuildToSettle();
	}

	/**
	 * Blocks until Eclipse's refresh and build jobs have drained, so callers can
	 * treat a successful response as "the rebuild is done." Best-effort: a join may
	 * be interrupted, in which case we simply return (the caller can retry).
	 */
	private static void waitForBuildToSettle() {
		final IJobManager jobs = Job.getJobManager();
		final Object[] families = {
				ResourcesPlugin.FAMILY_MANUAL_REFRESH,
				ResourcesPlugin.FAMILY_AUTO_REFRESH,
				ResourcesPlugin.FAMILY_MANUAL_BUILD,
				ResourcesPlugin.FAMILY_AUTO_BUILD,
		};
		for (final Object family : families) {
			try {
				jobs.join(family, new NullProgressMonitor());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (OperationCanceledException e) {
				return;
			}
		}
	}

	/**
	 * Refreshes the project tree from disk and, if requested and the project is
	 * open, rebuilds it so regenerated class files are available — and so a running
	 * debuggee can hot-swap them.
	 *
	 * <p>Defaults to an <b>incremental</b> build, on purpose. The thing that actually
	 * reloads classes into a running app (HotswapAgent watching the class files, or
	 * the IDE's hot-code-replace) reacts to the per-type <em>delta</em> an incremental
	 * build produces — exactly what an in-editor save produces. A full/clean build
	 * recompiles everything and does not yield that same delta, so the running app
	 * never picks the change up (the class file changes on disk, but nothing reloads
	 * it). We therefore mirror the in-editor save: refresh, then incremental build.
	 *
	 * <p>{@code clean=true} forces a CLEAN+FULL rebuild instead. That can't leave a
	 * half-compiled stub class, but it also won't hot-swap — use it only to recover a
	 * project from a bad build state, not in the normal edit→refresh→see-it loop.
	 */
	private static void refreshAndBuild(final IProject project, final boolean build, final boolean clean) throws CoreException {
		if (!project.isOpen()) {
			// A closed project can't be refreshed/built meaningfully; skip it
			// rather than forcing it open (the user closed it deliberately).
			return;
		}

		// Refresh first, as its own step, so Eclipse registers the on-disk changes as
		// a resource delta BEFORE we build — otherwise the incremental build sees
		// nothing to do and produces no class delta for the hot-swapper to pick up.
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());

		if (!build) {
			return;
		}

		if (clean) {
			project.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
			project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		}
		else {
			project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
		}
	}
}

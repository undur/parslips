package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * The set of projects a Java launch actually depends on — the launched project plus every
 * project it references, transitively — in build order. This mirrors what Eclipse's own
 * Java launch delegate computes before launching ({@code computeReferencedBuildOrder} over
 * {@link IProject#getReferencedProjects()}), and it's the set Eclipse checks for compile
 * errors in its "Errors exist in required project(s)" prompt.
 *
 * <p>Why we replicate it: the dev server's {@code /launch} preflight used to check only the
 * launched project, so a broken <em>dependency</em> sailed past preflight and was caught
 * by Eclipse instead — as a modal dialog on the UI thread, which an external agent cannot
 * see and cannot answer. Checking the same closure Eclipse checks lets the refusal (and the
 * names of the broken projects) reach the caller as data.
 *
 * <p>Note the source of the references: JDT maintains a project's referenced projects from
 * its classpath (project entries, and the Maven classpath container's workspace-resolved
 * dependencies), so for an m2e-managed WO/ng app this closure is the app plus every
 * framework whose source is open in the workspace.
 */
final class LaunchClosure {

	private LaunchClosure() {
	}

	/**
	 * The launched project and its transitive references, open ones only (a closed
	 * project can't be built or checked — Eclipse's delegate skips those too), sorted into
	 * workspace build order with the launched project's dependencies first.
	 */
	static List<IProject> of(IProject project) throws CoreException {
		final Set<IProject> unordered = new LinkedHashSet<>();
		unordered.add(project);
		addReferenced(project, unordered);

		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspace.ProjectOrder order = workspace.computeProjectOrder(unordered.toArray(new IProject[0]));
		final List<IProject> result = new ArrayList<>();
		for (final IProject p : order.projects) {
			if (p.isOpen()) {
				result.add(p);
			}
		}
		return result;
	}

	private static void addReferenced(IProject project, Set<IProject> references) throws CoreException {
		if (!project.isOpen()) {
			return;
		}
		for (final IProject referenced : project.getReferencedProjects()) {
			if (referenced.exists() && references.add(referenced)) {
				addReferenced(referenced, references);
			}
		}
	}

	/**
	 * Incrementally builds the closure in build order, then waits for the build jobs to
	 * drain — the same pre-launch build Eclipse performs ("Build before launch"), done
	 * BEFORE the error check so the check sees the build's outcome, not stale markers.
	 * Incremental on purpose: a no-op when nothing changed, and the cheapest way to make
	 * the compile-error check trustworthy.
	 */
	static void build(List<IProject> closure) throws CoreException {
		for (final IProject project : closure) {
			project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
		}
		RefreshProjectHandler.waitForBuildToSettle();
	}

	/** The projects in the closure that hold Java compile or build-path errors, in build order. */
	static List<IProject> withErrors(List<IProject> closure) {
		final List<IProject> broken = new ArrayList<>();
		for (final IProject project : closure) {
			if (!WorkspaceProblems.javaErrors(project, 1).isEmpty()) {
				broken.add(project);
			}
		}
		return broken;
	}
}

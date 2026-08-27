package org.objectstyle.wolips.wodclipse.core.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Revalidates every component template in one or more projects — the bulk counterpart of the
 * editor's per-file validation.
 *
 * <p>Why this exists: template validation is per-file and event-driven (editor save/open, the
 * incremental revalidators). A Java clean/rebuild never re-runs it, so problem markers written
 * under old circumstances outlive the circumstances — e.g. markers stamped at a severity the
 * preferences no longer prescribe, or laid down while type resolution was still warming up —
 * and linger as phantoms until each affected file happens to be revalidated. This class is the
 * "revalidate the world" hammer for exactly those situations, triggered from the validation
 * preference page (a button, and a prompt when validation settings change) and from the dev
 * server's {@code /revalidate} endpoint.
 *
 * <p>Enumeration mirrors the editor's own file-association rule (see
 * {@code NGEditorAssociationOverride}): only projects that {@link ParsleyProject#shouldHandleProject}
 * accepts are touched, and within them every {@code .wo} bundle (validated as the bundle folder,
 * once) and every standalone {@code .html} outside a bundle. Derived resources are skipped. So the
 * set validated here is exactly the set of files the editor would claim.
 */
public final class TemplateRevalidator {

	private TemplateRevalidator() {
	}

	/** The outcome of a bulk revalidation — how much was done, and whether it ran to completion. */
	public static final class Summary {
		public final int projectCount;
		public final int componentCount;
		public final boolean canceled;

		Summary(int projectCount, int componentCount, boolean canceled) {
			this.projectCount = projectCount;
			this.componentCount = componentCount;
			this.canceled = canceled;
		}
	}

	/**
	 * @return every validation target in the project — each {@code .wo} bundle folder (once), and
	 *         each standalone {@code .html} outside a bundle — or an empty list for projects the
	 *         component editor doesn't handle. Order is stable (resource-tree order).
	 */
	public static List<IResource> collectValidationTargets(IProject project) {
		final List<IResource> targets = new ArrayList<>();
		if (project == null || !project.isOpen() || !ParsleyProject.shouldHandleProject(project)) {
			return targets;
		}
		final Set<IResource> seenBundles = new LinkedHashSet<>();
		try {
			project.accept(resource -> {
				if (resource.isDerived()) {
					return false;
				}
				if (resource.getType() != IResource.FILE) {
					return true;
				}
				if (!"html".equalsIgnoreCase(resource.getFileExtension())) {
					return false;
				}
				final IResource parent = resource.getParent();
				if (parent != null && parent.getName().endsWith(".wo")) {
					// A bundle template: validate the bundle (html+wod together), once.
					if (seenBundles.add(parent)) {
						targets.add(parent);
					}
				}
				else {
					// A standalone template — the same rule the editor association uses.
					targets.add(resource);
				}
				return false;
			});
		}
		catch (Exception e) {
			// Best-effort enumeration; whatever was gathered before the failure still gets validated.
		}
		return targets;
	}

	/**
	 * Synchronously revalidates all templates in the given projects. Honors cancellation between
	 * components (a single component's validation is not interruptible).
	 */
	public static Summary revalidate(Collection<IProject> projects, IProgressMonitor monitor) {
		// Enumerate first so progress can report a real total.
		final List<IResource> targets = new ArrayList<>();
		int projectCount = 0;
		for (final IProject project : projects) {
			final List<IResource> projectTargets = collectValidationTargets(project);
			if (!projectTargets.isEmpty()) {
				projectCount++;
				targets.addAll(projectTargets);
			}
		}

		final SubMonitor progress = SubMonitor.convert(monitor, "Revalidating templates", targets.size());
		int validated = 0;
		for (final IResource target : targets) {
			if (progress.isCanceled()) {
				return new Summary(projectCount, validated, true);
			}
			progress.subTask(target.getFullPath().toString());
			WodBuilder.validateComponent(target, false, progress.newChild(1));
			validated++;
		}
		return new Summary(projectCount, validated, false);
	}

	/** All open projects the component editor handles — the "whole workspace" input set. */
	public static List<IProject> allHandledProjects() {
		final List<IProject> result = new ArrayList<>();
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.isOpen() && ParsleyProject.shouldHandleProject(project)) {
				result.add(project);
			}
		}
		return result;
	}

	/**
	 * Schedules the revalidation as a background workspace Job (progress in the UI, cancellable) —
	 * the form the preference-page triggers use.
	 */
	public static Job scheduleRevalidation(final List<IProject> projects) {
		final Job job = new Job("Revalidating Parsley templates") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final Summary summary = revalidate(projects, monitor);
				return summary.canceled ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
		return job;
	}
}

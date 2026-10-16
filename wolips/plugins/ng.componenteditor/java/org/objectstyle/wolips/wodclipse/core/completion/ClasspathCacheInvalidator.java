package org.objectstyle.wolips.wodclipse.core.completion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver;
import org.objectstyle.wolips.editor.template.TemplateAssistProcessor;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.builder.TemplateRevalidator;

/**
 * When a project's classpath changes, clears the editor's caches that are derived from it (the
 * tag alias maps, and the project's element types and tag infos) and re-validates the project's
 * templates, so their markers reflect the new classpath.
 *
 * <p>Those caches are filled on first use and otherwise cleared only when files change
 * ({@link WodParserCacheInvalidator}). Right after Eclipse starts, m2e is still resolving
 * classpaths while the first builds validate templates: a cache filled then reflects an
 * incomplete classpath (an ng project without ng-appserver's tag aliases, so {@code <wo:script>}
 * has no class) and would otherwise keep it until some unrelated file changed. The markers
 * validated from it outlive even that: a classpath change doesn't make the builder re-validate
 * templates. JDT reports every classpath change, m2e's resolution and a dependency update
 * included, as a delta on the project.
 *
 * <p>Classpath changes come in bursts (m2e resolves the workspace's projects one by one), so
 * the revalidation is collected into one background job that runs {@link #REVALIDATION_DELAY_MILLIS}
 * after the last change, once the running build has finished.
 */
public final class ClasspathCacheInvalidator implements IElementChangedListener {

	/** The delta flags meaning a project's classpath, as declared or as resolved, has changed. */
	private static final int CLASSPATH_CHANGED = IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED;

	/** Quiet time after the last classpath change before its projects are re-validated. */
	private static final long REVALIDATION_DELAY_MILLIS = 2000;

	private static ClasspathCacheInvalidator _installed;

	/** Projects whose classpath changed since the revalidation job last ran. */
	private final Set<IProject> _pendingProjects = new LinkedHashSet<>();

	private final Job _revalidationJob = new Job("Revalidating Parsley templates after a classpath change") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				// Validation resolves types through JDT; let the build the change triggered finish.
				Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Status.CANCEL_STATUS;
			}
			final List<IProject> projects = takePendingProjects();
			if (projects.isEmpty()) {
				return Status.OK_STATUS;
			}
			return TemplateRevalidator.revalidate(projects, monitor).canceled ? Status.CANCEL_STATUS : Status.OK_STATUS;
		}
	};

	private ClasspathCacheInvalidator() {
	}

	/** Starts listening to JDT for classpath changes. Idempotent. */
	public static synchronized void install() {
		if (_installed == null) {
			_installed = new ClasspathCacheInvalidator();
			JavaCore.addElementChangedListener(_installed, ElementChangedEvent.POST_CHANGE);
		}
	}

	/** Stops listening. */
	public static synchronized void uninstall() {
		if (_installed != null) {
			JavaCore.removeElementChangedListener(_installed);
			_installed._revalidationJob.cancel();
			_installed = null;
		}
	}

	/** Whether a project delta with these flags means its classpath changed. */
	static boolean isClasspathChange(int flags) {
		return (flags & CLASSPATH_CHANGED) != 0;
	}

	/** The open projects the editor handles among those pending, emptying the pending set. */
	private List<IProject> takePendingProjects() {
		final List<IProject> projects = new ArrayList<>();
		synchronized (_pendingProjects) {
			for (final IProject project : _pendingProjects) {
				if (project.isOpen() && ParsleyProject.shouldHandleProject(project)) {
					projects.add(project);
				}
			}
			_pendingProjects.clear();
		}
		return projects;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		boolean changed = false;
		// The event's delta is the Java model's; its children are the projects.
		for (final IJavaElementDelta projectDelta : event.getDelta().getAffectedChildren()) {
			if (projectDelta.getElement().getElementType() == IJavaElement.JAVA_PROJECT && isClasspathChange(projectDelta.getFlags())) {
				final IProject project = projectDelta.getElement().getJavaProject().getProject();
				WodCompletionUtils.clearElementTypeCacheForProject(project);
				TemplateAssistProcessor.clearTagInfoCacheForProject(project);
				synchronized (_pendingProjects) {
					_pendingProjects.add(project);
				}
				changed = true;
			}
		}
		if (changed) {
			// A project's alias map merges its dependencies' files, so one project's classpath
			// change can affect others; the maps are cheap to rebuild, so clear them all.
			ParsleyTagAliasResolver.clearCache();
			// Rescheduling a waiting job restarts its delay, which coalesces a burst of changes.
			_revalidationJob.schedule(REVALIDATION_DELAY_MILLIS);
		}
	}
}

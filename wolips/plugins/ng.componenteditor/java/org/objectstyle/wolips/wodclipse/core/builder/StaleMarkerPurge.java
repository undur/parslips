package org.objectstyle.wolips.wodclipse.core.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Bring out your dead: deletes orphaned problem markers that no tool will ever clear.
 *
 * <p>The removed legacy validators (Rhino JavaScript, JTidy HTML — see issue #4) wrote markers of
 * the <em>exact stock type</em> {@code IMarker.PROBLEM}. Stock markers carry no owner, and nothing
 * revalidates the files they sit on (with the validators gone, nothing ever will), so they survive
 * as permanently-stale corpses that make projects look broken on the tree. Eclipse itself offers no
 * "revalidate/clean all markers" — every honest tool uses a typed subtype it can find and delete;
 * the stock type is the unclaimed graveyard.
 *
 * <p>That gives the purge its safety rule: <b>delete only markers whose type is exactly
 * {@code IMarker.PROBLEM}</b> (no subtypes) on the file kinds the legacy validators touched.
 * Everything alive is typed and therefore untouched: this plugin's template/apiext problems
 * ({@code ng.componenteditor.problem}), JDT's Java problems, WTP's validation — all subtypes.
 */
public final class StaleMarkerPurge {

	/** The file extensions the legacy validators wrote stock markers onto. */
	private static final Set<String> DEFAULT_EXTENSIONS = new HashSet<>(Arrays.asList("js", "css", "html", "htm", "xml"));

	private StaleMarkerPurge() {
	}

	/** What a purge did: how many corpse markers were deleted, across how many files. */
	public static final class Summary {
		public final int deletedMarkers;
		public final int affectedFiles;

		Summary(int deletedMarkers, int affectedFiles) {
			this.deletedMarkers = deletedMarkers;
			this.affectedFiles = affectedFiles;
		}
	}

	/**
	 * Purges exact-stock-type problem markers from the given project's js/css/html/xml files.
	 * Safe by construction: typed markers (every living tool's) are never touched.
	 */
	public static Summary purge(IProject project) {
		int deleted = 0;
		final Set<IResource> files = new HashSet<>();
		if (project == null || !project.isOpen()) {
			return new Summary(0, 0);
		}
		try {
			// includeSubtypes=false is the whole trick: only the unclaimed stock type matches.
			for (final IMarker marker : project.findMarkers(IMarker.PROBLEM, false, IResource.DEPTH_INFINITE)) {
				final IResource resource = marker.getResource();
				final String extension = resource != null ? resource.getFileExtension() : null;
				if (extension != null && DEFAULT_EXTENSIONS.contains(extension.toLowerCase())) {
					marker.delete();
					deleted++;
					files.add(resource);
				}
			}
		}
		catch (Exception e) {
			// Best-effort: report what was purged before the failure.
		}
		return new Summary(deleted, files.size());
	}

	/** Purges every open project in the workspace. */
	public static Summary purgeWorkspace() {
		int deleted = 0;
		int files = 0;
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			final Summary summary = purge(project);
			deleted += summary.deletedMarkers;
			files += summary.affectedFiles;
		}
		return new Summary(deleted, files);
	}

	/** Convenience for callers wanting per-project accounting. */
	public static List<IProject> openProjects() {
		return Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
				.filter(IProject::isOpen)
				.toList();
	}
}

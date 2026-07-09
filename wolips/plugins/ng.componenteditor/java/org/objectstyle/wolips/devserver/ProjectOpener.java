package org.objectstyle.wolips.devserver;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Opens a workspace project <em>together with the workspace projects it depends on</em>,
 * transitively — because opening just the target is almost never enough here: these
 * workspaces resolve dependencies at the Maven level (m2e workspace resolution), which
 * only considers OPEN projects. Open an app whose framework projects are closed and you
 * get "build path errors" instead of a running app.
 *
 * <p>The wrinkle: for CLOSED projects, Eclipse APIs can't tell us dependencies (closed
 * projects have no resolved classpath and are absent from the m2e registry). But a closed
 * project still has a location on disk — so we read {@code pom.xml} files directly:
 * index every workspace project by its pom's {@code artifactId}, then BFS from the
 * target's pom through {@code <dependencies>}, opening every closed workspace project
 * we reach. Matching is by artifactId (unique in practice within a workspace); versions
 * are deliberately ignored — opening a near-miss project is harmless, leaving a needed
 * one closed is not.
 */
final class ProjectOpener {

	/** What an open-with-related call did: projects actually opened, in open order. */
	static final class Result {
		final List<String> opened = new ArrayList<>();
	}

	private ProjectOpener() {
	}

	/**
	 * Opens the target project (if closed) and every workspace project its pom reaches
	 * transitively, then builds the opened set and waits for the build to settle.
	 */
	static Result openWithRelated(IProject target) {
		final Result result = new Result();

		// artifactId -> workspace project, poms read from disk so closed projects count too.
		final Map<String, IProject> byArtifactId = new HashMap<>();
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			final String artifactId = artifactIdOf(project);
			if (artifactId != null) {
				byArtifactId.put(artifactId, project);
			}
		}

		final Set<String> visited = new HashSet<>();
		final Deque<IProject> queue = new ArrayDeque<>();
		queue.add(target);

		while (!queue.isEmpty()) {
			final IProject project = queue.removeFirst();
			if (!visited.add(project.getName())) {
				continue;
			}
			open(project, result);
			// Walk this project's pom dependencies — through open projects too, since the
			// path from the target to a closed framework often crosses an open one.
			for (final String depArtifactId : dependencyArtifactIds(project)) {
				final IProject dep = byArtifactId.get(depArtifactId);
				if (dep != null && !visited.contains(dep.getName())) {
					queue.addLast(dep);
				}
			}
			// Union in Eclipse's own project references (what the IDE's "open related
			// projects" feature follows) — readable now that the project is open. They
			// catch non-Maven relationships the pom-walk can't see; the pom-walk catches
			// the Maven ones m2e hasn't materialized as references yet because the
			// dependency projects were closed. Together they cover both worlds.
			try {
				for (final IProject referenced : project.getReferencedProjects()) {
					if (referenced.exists() && !visited.contains(referenced.getName())) {
						queue.addLast(referenced);
					}
				}
			}
			catch (Exception e) {
				// References unavailable (project still closed after a failed open, etc.) —
				// the pom-walk alone still covers the Maven case.
			}
		}

		buildAndSettle(result);
		return result;
	}

	private static void open(IProject project, Result result) {
		try {
			if (project.exists() && !project.isOpen()) {
				project.open(new NullProgressMonitor());
				result.opened.add(project.getName());
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

	/** Builds the newly opened projects and joins the build jobs so callers see a settled workspace. */
	private static void buildAndSettle(Result result) {
		try {
			for (final String name : result.opened) {
				final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
				if (project.isOpen()) {
					// CLEAN+FULL on purpose: a freshly opened project's persisted build state
					// is stale-but-trusted, so an incremental build no-ops and leaves output
					// that can't actually run (shakedown: main-bundle detection failed until a
					// clean). Nothing is running yet, so there's no hot-swap delta to preserve —
					// the incremental-only rule applies to the edit loop, not to opening.
					project.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
					project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
				}
			}
			final Object[] families = {
					ResourcesPlugin.FAMILY_MANUAL_REFRESH,
					ResourcesPlugin.FAMILY_AUTO_REFRESH,
					ResourcesPlugin.FAMILY_MANUAL_BUILD,
					ResourcesPlugin.FAMILY_AUTO_BUILD,
			};
			for (final Object family : families) {
				Job.getJobManager().join(family, new NullProgressMonitor());
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
	}

	/** The project's pom artifactId, read from disk (works for closed projects); null when there's no readable pom. */
	private static String artifactIdOf(IProject project) {
		final Document pom = parsePom(project);
		if (pom == null) {
			return null;
		}
		return directChildText(pom.getDocumentElement(), "artifactId");
	}

	/** The artifactIds of the pom's direct {@code <project><dependencies>} (not dependencyManagement/profiles). */
	private static List<String> dependencyArtifactIds(IProject project) {
		final List<String> result = new ArrayList<>();
		final Document pom = parsePom(project);
		if (pom == null) {
			return result;
		}
		final Element root = pom.getDocumentElement();
		final Element dependencies = directChild(root, "dependencies");
		if (dependencies == null) {
			return result;
		}
		final NodeList children = dependencies.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element dependency && "dependency".equals(dependency.getNodeName())) {
				final String artifactId = directChildText(dependency, "artifactId");
				if (artifactId != null && !artifactId.contains("${")) {
					result.add(artifactId);
				}
			}
		}
		return result;
	}

	private static Document parsePom(IProject project) {
		try {
			if (project.getLocation() == null) {
				return null;
			}
			final File pom = project.getLocation().append("pom.xml").toFile();
			if (!pom.isFile()) {
				return null;
			}
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			return factory.newDocumentBuilder().parse(pom);
		}
		catch (Exception e) {
			// An unparseable pom just means this project can't participate in the walk.
			return null;
		}
	}

	private static Element directChild(Element parent, String name) {
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node node = children.item(i);
			if (node instanceof Element element && name.equals(element.getNodeName())) {
				return element;
			}
		}
		return null;
	}

	private static String directChildText(Element parent, String name) {
		final Element child = directChild(parent, name);
		return child == null ? null : child.getTextContent().trim();
	}
}
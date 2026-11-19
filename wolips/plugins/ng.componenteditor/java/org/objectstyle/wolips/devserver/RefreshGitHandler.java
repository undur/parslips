package org.objectstyle.wolips.devserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * {@code /refreshGit?project=A,B} — make Eclipse's git status current after git ran outside it.
 *
 * <p>A commit, push, pull, checkout or rebase from a terminal or an agent changes the repository
 * on disk, but EGit shows the old state (ahead/behind counts, dirty markers, the branch) until its
 * own UI touches the repository. This rescans the repositories the named projects are in, each
 * once ({@link GitStatusRefresher}); without {@code project}, those of every open project.
 *
 * <p>Answers {@code {"rescanned":[{"project":…,"repository":…}],"notInGit":[…]}}, or a
 * {@code reason} when EGit isn't installed or a named project doesn't exist.
 * {@code /refreshProject} rescans too, so a session that refreshes before handing back leaves
 * the badges current even without this call.
 */
class RefreshGitHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final List<IProject> projects = new ArrayList<>();
		final String names = params.get("project");
		if (names == null || names.isBlank()) {
			for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (project.isOpen()) {
					projects.add(project);
				}
			}
		}
		else {
			for (final String name : names.split(",")) {
				final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name.trim());
				if (!project.exists()) {
					return new JsonObject().put("rescanned", List.of()).put("reason", "no project named '" + name.trim() + "' in the workspace").toString();
				}
				if (!project.isOpen()) {
					return new JsonObject().put("rescanned", List.of()).put("reason", "project '" + name.trim() + "' is closed")
							.put("hint", "/openProject?project=" + name.trim()).toString();
				}
				projects.add(project);
			}
		}

		final Map<String, File> rescanned;
		try {
			if (!GitStatusRefresher.isAvailable()) {
				return new JsonObject().put("rescanned", List.of()).put("reason", "EGit isn't installed in this Eclipse, so there's no git status to refresh").toString();
			}
			rescanned = GitStatusRefresher.rescan(projects);
		}
		catch (LinkageError e) {
			return new JsonObject().put("rescanned", List.of()).put("reason", "EGit isn't installed in this Eclipse, so there's no git status to refresh").toString();
		}

		final List<JsonObject> entries = new ArrayList<>();
		rescanned.forEach((project, repository) -> entries.add(new JsonObject().put("project", project).put("repository", repository.getPath())));
		final List<String> notInGit = new ArrayList<>();
		for (final IProject project : projects) {
			if (!rescanned.containsKey(project.getName()) && !inRescannedRepository(project, rescanned)) {
				notInGit.add(project.getName());
			}
		}
		final JsonObject json = new JsonObject().put("rescanned", entries);
		if (!notInGit.isEmpty()) {
			json.put("notInGit", notInGit);
		}
		return json.toString();
	}

	/** Whether the project lies in one of the rescanned repositories (it shared one with an earlier project). */
	private static boolean inRescannedRepository(IProject project, Map<String, File> rescanned) {
		if (project.getLocation() == null) {
			return false;
		}
		final String location = project.getLocation().toFile().getAbsolutePath();
		for (final File repository : rescanned.values()) {
			final String root = repository.getAbsolutePath();
			if (location.equals(root) || location.startsWith(root + File.separator)) {
				return true;
			}
		}
		return false;
	}
}

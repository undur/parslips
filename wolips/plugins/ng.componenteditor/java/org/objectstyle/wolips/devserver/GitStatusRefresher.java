package org.objectstyle.wolips.devserver;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.egit.core.info.GitInfo;
import org.eclipse.jgit.lib.Repository;

/**
 * Makes EGit notice git changes made outside Eclipse: a commit, push, pull or checkout from a
 * terminal or an agent. EGit learns of those by rescanning a repository, which it does when its
 * own UI touches the repository; until then the Package Explorer's badges (ahead/behind counts,
 * dirty markers, the branch) show the old state. {@link Repository#scanForRepoChanges()} rescans
 * the refs and the index and fires the change events EGit's decorators listen to.
 *
 * <p>EGit is an optional dependency: every EGit and JGit reference lives in this class, and
 * {@link #isAvailable()} says whether they resolve, so the rest of the plugin works without EGit.
 */
final class GitStatusRefresher {

	private GitStatusRefresher() {
	}

	/** Whether EGit is installed (its classes resolve). */
	static boolean isAvailable() {
		try {
			Class.forName("org.eclipse.egit.core.info.GitInfo", false, GitStatusRefresher.class.getClassLoader());
			return true;
		}
		catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/**
	 * Rescans the git repositories the given projects are in, each repository once.
	 *
	 * @return the rescanned repositories' working directories, keyed by the first project in each;
	 *         a project that isn't in a git repository (or isn't open) is left out
	 */
	static Map<String, File> rescan(Iterable<IProject> projects) throws Exception {
		final Map<File, String> byRepository = new LinkedHashMap<>();
		for (final IProject project : projects) {
			if (!project.isOpen()) {
				continue;
			}
			final GitInfo info = Adapters.adapt(project, GitInfo.class);
			final Repository repository = info == null ? null : info.getRepository();
			if (repository == null) {
				continue;
			}
			final File directory = repository.isBare() ? repository.getDirectory() : repository.getWorkTree();
			if (!byRepository.containsKey(directory)) {
				repository.scanForRepoChanges();
				byRepository.put(directory, project.getName());
			}
		}
		final Map<String, File> rescanned = new LinkedHashMap<>();
		byRepository.forEach((directory, project) -> rescanned.put(project, directory));
		return rescanned;
	}
}

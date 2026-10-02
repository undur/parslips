package org.objectstyle.wolips.devserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * {@code /where?path=DIR} — is this directory something Eclipse sees?
 *
 * <p>The dev server works on the projects in the Eclipse workspace. An agent editing files
 * anywhere else — a git worktree, a second clone, a copy — gets {@code ok} from every call while
 * none of its edits take effect: {@code /refreshProject} refreshes the workspace's checkout,
 * {@code /validate} checks its templates, the app runs its classes. Nothing says so, and the
 * developer can't follow the work in Eclipse either. One call before the first edit settles it.
 *
 * <p>Answers {@code inWorkspace:true} with the project, its location and the path relative to
 * it; or {@code inWorkspace:false} with a {@code reason}. When the directory is a git worktree
 * of a checkout Eclipse does have, the reason says so and names the directory to work in.
 */
class WhereHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String pathParam = params.get("path");
		if (pathParam == null || pathParam.isEmpty()) {
			return JsonObject.error("missing required parameter 'path' (an absolute directory or file path, e.g. the agent's working directory)");
		}
		final Path path = Path.of(pathParam).toAbsolutePath().normalize();
		final JsonObject json = new JsonObject().put("path", path.toString());

		final IProject project = projectContaining(path);
		if (project != null) {
			final Path location = Path.of(project.getLocation().toOSString());
			return json.put("inWorkspace", true)
					.put("project", project.getName())
					.put("projectPath", location.toString())
					.put("relative", location.relativize(path).toString())
					.put("open", project.isOpen())
					.toString();
		}

		json.put("inWorkspace", false);
		final Path mainCheckout = worktreeMainCheckout(path);
		if (mainCheckout != null) {
			final Path counterpart = mainCheckout.resolve(worktreeRoot(path).relativize(path));
			final IProject inMain = projectContaining(counterpart);
			if (inMain != null) {
				return json.put("reason", "this is a git worktree of " + mainCheckout + "; Eclipse has project '" + inMain.getName()
						+ "' from that checkout, not from here, so edits here are invisible to it (refresh, validation and the running app all use the checkout)")
						.put("workIn", counterpart.toString())
						.toString();
			}
			return json.put("reason", "this is a git worktree of " + mainCheckout + ", and no Eclipse workspace project is in either place").toString();
		}
		return json.put("reason", "no project in the Eclipse workspace contains this path; edits here are invisible to Eclipse - work in a workspace project's directory (see /status for projects), or bring this one in with /importProject").toString();
	}

	/** The workspace project whose location contains the path (the deepest, for nested projects), or null. */
	static IProject projectContaining(Path path) {
		IProject best = null;
		int bestDepth = -1;
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.getLocation() == null) {
				continue;
			}
			final Path location = Path.of(project.getLocation().toOSString()).toAbsolutePath().normalize();
			if (path.startsWith(location) && location.getNameCount() > bestDepth) {
				best = project;
				bestDepth = location.getNameCount();
			}
		}
		// A linked or virtual project location isn't covered above; ask the workspace too.
		if (best == null) {
			for (final IContainer container : ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(path.toUri())) {
				return container.getProject();
			}
		}
		return best;
	}

	/**
	 * When the path is inside a git worktree (its root has a {@code .git} FILE reading
	 * {@code gitdir: <main>/.git/worktrees/<name>}), the main checkout's directory; else null.
	 */
	static Path worktreeMainCheckout(Path path) {
		final Path root = worktreeRoot(path);
		if (root == null) {
			return null;
		}
		try {
			final String gitdir = Files.readString(root.resolve(".git")).strip();
			return mainCheckoutOf(gitdir, root);
		}
		catch (final IOException e) {
			return null;
		}
	}

	/** Parses a worktree's {@code .git} file content into the main checkout's directory, or null. */
	static Path mainCheckoutOf(String gitFileContent, Path worktreeRoot) {
		if (!gitFileContent.startsWith("gitdir:")) {
			return null;
		}
		Path gitdir = Path.of(gitFileContent.substring("gitdir:".length()).strip());
		if (!gitdir.isAbsolute()) {
			gitdir = worktreeRoot.resolve(gitdir).normalize();
		}
		// <main>/.git/worktrees/<name> → <main>
		final Path worktrees = gitdir.getParent();
		if (worktrees == null || !"worktrees".equals(String.valueOf(worktrees.getFileName())) || worktrees.getParent() == null) {
			return null;
		}
		return worktrees.getParent().getParent();
	}

	/** The nearest ancestor (or self) whose {@code .git} is a file, as a worktree's is; null when the nearest {@code .git} is a directory or absent. */
	private static Path worktreeRoot(Path path) {
		for (Path p = path; p != null; p = p.getParent()) {
			final Path git = p.resolve(".git");
			if (Files.isRegularFile(git)) {
				return p;
			}
			if (Files.isDirectory(git)) {
				return null; // a regular checkout
			}
		}
		return null;
	}
}

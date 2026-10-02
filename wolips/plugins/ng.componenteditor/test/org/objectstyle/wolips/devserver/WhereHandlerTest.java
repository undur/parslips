package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * {@link WhereHandler}'s worktree recognition: a git worktree's root has a {@code .git} FILE
 * pointing into the main checkout's {@code .git/worktrees/}, which names the checkout Eclipse
 * most likely has.
 */
public class WhereHandlerTest {

	@Test
	public void absoluteGitdirNamesTheMainCheckout() {
		assertEquals(Path.of("/Users/dev/git/Parsley"),
				WhereHandler.mainCheckoutOf("gitdir: /Users/dev/git/Parsley/.git/worktrees/Parsley-main", Path.of("/Users/dev/git/Parsley-main")));
	}

	@Test
	public void relativeGitdirIsResolvedAgainstTheWorktree() {
		assertEquals(Path.of("/Users/dev/git/Parsley"),
				WhereHandler.mainCheckoutOf("gitdir: ../Parsley/.git/worktrees/Parsley-main", Path.of("/Users/dev/git/Parsley-main")));
	}

	@Test
	public void aSubmoduleIsNotAWorktree() {
		// Submodules also have a .git file, pointing into .git/modules/ — not a worktree.
		assertNull(WhereHandler.mainCheckoutOf("gitdir: ../.git/modules/lib", Path.of("/Users/dev/git/app/lib")));
		assertNull(WhereHandler.mainCheckoutOf("not a gitdir line", Path.of("/x")));
	}

	@Test
	public void realWorktreeLayoutOnDisk() throws Exception {
		final Path base = Files.createTempDirectory("parslips-where");
		final Path main = Files.createDirectories(base.resolve("app/.git/worktrees/app-wt"));
		final Path worktree = Files.createDirectories(base.resolve("app-wt/src/main"));
		Files.writeString(base.resolve("app-wt/.git"), "gitdir: " + main + "\n");
		assertEquals(base.resolve("app").toRealPath(), WhereHandler.worktreeMainCheckout(worktree).toRealPath());
		// A regular checkout (a .git DIRECTORY) is not a worktree.
		assertNull(WhereHandler.worktreeMainCheckout(base.resolve("app")));
	}
}

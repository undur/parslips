package org.objectstyle.wolips.devserver;

import static org.junit.Assert.*;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

/**
 * {@code /refreshGit}: refuses an unknown project, and lists a project outside any git repository
 * as {@code notInGit}, or says EGit isn't installed where it isn't (the test runtime may lack it).
 */
public class RefreshGitHandlerTest {

	@Test
	public void unknownProjectIsRefused() throws Exception {
		final String answer = new RefreshGitHandler().handle(Map.of("project", "NoSuchProjectForRefreshGit"));
		assertTrue(answer, answer.contains("\"reason\":\"no project named 'NoSuchProjectForRefreshGit' in the workspace\""));
		assertTrue(answer, answer.contains("\"rescanned\":[]"));
	}

	@Test
	public void projectOutsideGitIsNamed() throws Exception {
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("RefreshGitHandlerTestProject");
		if (!project.exists()) {
			project.create(new NullProgressMonitor());
		}
		project.open(new NullProgressMonitor());
		try {
			final String answer = new RefreshGitHandler().handle(Map.of("project", project.getName()));
			if (GitStatusRefresher.isAvailable()) {
				assertTrue(answer, answer.contains("\"notInGit\":[\"RefreshGitHandlerTestProject\"]"));
				assertTrue(answer, answer.contains("\"rescanned\":[]"));
			}
			else {
				assertTrue(answer, answer.contains("EGit isn't installed"));
			}
		}
		finally {
			project.delete(true, true, new NullProgressMonitor());
		}
	}
}

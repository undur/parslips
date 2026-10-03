package org.objectstyle.wolips.devserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * Catches the edits Eclipse's refresh doesn't see.
 *
 * <p>{@code refreshLocal} decides a file changed by comparing its timestamp — at one-second
 * granularity — and its length with what it recorded. Two edits to a file within a second
 * that leave its length unchanged look like no change at all: the refresh after the second
 * one sees nothing, the build compiles nothing, {@code /refreshProject} answers {@code ok}, and
 * the class file keeps the first edit until the source is touched again. (Measured: a same-length
 * edit stamped +1ms or +500ms within the same second was missed, every time; a length change,
 * or the next second, was seen. An agent editing quickly hits it.)
 *
 * <p>So the dev server keeps its own record: per project, each file's timestamp at the
 * filesystem's full precision (nanoseconds, through NIO) as of the last refresh. After
 * Eclipse's refresh, every file whose precise timestamp differs from the record is
 * {@link IFile#touch touched} — a change delta Eclipse can't miss, so the build compiles it —
 * and the record is re-read AFTER the touches, so nothing is selected twice. A touch Eclipse
 * didn't need costs one recompile of that file. The first refresh of a project only records.
 */
final class PreciseRefresh {

	/** Per project: workspace path of each file → its precise timestamp at the last refresh. */
	private static final Map<String, Map<String, Long>> RECORDS = new ConcurrentHashMap<>();

	private PreciseRefresh() {
	}

	/**
	 * Touches the project's files whose precise timestamp changed since the last refresh
	 * (call right after {@code refreshLocal}, before building), and records the new ones.
	 *
	 * @return how many files were touched
	 */
	static int touchMissed(IProject project) throws CoreException {
		final Map<String, Long> previous = RECORDS.get(project.getName());
		final Map<String, IFile> changed = new HashMap<>();
		final Map<String, Long> current = snapshot(project);
		if (previous != null) {
			for (final Map.Entry<String, Long> entry : current.entrySet()) {
				final Long before = previous.get(entry.getKey());
				if (before != null && !before.equals(entry.getValue())) {
					changed.put(entry.getKey(), project.getWorkspace().getRoot().getFile(org.eclipse.core.runtime.Path.fromPortableString(entry.getKey())));
				}
			}
		}
		for (final IFile file : changed.values()) {
			if (file.exists()) {
				file.touch(new NullProgressMonitor());
			}
		}
		// Re-read after touching (a touch may move the timestamp on disk), so the next refresh
		// compares against what is there now and doesn't select these files again.
		RECORDS.put(project.getName(), changed.isEmpty() ? current : snapshot(project));
		return changed.size();
	}

	/** Touches one file after its refresh: the single-path case, where the file is known. */
	static void touch(IFile file) throws CoreException {
		if (file.exists()) {
			file.touch(new NullProgressMonitor());
		}
	}

	/**
	 * Every source file of the project with its precise timestamp. Derived resources (output
	 * folders, target/) are skipped: they are the build's, not the author's.
	 */
	private static Map<String, Long> snapshot(IProject project) throws CoreException {
		final Map<String, Long> times = new HashMap<>();
		project.accept(resource -> {
			if (resource.isDerived() || resource.isTeamPrivateMember() || resource.getName().startsWith(".")) {
				return false;
			}
			if (resource.getType() == IResource.FILE && resource.getLocation() != null) {
				final Long time = preciseTime(resource.getLocation().toFile().toPath());
				if (time != null) {
					times.put(resource.getFullPath().toPortableString(), time);
				}
			}
			return true;
		});
		return times;
	}

	private static Long preciseTime(Path path) {
		try {
			return Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS);
		}
		catch (final IOException e) {
			return null;
		}
	}
}

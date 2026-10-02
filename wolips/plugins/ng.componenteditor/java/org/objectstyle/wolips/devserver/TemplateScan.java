package org.objectstyle.wolips.devserver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Walking templates and turning text matches into locations, for {@code /callers} and
 * {@code /find}. The matching itself is the refactoring code's ({@link RenameComponentProcessor},
 * {@code RenameBindingKeyProcessor}) — the same scans the Usages tab and JDT's Find References
 * participant run, so the endpoints agree with what the editor shows.
 */
final class TemplateScan {

	private TemplateScan() {
	}

	/** Receives each template file of a scan with its content. */
	interface Visitor {
		void visit(IFile file, String content, boolean isWod) throws Exception;
	}

	/**
	 * The project and every open project that depends on it, transitively — where a component
	 * can be used. A component in a framework is used by the applications built on it.
	 */
	static Set<IProject> withDependents(IProject project) {
		final Set<IProject> projects = new LinkedHashSet<>();
		collect(project, projects);
		return projects;
	}

	private static void collect(IProject project, Set<IProject> into) {
		if (!project.isOpen() || !into.add(project)) {
			return;
		}
		for (final IProject referencing : project.getReferencingProjects()) {
			collect(referencing, into);
		}
	}

	/** Visits every non-derived .html and .wod file in the projects. */
	static void templates(Set<IProject> projects, Visitor visitor) throws CoreException {
		for (final IProject project : projects) {
			project.accept(resource -> {
				if (resource.isDerived()) {
					return false; // target/classes copies of templates are not sources
				}
				if (resource.getType() != IResource.FILE) {
					return true;
				}
				final IFile file = (IFile) resource;
				final String extension = file.getFileExtension();
				final boolean isHtml = "html".equalsIgnoreCase(extension);
				final boolean isWod = "wod".equalsIgnoreCase(extension);
				if (!isHtml && !isWod) {
					return false;
				}
				try {
					final String content = RenameComponentProcessor.readFileContent(file);
					if (content != null) {
						visitor.visit(file, content, isWod);
					}
				}
				catch (final Exception e) {
					// An unreadable file is skipped, as the Usages tab skips it.
				}
				return false;
			});
		}
	}

	/**
	 * The component a template file belongs to: its .wo folder's name for a bundle template,
	 * the file's own name for a standalone one.
	 */
	static String owner(IFile file) {
		final IContainer parent = file.getParent();
		if (parent != null && "wo".equals(parent.getFileExtension())) {
			return parent.getFullPath().removeFileExtension().lastSegment();
		}
		return file.getFullPath().removeFileExtension().lastSegment();
	}

	/**
	 * A match as JSON: workspace path, 1-based line and column, and the trimmed source line,
	 * so an agent reading the answer usually needs no second read to see the usage.
	 */
	static JsonObject location(IFile file, String content, int offset) {
		int line = 1;
		int lineStart = 0;
		for (int i = 0; i < offset && i < content.length(); i++) {
			if (content.charAt(i) == '\n') {
				line++;
				lineStart = i + 1;
			}
		}
		int lineEnd = content.indexOf('\n', lineStart);
		if (lineEnd < 0) {
			lineEnd = content.length();
		}
		String text = content.substring(lineStart, lineEnd).strip();
		if (text.length() > 200) {
			text = text.substring(0, 200) + "…";
		}
		return new JsonObject()
				.put("file", file.getFullPath().toString())
				.put("line", line)
				.put("column", offset - lineStart + 1)
				.put("text", text);
	}

	/** Every offset match as a location. */
	static void addAll(List<JsonObject> into, IFile file, String content, List<int[]> offsets) {
		for (final int[] offset : offsets) {
			into.add(location(file, content, offset[0]));
		}
	}
}

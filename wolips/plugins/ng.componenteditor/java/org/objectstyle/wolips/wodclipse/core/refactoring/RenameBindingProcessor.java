package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

/**
 * Scans templates for binding name references and creates text edits to rename them.
 *
 * <p>Given a component name (e.g. "MyComponent") and one or more binding renames
 * (oldName → newName), this processor scans all {@code .html} and {@code .wod}
 * files in the source project <em>and all projects that depend on it</em>
 * (transitively) for usages of the old binding names on that component, and
 * produces {@link TextFileChange}s to rewrite them.
 *
 * <p>All renames are collected in a single pass per file, producing one
 * {@link TextFileChange} per file with all edits merged into a single
 * {@link MultiTextEdit}. This ensures correct offset handling when multiple
 * bindings are renamed in the same tag — each edit references the original
 * file content, and LTK applies them all atomically.
 *
 * <p>The transitive dependency scan ensures that if a component is defined in
 * a framework/library project, templates in application projects that depend
 * on it are also updated.
 *
 * <p>Follows the same scanning pattern as {@link RenameComponentProcessor}, but
 * targets binding attribute names within component tags rather than element type
 * names.
 */
public class RenameBindingProcessor {

	/**
	 * Scans all templates in the project and its dependent projects for
	 * binding references to the given component, and creates text edits to
	 * rename all specified bindings.
	 *
	 * <p>The scan covers the source project (where the {@code .api} file lives)
	 * plus all projects that transitively depend on it. This ensures that
	 * components defined in library/framework projects have their binding
	 * references updated in consuming application projects.
	 *
	 * <p>All renames are processed in a single pass per file, producing one
	 * {@link TextFileChange} per affected file. This avoids offset corruption
	 * when multiple bindings are renamed in the same file — all edits are
	 * based on the original file content and applied atomically by LTK.
	 *
	 * <p>In HTML templates, finds {@code <wo:ComponentName oldBinding="...">}
	 * and replaces the attribute name. In WOD files, finds
	 * {@code Foo : ComponentName { oldBinding = ...; }} and replaces the
	 * binding name.
	 *
	 * @param project the project containing the component's {@code .api} file
	 * @param componentName the element type name (e.g. "MyComponent")
	 * @param renames map of old binding name → new binding name
	 * @return a CompositeChange with all text edits, or null if no references found
	 */
	public static CompositeChange computeBindingReferenceChanges(
			IProject project,
			String componentName,
			Map<String, String> renames) throws CoreException {

		// Collect the source project plus all projects that (transitively)
		// depend on it. A component defined in a framework project may be
		// used in templates across multiple application projects.
		Set<IProject> projectsToScan = new HashSet<>();
		collectDependentProjects(project, projectsToScan);

		List<Change> changes = new ArrayList<>();

		IResourceVisitor visitor = new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				if (resource.isDerived()) {
					return false;
				}
				if (resource.getType() != IResource.FILE) {
					return true;
				}

				IFile file = (IFile) resource;
				String extension = file.getFileExtension();
				if (extension == null) {
					return false;
				}

				try {
					if ("html".equalsIgnoreCase(extension)) {
						TextFileChange change = createHtmlBindingChange(file, componentName, renames);
						if (change != null) {
							changes.add(change);
						}
					}
					else if ("wod".equalsIgnoreCase(extension)) {
						TextFileChange change = createWodBindingChange(file, componentName, renames);
						if (change != null) {
							changes.add(change);
						}
					}
				}
				catch (IOException e) {
					org.objectstyle.wolips.bindings.Activator.getDefault().log(
							"Failed to scan " + file.getFullPath() + " for binding references", e);
				}

				return false;
			}
		};

		for (IProject p : projectsToScan) {
			if (p.isAccessible()) {
				p.accept(visitor);
			}
		}

		if (changes.isEmpty()) {
			return null;
		}

		String description;
		if (renames.size() == 1) {
			Map.Entry<String, String> entry = renames.entrySet().iterator().next();
			description = "Rename binding '" + entry.getKey() + "' to '" + entry.getValue()
					+ "' in " + componentName;
		} else {
			description = "Rename " + renames.size() + " bindings in " + componentName;
		}
		CompositeChange composite = new CompositeChange(description);
		for (Change change : changes) {
			composite.add(change);
		}
		return composite;
	}

	/**
	 * Collects the given project and all projects that transitively depend on
	 * it (via {@link IProject#getReferencingProjects()}).
	 *
	 * <p>Uses a visited set for cycle detection — project dependency graphs
	 * can occasionally contain cycles due to misconfiguration, and we must
	 * not recurse infinitely.
	 *
	 * @param project the root project to start from
	 * @param result accumulates the projects to scan (includes the root)
	 */
	private static void collectDependentProjects(IProject project, Set<IProject> result) {
		if (!result.add(project)) {
			return; // already visited — prevents cycles
		}
		for (IProject dependent : project.getReferencingProjects()) {
			collectDependentProjects(dependent, result);
		}
	}

	/**
	 * Scans an HTML template for {@code <wo:ComponentName} tags and replaces
	 * all renamed binding attribute names in a single pass.
	 *
	 * <p>Strategy: find each opening {@code <wo:ComponentName} tag, extract
	 * the tag's attribute region (up to the closing {@code >} or {@code />}),
	 * then for each rename, find the old binding name as an attribute name
	 * within that region.
	 *
	 * <p>All edits for all renames are collected into a single
	 * {@link MultiTextEdit}, ensuring correct offset handling when multiple
	 * bindings in the same tag are renamed simultaneously.
	 */
	static TextFileChange createHtmlBindingChange(
			IFile file,
			String componentName,
			Map<String, String> renames) throws CoreException, IOException {

		String content = RenameComponentProcessor.readFileContent(file);
		if (content == null) {
			return null;
		}

		// Find <wo:ComponentName opening tags (case-insensitive wo: prefix,
		// case-sensitive component name)
		Pattern tagPattern = Pattern.compile(
				"(?i:<wo:)" + Pattern.quote(componentName) + "(?=[\\s>/$])",
				0);

		Matcher tagMatcher = tagPattern.matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (tagMatcher.find()) {
			// Find the end of this tag (> or />)
			int tagEnd = findTagEnd(content, tagMatcher.end());
			if (tagEnd < 0) {
				continue;
			}

			// Search for each renamed binding within this tag's span
			String tagContent = content.substring(tagMatcher.end(), tagEnd);

			for (Map.Entry<String, String> rename : renames.entrySet()) {
				String oldName = rename.getKey();
				String newName = rename.getValue();

				// The binding name must be preceded by whitespace (start of
				// attribute) and followed by optional whitespace and =
				// (attribute assignment). This prevents matching attribute
				// values or partial names.
				Pattern bindingPattern = Pattern.compile(
						"(?<=\\s)" + Pattern.quote(oldName) + "\\s*=");
				Matcher bindingMatcher = bindingPattern.matcher(tagContent);

				while (bindingMatcher.find()) {
					int absoluteOffset = tagMatcher.end() + bindingMatcher.start();
					edits.add(new ReplaceEdit(absoluteOffset, oldName.length(), newName));
				}
			}
		}

		return createChange(file, edits, "Rename binding(s) in " + file.getName());
	}

	/**
	 * Scans a WOD file for element declarations of the given component type
	 * and replaces all renamed binding names in a single pass.
	 *
	 * <p>WOD format: {@code ElementName : ComponentName { bindingName = value; }}
	 *
	 * <p>Strategy: find each {@code : ComponentName {}, then within the braces
	 * find each old binding name followed by {@code =}.
	 *
	 * <p>All edits for all renames are collected into a single
	 * {@link MultiTextEdit}, ensuring correct offset handling when multiple
	 * bindings in the same declaration are renamed simultaneously.
	 */
	static TextFileChange createWodBindingChange(
			IFile file,
			String componentName,
			Map<String, String> renames) throws CoreException, IOException {

		String content = RenameComponentProcessor.readFileContent(file);
		if (content == null) {
			return null;
		}

		// Find element declarations for this component type
		Pattern elementPattern = Pattern.compile(
				":\\s*" + Pattern.quote(componentName) + "\\s*\\{");

		Matcher elementMatcher = elementPattern.matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (elementMatcher.find()) {
			// Find the closing brace for this declaration
			int braceStart = elementMatcher.end();
			int braceEnd = content.indexOf('}', braceStart);
			if (braceEnd < 0) {
				continue;
			}

			// Search for each renamed binding within the braces
			String braceContent = content.substring(braceStart, braceEnd);

			for (Map.Entry<String, String> rename : renames.entrySet()) {
				String oldName = rename.getKey();
				String newName = rename.getValue();

				// Binding name at the start of a line (preceded by whitespace
				// or start of block) followed by =
				Pattern bindingPattern = Pattern.compile(
						"(?<=^|[;\\s])" + Pattern.quote(oldName) + "\\s*=");
				Matcher bindingMatcher = bindingPattern.matcher(braceContent);

				while (bindingMatcher.find()) {
					int absoluteOffset = braceStart + bindingMatcher.start();
					edits.add(new ReplaceEdit(absoluteOffset, oldName.length(), newName));
				}
			}
		}

		return createChange(file, edits, "Rename binding(s) in " + file.getName());
	}

	/**
	 * Finds the end of an HTML tag starting from the given position.
	 * Returns the index of the closing {@code >}, or -1 if not found.
	 * Handles quoted attribute values (won't match {@code >} inside quotes).
	 */
	private static int findTagEnd(String content, int start) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;

		for (int i = start; i < content.length(); i++) {
			char c = content.charAt(i);

			if (inSingleQuote) {
				if (c == '\'') {
					inSingleQuote = false;
				}
			}
			else if (inDoubleQuote) {
				if (c == '"') {
					inDoubleQuote = false;
				}
			}
			else if (c == '\'') {
				inSingleQuote = true;
			}
			else if (c == '"') {
				inDoubleQuote = true;
			}
			else if (c == '>') {
				return i;
			}
			else if (c == '<') {
				// Malformed HTML — hit another opening tag before closing this one
				return -1;
			}
		}

		return -1;
	}

	/**
	 * Creates a {@link TextFileChange} from a list of edits, or returns null
	 * if the list is empty.
	 */
	private static TextFileChange createChange(IFile file, List<ReplaceEdit> edits, String description) {
		if (edits.isEmpty()) {
			return null;
		}

		TextFileChange change = new TextFileChange(description, file);
		MultiTextEdit multiEdit = new MultiTextEdit();
		for (ReplaceEdit edit : edits) {
			multiEdit.addChild(edit);
		}
		change.setEdit(multiEdit);
		return change;
	}
}

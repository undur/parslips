package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.BuildProperties;

/**
 * Scans a component's own template files for binding key references and creates
 * text edits to rename them.
 *
 * <p>When a method or field that serves as a binding key is renamed in a
 * component's Java class (via Refactor &gt; Rename), the corresponding key
 * references in the component's own template files should update automatically.
 * This processor handles the scanning and edit creation.
 *
 * <p>Binding keys appear in two places:
 * <ul>
 *   <li><b>WOD binding values</b>: {@code value = title;} or
 *       {@code value = title.length;} — the key is the first segment of
 *       a key path on the right-hand side of the {@code =}.</li>
 *   <li><b>Inline HTML binding values</b>: {@code value="$title"} or
 *       {@code value="$title.length"} — the key follows the inline binding
 *       prefix (typically {@code $}) inside an attribute value.</li>
 * </ul>
 *
 * <p>This is a <em>local</em> refactoring — only the component's own template
 * files are scanned, not other components' templates. A component's binding
 * keys are its own internal wiring, not part of its public API.
 *
 * <p>Conservative matching ensures only exact key matches are replaced:
 * <ul>
 *   <li>String literals ({@code "title"}) are not matched in WOD — the quotes
 *       prevent the regex from firing.</li>
 *   <li>Caret references ({@code ^parent.title}) are not matched — the
 *       {@code ^} breaks the WOD pattern.</li>
 *   <li>Substring matches ({@code titleCase}) are excluded by lookahead
 *       assertions that require a word boundary character after the key.</li>
 * </ul>
 *
 * <p>Follows the same {@link TextFileChange}/{@link MultiTextEdit}/
 * {@link ReplaceEdit} pattern as {@link RenameComponentProcessor} and
 * {@link RenameBindingProcessor}.
 */
public class RenameBindingKeyProcessor {

	/**
	 * Computes all text edits needed to rename a binding key in a component's
	 * own template files (HTML and WOD).
	 *
	 * @param project the project containing the component
	 * @param componentName the component's simple name (e.g. "MyComponent")
	 * @param oldKey the current binding key (e.g. "title")
	 * @param newKey the new binding key (e.g. "heading")
	 * @param inlineBindingPrefix the prefix used for inline bindings in HTML
	 *        templates (typically "$"), obtained from
	 *        {@link BuildProperties#getInlineBindingPrefix()}
	 * @return a CompositeChange containing all text edits, or null if no
	 *         references were found
	 */
	public static CompositeChange computeBindingKeyChanges(
			IProject project,
			String componentName,
			String oldKey,
			String newKey,
			String inlineBindingPrefix) throws CoreException {

		LocalizedComponentsLocateResult locateResult;
		try {
			locateResult = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
		}
		catch (LocateException e) {
			throw new CoreException(new org.eclipse.core.runtime.Status(
					org.eclipse.core.runtime.IStatus.ERROR,
					"ng.componenteditor",
					"Failed to locate component '" + componentName + "'", e));
		}

		if (locateResult == null || !locateResult.hasContent()) {
			return null;
		}

		List<TextFileChange> changes = new ArrayList<>();

		// Scan the WOD file for binding key references
		try {
			IFile wodFile = locateResult.getFirstWodFile();
			if (wodFile != null && wodFile.exists()) {
				TextFileChange change = createWodKeyChange(wodFile, oldKey, newKey);
				if (change != null) {
					changes.add(change);
				}
			}
		}
		catch (IOException e) {
			org.objectstyle.wolips.bindings.Activator.getDefault().log(
					"Failed to scan WOD file for binding key references", e);
		}

		// Scan the HTML file for inline binding key references
		try {
			IFile htmlFile = locateResult.getFirstHtmlFile();
			if (htmlFile != null && htmlFile.exists()) {
				TextFileChange change = createHtmlKeyChange(htmlFile, oldKey, newKey, inlineBindingPrefix);
				if (change != null) {
					changes.add(change);
				}
			}
		}
		catch (IOException e) {
			org.objectstyle.wolips.bindings.Activator.getDefault().log(
					"Failed to scan HTML file for binding key references", e);
		}

		if (changes.isEmpty()) {
			return null;
		}

		CompositeChange composite = new CompositeChange(
				"Rename binding key '" + oldKey + "' to '" + newKey + "' in " + componentName);
		for (TextFileChange change : changes) {
			composite.add(change);
		}
		return composite;
	}

	/**
	 * Scans a WOD file for binding key references and creates text edits to
	 * rename them.
	 *
	 * <p>Matches the old key as a binding value (right-hand side of {@code =})
	 * at the start of a key path. The key must be preceded by {@code =} and
	 * optional whitespace, and followed by a key-path terminator ({@code ;},
	 * {@code .}, {@code |}, whitespace, or end of string).
	 *
	 * <p>This naturally excludes:
	 * <ul>
	 *   <li>String literals: {@code "title"} — the opening quote between
	 *       {@code =} and the key prevents the match.</li>
	 *   <li>Caret references: {@code ^parent.title} — the {@code ^} breaks
	 *       the pattern.</li>
	 *   <li>Binding names (LHS): {@code title = ...} — no preceding
	 *       {@code =}.</li>
	 *   <li>Substrings: {@code titleCase} — the lookahead requires a
	 *       terminator after the key.</li>
	 * </ul>
	 *
	 * <p>Package-visible for testing via {@link RenameBindingKeyProcessorTest}.
	 *
	 * @return a TextFileChange, or null if no references were found
	 */
	static TextFileChange createWodKeyChange(IFile file, String oldKey, String newKey) throws CoreException, IOException {
		String content = RenameComponentProcessor.readFileContent(file);
		if (content == null) {
			return null;
		}

		List<ReplaceEdit> edits = findWodKeyEdits(content, oldKey, newKey);

		if (edits.isEmpty()) {
			return null;
		}

		TextFileChange change = new TextFileChange("Rename key '" + oldKey + "' in " + file.getName(), file);
		MultiTextEdit multiEdit = new MultiTextEdit();
		for (ReplaceEdit edit : edits) {
			multiEdit.addChild(edit);
		}
		change.setEdit(multiEdit);
		return change;
	}

	/**
	 * Finds all WOD binding key references in the given content string and
	 * returns {@link ReplaceEdit} objects to rename them.
	 *
	 * <p>Package-visible so the test class can exercise the same regex logic
	 * against raw strings without requiring Eclipse workspace objects.
	 *
	 * @param content the WOD file content
	 * @param oldKey the current binding key to find
	 * @param newKey the replacement binding key
	 * @return list of ReplaceEdit objects
	 */
	static List<ReplaceEdit> findWodKeyEdits(String content, String oldKey, String newKey) {
		// Match: = followed by optional whitespace, then the key, then a
		// key-path terminator. The key is captured by its position within
		// the overall match.
		Pattern pattern = Pattern.compile(
				"=\\s*" + Pattern.quote(oldKey) + "(?=[;\\s.|]|$)");

		Matcher matcher = pattern.matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (matcher.find()) {
			// The match includes "= " prefix — the key starts at the end
			// of the match minus the key length
			int keyStart = matcher.end() - oldKey.length();
			edits.add(new ReplaceEdit(keyStart, oldKey.length(), newKey));
		}

		return edits;
	}

	/**
	 * Scans an HTML template file for inline binding key references and creates
	 * text edits to rename them.
	 *
	 * <p>Matches the inline binding prefix (typically {@code $}) followed
	 * immediately by the old key, inside attribute values. The key must be
	 * followed by a value terminator ({@code "}, {@code '}, {@code .},
	 * {@code |}, whitespace, or end of string).
	 *
	 * <p>This naturally excludes:
	 * <ul>
	 *   <li>Literal attribute values: {@code value="just text"} — no prefix
	 *       before the text.</li>
	 *   <li>Substrings: {@code $titleCase} — the lookahead requires a
	 *       terminator after the key.</li>
	 * </ul>
	 *
	 * <p>Package-visible for testing via {@link RenameBindingKeyProcessorTest}.
	 *
	 * @return a TextFileChange, or null if no references were found
	 */
	static TextFileChange createHtmlKeyChange(IFile file, String oldKey, String newKey, String inlineBindingPrefix) throws CoreException, IOException {
		String content = RenameComponentProcessor.readFileContent(file);
		if (content == null) {
			return null;
		}

		List<ReplaceEdit> edits = findHtmlKeyEdits(content, oldKey, newKey, inlineBindingPrefix);

		if (edits.isEmpty()) {
			return null;
		}

		TextFileChange change = new TextFileChange("Rename key '" + oldKey + "' in " + file.getName(), file);
		MultiTextEdit multiEdit = new MultiTextEdit();
		for (ReplaceEdit edit : edits) {
			multiEdit.addChild(edit);
		}
		change.setEdit(multiEdit);
		return change;
	}

	/**
	 * Finds all inline HTML binding key references in the given content string
	 * and returns {@link ReplaceEdit} objects to rename them.
	 *
	 * <p>Package-visible so the test class can exercise the same regex logic
	 * against raw strings without requiring Eclipse workspace objects.
	 *
	 * @param content the HTML file content
	 * @param oldKey the current binding key to find
	 * @param newKey the replacement binding key
	 * @param inlineBindingPrefix the inline binding prefix (e.g. "$")
	 * @return list of ReplaceEdit objects
	 */
	static List<ReplaceEdit> findHtmlKeyEdits(String content, String oldKey, String newKey, String inlineBindingPrefix) {
		// Match: the inline binding prefix followed immediately by the key,
		// then a value terminator. Only the key portion is replaced.
		Pattern pattern = Pattern.compile(
				Pattern.quote(inlineBindingPrefix) + Pattern.quote(oldKey) + "(?=[\"'.\\s|]|$)");

		Matcher matcher = pattern.matcher(content);
		List<ReplaceEdit> edits = new ArrayList<>();

		while (matcher.find()) {
			// The match includes the prefix — the key starts after the prefix
			int keyStart = matcher.start() + inlineBindingPrefix.length();
			edits.add(new ReplaceEdit(keyStart, oldKey.length(), newKey));
		}

		return edits;
	}
}

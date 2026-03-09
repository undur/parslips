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
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
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
		List<int[]> offsets = findWodKeyOffsets(content, oldKey);
		List<ReplaceEdit> edits = new ArrayList<>();
		for (int[] offset : offsets) {
			edits.add(new ReplaceEdit(offset[0], offset[1], newKey));
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
		List<int[]> offsets = findHtmlKeyOffsets(content, oldKey, inlineBindingPrefix);
		List<ReplaceEdit> edits = new ArrayList<>();
		for (int[] offset : offsets) {
			edits.add(new ReplaceEdit(offset[0], offset[1], newKey));
		}
		return edits;
	}

	// ---- Offset-only scanning (shared by refactoring and search) ----

	/**
	 * Finds all WOD binding key references in the given content and returns
	 * their character offsets.
	 *
	 * <p>Uses the same regex as the rename refactoring: matches the key as
	 * a binding value (right-hand side of {@code =}) at the start of a key
	 * path.
	 *
	 * @param content the WOD file content
	 * @param key the binding key to find
	 * @return list of {@code [offset, length]} pairs
	 */
	public static List<int[]> findWodKeyOffsets(String content, String key) {
		Pattern pattern = Pattern.compile(
				"=\\s*" + Pattern.quote(key) + "(?=[;\\s.|]|$)");
		Matcher matcher = pattern.matcher(content);
		List<int[]> offsets = new ArrayList<>();
		while (matcher.find()) {
			int keyStart = matcher.end() - key.length();
			offsets.add(new int[] { keyStart, key.length() });
		}
		return offsets;
	}

	/**
	 * Finds all inline HTML binding key references in the given content and
	 * returns their character offsets.
	 *
	 * <p>Uses the same regex as the rename refactoring: matches the inline
	 * binding prefix (typically {@code $}) followed immediately by the key,
	 * inside attribute values.
	 *
	 * @param content the HTML file content
	 * @param key the binding key to find
	 * @param inlineBindingPrefix the inline binding prefix (e.g. "$")
	 * @return list of {@code [offset, length]} pairs
	 */
	public static List<int[]> findHtmlKeyOffsets(String content, String key, String inlineBindingPrefix) {
		Pattern pattern = Pattern.compile(
				Pattern.quote(inlineBindingPrefix) + Pattern.quote(key) + "(?=[\"'.\\s|]|$)");
		Matcher matcher = pattern.matcher(content);
		List<int[]> offsets = new ArrayList<>();
		while (matcher.find()) {
			int keyStart = matcher.start() + inlineBindingPrefix.length();
			offsets.add(new int[] { keyStart, key.length() });
		}
		return offsets;
	}

	// ---- Key derivation (shared by refactoring and search) ----

	/**
	 * Derives a binding key from a Java member (method or field) by stripping
	 * KVC prefixes.
	 *
	 * @see #deriveBindingKeyFromMethodName(String)
	 * @see #deriveBindingKeyFromFieldName(String)
	 */
	public static String deriveBindingKey(IMember member) {
		String name = member.getElementName();
		if (member instanceof IField) {
			return deriveBindingKeyFromFieldName(name);
		}
		return deriveBindingKeyFromMethodName(name);
	}

	/**
	 * Derives a binding key from a method name by stripping KVC getter/setter
	 * prefixes and lowercasing the first letter.
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code title()} → "title"</li>
	 *   <li>{@code getTitle()} → "title"</li>
	 *   <li>{@code isEnabled()} → "enabled"</li>
	 *   <li>{@code setTitle()} → "title"</li>
	 *   <li>{@code _getTitle()} → "title"</li>
	 *   <li>{@code _title()} → "title"</li>
	 * </ul>
	 */
	public static String deriveBindingKeyFromMethodName(String methodName) {
		// 1. Try explicit getter prefixes (skip "" and bare "_")
		for (String prefix : BindingReflectionUtils.GET_METHOD_PREFIXES) {
			if (prefix.length() > 1 && methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 2. Try setter prefixes
		for (String prefix : BindingReflectionUtils.SET_METHOD_PREFIXES) {
			if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				String remainder = methodName.substring(prefix.length());
				if (Character.isUpperCase(remainder.charAt(0))) {
					return BindingReflectionUtils.toLowercaseFirstLetter(remainder);
				}
			}
		}

		// 3. Bare "_" prefix
		if (methodName.startsWith("_") && methodName.length() > 1) {
			return methodName.substring(1);
		}

		// 4. No prefix — the method name IS the key
		return methodName;
	}

	/**
	 * Derives a binding key from a field name by stripping the underscore
	 * prefix if present.
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code title} → "title"</li>
	 *   <li>{@code _title} → "title"</li>
	 * </ul>
	 */
	public static String deriveBindingKeyFromFieldName(String fieldName) {
		if (fieldName.startsWith("_") && fieldName.length() > 1) {
			return fieldName.substring(1);
		}
		return fieldName;
	}
}

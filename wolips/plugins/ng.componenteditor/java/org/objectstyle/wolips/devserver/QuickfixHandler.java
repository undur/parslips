package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.builder.WodBuilder;
import org.objectstyle.wolips.wodclipse.core.quickfix.KeypathQuickFixGenerator;
import org.objectstyle.wolips.wodclipse.core.quickfix.ReplaceKeypathQuickFix;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionInfo;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionOperation;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyInfo;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyOperation;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * {@code /quickfix} — the editor's quick fixes (Cmd+1 on a template problem), as data.
 *
 * <ul>
 *   <li>{@code component=X} — validates the component and lists its template problems, each
 *       with the fixes the editor would offer: {@code replace} (a "did you mean" key or element
 *       name), {@code createKey} or {@code createAction} (add the missing key, or action method,
 *       to the component's class). Every problem and fix has an {@code id}.</li>
 *   <li>{@code component=X&problem=ID&fix=N} — applies one, then answers with the component's
 *       problems after re-validation. {@code createKey} takes an optional {@code type} (default
 *       {@code java.lang.String}).</li>
 * </ul>
 *
 * <p>Applying is made safe against a stale list: a {@code replace} re-reads the template and
 * refuses if the text at the problem no longer says what the problem said.
 *
 * <p>Edits run on the UI thread, through the same document machinery as the editor, so an
 * open editor shows the change; a replace goes on Eclipse's undo stack.
 */
class QuickfixHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return JsonObject.missing("component");
		}
		final String problemId = params.get("problem");
		final String fixId = params.get("fix");
		if (problemId != null && (fixId == null || fixId.isEmpty())) {
			return JsonObject.error("missing required parameter 'fix' (the id of one of the problem's fixes) — without 'problem', the problems and their fixes are listed");
		}

		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		if (found == null) {
			return new JsonObject().put("component", componentName).put("problems", List.of())
					.put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}

		final List<Problem> problems = problems(found);
		if (problemId == null) {
			return list(found, problems).toString();
		}

		final Problem problem = problems.stream().filter(p -> p.id.equals(problemId)).findFirst().orElse(null);
		if (problem == null) {
			return new JsonObject().put("component", componentName).put("fixed", false)
					.put("reason", "no current problem '" + problemId + "' in " + componentName + " - it may already be fixed; list the problems again for current ids")
					.toString();
		}
		final Fix fix = problem.fixes.stream().filter(f -> f.id.equals(fixId)).findFirst().orElse(null);
		if (fix == null) {
			return JsonObject.error("problem '" + problemId + "' has no fix '" + fixId + "' (its fixes: "
					+ problem.fixes.stream().map(f -> f.id).toList() + ")");
		}
		return apply(found, problem, fix, params.get("type")).toString();
	}

	// ---- problems and their fixes ----

	private record Fix(String id, String kind, String value, String description) {
		JsonObject toJson() {
			final JsonObject json = new JsonObject().put("id", id).put("fix", kind);
			if ("replace".equals(kind)) {
				json.put("with", value);
			}
			else {
				json.put("name", value);
			}
			return json.put("description", description);
		}
	}

	private record Problem(String id, IMarker marker, IFile file, String message, int line, String invalidName, List<Fix> fixes) {
	}

	/**
	 * The component's template problems after a fresh validation, with fixes. A problem's id is
	 * where it is — {@code html:LINE:OFFSET} — not its marker's id: validation recreates the
	 * markers on every run, so a marker id from one call is gone by the next. A position stays
	 * put until the template changes, and a replace re-checks the text there before editing.
	 */
	private static List<Problem> problems(DevServerComponents.Found found) throws Exception {
		final IFile html = found.descriptor().getHtmlFile();
		final IContainer parent = html.getParent();
		final IResource validationResource = found.descriptor().isBundle() ? parent : html;
		validationResource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		WodBuilder.validateComponent(validationResource, false, new NullProgressMonitor());

		final List<Problem> problems = new ArrayList<>();
		addProblems(html, "html", found, problems);
		final IFile wod = found.descriptor().getWodFile();
		if (wod != null && wod.exists()) {
			addProblems(wod, "wod", found, problems);
		}
		problems.sort(Comparator.comparing((Problem p) -> p.file.getName()).thenComparingInt(p -> p.line));
		return problems;
	}

	private static void addProblems(IFile file, String kind, DevServerComponents.Found found, List<Problem> into) throws Exception {
		for (final IMarker marker : file.findMarkers(Activator.TEMPLATE_PROBLEM_MARKER, true, IResource.DEPTH_ZERO)) {
			final String message = marker.getAttribute(IMarker.MESSAGE, "");
			final String invalidKey = KeypathQuickFixGenerator.extractInvalidKey(message);
			final String invalidName = invalidKey != null ? invalidKey : KeypathQuickFixGenerator.extractInvalidElementType(message);
			final List<Fix> fixes = new ArrayList<>();
			if (invalidName != null) {
				final String suggestions = marker.getAttribute("suggestions", "");
				for (final String suggestion : suggestions.split(";")) {
					final String trimmed = suggestion.trim();
					if (!trimmed.isEmpty()) {
						fixes.add(new Fix(Integer.toString(fixes.size() + 1), "replace", trimmed, "Replace '" + invalidName + "' with '" + trimmed + "'"));
					}
				}
				// A key missing on the component's own class (not deeper in a keypath, which would
				// mean editing another class) can be created — as an action for an action binding.
				final boolean directKey = invalidKey != null && !message.contains("for the keypath");
				if (directKey && found.descriptor().getJavaType() != null) {
					final String bindingName = marker.getAttribute("bindingName", null);
					final boolean action = bindingName != null && ApiUtils.isActionBindingName(bindingName);
					fixes.add(new Fix(Integer.toString(fixes.size() + 1), action ? "createAction" : "createKey", invalidKey,
							(action ? "Create action method '" : "Create key '") + invalidKey + "' in " + found.descriptor().getJavaType().getElementName()));
				}
			}
			final int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
			into.add(new Problem(kind + ":" + line + ":" + marker.getAttribute(IMarker.CHAR_START, -1), marker, file, message, line, invalidName, fixes));
		}
	}

	private static JsonObject list(DevServerComponents.Found found, List<Problem> problems) {
		final List<JsonObject> json = new ArrayList<>();
		for (final Problem problem : problems) {
			json.add(problemJson(problem));
		}
		return new JsonObject().put("component", found.descriptor().getName()).put("problems", json);
	}

	private static JsonObject problemJson(Problem problem) {
		final List<JsonObject> fixes = new ArrayList<>();
		for (final Fix fix : problem.fixes) {
			fixes.add(fix.toJson());
		}
		return new JsonObject()
				.put("id", problem.id)
				.put("file", problem.file.getFullPath().toString())
				.put("line", problem.line)
				.put("message", problem.message)
				.put("fixes", fixes);
	}

	// ---- applying ----

	private static JsonObject apply(DevServerComponents.Found found, Problem problem, Fix fix, String type) throws Exception {
		final JsonObject result = new JsonObject().put("component", found.descriptor().getName()).put("problem", problem.id).put("applied", fix.description);
		final String[] refusal = new String[1];
		final String[] note = new String[1];
		final Exception[] failure = new Exception[1];
		Display.getDefault().syncExec(() -> {
			try {
				if ("replace".equals(fix.kind)) {
					refusal[0] = replace(problem, fix.value);
				}
				else {
					note[0] = create(found.descriptor().getJavaType(), fix, type);
				}
			}
			catch (final Exception e) {
				failure[0] = e;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
		if (refusal[0] != null) {
			return new JsonObject().put("component", found.descriptor().getName()).put("problem", problem.id).put("fixed", false).put("reason", refusal[0]);
		}
		result.put("fixed", true);
		result.putIfPresent("note", note[0]);

		// A created key or action is Java: say if the class doesn't compile after it (a type
		// that doesn't resolve, say). The template is satisfied either way, so the problems
		// list below would look clean while the app can't take the change.
		ResourcesPlugin.getWorkspace().build(org.eclipse.core.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
		if (!"replace".equals(fix.kind)) {
			final String javaPath = found.descriptor().getJavaFile() == null ? null : found.descriptor().getJavaFile().getProjectRelativePath().toString();
			final List<JsonObject> javaErrors = new ArrayList<>();
			for (final WorkspaceProblems.Problem error : WorkspaceProblems.javaErrors(found.javaProject().getProject(), 50)) {
				if (error.resource.equals(javaPath)) {
					javaErrors.add(new JsonObject().put("line", error.line).put("message", error.message));
				}
			}
			if (!javaErrors.isEmpty()) {
				result.put("javaErrors", javaErrors).put("hint", "the class no longer compiles; fix it before exercising the page");
			}
		}

		// What's left, so the caller sees the effect without a second call. (A created key
		// resolves only after its class compiles: the incremental build ran above.)
		final List<JsonObject> remaining = new ArrayList<>();
		for (final Problem left : problems(found)) {
			remaining.add(problemJson(left));
		}
		return result.put("problems", remaining);
	}

	/**
	 * Replaces the invalid name at the problem with the suggestion — and, for an element
	 * name on a tag with a closing tag, the closing tag's name too, as the editor's fix does.
	 * Returns a refusal reason, or null when done.
	 */
	private static String replace(Problem problem, String replacement) throws Exception {
		final IMarker marker = problem.marker;
		final int start = marker.getAttribute(IMarker.CHAR_START, -1);
		final int end = marker.getAttribute(IMarker.CHAR_END, -1);
		final String content = RenameComponentProcessor.readFileContent(problem.file);
		if (start < 0 || end <= start || end > content.length()) {
			return "the problem has no usable position in " + problem.file.getName();
		}
		final int keyOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(content.substring(start, end), problem.invalidName);
		if (keyOffset < 0) {
			return "the template no longer says '" + problem.invalidName + "' where the problem was reported - list the problems again";
		}
		final MultiTextEdit edits = new MultiTextEdit();
		edits.addChild(new ReplaceEdit(start + keyOffset, problem.invalidName.length(), replacement));
		final int closeStart = marker.getAttribute("closeTagStart", -1);
		final int closeEnd = marker.getAttribute("closeTagEnd", -1);
		if (closeStart >= 0 && closeEnd > closeStart && closeEnd <= content.length()) {
			final int closeOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(content.substring(closeStart, closeEnd), problem.invalidName);
			if (closeOffset >= 0) {
				edits.addChild(new ReplaceEdit(closeStart + closeOffset, problem.invalidName.length(), replacement));
			}
		}
		final TextFileChange change = new TextFileChange("Replace '" + problem.invalidName + "' with '" + replacement + "'", problem.file);
		change.setEdit(edits);
		change.initializeValidationData(new NullProgressMonitor());
		final PerformChangeOperation operation = new PerformChangeOperation(change);
		operation.setUndoManager(RefactoringCore.getUndoManager(), change.getName());
		ResourcesPlugin.getWorkspace().run(operation, new NullProgressMonitor());
		return null;
	}

	/**
	 * Adds the key (field + accessor + mutator, as the editor's Add Key dialog does by default)
	 * or the action method to the component's class. Returns a note for the caller, or null.
	 *
	 * <p>When the class is open in an editor with unsaved changes, the addition joins those
	 * unsaved changes — saving would also save the developer's pending edits, so it is left
	 * for them, and the note says so. Otherwise it is saved.
	 */
	private static String create(IType type, Fix fix, String typeName) throws Exception {
		final ICompilationUnit unit = type.getCompilationUnit();
		final boolean pendingEdits = unit.isWorkingCopy() && unit.hasUnsavedChanges();
		if ("createAction".equals(fix.kind)) {
			final AddActionInfo info = new AddActionInfo(type);
			info.setName(fix.value);
			new AddActionOperation(info).run(null);
		}
		else {
			final AddKeyInfo info = new AddKeyInfo(type);
			info.setName(fix.value);
			if (typeName != null && !typeName.isEmpty()) {
				// Only the key's type. (AddKeyInfo's parameter type is a generic ELEMENT type,
				// List<X>'s X; setting it to the type itself generated String<String>.)
				info.setTypeName(typeName);
			}
			new AddKeyOperation(info).run(null);
		}
		if (unit.isWorkingCopy()) {
			if (pendingEdits) {
				return type.getElementName() + " is open with unsaved edits, so the new member is in the editor, unsaved; the build won't see it until the developer saves";
			}
			unit.commitWorkingCopy(false, new NullProgressMonitor());
		}
		return "createAction".equals(fix.kind) ? "the action returns null (stays on the page); fill in its body"
				: "added a private field with an accessor and a mutator";
	}
}

package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.IWodModel;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameBindingProcessor;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameElementsRefactoring;

/**
 * {@code /rename} — rename across Java, HTML and WOD in one atomic step, through the editor's
 * own refactorings, instead of three hand edits an agent can desynchronise.
 *
 * <ul>
 *   <li>{@code kind=component&component=Old&to=New} — the class (a JDT rename: every Java
 *       reference follows), its template files (.wo folder or .html, and .api), and every
 *       {@code <wo:Old>} / {@code X : Old {}} in the project and the projects that depend on it.
 *       A template-only component (no class) renames its files and references.</li>
 *   <li>{@code kind=key&component=C&from=old&to=new} — a key of a component: every member that
 *       implements it ({@code old()}, {@code getOld()}, {@code setOld()}, a field {@code old} or
 *       {@code _old}, each by its own name pattern) as JDT renames, the component's own
 *       template references ({@code $old…}, {@code = old…;}), and the binding attribute at every
 *       call site ({@code <wo:C old=…>}), since for a component the key IS the binding.</li>
 *   <li>{@code kind=element&component=C&from=Old&to=New} — a WOD element name in a bundle
 *       template: {@code <webobject name="Old">} and its {@code Old : Type {…}} entry.</li>
 * </ul>
 *
 * <p>{@code preview=true} lists the changes without making them — files edited (and how many
 * edits), files renamed. Every rename goes on Eclipse's undo stack, so the developer can undo
 * it from the Edit menu.
 *
 * <p>Runs on the UI thread: a refactoring edits the documents of any open editor, and editors
 * update their widgets from document events, which SWT allows only on the UI thread.
 */
class RenameHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String kind = params.get("kind");
		if (kind == null || kind.isEmpty()) {
			return JsonObject.error("missing required parameter 'kind' (component, key or element)");
		}
		if (!List.of("component", "key", "element").contains(kind)) {
			return JsonObject.error("kind must be component, key or element");
		}
		final String className = params.get("class");
		if ("key".equals(kind) && className != null && !className.isEmpty()) {
			return renameClassKey(className, params);
		}
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return JsonObject.missing("component");
		}
		final String to = params.get("to");
		if (to == null || to.isEmpty()) {
			return JsonObject.missing("to");
		}
		final String from = "component".equals(kind) ? componentName : params.get("from");
		if (from == null || from.isEmpty()) {
			return JsonObject.missing("from");
		}
		if (!isIdentifier(to)) {
			return JsonObject.error("'" + to + "' is not a valid Java identifier");
		}
		final boolean preview = "true".equalsIgnoreCase(params.get("preview"));

		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		final JsonObject result = new JsonObject().put("kind", kind).put("component", componentName).put("from", from).put("to", to);
		if (found == null) {
			return result.put("renamed", false).put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}

		// Everything below edits documents that open editors may hold: UI thread (see class doc).
		final String[] answer = new String[1];
		final Exception[] failure = new Exception[1];
		Display.getDefault().syncExec(() -> {
			try {
				answer[0] = switch (kind) {
					case "component" -> renameComponent(found, to, preview, result);
					case "key" -> renameKey(found, from, to, preview, result);
					default -> renameElement(found, from, to, preview, result);
				};
			}
			catch (final Exception e) {
				failure[0] = e;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
		return answer[0];
	}

	// ---- kind=component ----

	private static String renameComponent(DevServerComponents.Found found, String to, boolean preview, JsonObject result) throws Exception {
		final IProject project = found.javaProject().getProject();
		final String from = found.descriptor().getName();
		if (RenameComponentProcessor.componentExists(project, to)) {
			return result.put("renamed", false).put("reason", "a component named '" + to + "' already exists in " + project.getName()).toString();
		}
		final IType type = found.descriptor().getJavaType();
		final List<Step> steps = new ArrayList<>();
		if (type != null) {
			// The class rename carries the component's files and its own project's references:
			// RenameComponentParticipant joins it. Without the participant (WOLips installed and
			// no project.base) the templates would be left behind — refuse instead.
			if (!ParsleyProject.shouldRefactor(project)) {
				return result.put("renamed", false).put("reason", "the editor's rename participants are off in " + project.getName()
						+ " (WOLips is installed and build.properties sets no project.base), so templates would not follow the class").toString();
			}
			steps.add(Step.jdt(renameDescriptor(IJavaRefactorings.RENAME_TYPE, type, to)));
		}
		else {
			final CompositeChange own = RenameComponentProcessor.computeChanges(project, from, to);
			if (own != null) {
				steps.add(Step.change(own));
			}
			final CompositeChange ownReferences = RenameComponentProcessor.computeReferenceChanges(project, from, to,
					RenameComponentProcessor.collectOwnFilePaths(project, from));
			if (ownReferences != null) {
				steps.add(Step.change(ownReferences));
			}
		}
		// The participant covers the component's own project; callers in projects that depend on
		// it (an application using a framework's component) are updated here.
		for (final IProject dependent : TemplateScan.withDependents(project)) {
			if (dependent.equals(project)) {
				continue;
			}
			final CompositeChange references = RenameComponentProcessor.computeReferenceChanges(dependent, from, to, null);
			if (references != null) {
				steps.add(Step.change(references));
			}
		}
		return run(steps, preview, result);
	}

	// ---- kind=key ----

	private static String renameKey(DevServerComponents.Found found, String from, String to, boolean preview, JsonObject result) throws Exception {
		final IType type = found.descriptor().getJavaType();
		if (type == null) {
			return result.put("renamed", false).put("reason", "component '" + found.descriptor().getName() + "' has no Java class, so it has no keys").toString();
		}
		final IProject project = found.javaProject().getProject();
		if (!ParsleyProject.shouldRefactor(project)) {
			return result.put("renamed", false).put("reason", "the editor's rename participants are off in " + project.getName()
					+ " (WOLips is installed and build.properties sets no project.base), so templates would not follow the key").toString();
		}
		final Set<IMember> members = keyMembers(found, type, from);
		final Change literals = bindingNameLiterals(type, from, to);
		if (members.isEmpty() && literals == null) {
			return result.put("renamed", false).put("reason", "'" + from + "' is neither a key declared in the workspace sources of " + type.getFullyQualifiedName('.')
					+ " nor a binding it reads by name (valueForBinding(\"" + from + "\") and kin)").toString();
		}
		final List<Step> steps = new ArrayList<>();
		// First, so the JDT steps after it are re-checked against the edited file (see run()).
		if (literals != null) {
			steps.add(Step.change(literals));
		}
		for (final IMember member : members) {
			final String newName = renamedMemberName(member.getElementName(), from, to);
			if (newName == null) {
				continue;
			}
			steps.add(Step.jdt(renameDescriptor(member instanceof IField ? IJavaRefactorings.RENAME_FIELD : IJavaRefactorings.RENAME_METHOD, member, newName)));
		}
		// Call sites pass the key in as a binding: <wo:Component from="…"> becomes to="…".
		final CompositeChange callSites = RenameBindingProcessor.computeBindingReferenceChanges(project, found.descriptor().getName(), Map.of(from, to));
		if (callSites != null) {
			steps.add(Step.change(callSites));
		}
		return run(steps, preview, result);
	}

	/**
	 * The binding names the component reads or writes BY NAME — {@code valueForBinding("team")}
	 * and kin — as a text change, or null when it has none. A non-synchronizing component reads
	 * its bindings this way; renaming the accessor and the call sites but not the literal left
	 * every call site passing a binding the component no longer reads (renamed:true, everything
	 * validating clean, and the page failing at render).
	 */
	private static Change bindingNameLiterals(IType type, String from, String to) {
		final String source = org.objectstyle.wolips.bindings.utils.BindingNameLiterals.sourceOf(type);
		if (source == null || !(type.getResource() instanceof org.eclipse.core.resources.IFile file)) {
			return null;
		}
		final org.eclipse.text.edits.MultiTextEdit edits = new org.eclipse.text.edits.MultiTextEdit();
		for (final org.objectstyle.wolips.bindings.utils.BindingNameLiterals.Occurrence occurrence : org.objectstyle.wolips.bindings.utils.BindingNameLiterals.find(source)) {
			if (occurrence.name().equals(from)) {
				edits.addChild(new org.eclipse.text.edits.ReplaceEdit(occurrence.offset(), from.length(), to));
			}
		}
		if (!edits.hasChildren()) {
			return null;
		}
		final TextFileChange change = new TextFileChange("Rename binding '" + from + "' read by name in " + type.getElementName(), file);
		change.setEdit(edits);
		return change;
	}

	/**
	 * Every member of the component that implements the key: the accessors and mutators the
	 * binding machinery recognises for it (methods and fields), source only — a framework's
	 * members can't be renamed.
	 */
	private static Set<IMember> keyMembers(DevServerComponents.Found found, IType type, String key) throws Exception {
		final Set<IMember> members = new LinkedHashSet<>();
		final List<BindingValueKey> keys = new ArrayList<>();
		keys.addAll(WodParserCache.getTypeCache().getBindingValueAccessorKeys(found.javaProject(), type, key));
		keys.addAll(WodParserCache.getTypeCache().getBindingValueMutatorKeys(found.javaProject(), type, key));
		for (final BindingValueKey bindingKey : keys) {
			final IMember member = bindingKey.getBindingMember();
			if (member != null && !member.isBinary() && member.getElementType() != IJavaElement.TYPE) {
				members.add(member);
			}
		}
		return members;
	}

	/**
	 * The member's new name: the key inside it replaced, keeping its pattern — {@code name} →
	 * {@code title}, {@code _name} → {@code _title}, {@code getName} → {@code getTitle},
	 * {@code setName} → {@code setTitle}. Null when the key isn't found in the name (a member
	 * the machinery matched some other way is left alone rather than guessed at).
	 */
	static String renamedMemberName(String memberName, String from, String to) {
		final int lower = memberName.lastIndexOf(from);
		if (lower >= 0 && lower + from.length() == memberName.length()) {
			return memberName.substring(0, lower) + to;
		}
		final String capitalizedFrom = Character.toUpperCase(from.charAt(0)) + from.substring(1);
		final int upper = memberName.lastIndexOf(capitalizedFrom);
		if (upper > 0 && upper + capitalizedFrom.length() == memberName.length()) {
			return memberName.substring(0, upper) + Character.toUpperCase(to.charAt(0)) + to.substring(1);
		}
		return null;
	}

	// ---- kind=key&class=… — a key of a model (or any non-component) class ----

	/**
	 * Renames a key of any class, as templates reach it through keypaths: its members (JDT,
	 * so Java follows) and every template keypath segment, in any component of the class's
	 * project and its dependents, that resolves to them ({@code $team.playerCount} →
	 * {@code $team.squadSize}). Components go through {@code component=}, where the editor's
	 * rename participants own their templates.
	 */
	private static String renameClassKey(String className, Map<String, String> params) throws Exception {
		final String from = params.get("from");
		final String to = params.get("to");
		if (from == null || from.isEmpty()) {
			return JsonObject.missing("from");
		}
		if (to == null || to.isEmpty()) {
			return JsonObject.missing("to");
		}
		if (!isIdentifier(to)) {
			return JsonObject.error("'" + to + "' is not a valid Java identifier");
		}
		final boolean preview = "true".equalsIgnoreCase(params.get("preview"));
		final JsonObject result = new JsonObject().put("kind", "key").put("class", className).put("from", from).put("to", to);
		final DevServerJava.TypeLookup lookup = DevServerJava.findSourceType(className, DevServerComponents.projectParam(params));
		if (lookup.type() == null) {
			return result.put("renamed", false).put("reason", lookup.problem()).toString();
		}
		final IType type = lookup.type();
		if (org.objectstyle.wolips.bindings.utils.BindingReflectionUtils.isWOComponent(type, WodParserCache.getTypeCache())) {
			return JsonObject.error(type.getElementName() + " is a component; rename its keys with component=" + type.getElementName() + " (its own templates and call sites follow)");
		}

		final String[] answer = new String[1];
		final Exception[] failure = new Exception[1];
		Display.getDefault().syncExec(() -> {
			try {
				final java.util.Set<IMember> members = KeypathScan.members(type, from);
				members.removeIf(IMember::isBinary);
				if (members.isEmpty()) {
					answer[0] = result.put("renamed", false).put("reason", "'" + from + "' is not a key declared in the workspace sources of " + type.getFullyQualifiedName('.')).toString();
					return;
				}
				final List<Step> steps = new ArrayList<>();
				for (final IMember member : members) {
					final String newName = renamedMemberName(member.getElementName(), from, to);
					if (newName != null) {
						steps.add(Step.jdt(renameDescriptor(member instanceof IField ? IJavaRefactorings.RENAME_FIELD : IJavaRefactorings.RENAME_METHOD, member, newName)));
					}
				}
				// The template segments, one change per file.
				final Map<org.eclipse.core.resources.IFile, org.eclipse.text.edits.MultiTextEdit> edits = new java.util.LinkedHashMap<>();
				for (final KeypathScan.Use use : KeypathScan.uses(TemplateScan.withDependents(type.getJavaProject().getProject()), members)) {
					edits.computeIfAbsent(use.file(), f -> new org.eclipse.text.edits.MultiTextEdit())
							.addChild(new org.eclipse.text.edits.ReplaceEdit(use.offset(), from.length(), to));
				}
				for (final Map.Entry<org.eclipse.core.resources.IFile, org.eclipse.text.edits.MultiTextEdit> entry : edits.entrySet()) {
					final TextFileChange change = new TextFileChange("Rename '" + from + "' in keypaths", entry.getKey());
					change.setEdit(entry.getValue());
					steps.add(Step.change(change));
				}
				answer[0] = run(steps, preview, result);
			}
			catch (final Exception e) {
				failure[0] = e;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
		return answer[0];
	}

	// ---- kind=element ----

	private static String renameElement(DevServerComponents.Found found, String from, String to, boolean preview, JsonObject result) throws Exception {
		if (!found.descriptor().isBundle()) {
			return result.put("renamed", false).put("reason", "component '" + found.descriptor().getName()
					+ "' is a standalone template; WOD element names exist only in bundle templates (.wo with a .wod)").toString();
		}
		final WodParserCache cache = WodParserCache.parser(found.javaProject().getProject(), found.descriptor().getName());
		final IWodModel wod = cache.getWodEntry().getModel();
		if (wod == null || wod.getElementNamed(from) == null) {
			return result.put("renamed", false).put("reason", "no WOD element named '" + from + "' in " + found.descriptor().getName()).toString();
		}
		if (wod.getElementNamed(to) != null) {
			return result.put("renamed", false).put("reason", "a WOD element named '" + to + "' already exists in " + found.descriptor().getName()).toString();
		}
		final List<JsonObject> changes = List.of(
				new JsonObject().put("file", found.descriptor().getHtmlFile().getFullPath().toString()).put("change", "<webobject name=\"" + from + "\"> → \"" + to + "\""),
				new JsonObject().put("file", found.descriptor().getWodFile().getFullPath().toString()).put("change", from + " : … → " + to + " : …"));
		if (preview) {
			return result.put("preview", true).put("changes", changes).toString();
		}
		RenameElementsRefactoring.run(from, to, cache, new NullProgressMonitor());
		return result.put("renamed", true).put("changes", changes).toString();
	}

	// ---- running refactorings ----

	/** One unit of work: a JDT refactoring (with its participants) or a ready-made change. */
	private record Step(RenameJavaElementDescriptor descriptor, Change change) {
		static Step jdt(RenameJavaElementDescriptor descriptor) {
			return new Step(descriptor, null);
		}

		static Step change(Change change) {
			return new Step(null, change);
		}
	}

	private static RenameJavaElementDescriptor renameDescriptor(String id, IJavaElement element, String newName) {
		final RenameJavaElementDescriptor descriptor = (RenameJavaElementDescriptor) RefactoringCore.getRefactoringContribution(id).createDescriptor();
		descriptor.setJavaElement(element);
		descriptor.setNewName(newName);
		descriptor.setUpdateReferences(true);
		return descriptor;
	}

	/**
	 * Checks every step first — any problem refuses the whole rename before anything is
	 * touched — then, unless previewing, performs them in order, each on the undo stack.
	 *
	 * <p>Each JDT step's change is created immediately before it is performed, never all up
	 * front: two steps can edit the same file (a field rename and its accessor's rename both
	 * carry a participant edit to the component's template), and a change computed before the
	 * earlier step ran would apply stale offsets.
	 */
	private static String run(List<Step> steps, boolean preview, JsonObject result) throws CoreException {
		final NullProgressMonitor monitor = new NullProgressMonitor();
		final List<String> warnings = new ArrayList<>();
		final List<Refactoring> checked = new ArrayList<>();
		for (final Step step : steps) {
			if (step.descriptor() == null) {
				checked.add(null);
				continue;
			}
			final Checked check = check(step.descriptor(), monitor, warnings);
			if (check.reason() != null) {
				return result.put("renamed", false).put("reason", check.reason()).toString();
			}
			checked.add(check.refactoring());
		}
		if (!warnings.isEmpty()) {
			result.put("warnings", warnings);
		}

		final List<JsonObject> described = new ArrayList<>();
		if (preview) {
			for (int i = 0; i < steps.size(); i++) {
				final Change change = steps.get(i).change() != null ? steps.get(i).change() : checked.get(i).createChange(monitor);
				describe(change, described);
				change.dispose();
			}
			return result.put("preview", true).put("changes", described).toString();
		}

		for (int i = 0; i < steps.size(); i++) {
			final Step step = steps.get(i);
			Change change = step.change();
			if (change == null) {
				// Re-checked against the workspace as the earlier steps left it.
				Refactoring refactoring = checked.get(i);
				if (i > 0) {
					final Checked recheck = check(step.descriptor(), monitor, new ArrayList<>());
					if (recheck.reason() != null) {
						// Earlier steps are done (and undoable from Edit > Undo); say exactly that.
						return result.put("renamed", "partial").put("reason", recheck.reason()).put("changes", described).toString();
					}
					refactoring = recheck.refactoring();
				}
				change = refactoring.createChange(monitor);
			}
			describe(change, described);
			change.initializeValidationData(monitor);
			final PerformChangeOperation operation = new PerformChangeOperation(change);
			operation.setUndoManager(RefactoringCore.getUndoManager(), change.getName());
			ResourcesPlugin.getWorkspace().run(operation, monitor);
		}
		return result.put("renamed", true).put("changes", described).toString();
	}

	/** A refactoring whose conditions passed, or the reason it can't run. */
	private record Checked(Refactoring refactoring, String reason) {
	}

	/**
	 * Creates the descriptor's refactoring and checks its conditions. Warnings (a name that
	 * shadows another, say) don't stop a rename — the developer's dialog would let them
	 * continue too — but are reported.
	 */
	private static Checked check(RenameJavaElementDescriptor descriptor, NullProgressMonitor monitor, List<String> warnings) throws CoreException {
		final RefactoringStatus status = new RefactoringStatus();
		final Refactoring refactoring = descriptor.createRefactoring(status);
		if (refactoring == null || status.hasFatalError()) {
			return new Checked(null, status.getMessageMatchingSeverity(RefactoringStatus.FATAL));
		}
		final RefactoringStatus check = refactoring.checkAllConditions(monitor);
		if (check.hasError()) {
			return new Checked(null, check.getMessageMatchingSeverity(check.getSeverity()));
		}
		if (check.hasWarning()) {
			warnings.add(check.getMessageMatchingSeverity(RefactoringStatus.WARNING));
		}
		return new Checked(refactoring, null);
	}

	/** A change tree as its leaves: text edits per file, and renames/moves by their names. */
	private static void describe(Change change, List<JsonObject> into) {
		if (change instanceof CompositeChange composite) {
			for (final Change child : composite.getChildren()) {
				describe(child, into);
			}
			return;
		}
		if (change instanceof TextFileChange textFileChange) {
			into.add(new JsonObject().put("file", textFileChange.getFile().getFullPath().toString()).put("edits", editCount(textFileChange)));
			return;
		}
		if (change instanceof TextChange textChange) {
			into.add(new JsonObject().put("change", textChange.getName()).put("edits", editCount(textChange)));
			return;
		}
		into.add(new JsonObject().put("change", change.getName()));
	}

	private static int editCount(TextChange change) {
		final org.eclipse.text.edits.TextEdit edit = change.getEdit();
		if (edit == null) {
			return 0;
		}
		return edit.hasChildren() ? edit.getChildrenSize() : 1;
	}

	static boolean isIdentifier(String name) {
		if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
			return false;
		}
		for (int i = 1; i < name.length(); i++) {
			if (!Character.isJavaIdentifierPart(name.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}

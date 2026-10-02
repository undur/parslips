package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameBindingKeyProcessor;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * {@code /find} — go-to-definition and find-references for a component's key, as data: where
 * {@code key} is declared (the method or field a template binding reaches), where the
 * component's own templates use it ({@code $key…} in the HTML, {@code = key…;} in the WOD), and
 * where Java code references it. Replaces grepping for what the editor already resolves — a grep
 * can't tell {@code name} the key from {@code name} anywhere else.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code component} — the component whose key it is; or {@code class} — any class (a
 *       model class, typically), whose key templates reach through keypaths
 *       ({@code $team.playerCount}): then the answer has every template segment, in any
 *       component, that resolves to it.</li>
 *   <li>{@code key} — required; one key of the component (the first segment of a keypath).
 *       For what a longer keypath resolves to, hop by hop, use {@code /keypath}.</li>
 *   <li>{@code project} — optional hint.</li>
 * </ul>
 *
 * <p>Template references are the component's own templates, matching the editor's Find
 * References; who passes values INTO the component is {@code /callers}.
 */
class FindHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		final String className = params.get("class");
		if ((componentName == null || componentName.isEmpty()) && (className == null || className.isEmpty())) {
			return JsonObject.error("missing required parameter 'component' (a component's key) or 'class' (a key of any class, e.g. a model class, as templates reach it through keypaths)");
		}
		String key = params.get("key");
		if (key == null || key.isEmpty()) {
			return JsonObject.missing("key");
		}
		if (key.startsWith("$")) {
			key = key.substring(1);
		}
		if (key.contains(".")) {
			return JsonObject.error("key must be a single key, not a keypath ('" + key + "'); /keypath resolves a keypath hop by hop, and /find?key="
					+ key.substring(0, key.indexOf('.')) + " finds its first key");
		}
		if (className != null && !className.isEmpty()) {
			return findClassKey(className, key, DevServerComponents.projectParam(params), "true".equalsIgnoreCase(params.get("debug")));
		}

		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		if (found == null) {
			return new JsonObject().put("component", componentName).put("key", key).put("found", false)
					.put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}
		final IType type = found.descriptor().getJavaType();
		if (type == null) {
			return new JsonObject().put("component", componentName).put("key", key).put("found", false)
					.put("reason", "component '" + componentName + "' has no Java class, so it declares no keys").toString();
		}

		final BindingValueKeyPath path = new BindingValueKeyPath(key, type, type.getJavaProject(), WodParserCache.getTypeCache());
		final BindingValueKey[] keys = path.getBindingKeys();
		final IMember member = keys.length > 0 ? keys[0].getBindingMember() : null;

		final JsonObject json = new JsonObject()
				.put("component", found.descriptor().getName())
				.put("key", key);
		if (member == null) {
			// Not declared: the template references can still be listed — they are what an
			// agent needs to see before adding the key, or fixing the name.
			json.put("found", false).put("reason", "'" + key + "' is not a key of " + type.getFullyQualifiedName('.'));
		}
		else {
			json.put("found", true)
					.put("declaration", DevServerJava.location(member)
							.put("member", member.getElementName())
							.put("via", DevServerJava.memberKind(member))
							.put("type", DevServerJava.typeName(keys[0])));
		}
		json.put("templates", templateReferences(found, key));
		if (member != null) {
			json.put("java", javaReferences(member));
		}
		return json.toString();
	}

	/**
	 * A key of any class, as templates reach it: the declaration(s), every template keypath
	 * segment (from any component, in the class's project and the projects depending on it)
	 * that resolves to it, and its Java references.
	 */
	private static String findClassKey(String className, String key, String projectHint, boolean debug) throws Exception {
		final DevServerJava.TypeLookup lookup = DevServerJava.findSourceType(className, projectHint);
		final JsonObject json = new JsonObject().put("class", className).put("key", key);
		if (lookup.type() == null) {
			return json.put("found", false).put("reason", lookup.problem()).toString();
		}
		final IType type = lookup.type();
		json.put("class", type.getFullyQualifiedName('.'));
		final java.util.Set<IMember> members = KeypathScan.members(type, key);
		if (members.isEmpty()) {
			return json.put("found", false).put("reason", "'" + key + "' is not a key of " + type.getFullyQualifiedName('.')).toString();
		}
		final List<JsonObject> declarations = new ArrayList<>();
		final List<JsonObject> java = new ArrayList<>();
		for (final IMember member : members) {
			declarations.add(DevServerJava.location(member).put("member", member.getElementName()).put("via", DevServerJava.memberKind(member)));
			java.addAll(javaReferences(member));
		}
		final List<JsonObject> templates = new ArrayList<>();
		final List<JsonObject> trace = debug ? new ArrayList<>() : null;
		for (final KeypathScan.Use use : KeypathScan.uses(TemplateScan.withDependents(type.getJavaProject().getProject()), members, trace)) {
			templates.add(TemplateScan.location(use.file(), use.content(), use.offset()).put("component", use.component()));
		}
		json.put("found", true).put("declarations", declarations).put("templates", templates).put("java", java);
		if (trace != null) {
			json.put("scanned", trace);
		}
		return json.toString();
	}

	/** Uses of the key in the component's own HTML and WOD, as the editor's Find References reports them. */
	private static List<JsonObject> templateReferences(DevServerComponents.Found found, String key) throws Exception {
		final IProject project = found.javaProject().getProject();
		final BuildProperties buildProperties = (BuildProperties) project.getAdapter(BuildProperties.class);
		final String prefix = buildProperties != null ? buildProperties.getInlineBindingPrefix() : "$";
		final List<JsonObject> references = new ArrayList<>();
		final IFile html = found.descriptor().getHtmlFile();
		if (html != null && html.exists()) {
			final String content = RenameComponentProcessor.readFileContent(html);
			TemplateScan.addAll(references, html, content, RenameBindingKeyProcessor.findHtmlKeyOffsets(content, key, prefix));
		}
		final IFile wod = found.descriptor().getWodFile();
		if (wod != null && wod.exists()) {
			final String content = RenameComponentProcessor.readFileContent(wod);
			TemplateScan.addAll(references, wod, content, RenameBindingKeyProcessor.findWodKeyOffsets(content, key));
		}
		return references;
	}

	/**
	 * References to the member from Java source, through JDT's search engine (exact, not
	 * textual). Template matches are reported separately above, so only Java files are kept here.
	 */
	private static List<JsonObject> javaReferences(IMember member) throws Exception {
		final List<JsonObject> references = new ArrayList<>();
		final SearchPattern pattern = SearchPattern.createPattern(member, IJavaSearchConstants.REFERENCES);
		new SearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				SearchEngine.createWorkspaceScope(), new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch match) throws org.eclipse.core.runtime.CoreException {
						if (!(match.getResource() instanceof IFile file) || !"java".equals(file.getFileExtension())) {
							return;
						}
						try {
							references.add(TemplateScan.location(file, RenameComponentProcessor.readFileContent(file), match.getOffset()));
						}
						catch (final java.io.IOException e) {
							// Unreadable: skip, as the template scan does.
						}
					}
				}, null);
		return references;
	}
}

package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.objectstyle.wolips.bindings.api.ApiextJsonRenderer;
import org.objectstyle.wolips.bindings.api.ElementApiResolver;
import org.objectstyle.wolips.bindings.api.ElementApiResolver.ResolvedElementApi;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * {@code /componentApi} — what one of the project's OWN components accepts, so an agent
 * embedding {@code <wo:MyComponent …>} knows its bindings without opening its Java and
 * template. {@code /elementApi} covers framework elements, whose APIs are stable and declared;
 * a project's components churn, and usually declare nothing.
 *
 * <p>The bindings come from the same place attribute completion takes them:
 * <ul>
 *   <li>the component's {@code .apiext} or {@code .api} when it has one
 *       ({@code "source":"apiext"|"api"}, the full interpreted API as {@code /elementApi}
 *       renders it), else</li>
 *   <li>its settable keys — setters and public fields declared in the project's own classes,
 *       the values a parent can push in ({@code "source":"keys"}), each with its type and
 *       declaration. Framework keys inherited from WOComponent/NGComponent are left out: they
 *       are never what an author binds.</li>
 * </ul>
 *
 * <p>Request parameters: {@code component} (required), {@code project} (optional hint).
 */
class ComponentApiHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return JsonObject.missing("component");
		}
		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		if (found == null) {
			return new JsonObject().put("component", componentName).put("found", false)
					.put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}
		return describe(found).toString();
	}

	/** The component's API as JSON. Package-visible: {@code /context} embeds it. */
	static JsonObject describe(DevServerComponents.Found found) throws Exception {
		final IJavaProject javaProject = found.javaProject();
		final IType type = found.descriptor().getJavaType();
		final String name = found.descriptor().getName();

		final JsonObject json = new JsonObject()
				.put("component", name)
				.put("found", true)
				.put("project", found.projectName())
				.put("class", type == null ? null : type.getFullyQualifiedName('.'))
				.put("format", DevServerComponents.formatName(found.descriptor()))
				.put("files", DevServerComponents.filesJson(found.descriptor()));

		// A declared API wins, exactly as it does for validation and completion.
		final ResolvedElementApi resolved = ElementApiResolver.resolve(type, javaProject, name, name);
		if (resolved.exists()) {
			return json.put("source", ElementApiHandler.kind(resolved))
					.raw("api", ApiextJsonRenderer.render(resolved.getModel().getClassName(), resolved.getModel()));
		}
		if (type == null) {
			return json.put("source", "none").put("bindings", List.of())
					.put("note", "a template-only component (no Java class) takes no bindings of its own");
		}
		// A non-synchronizing component's setters aren't bindings: nothing is pushed into them.
		final List<BindingValueKey> keys = BindingReflectionUtils.synchronizesVariablesWithBindings(type) == Boolean.FALSE ? List.of() : settableKeys(javaProject, type);
		final List<JsonObject> bindings = keysJson(keys);
		final java.util.Set<String> listed = new java.util.HashSet<>();
		for (final BindingValueKey key : keys) {
			listed.add(key.getBindingName());
		}
		bindings.addAll(namedBindingsJson(type, listed));
		return json.put("source", "keys").put("bindings", bindings);
	}

	/**
	 * The bindings the component reads by name ({@code valueForBinding("team")} and kin) and
	 * that aren't already listed as keys — the whole API of a non-synchronizing component.
	 */
	private static List<JsonObject> namedBindingsJson(IType type, java.util.Set<String> listed) {
		final String source = org.objectstyle.wolips.bindings.utils.BindingNameLiterals.sourceOf(type);
		final List<JsonObject> named = new ArrayList<>();
		for (final org.objectstyle.wolips.bindings.utils.BindingNameLiterals.Occurrence occurrence : org.objectstyle.wolips.bindings.utils.BindingNameLiterals.find(source)) {
			if (!listed.add(occurrence.name())) {
				continue;
			}
			named.add(new JsonObject()
					.put("name", occurrence.name())
					.put("declaredIn", type.getFullyQualifiedName('.'))
					.put("via", "valueForBinding")
					.put("at", new JsonObject().put("file", type.getResource() == null ? null : type.getResource().getFullPath().toString()).put("line", lineAt(source, occurrence.offset()))));
		}
		return named;
	}

	private static int lineAt(String source, int offset) {
		int line = 1;
		for (int i = 0; i < offset; i++) {
			if (source.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/**
	 * The keys a parent can set: mutators and fields, as completion offers them, minus system
	 * bindings and anything declared outside the workspace (framework base classes). One per
	 * name. Package-visible: {@code /context} lists a used component's keys from it.
	 */
	static List<BindingValueKey> settableKeys(IJavaProject javaProject, IType type) throws Exception {
		final List<BindingValueKey> keys = BindingReflectionUtils.getBindingKeys(javaProject, type, "", false,
				BindingReflectionUtils.MUTATORS_ONLY, false, WodParserCache.getTypeCache());
		final List<BindingValueKey> settable = new ArrayList<>();
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (final BindingValueKey key : keys) {
			if (BindingReflectionUtils.isSystemBindingValueKey(key, false)) {
				continue;
			}
			if (key.getBindingMember() == null || key.getBindingMember().isBinary()) {
				continue;
			}
			if (seen.add(key.getBindingName())) {
				settable.add(key);
			}
		}
		settable.sort(java.util.Comparator.comparing(BindingValueKey::getBindingName));
		return settable;
	}

	private static List<JsonObject> keysJson(List<BindingValueKey> keys) {
		final List<JsonObject> bindings = new ArrayList<>();
		for (final BindingValueKey key : keys) {
			bindings.add(new JsonObject()
					.put("name", key.getBindingName())
					.put("type", DevServerJava.typeName(key))
					.put("declaredIn", key.getDeclaringType() == null ? null : key.getDeclaringType().getFullyQualifiedName('.'))
					.put("via", DevServerJava.memberKind(key.getBindingMember()))
					.put("at", DevServerJava.location(key.getBindingMember())));
		}
		return bindings;
	}
}

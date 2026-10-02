package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.IJavaProject;
import org.objectstyle.wolips.bindings.api.ElementCatalog;

/**
 * {@code /elementRegistry} — every element a project can use, as data: the Element Reference
 * view's roster. Where {@code /elementApi} answers "what does this element take?", this answers
 * "what elements are there?" — with each one's tags (the shortcuts and aliases that resolve to
 * it), where it comes from, whether it has a definition, whether it's deprecated, and what
 * replaces it in this project (WOString → ERXWOString under ERExtensions' aliases).
 *
 * <p>Project-scoped and alias-aware, because the answer differs per project: the classpath
 * decides which elements exist, and the project's tag aliases decide what a name means.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code project} — required.</li>
 *   <li>{@code filter} — optional; keeps elements whose name or any of whose tags contains
 *       this text, ignoring case.</li>
 * </ul>
 *
 * <p>Enumeration reads every element's definition (jar I/O), so a big classpath takes a few
 * seconds; the Element Reference view does the same work off the UI thread.
 */
class ElementRegistryHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String projectName = DevServerComponents.projectParam(params);
		if (projectName == null || projectName.isEmpty()) {
			return JsonObject.missing("project");
		}
		final String refusal = DevServerComponents.projectRefusal(projectName,
				new JsonObject().put("project", projectName).put("count", 0).put("elements", List.of()));
		if (refusal != null) {
			return refusal;
		}
		final IJavaProject javaProject = DevServerComponents.javaProject(projectName);
		final String filter = params.get("filter") == null ? null : params.get("filter").toLowerCase(Locale.ROOT);

		final List<JsonObject> elements = new ArrayList<>();
		for (final ElementCatalog.Entry entry : ElementCatalog.forProject(javaProject)) {
			if (filter != null && !matches(entry, filter)) {
				continue;
			}
			elements.add(toJson(entry));
		}

		final JsonObject response = new JsonObject().put("project", projectName).put("count", elements.size());
		if (filter != null) {
			response.put("filter", params.get("filter"));
		}
		return response.put("elements", elements).toString();
	}

	private static boolean matches(ElementCatalog.Entry entry, String filter) {
		if (entry.getSimpleName().toLowerCase(Locale.ROOT).contains(filter)) {
			return true;
		}
		for (final String tag : entry.getTags()) {
			if (tag.toLowerCase(Locale.ROOT).contains(filter)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * One element. {@code definition} is {@code apiext}, {@code api} (legacy) or {@code none} —
	 * with {@code none}, the editor knows the element exists but nothing about its bindings.
	 */
	static JsonObject toJson(ElementCatalog.Entry entry) {
		final JsonObject json = new JsonObject()
				.put("name", entry.getSimpleName())
				.put("class", entry.getQualifiedName());
		if (!entry.getTags().isEmpty()) {
			json.put("tags", entry.getTags());
		}
		json.putIfPresent("origin", entry.getOrigin());
		json.put("definition", entry.getDefinitionKind().name().toLowerCase(Locale.ROOT));
		if (entry.isDeprecated()) {
			json.put("deprecated", true);
		}
		json.putIfPresent("overriddenBy", entry.getOverriddenBy());
		return json;
	}
}

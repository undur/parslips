package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * {@code /callers} — which templates use a component: every {@code <wo:Name>} tag and every
 * {@code X : Name {…}} WOD declaration, with file, line and the source line. The question before
 * changing a component's bindings ("who passes what?") and before renaming or deleting it.
 *
 * <p>Scans the component's project and every project that depends on it, as the component
 * editor's Usages tab does (the same matching); the component's own files are skipped.
 *
 * <p>Request parameters: {@code component} (required), {@code project} (optional hint).
 */
class CallersHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return JsonObject.missing("component");
		}
		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		if (found == null) {
			return new JsonObject().put("component", componentName).put("count", 0).put("callers", List.of())
					.put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}
		return callers(found).toString();
	}

	/** The component's callers as JSON. Package-visible: {@code /context} embeds it. */
	static JsonObject callers(DevServerComponents.Found found) throws Exception {
		final String name = found.descriptor().getName();
		final List<JsonObject> usages = new ArrayList<>();
		final Set<String> callingComponents = new LinkedHashSet<>();
		TemplateScan.templates(TemplateScan.withDependents(found.javaProject().getProject()), (file, content, isWod) -> {
			final String owner = TemplateScan.owner(file);
			if (name.equals(owner)) {
				return;
			}
			final List<int[]> offsets = isWod
					? RenameComponentProcessor.findWodElementOffsets(content, name)
					: RenameComponentProcessor.findHtmlElementOffsets(content, name);
			for (final int[] offset : offsets) {
				usages.add(TemplateScan.location(file, content, offset[0]).put("component", owner));
				callingComponents.add(owner);
			}
		});
		return new JsonObject()
				.put("component", name)
				.put("project", found.projectName())
				.put("count", usages.size())
				.put("components", new ArrayList<>(callingComponents))
				.put("callers", usages);
	}
}

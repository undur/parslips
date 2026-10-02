package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.objectstyle.wolips.bindings.api.ApiextModel;
import org.objectstyle.wolips.bindings.api.ElementApiResolver.ResolvedElementApi;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.variables.TemplateRuntime;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * {@code /context} — everything an agent loads into its head before editing a component, in
 * one call: what a developer gets by opening the {@code .wo} bundle. Its files and API
 * ({@code /componentApi}), its current problems ({@code /validate}), who uses it
 * ({@code /callers}), and the elements its template uses — each resolved as the template
 * resolves it, with its binding names and which are required, so writing a tag needs no
 * further lookup. ({@code /elementApi} has the full API of any of them.)
 *
 * <p>Request parameters: {@code component} (required), {@code project} (optional hint).
 */
class ContextHandler implements DevServerHandler {

	/** {@code <wo:Name} in a template — an element used inline. */
	static final Pattern INLINE_TAG = Pattern.compile("<wo:([A-Za-z_][\\w.]*)");

	/** {@code Name : Type {…}} in a .wod file — an element declared for a {@code <webobject>}. */
	static final Pattern WOD_DECLARATION = Pattern.compile("(?m)^\\s*[\\w.]+\\s*:\\s*([A-Za-z_][\\w.]*)\\s*\\{");

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

		final JsonObject context = ComponentApiHandler.describe(found);
		context.raw("validation", new ValidateComponentHandler().handle(Map.of("component", found.descriptor().getName(), "project", found.projectName())));
		context.put("uses", usedElements(found));
		context.put("usedBy", CallersHandler.callers(found));
		return context.toString();
	}

	/**
	 * The elements the template uses, in order of first use: the name as written, what it
	 * resolves to, its definition kind, and its bindings (name, plus {@code required}).
	 */
	private static List<JsonObject> usedElements(DevServerComponents.Found found) throws Exception {
		final Set<String> names = new LinkedHashSet<>();
		collect(found.descriptor().getHtmlFile(), INLINE_TAG, names);
		collect(found.descriptor().getWodFile(), WOD_DECLARATION, names);

		// Resolve as THIS template resolves names: in a hybrid project an ng template's tags
		// name ng elements even though the project is a WO one.
		final ParsleyProject parsleyProject = (ParsleyProject) found.javaProject().getProject().getAdapter(ParsleyProject.class);
		final TemplateRuntime runtime = parsleyProject == null ? null : TemplateRuntime.of(parsleyProject, found.descriptor().getHtmlFile());

		final List<JsonObject> elements = new ArrayList<>();
		for (final String name : names) {
			final ResolvedElementApi resolved = ElementApiHandler.resolveApi(name, found.javaProject(), runtime);
			final JsonObject element = new JsonObject().put("name", name);
			if (!resolved.exists()) {
				// One of the project's own components, declaring nothing: its settable keys are
				// what it takes, as /componentApi reports (and attribute completion offers).
				final DevServerComponents.Found component = DevServerComponents.find(name, found.projectName());
				if (component != null && component.descriptor().getJavaType() != null) {
					final org.eclipse.jdt.core.IType type = component.descriptor().getJavaType();
					final java.util.Set<String> keys = new java.util.LinkedHashSet<>();
					if (org.objectstyle.wolips.bindings.utils.BindingReflectionUtils.synchronizesVariablesWithBindings(type) != Boolean.FALSE) {
						for (final org.objectstyle.wolips.bindings.wod.BindingValueKey key : ComponentApiHandler.settableKeys(component.javaProject(), type)) {
							keys.add(key.getBindingName());
						}
					}
					// What it reads by name: a non-synchronizing component's whole API.
					keys.addAll(org.objectstyle.wolips.bindings.utils.BindingNameLiterals.names(type));
					elements.add(element.put("resolved", type.getFullyQualifiedName('.'))
							.put("definition", "keys").put("bindings", new ArrayList<>(keys)));
					continue;
				}
				elements.add(element.put("definition", "none"));
				continue;
			}
			final ApiextModel model = resolved.getModel();
			element.put("resolved", model.getClassName()).put("definition", ElementApiHandler.kind(resolved));
			final List<String> bindings = new ArrayList<>();
			final List<String> required = new ArrayList<>();
			for (final ApiextModel.Binding binding : model.getBindings()) {
				bindings.add(binding.getName());
				if (binding.isRequired()) {
					required.add(binding.getName());
				}
			}
			element.put("bindings", bindings);
			if (!required.isEmpty()) {
				element.put("required", required);
			}
			elements.add(element);
		}
		return elements;
	}

	private static void collect(IFile file, Pattern pattern, Set<String> into) throws Exception {
		if (file == null || !file.exists()) {
			return;
		}
		final Matcher matcher = pattern.matcher(RenameComponentProcessor.readFileContent(file));
		while (matcher.find()) {
			into.add(matcher.group(1));
		}
	}
}

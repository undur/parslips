package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiextJsonRenderer;
import org.objectstyle.wolips.bindings.api.ApiextModel;
import org.objectstyle.wolips.bindings.api.ApiextSerializer;
import org.objectstyle.wolips.bindings.api.ElementApiResolver;
import org.objectstyle.wolips.bindings.api.ElementApiResolver.ResolvedElementApi;
import org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;

/**
 * Returns the resolved binding API of one or more elements, in the context of a project — so a
 * tool can read a tag's real API (its bindings, their types and directions, its cross-binding
 * constraints, its content/unknown-attribute policies) over HTTP, instead of reading the element's
 * Java source to work it out. This is the hover / Element Reference, as data.
 *
 * <p>Request parameters:
 * <ul>
 *   <li><b>element</b> — required; one element tag name, or a comma-separated list. Names are
 *       resolved the same way a template resolves them: through the project's Parsley tag aliases
 *       (so {@code str} resolves to {@code WOString} to {@code ERXWOString}), then to the element's
 *       Java type, then to its {@code .apiext} (project or bundled), falling back to legacy
 *       {@code .api}.</li>
 *   <li><b>project</b> (or <b>app</b>) — optional project-name hint. Without it, the first open
 *       project in which the element resolves wins — fine for the common single-workspace case.</li>
 *   <li><b>raw</b> — when {@code true}, returns the canonical {@code .apiext} XML for each element
 *       instead of the interpreted JSON (only for elements that actually have an {@code .apiext};
 *       a legacy-{@code .api}-only or undefined element reports {@code raw:null}).</li>
 * </ul>
 *
 * <p>Response — always a JSON object keyed by requested name:
 *
 * <pre>
 *   {"elements":[
 *     {"requested":"str","resolved":"WOString","kind":"apiext","api":{…interpreted…}},
 *     {"requested":"Nonesuch","resolved":"Nonesuch","kind":"none","api":null}
 *   ]}
 * </pre>
 *
 * Runs on the dev-server request thread; JDT model reads take their own locks — no UI-thread
 * dispatch needed (the same rationale as {@code /validate}).
 */
class ElementApiHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String elementParam = params.get("element");
		if (elementParam == null || elementParam.isEmpty()) {
			return "{\"error\":\"missing required parameter 'element' (an element name, or a comma-separated list)\"}";
		}

		final String projectHint = params.get("project") != null ? params.get("project") : params.get("app");
		final boolean raw = "true".equalsIgnoreCase(params.get("raw"));

		final IJavaProject hinted = resolveProject(projectHint);

		final StringBuilder b = new StringBuilder(1024);
		b.append("{\"elements\":[");
		boolean first = true;
		for (final String rawName : elementParam.split(",")) {
			final String name = rawName.trim();
			if (name.isEmpty()) {
				continue;
			}
			if (!first) {
				b.append(',');
			}
			first = false;
			appendElement(b, name, hinted, raw);
		}
		b.append("]}");
		return b.toString();
	}

	/**
	 * Resolves one element name in the given project (or, if that's null, across every open project
	 * until one yields a definition) and appends its {@code {requested, resolved, kind, api}} object.
	 */
	private static void appendElement(StringBuilder b, String name, IJavaProject hinted, boolean raw) {
		try {
			if (hinted != null) {
				appendResolved(b, name, hinted, raw);
				return;
			}
			for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (!project.isOpen()) {
					continue;
				}
				final IJavaProject jp = JavaCore.create(project);
				if (jp != null && jp.exists()) {
					final ResolvedElementApi resolved = resolveApi(name, jp);
					if (resolved.exists()) {
						appendObject(b, name, resolved, raw);
						return;
					}
				}
			}
			// Nothing resolved in any project.
			appendMissing(b, name);
		}
		catch (final Exception e) {
			b.append("{\"requested\":").append(DevServerJson.str(name))
					.append(",\"kind\":\"error\",\"error\":").append(DevServerJson.str(e.getMessage())).append('}');
		}
	}

	private static void appendResolved(StringBuilder b, String name, IJavaProject project, boolean raw) {
		final ResolvedElementApi resolved = resolveApi(name, project);
		if (resolved.exists()) {
			appendObject(b, name, resolved, raw);
		}
		else {
			appendMissing(b, name);
		}
	}

	/**
	 * Resolves a tag name to its element API the way a template does — but layering BOTH name
	 * mechanisms so a lookup finds what an author would type. A tag name can be:
	 * <ul>
	 *   <li>a Parsley tag alias ({@code parsley-tag-aliases.properties}: {@code str} -> {@code WOString}), or</li>
	 *   <li>a legacy tag shortcut ({@code ApiCache.getTagShortcuts()}: {@code link} -> {@code WOHyperlink}), or</li>
	 *   <li>the element's own (simple or qualified) class name.</li>
	 * </ul>
	 * The template validator picks ONE mechanism per project (aliases when active, else shortcuts);
	 * here we try aliases first and fall back to shortcuts when they didn't resolve the name, so
	 * {@code str}/{@code link}/{@code textfield} resolve even in an alias-using project. This is the
	 * more forgiving behavior a lookup wants — you type the tag you see, and get its API.
	 */
	private static ResolvedElementApi resolveApi(String name, IJavaProject project) {
		String resolvedName = name;

		// 1) Parsley tag aliases, when the project declares them.
		if (ParsleyTagAliasResolver.isActiveFor(project)) {
			resolvedName = ParsleyTagAliasResolver.resolveForBindings(project, name);
		}

		// 2) Legacy tag shortcut — applied when aliases left the name unchanged (so the classic
		//    shortcuts still resolve in an alias-using project, and are the only path in one without).
		if (resolvedName.equals(name)) {
			for (final TagShortcut shortcut : ApiCache.getTagShortcuts()) {
				if (name.equalsIgnoreCase(shortcut.getShortcut())) {
					resolvedName = shortcut.getActual();
					break;
				}
			}
		}

		IType type = null;
		try {
			type = BindingReflectionUtils.findElementType(project, resolvedName, false, new TypeCache());
		}
		catch (final Exception e) {
			// No type — resolution can still succeed via a bundled/global .apiext or .api by name.
		}

		return ElementApiResolver.resolve(type, project, resolvedName, resolvedName);
	}

	private static void appendObject(StringBuilder b, String requested, ResolvedElementApi resolved, boolean raw) {
		final ApiextModel model = resolved.getModel();
		b.append("{\"requested\":").append(DevServerJson.str(requested));
		b.append(",\"resolved\":").append(DevServerJson.str(model.getClassName()));
		b.append(",\"kind\":").append(DevServerJson.str(kind(resolved)));
		if (raw) {
			// Only a real .apiext has canonical XML; adapted legacy .api does not round-trip to it.
			final String xml = resolved.isApiext() ? ApiextSerializer.serialize(model) : null;
			b.append(",\"raw\":").append(DevServerJson.str(xml));
		}
		else {
			b.append(",\"api\":").append(ApiextJsonRenderer.render(model.getClassName(), model));
		}
		b.append('}');
	}

	private static void appendMissing(StringBuilder b, String name) {
		b.append("{\"requested\":").append(DevServerJson.str(name))
				.append(",\"resolved\":").append(DevServerJson.str(name))
				.append(",\"kind\":\"none\",\"api\":null}");
	}

	private static String kind(ResolvedElementApi resolved) {
		switch (resolved.getKind()) {
		case APIEXT:
			return "apiext";
		case LEGACY_API:
			return "api";
		case NONE:
		default:
			return "none";
		}
	}

	private static IJavaProject resolveProject(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (project != null && project.isOpen()) {
			final IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null && javaProject.exists()) {
				return javaProject;
			}
		}
		return null;
	}
}

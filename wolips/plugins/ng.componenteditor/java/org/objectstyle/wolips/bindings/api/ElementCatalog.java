package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.SearchPattern;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;

/**
 * Enumerates every element available to a project — the same element types the
 * {@code <wo:…>} completion offers — and resolves each to a {@link Entry} carrying its
 * name, originating framework, and which kind of definition it has ({@code .apiext},
 * {@code .api}, or none).
 *
 * <p>This backs the element-help view, which doubles as a coverage dashboard for the
 * {@code .api} &rarr; {@code .apiext} migration: at a glance you can see which elements
 * have been given a rich definition and which still rely on the legacy one (or have none).
 *
 * <p>The catalog also renders the detail card for a selected entry, reusing the exact same
 * lookup precedence as the editor hover (project {@code .apiext} &rarr; bundled
 * {@code .apiext} &rarr; project {@code .api} &rarr; global {@code WebObjectDefinitions.xml}
 * &rarr; no API) so the view and the hover always agree.
 */
public final class ElementCatalog {

	/** Which kind of API definition an element has. Drives the list's visual distinction. */
	public enum DefinitionKind {
		/** A rich {@code .apiext} definition (migrated). */
		APIEXT,
		/** A classic {@code .api} / {@code WebObjectDefinitions.xml} definition only (not yet migrated). */
		API,
		/** No definition at all (the list grays these out). */
		NONE
	}

	/** One element in the catalog: its names, resolved type, origin, and definition kind. */
	public static final class Entry {
		private final String _simpleName;
		private final String _qualifiedName;
		private final IType _type;
		private final String _origin;
		private final DefinitionKind _definitionKind;
		private final List<String> _tags;
		private final String _overriddenBy;
		private final boolean _deprecated;

		Entry(String simpleName, String qualifiedName, IType type, String origin, DefinitionKind definitionKind, List<String> tags, String overriddenBy, boolean deprecated) {
			_simpleName = simpleName;
			_qualifiedName = qualifiedName;
			_type = type;
			_origin = origin;
			_definitionKind = definitionKind;
			_tags = java.util.Collections.unmodifiableList(tags);
			_overriddenBy = overriddenBy;
			_deprecated = deprecated;
		}

		/** Short element name (e.g. "WOString"). */
		public String getSimpleName() {
			return _simpleName;
		}

		/**
		 * The element this one is overridden/replaced by in the current project (e.g. WOString
		 * is overridden by ERXWOString when ERExtensions' aliases are active), or null if this
		 * element is not replaced. Derived from the project's Parsley tag aliases.
		 */
		public String getOverriddenBy() {
			return _overriddenBy;
		}

		/** True if this element is replaced by another in the current project. */
		public boolean isOverridden() {
			return _overriddenBy != null;
		}

		/**
		 * Tag shortcuts that resolve to this element (e.g. "repetition" for WORepetition),
		 * excluding the element's own class name; sorted, never null, may be empty.
		 */
		public List<String> getTags() {
			return _tags;
		}

		/** Fully-qualified class name. */
		public String getQualifiedName() {
			return _qualifiedName;
		}

		/** The resolved element type. */
		public IType getType() {
			return _type;
		}

		/** Originating framework/bundle/project name, or null if unknown. */
		public String getOrigin() {
			return _origin;
		}

		/** Whether this element has an {@code .apiext}, {@code .api}, or no definition. */
		public DefinitionKind getDefinitionKind() {
			return _definitionKind;
		}

		/** True if this element has any API definition (i.e. not {@link DefinitionKind#NONE}). */
		public boolean hasDefinition() {
			return _definitionKind != DefinitionKind.NONE;
		}

		/**
		 * True if the element's definition marks it {@code <deprecated>} at the element level (#5) — the
		 * Element Reference strikes such entries through. Resolved during enumeration alongside the
		 * definition kind, so reading it here is free.
		 */
		public boolean isDeprecated() {
			return _deprecated;
		}
	}

	private ElementCatalog() {
	}

	/**
	 * Enumerates all elements available to {@code project}, sorted by simple name
	 * (case-insensitive). Returns an empty list if the project is null or enumeration fails.
	 */
	public static List<Entry> forProject(IJavaProject project) {
		final List<Entry> entries = new ArrayList<>();
		if (project == null) {
			return entries;
		}
		try {
			// Reverse-map element class name -> tag shortcuts that resolve to it (e.g.
			// "repetition" -> WORepetition). Keyed by simple class name; computed once.
			final java.util.Map<String, List<String>> shortcutsByElement = shortcutsByElement(project);

			// Same enumeration the <wo:…> completion uses: every element type on the classpath.
			final TypeNameCollector collector = new TypeNameCollector(project, false);
			BindingReflectionUtils.findMatchingElementClassNames("", SearchPattern.R_PREFIX_MATCH, collector, null);

			final boolean aliasesActive = ParsleyTagAliasResolver.isActiveFor(project);
			for (final String qualifiedName : collector.getTypeNames()) {
				final IType type = collector.getTypeForClassName(qualifiedName);
				final String simpleName = BindingReflectionUtils.getShortClassName(qualifiedName);
				final String origin = ApiUtils.resolveOrigin(type);
				final ResolvedDefinition def = resolveDefinition(type, simpleName, project);
				final DefinitionKind kind = def.kind;
				final List<String> tags = shortcutsByElement.getOrDefault(simpleName, java.util.Collections.emptyList());
				// Is this element replaced by another in this project? Resolve its own name
				// through the aliases; if it resolves to a different element, that's the override.
				String overriddenBy = null;
				if (aliasesActive) {
					final String resolved = ParsleyTagAliasResolver.resolve(project, simpleName);
					final int dot = resolved.lastIndexOf('.');
					final String resolvedSimple = dot >= 0 ? resolved.substring(dot + 1) : resolved;
					if (!resolvedSimple.equals(simpleName)) {
						overriddenBy = resolvedSimple;
					}
				}
				entries.add(new Entry(simpleName, qualifiedName, type, origin, kind, tags, overriddenBy, def.deprecated));
			}
		} catch (Throwable t) {
			// Best-effort enumeration — return whatever we gathered.
		}
		entries.sort(Comparator.comparing(e -> e.getSimpleName().toLowerCase()));
		return entries;
	}

	/**
	 * Builds a map from element simple class name to the tag shortcuts that resolve to it
	 * (e.g. {@code "WORepetition" -> ["repetition"]}), excluding any shortcut equal to the
	 * element name itself. Shortcuts per element are sorted. Project-wide (shortcuts are a
	 * global preference), so it's computed once per enumeration.
	 */
	private static java.util.Map<String, List<String>> shortcutsByElement(IJavaProject project) {
		final java.util.Map<String, List<String>> map = new java.util.HashMap<>();
		try {
			if (ParsleyTagAliasResolver.isActiveFor(project)) {
				// New mechanism: each alias key resolves (recursively) to a final element; the
				// tag belongs on that resolved element's row. Skip keys that ARE element names
				// (the replacement entries like WOString -> ERXWOString) so only the friendly
				// shortcuts (str, foreach, …) show as tags.
				for (final String alias : ParsleyTagAliasResolver.aliasMap(project).keySet()) {
					final String resolved = ParsleyTagAliasResolver.resolve(project, alias);
					final int dot = resolved.lastIndexOf('.');
					final String element = dot >= 0 ? resolved.substring(dot + 1) : resolved;
					if (alias.equalsIgnoreCase(element)) {
						continue;
					}
					map.computeIfAbsent(element, k -> new ArrayList<>()).add(alias);
				}
			}
			else {
				// Legacy shortcuts, restricted to the ones whose class exists in an ng project.
				final org.objectstyle.wolips.variables.ParsleyProject parsleyProject = (org.objectstyle.wolips.variables.ParsleyProject) project.getProject().getAdapter(org.objectstyle.wolips.variables.ParsleyProject.class);
				for (final TagShortcut shortcut : TagShortcut.applicableTo(project, parsleyProject, new org.objectstyle.wolips.bindings.wod.TypeCache())) {
					final String name = shortcut.getShortcut();
					final String actual = shortcut.getActual(parsleyProject);
					if (name == null || actual == null) {
						continue;
					}
					final int dot = actual.lastIndexOf('.');
					final String element = dot >= 0 ? actual.substring(dot + 1) : actual;
					if (name.equalsIgnoreCase(element)) {
						continue; // skip a "shortcut" that is just the element name
					}
					map.computeIfAbsent(element, k -> new ArrayList<>()).add(name);
				}
			}
			for (final List<String> tags : map.values()) {
				tags.sort(String.CASE_INSENSITIVE_ORDER);
			}
		} catch (Throwable t) {
			// Best-effort — no tags column if shortcuts can't be read.
		}
		return map;
	}

	/**
	 * Determines which kind of definition an element has, via the shared {@link ElementApiResolver}
	 * seam (a project/bundled {@code .apiext} wins per-element, else any {@code .api}/global
	 * definition, else none) — so the badge always agrees with what the hover/card renders.
	 */
	/** The definition kind plus element-level deprecation, resolved together in one pass. */
	private static final class ResolvedDefinition {
		final DefinitionKind kind;
		final boolean deprecated;

		ResolvedDefinition(DefinitionKind kind, boolean deprecated) {
			this.kind = kind;
			this.deprecated = deprecated;
		}
	}

	/**
	 * Resolves an element's definition kind AND whether it is marked {@code <deprecated>} in a SINGLE
	 * {@link ElementApiResolver#resolve} call. Deprecation piggybacks on the resolution already done for
	 * the kind — no second (I/O-bearing) lookup — which matters because this runs once per element during
	 * enumeration and a duplicate resolve per row was what previously beach-balled the UI thread.
	 */
	private static ResolvedDefinition resolveDefinition(IType type, String simpleName, IJavaProject project) {
		// Primary lookup by FQN (findGlobal* strips to the simple name internally), simpleName as the
		// alternate — matching the historical resolution exactly.
		final String fqn = type != null ? type.getFullyQualifiedName() : simpleName;
		final ElementApiResolver.ResolvedElementApi resolved =
				ElementApiResolver.resolve(type, fqn, simpleName, simpleName, new TypeCache().getApiCache(project));
		final boolean deprecated = resolved.exists() && resolved.getModel() != null && resolved.getModel().isDeprecated();
		final DefinitionKind kind;
		switch (resolved.getKind()) {
		case APIEXT:
			kind = DefinitionKind.APIEXT;
			break;
		case LEGACY_API:
			kind = DefinitionKind.API;
			break;
		case NONE:
		default:
			kind = DefinitionKind.NONE;
			break;
		}
		return new ResolvedDefinition(kind, deprecated);
	}

	/**
	 * Renders the detail card (body-fragment HTML) for an entry, using the same lookup
	 * precedence and renderer as the editor hover, with the origin attached. For an entry
	 * with no definition, renders the tidy "no API" card.
	 */
	public static String renderCardBody(Entry entry, IJavaProject project) {
		if (entry == null) {
			return "";
		}
		final IType type = entry.getType();
		final String displayName = entry.getSimpleName();
		final String origin = entry.getOrigin();
		final String overriddenBy = entry.getOverriddenBy(); // orange "overridden by X" banner, or null

		try {
			// One resolution through the shared seam (apiext-wins per element); render whatever
			// model it yields (parsed .apiext, or a legacy .api adapted into an ApiextModel).
			final String fqn = type != null ? type.getFullyQualifiedName() : displayName;
			final ElementApiResolver.ResolvedElementApi resolved =
					ElementApiResolver.resolve(type, fqn, displayName, displayName, new TypeCache().getApiCache(project));
			if (resolved.exists()) {
				// Pass origin to the renderer rather than stamping it on the model: the resolved
				// model may be a shared, cached instance, so mutating it would bleed one project's
				// origin into another's (and race across threads).
				final ApiextModel m = resolved.getModel();
				return ApiextHtmlRenderer.renderBody(displayName, null, overriddenBy, origin, m);
			}
		} catch (Throwable t) {
			// fall through to the no-API card
		}

		return ApiextHtmlRenderer.renderNoApiBody(displayName, origin);
	}
}

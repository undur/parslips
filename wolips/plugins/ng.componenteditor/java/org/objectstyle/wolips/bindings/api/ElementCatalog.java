package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.SearchPattern;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
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

		Entry(String simpleName, String qualifiedName, IType type, String origin, DefinitionKind definitionKind) {
			_simpleName = simpleName;
			_qualifiedName = qualifiedName;
			_type = type;
			_origin = origin;
			_definitionKind = definitionKind;
		}

		/** Short element name (e.g. "WOString"). */
		public String getSimpleName() {
			return _simpleName;
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
			// Same enumeration the <wo:…> completion uses: every element type on the classpath.
			final TypeNameCollector collector = new TypeNameCollector(project, false);
			BindingReflectionUtils.findMatchingElementClassNames("", SearchPattern.R_PREFIX_MATCH, collector, null);

			for (final String qualifiedName : collector.getTypeNames()) {
				final IType type = collector.getTypeForClassName(qualifiedName);
				final String simpleName = BindingReflectionUtils.getShortClassName(qualifiedName);
				final String origin = ApiUtils.resolveOrigin(type);
				final DefinitionKind kind = definitionKindFor(type, simpleName, project);
				entries.add(new Entry(simpleName, qualifiedName, type, origin, kind));
			}
		} catch (Throwable t) {
			// Best-effort enumeration — return whatever we gathered.
		}
		entries.sort(Comparator.comparing(e -> e.getSimpleName().toLowerCase()));
		return entries;
	}

	/**
	 * Determines which kind of definition an element has, in the same precedence the hover
	 * uses for choosing what to render: a project/bundled {@code .apiext} wins, else any
	 * {@code .api}/global definition, else none.
	 */
	private static DefinitionKind definitionKindFor(IType type, String simpleName, IJavaProject project) {
		// .apiext — project sibling, or bundled for built-in elements.
		if (type != null && ApiUtils.findApiextBytes(type) != null) {
			return DefinitionKind.APIEXT;
		}
		if (ApiUtils.findGlobalApiextBytes(type != null ? type.getFullyQualifiedName() : simpleName) != null
				|| ApiUtils.findGlobalApiextBytes(simpleName) != null) {
			return DefinitionKind.APIEXT;
		}

		// .api — project file, or the global WebObjectDefinitions.xml.
		try {
			if (type != null && ApiUtils.findApiSnapshot(type, new TypeCache().getApiCache(project)) != null) {
				return DefinitionKind.API;
			}
		} catch (Throwable t) {
			// fall through to global lookup
		}
		if (ApiUtils.findGlobalApiSnapshotByClassName(simpleName) != null) {
			return DefinitionKind.API;
		}

		return DefinitionKind.NONE;
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

		try {
			// Project / bundled .apiext first.
			if (type != null) {
				final byte[] apiextBytes = ApiUtils.findApiextBytes(type);
				if (apiextBytes != null) {
					final ApiextModel m = ApiextModel.parse(apiextBytes);
					if (m != null) {
						m.setOrigin(origin);
						return ApiextHtmlRenderer.renderBody(displayName, m);
					}
				}
			}
			byte[] globalApiext = ApiUtils.findGlobalApiextBytes(type != null ? type.getFullyQualifiedName() : displayName);
			if (globalApiext == null) {
				globalApiext = ApiUtils.findGlobalApiextBytes(displayName);
			}
			if (globalApiext != null) {
				final ApiextModel m = ApiextModel.parse(globalApiext);
				if (m != null) {
					m.setOrigin(origin);
					return ApiextHtmlRenderer.renderBody(displayName, m);
				}
			}

			// Classic .api / global definition.
			ApiSnapshot api = null;
			if (type != null) {
				try {
					api = ApiUtils.findApiSnapshot(type, new TypeCache().getApiCache(project));
				} catch (Throwable t) {
					// fall through to global lookup
				}
			}
			if (api == null) {
				api = ApiUtils.findGlobalApiSnapshotByClassName(displayName);
			}
			if (api != null) {
				final ApiextModel m = ApiextModel.fromApiSnapshot(displayName, api);
				m.setOrigin(origin);
				return ApiextHtmlRenderer.renderBody(displayName, m);
			}
		} catch (Throwable t) {
			// fall through to the no-API card
		}

		return ApiextHtmlRenderer.renderNoApiBody(displayName, origin);
	}
}

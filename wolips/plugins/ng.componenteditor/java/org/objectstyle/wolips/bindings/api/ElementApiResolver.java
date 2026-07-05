package org.objectstyle.wolips.bindings.api;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.objectstyle.wolips.bindings.wod.TypeCache;

/**
 * The single resolution seam between the two element-API worlds: the successor {@code .apiext} format
 * and the legacy {@code .api} format. <b>Every</b> consumer — hover, Element Reference, and (once
 * constraint validation lands) the template validator and completion — should ask this one method
 * "which API owns this element?" so the precedence rule lives in exactly one place.
 * <p>
 * The rule, per-element and apiext-wins:
 * <ol>
 * <li>If the element has an {@code .apiext} (a project sibling, or a bundled/global one), the
 *     {@code .apiext} world owns it <b>entirely</b> — its {@code .api} (if any) is invisible. This is
 *     the "{@code .apiext} is a successor, not a superset" principle applied at the tooling layer: no
 *     element is ever half in each world.</li>
 * <li>Else, if it has an {@code .api} (project or global), the legacy world owns it — a frozen island
 *     that exists only until the element is migrated.</li>
 * <li>Else, neither — no API definition.</li>
 * </ol>
 * The resolution precedence (project {@code .apiext} → global {@code .apiext} → project {@code .api} →
 * global-by-class-name {@code .api}) mirrors what {@code WodAnnotationHover} historically did inline;
 * this class is the extraction of that logic so it is shared and authoritative.
 */
public final class ElementApiResolver {

	/** Which world owns an element's API. */
	public enum Kind {
		/** An {@code .apiext} definition owns the element; {@code .api} is ignored. */
		APIEXT,
		/** Only a legacy {@code .api} definition exists (the frozen legacy island). */
		LEGACY_API,
		/** No API definition of either kind. */
		NONE
	}

	/**
	 * The resolved API for an element. Always exposes an {@link ApiextModel} for uniform presentation
	 * (a legacy {@code .api} is adapted via {@link ApiextModel#fromApiSnapshot}); {@link #getKind()}
	 * lets validation branch on whether the element is genuinely {@code .apiext}-owned (and therefore
	 * carries typed constraints to evaluate) versus a legacy adaptation (which does not).
	 */
	public static final class ResolvedElementApi {
		private final Kind _kind;
		private final ApiextModel _model; // null only for NONE

		ResolvedElementApi(Kind kind, ApiextModel model) {
			_kind = kind;
			_model = model;
		}

		public Kind getKind() {
			return _kind;
		}

		/** True if an {@code .apiext} owns this element (validation should evaluate its constraints). */
		public boolean isApiext() {
			return _kind == Kind.APIEXT;
		}

		public boolean exists() {
			return _kind != Kind.NONE;
		}

		/**
		 * The presentation model — the parsed {@code .apiext}, or a legacy {@code .api} adapted into an
		 * {@code ApiextModel} (extension fields empty). Null only when {@link #getKind()} is
		 * {@link Kind#NONE}.
		 */
		public ApiextModel getModel() {
			return _model;
		}
	}

	private static final ResolvedElementApi NONE = new ResolvedElementApi(Kind.NONE, null);

	private ElementApiResolver() {
		// static only
	}

	/**
	 * Resolves the owning API for an element, by type.
	 *
	 * @param elementType the resolved element type, or null if it couldn't be resolved
	 * @param primaryName the primary name for global {@code .apiext}/{@code .api}-by-name lookups —
	 *                    typically the alias-resolved element name
	 * @param altName     a secondary name to try for the global lookups (e.g. the original,
	 *                    pre-alias tag name), or null; the historical resolution tried both so a
	 *                    global definition registered under either name is found
	 * @param displayName the name to stamp on an adapted legacy model; never null
	 * @param cache       the API cache for {@code .api} snapshot lookups
	 * @return the resolution; never null (use {@link ResolvedElementApi#exists()} to test for NONE)
	 */
	public static ResolvedElementApi resolve(IType elementType, String primaryName, String altName, String displayName, ApiCache cache) {
		// 1) project .apiext
		if (elementType != null) {
			final byte[] apiextBytes = ApiUtils.findApiextBytes(elementType);
			if (apiextBytes != null) {
				final ApiextModel m = ApiextModel.parse(apiextBytes);
				if (m != null) {
					return new ResolvedElementApi(Kind.APIEXT, m);
				}
			}
		}

		// 2) global/bundled .apiext (primary name, then alt name). Uses the PARSED, CACHED model —
		// this is the validation hot path (every <wo:…> resolves through here on every save), so it
		// must not re-read the bundle entry and re-parse the XML per element.
		ApiextModel global = ApiUtils.findGlobalApiextModel(primaryName);
		if (global == null && altName != null && !altName.equals(primaryName)) {
			global = ApiUtils.findGlobalApiextModel(altName);
		}
		if (global != null) {
			return new ResolvedElementApi(Kind.APIEXT, global);
		}

		// 3) project .api — only reached because no .apiext owns this element
		ApiSnapshot api = null;
		if (elementType != null) {
			try {
				api = ApiUtils.findApiSnapshot(elementType, cache);
			}
			catch (ApiModelException e) {
				// fall through to global lookup
			}
		}
		// 4) global .api by class name (primary name, then alt name)
		if (api == null) {
			api = ApiUtils.findGlobalApiSnapshotByClassName(primaryName);
		}
		if (api == null && altName != null && !altName.equals(primaryName)) {
			api = ApiUtils.findGlobalApiSnapshotByClassName(altName);
		}
		if (api != null) {
			return new ResolvedElementApi(Kind.LEGACY_API, ApiextModel.fromApiSnapshot(displayName, api));
		}

		return NONE;
	}

	/** Convenience overload with a single lookup name (no distinct pre-alias alternate). */
	public static ResolvedElementApi resolve(IType elementType, String lookupName, String displayName, ApiCache cache) {
		return resolve(elementType, lookupName, null, displayName, cache);
	}

	/**
	 * Convenience overload when the caller has a resolved {@link IType} and its {@link IJavaProject}
	 * but not a pre-fetched {@link ApiCache}. Uses a fresh {@link TypeCache} — callers on hot paths
	 * should prefer an overload taking a shared {@link ApiCache}.
	 */
	public static ResolvedElementApi resolve(IType elementType, IJavaProject project, String lookupName, String displayName) {
		final ApiCache cache = project != null ? new TypeCache().getApiCache(project) : null;
		return resolve(elementType, lookupName, null, displayName, cache);
	}
}

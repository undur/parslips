package org.objectstyle.wolips.bindings.wod;

import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.IApiBinding;

/**
 * Represents a binding visible in the inspector UI — either defined in the
 * component's {@code .api} file, or used in the WOD/template but not defined
 * in any API.
 *
 * <p>This replaces the old pattern where {@code IWodBinding} extended
 * {@code IApiBinding}, allowing both types to be mixed into a single
 * {@code IApiBinding[]} array. That inheritance was incorrect: a WOD binding
 * is a <em>usage</em> of an API binding, not a replacement for one.
 *
 * <p>{@code VisibleBinding} is a thin presentation-layer wrapper that provides
 * the information the inspector UI needs:
 * <ul>
 *   <li>{@link #getName()} — the binding name (always available)</li>
 *   <li>{@link #isAction()} — whether this is an action binding</li>
 *   <li>{@link #isDefinedInApi()} — whether the binding comes from a {@code .api}
 *       file (vs. being used in WOD but not defined in any API)</li>
 *   <li>{@link #getApiBinding()} — the underlying API binding definition, if any</li>
 * </ul>
 */
public class VisibleBinding implements Comparable<VisibleBinding> {
	private final String _name;
	private final IApiBinding _apiBinding;
	private final boolean _isAction;

	private VisibleBinding(String name, IApiBinding apiBinding, boolean isAction) {
		_name = name;
		_apiBinding = apiBinding;
		_isAction = isAction;
	}

	/**
	 * Creates a {@code VisibleBinding} from an API binding definition.
	 * The {@link #isAction()} check uses full API metadata (defaults category
	 * and name pattern).
	 */
	public static VisibleBinding fromApi(IApiBinding apiBinding) {
		return new VisibleBinding(apiBinding.getName(), apiBinding, ApiUtils.isActionBinding(apiBinding));
	}

	/**
	 * Creates a {@code VisibleBinding} from a WOD binding that is not defined
	 * in any {@code .api} file. The {@link #isAction()} check uses only the
	 * name pattern heuristic (no API metadata available).
	 */
	public static VisibleBinding fromWod(IWodBinding wodBinding) {
		return new VisibleBinding(wodBinding.getName(), null, ApiUtils.isActionBindingName(wodBinding.getName()));
	}

	/** Returns the binding name. */
	public String getName() {
		return _name;
	}

	/**
	 * Returns true if this is an action binding. For API-defined bindings,
	 * this checks the defaults category; for WOD-only bindings, this uses
	 * the name pattern heuristic.
	 */
	public boolean isAction() {
		return _isAction;
	}

	/**
	 * Returns true if this binding is defined in the component's {@code .api}
	 * file. Returns false for bindings that appear in the WOD/template but
	 * have no API definition.
	 *
	 * <p>The inspector uses this to distinguish "known" bindings from
	 * "extra" bindings added by the user (shown with bold font).
	 */
	public boolean isDefinedInApi() {
		return _apiBinding != null;
	}

	/**
	 * Returns the underlying API binding definition, or {@code null} if this
	 * binding is not defined in any {@code .api} file.
	 *
	 * <p>Currently unused — retained for potential future use.
	 */
	private IApiBinding getApiBinding() {
		return _apiBinding;
	}

	/**
	 * Compares by name for sorted display in the inspector UI.
	 */
	@Override
	public int compareTo(VisibleBinding other) {
		if (other == null) return -1;
		if (_name == null) return -1;
		return _name.compareTo(other._name);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof VisibleBinding)) return false;
		VisibleBinding other = (VisibleBinding) o;
		return _name != null ? _name.equals(other._name) : other._name == null;
	}

	@Override
	public int hashCode() {
		return _name != null ? _name.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "[VisibleBinding: name=" + _name + ", definedInApi=" + isDefinedInApi() + ", isAction=" + _isAction + "]";
	}
}

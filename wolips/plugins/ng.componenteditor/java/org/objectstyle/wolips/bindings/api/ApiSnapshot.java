package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed representation of a {@code .api} file's component definition.
 *
 * <p>On the <b>read path</b> (validation, autocomplete, hover), instances are
 * produced by {@link ApiParser} and treated as effectively immutable — each
 * consumer gets its own snapshot, so no synchronization is needed.
 *
 * <p>On the <b>write path</b> (the {@code .api} editor, {@code GenerateAPIAction}),
 * the same class is used mutably: bindings are added/removed, component content
 * is toggled, and the result is serialized back to XML via {@link ApiSerializer}.
 *
 * <p>The mutation methods ({@link #addBinding}, {@link #removeBinding},
 * {@link #setComponentContent}) rebuild the internal lookup map to keep
 * {@link #getBinding(String)} consistent.
 */
public final class ApiSnapshot {

	private final String _className;
	private boolean _componentContent;

	/** Mutable backing list — returned views are unmodifiable. */
	private final List<IApiBinding> _bindings;

	/** Mutable backing map — rebuilt after structural changes. */
	private Map<String, IApiBinding> _bindingsByName;

	private final List<ApiValidation> _validations;
	private final String _preview;

	/**
	 * Creates a snapshot of a component's API definition.
	 *
	 * @param className the fully-qualified component class name
	 * @param componentContent whether the component accepts child content
	 * @param bindings the component's binding definitions (defensive copy taken)
	 * @param validations the component's validation rules (defensive copy taken)
	 * @param preview pre-serialized preview XML content, or null
	 */
	public ApiSnapshot(String className, boolean componentContent, List<IApiBinding> bindings, List<ApiValidation> validations, String preview) {
		_className = className;
		_componentContent = componentContent;
		_bindings = new ArrayList<>(bindings);
		_validations = new ArrayList<>(validations);
		_preview = preview;
		rebuildNameMap();
	}

	/** Rebuilds the name→binding lookup map from the current binding list. */
	private void rebuildNameMap() {
		Map<String, IApiBinding> byName = new LinkedHashMap<>();
		for (IApiBinding binding : _bindings) {
			if (binding.getName() != null) {
				byName.put(binding.getName(), binding);
			}
		}
		_bindingsByName = byName;
	}

	/** Returns the fully-qualified class name of the component (from {@code <wo class="...">}). */
	public String getClassName() {
		return _className;
	}

	/** Returns true if the component accepts child content ({@code wocomponentcontent="true"}). */
	public boolean isComponentContent() {
		return _componentContent;
	}

	/** Sets whether the component accepts child content. */
	public void setComponentContent(boolean componentContent) {
		_componentContent = componentContent;
	}

	/** Returns the component's binding definitions. The returned list is unmodifiable. */
	public List<IApiBinding> getBindings() {
		return Collections.unmodifiableList(_bindings);
	}

	/**
	 * Returns the binding with the given name, or null if no such binding exists.
	 * Uses O(1) map lookup.
	 */
	public IApiBinding getBinding(String name) {
		return _bindingsByName.get(name);
	}

	/**
	 * Adds a new binding with the given name. If a binding with that name already
	 * exists, returns the existing one without creating a duplicate.
	 *
	 * @param name the binding name
	 * @return the new (or existing) binding
	 */
	public SimpleApiBinding addBinding(String name) {
		IApiBinding existing = _bindingsByName.get(name);
		if (existing instanceof SimpleApiBinding) {
			return (SimpleApiBinding) existing;
		}

		SimpleApiBinding binding = new SimpleApiBinding(name);
		_bindings.add(binding);
		_bindingsByName.put(name, binding);
		return binding;
	}

	/**
	 * Removes the binding with the given name, along with any validation rules
	 * that reference only that binding (single-child unbound/unsettable patterns).
	 *
	 * @param name the binding name to remove
	 */
	public void removeBinding(String name) {
		IApiBinding binding = _bindingsByName.remove(name);
		if (binding != null) {
			_bindings.remove(binding);
			// Remove validation rules that reference only this binding
			// (single-child <unbound>/<unsettable> patterns)
			_validations.removeIf(validation -> {
				List<ApiValidation> children = validation.getChildren();
				if (children.size() == 1) {
					ApiValidation child = children.get(0);
					return name.equals(child.getBindingName());
				}
				return false;
			});
		}
	}

	/**
	 * Notifies the snapshot that a binding's name has changed, so the internal
	 * lookup map can be rebuilt. Call this after modifying a binding's name via
	 * {@link SimpleApiBinding#setName(String)}.
	 */
	public void bindingNameChanged() {
		rebuildNameMap();
	}

	/**
	 * Removes any single-child validation rule that uses the given predicate kind
	 * for the given binding name. Used when toggling required/willSet off to clean
	 * up implicit validation rules (e.g. a single {@code <unbound>} or
	 * {@code <unsettable>} check).
	 *
	 * @param bindingName the binding to remove validations for
	 * @param kind the validation kind to match (e.g. UNBOUND, UNSETTABLE)
	 */
	public void removeImplicitValidation(String bindingName, ApiValidation.Kind kind) {
		_validations.removeIf(validation -> {
			List<ApiValidation> children = validation.getChildren();
			if (children.size() == 1) {
				ApiValidation child = children.get(0);
				return child.getKind() == kind && bindingName.equals(child.getBindingName());
			}
			return false;
		});
	}

	/**
	 * Returns only the bindings that are marked as required.
	 * Convenience filter over {@link #getBindings()}.
	 */
	public List<IApiBinding> getRequiredBindings() {
		List<IApiBinding> required = new ArrayList<>();
		for (IApiBinding binding : _bindings) {
			if (binding.isRequired()) {
				required.add(binding);
			}
		}
		return required;
	}

	/** Returns the component's validation rules. The returned list is unmodifiable. */
	public List<ApiValidation> getValidations() {
		return Collections.unmodifiableList(_validations);
	}

	/**
	 * Evaluates all validation rules against the given bindings map and returns
	 * the validations that failed (i.e. whose condition evaluated to true,
	 * meaning the constraint was violated).
	 *
	 * @param bindings the current binding name → value map for the component instance
	 * @return list of failed validations (never null, may be empty)
	 */
	public List<ApiValidation> getFailedValidations(Map<String, String> bindings) {
		List<ApiValidation> failed = new ArrayList<>();
		for (ApiValidation validation : _validations) {
			if (validation.evaluate(bindings)) {
				failed.add(validation);
			}
		}
		return failed;
	}

	/** Returns the pre-serialized preview content, or null if no preview is defined. */
	public String getPreview() {
		return _preview;
	}
}

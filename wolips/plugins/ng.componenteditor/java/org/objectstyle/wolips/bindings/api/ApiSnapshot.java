package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a parsed {@code .api} file's component definition.
 *
 * <p>This is the read-path replacement for the mutable DOM-backed
 * {@link Wo}/{@link Wodefinitions} classes. All fields are final and all
 * collections are unmodifiable, making instances inherently thread-safe
 * with no synchronization needed.
 *
 * <p>Produced by {@link ApiParser} from XML. The DOM is not retained —
 * all data is extracted at parse time and stored in typed fields.
 *
 * <p>The {@code .api} editor continues to use the DOM-backed classes for
 * mutation and persistence; this class is used everywhere else (validation,
 * autocomplete, hover documentation, binding inspection).
 */
public final class ApiSnapshot {

	private final String _className;
	private final boolean _componentContent;
	private final List<IApiBinding> _bindings;
	private final Map<String, IApiBinding> _bindingsByName;
	private final List<ApiValidation> _validations;
	private final String _preview;

	/**
	 * Creates an immutable snapshot of a component's API definition.
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
		_bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
		_validations = Collections.unmodifiableList(new ArrayList<>(validations));
		_preview = preview;

		// Build the name lookup map, preserving insertion order
		Map<String, IApiBinding> byName = new LinkedHashMap<>();
		for (IApiBinding binding : _bindings) {
			if (binding.getName() != null) {
				byName.put(binding.getName(), binding);
			}
		}
		_bindingsByName = Collections.unmodifiableMap(byName);
	}

	/** Returns the fully-qualified class name of the component (from {@code <wo class="...">}). */
	public String getClassName() {
		return _className;
	}

	/** Returns true if the component accepts child content ({@code wocomponentcontent="true"}). */
	public boolean isComponentContent() {
		return _componentContent;
	}

	/** Returns the component's binding definitions. The returned list is unmodifiable. */
	public List<IApiBinding> getBindings() {
		return _bindings;
	}

	/**
	 * Returns the binding with the given name, or null if no such binding exists.
	 * Uses O(1) map lookup.
	 */
	public IApiBinding getBinding(String name) {
		return _bindingsByName.get(name);
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
		return _validations;
	}

	/**
	 * Evaluates all validation rules against the given bindings map and returns
	 * the validations that failed (i.e. whose condition evaluated to true,
	 * meaning the constraint was violated).
	 *
	 * <p>This replicates the logic of {@link Wo#getFailedValidations(Map)}.
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

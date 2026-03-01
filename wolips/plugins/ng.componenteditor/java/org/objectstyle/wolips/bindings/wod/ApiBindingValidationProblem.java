package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.api.IApiBinding;

/**
 * A WOD validation problem indicating that a required binding is missing.
 *
 * <p>Retains a reference to the {@link IApiBinding} definition since this
 * problem is always created from a real API binding (in
 * {@link AbstractWodElement#fillInProblems}), unlike other binding problems
 * which come from WOD bindings during value validation.
 */
public class ApiBindingValidationProblem extends WodBindingProblem {
	private IApiBinding _binding;

	public ApiBindingValidationProblem(IWodElement element, IApiBinding binding, String className, Position position, int lineNumber, boolean warning) {
		super(element, binding.getName(), "Binding '" + binding.getName() + "' is required for " + className + ".", position, lineNumber, warning);
		_binding = binding;
	}

	/** Returns the API binding definition that is required but missing. */
	public IApiBinding getBinding() {
		return _binding;
	}
}

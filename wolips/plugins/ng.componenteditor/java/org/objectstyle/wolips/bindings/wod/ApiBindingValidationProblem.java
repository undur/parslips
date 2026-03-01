package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.api.IApiBinding;

/**
 * A WOD validation problem indicating that a required binding is missing.
 *
 * <p>Previously stored a DOM-backed {@link org.objectstyle.wolips.bindings.api.Binding}
 * reference; now uses the interface {@link IApiBinding} and a separately
 * provided class name, since the immutable {@link org.objectstyle.wolips.bindings.api.ApiSnapshot}
 * model does not link bindings back to their parent.
 */
public class ApiBindingValidationProblem extends WodBindingProblem {
	private IApiBinding _binding;

	public ApiBindingValidationProblem(IWodElement element, IApiBinding binding, String className, Position position, int lineNumber, boolean warning) {
		super(element, binding, binding.getName(), "Binding '" + binding.getName() + "' is required for " + className + ".", position, lineNumber, warning);
		_binding = binding;
	}

	@Override
	public IApiBinding getBinding() {
		return _binding;
	}
}

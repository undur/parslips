package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;

/**
 * Base class for WOD binding-related validation problems. Tracks the element
 * and binding name involved in the problem.
 *
 * <p>Previously stored an {@code IApiBinding} reference, but it had zero
 * callers — the binding name (available via {@link #getBindingName()}) is
 * all that consumers need. The subclass {@link ApiBindingValidationProblem}
 * retains its own {@code IApiBinding} for cases where the full binding
 * definition is needed.
 */
public abstract class WodBindingProblem extends WodProblem implements IWodElementProblem {
  private IWodElement _element;
  private String _bindingName;

  public WodBindingProblem(IWodElement element, String bindingName, String message, Position position, int lineNumber, boolean warning) {
    super(message, position, lineNumber, warning);
    _element = element;
    _bindingName = bindingName;
  }

  public IWodElement getElement() {
    return _element;
  }

  public String getBindingName() {
    return _bindingName;
  }
}

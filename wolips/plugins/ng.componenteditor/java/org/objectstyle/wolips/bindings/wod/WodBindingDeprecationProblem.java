package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;

/**
 * A WOD validation problem for bindings whose values resolve to deprecated
 * Java members.
 */
public class WodBindingDeprecationProblem extends WodBindingProblem {
  public WodBindingDeprecationProblem(IWodElement element, String bindingName, String message, Position position, int lineNumber, boolean warning) {
    super(element, bindingName, message, position, lineNumber, warning);
  }
}

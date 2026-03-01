package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;

/**
 * A WOD validation problem for binding name issues (e.g. duplicate binding names).
 */
public class WodBindingNameProblem extends WodBindingProblem {
	public WodBindingNameProblem(IWodElement element, String bindingName, String message, Position position, int lineNumber, boolean warning) {
		super(element, bindingName, message, position, lineNumber, warning);
	}
}

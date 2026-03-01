package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;

/**
 * A WOD validation problem for binding value issues (e.g. missing key paths,
 * unverifiable operators, deprecated values).
 */
public class WodBindingValueProblem extends WodBindingProblem {
	public WodBindingValueProblem(IWodElement element, String bindingName, String message, Position position, int lineNumber, boolean warning) {
		super(element, bindingName, message, position, lineNumber, warning);
	}
}

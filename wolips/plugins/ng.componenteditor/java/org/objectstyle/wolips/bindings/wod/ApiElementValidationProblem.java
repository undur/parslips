package org.objectstyle.wolips.bindings.wod;

import org.eclipse.jface.text.Position;
import org.objectstyle.wolips.bindings.api.ApiValidation;

/**
 * A WOD validation problem indicating that a component validation rule failed.
 *
 * <p>Stores an immutable {@link ApiValidation} reference so that callers can
 * query which binding names are affected by the failed validation (e.g. for
 * determining whether a binding's marker should be highlighted).
 */
public class ApiElementValidationProblem extends WodElementProblem {

	/** The validation rule that failed. Used for {@link #isAffectedByBindingNamed(String)} queries. */
	private final ApiValidation _validation;

	/**
	 * Creates a new validation problem.
	 *
	 * @param element the WOD element that failed validation
	 * @param validation the immutable validation rule that failed
	 * @param position the document position for the error marker
	 * @param lineNumber the line number for the error marker
	 * @param warning true if this should be reported as a warning rather than an error
	 */
	public ApiElementValidationProblem(IWodElement element, ApiValidation validation, Position position, int lineNumber, boolean warning) {
		super(element, validation.getMessage(), position, lineNumber, warning);
		_validation = validation;
	}

	/**
	 * Returns the immutable validation rule that failed.
	 */
	public ApiValidation getValidation() {
		return _validation;
	}
}

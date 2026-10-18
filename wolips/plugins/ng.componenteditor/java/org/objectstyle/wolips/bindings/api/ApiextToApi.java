package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.List;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Content;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;

/**
 * The part of an {@code .apiext} that a WebObjects {@code .api} file can carry, as an
 * {@link ApiSnapshot}: what WebObjects-era tools (WOLips, among others) read.
 *
 * <ul>
 *   <li>bindings, with their {@code defaults} preset;</li>
 *   <li>{@code wocomponentcontent}: true unless content is forbidden or undeclared;</li>
 *   <li>validation rules, each describing a <em>violation</em> (an {@code .api} rule fires when all
 *       its conditions hold): a required binding that's unbound; a binding the element pushes to
 *       that's bound to something unsettable; a {@code <requires>} whose antecedent holds while its
 *       consequent doesn't meet the obligation; a {@code <choose>} whose count of satisfied
 *       alternatives is outside its bounds.</li>
 * </ul>
 *
 * Types, docs, deprecation, literal defaults and the unknown-attributes policy have no {@code .api}
 * form and are left out. Rule messages are the sentences the editor shows for the same problems.
 */
public final class ApiextToApi {

	private ApiextToApi() {
	}

	/** The {@code .api} projection of the given {@code .apiext}. */
	public static ApiSnapshot convert(ApiextModel apiext) {
		final List<IApiBinding> bindings = new ArrayList<>();
		final List<ApiValidation> validations = new ArrayList<>();

		for (final Binding binding : apiext.getBindings()) {
			final SimpleApiBinding apiBinding = new SimpleApiBinding(binding.getName());
			if (binding.getDefaults() != null && !binding.getDefaults().isEmpty()) {
				apiBinding.setDefaults(binding.getDefaults());
			}
			bindings.add(apiBinding);

			if (binding.isRequired()) {
				validations.add(rule("'" + binding.getName() + "' is a required binding.", unbound(binding.getName())));
			}
			if (!binding.getPushTypes().isEmpty() && !hasUnconditionalSettable(apiext, binding.getName())) {
				validations.add(rule("'" + binding.getName() + "' must be bound to a settable value.", bound(binding.getName()), leaf(ApiValidation.Kind.UNSETTABLE, binding.getName())));
			}
		}

		for (final Constraint constraint : apiext.getConstraints()) {
			final String message = constraint.getMessage() != null ? constraint.getMessage() : ApiextConstraintMessages.describe(constraint);
			if (constraint instanceof Choose choose) {
				final ApiValidation violation = chooseViolation(choose);
				if (violation != null) {
					validations.add(rule(message, violation));
				}
			}
			else if (constraint instanceof Requires requires) {
				validations.add(rule(message, requiresViolation(requires)));
			}
		}

		final Content content = apiext.getContent();
		final boolean componentContent = content != null && content != Content.FORBIDDEN;
		return new ApiSnapshot(apiext.getClassName(), componentContent, bindings, validations, null);
	}

	/** Whether a {@code <requires must="settable">} without antecedent already covers the binding. */
	private static boolean hasUnconditionalSettable(ApiextModel apiext, String bindingName) {
		for (final Constraint constraint : apiext.getConstraints()) {
			if (constraint instanceof Requires requires && requires.getAntecedent() == null && requires.getMust() == ApiextModel.Obligation.SETTABLE && bindingName.equals(requires.getBinding())) {
				return true;
			}
		}
		return false;
	}

	/** True when fewer than {@code min} or more than {@code max} alternatives are satisfied; null when unbounded. */
	private static ApiValidation chooseViolation(Choose choose) {
		final List<ApiValidation> alternatives = new ArrayList<>();
		for (final Alternative alternative : choose.getAlternatives()) {
			alternatives.add(satisfied(alternative));
		}
		final Integer min = choose.getMin() != null && choose.getMin() > 0 ? choose.getMin() : null;
		final Integer max = choose.getMax();
		if (min != null && min.equals(max)) {
			return count("!=" + min, alternatives);
		}
		if (min != null && max != null) {
			return composite(ApiValidation.Kind.OR, List.of(count("<" + min, alternatives), count(">" + max, alternatives)));
		}
		if (min != null) {
			return count("<" + min, alternatives);
		}
		if (max != null) {
			return count(">" + max, alternatives);
		}
		return null;
	}

	/** The antecedent holds (when there is one) and the consequent fails its obligation. */
	private static ApiValidation requiresViolation(Requires requires) {
		final List<ApiValidation> conditions = new ArrayList<>();
		if (requires.getAntecedent() != null) {
			conditions.add(satisfied(requires.getAntecedent()));
		}
		final String binding = requires.getBinding();
		switch (requires.getMust()) {
			case BOUND -> conditions.add(unbound(binding));
			case SETTABLE -> {
				conditions.add(bound(binding));
				conditions.add(leaf(ApiValidation.Kind.UNSETTABLE, binding));
			}
			case GETTABLE -> {
				conditions.add(bound(binding));
				conditions.add(leaf(ApiValidation.Kind.UNGETTABLE, binding));
			}
		}
		return conditions.size() == 1 ? conditions.get(0) : composite(ApiValidation.Kind.AND, conditions);
	}

	/** An alternative is satisfied when its binding is bound, or any of its {@code <any-of>} bindings. */
	private static ApiValidation satisfied(Alternative alternative) {
		if (!alternative.isAnyOf()) {
			return bound(alternative.getBindingNames().get(0));
		}
		final List<ApiValidation> anyOf = new ArrayList<>();
		for (final String name : alternative.getBindingNames()) {
			anyOf.add(bound(name));
		}
		return composite(ApiValidation.Kind.OR, anyOf);
	}

	private static ApiValidation rule(String message, ApiValidation... conditions) {
		return new ApiValidation(ApiValidation.Kind.VALIDATION, message, null, List.of(conditions));
	}

	private static ApiValidation count(String test, List<ApiValidation> children) {
		return new ApiValidation(ApiValidation.Kind.COUNT, null, test, children);
	}

	private static ApiValidation composite(ApiValidation.Kind kind, List<ApiValidation> children) {
		return new ApiValidation(kind, null, null, children);
	}

	private static ApiValidation bound(String name) {
		return leaf(ApiValidation.Kind.BOUND, name);
	}

	private static ApiValidation unbound(String name) {
		return leaf(ApiValidation.Kind.UNBOUND, name);
	}

	private static ApiValidation leaf(ApiValidation.Kind kind, String name) {
		return new ApiValidation(kind, name);
	}
}

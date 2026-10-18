package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.List;

import org.objectstyle.wolips.bindings.api.ApiextModel.Content;
import org.objectstyle.wolips.bindings.api.ApiextModel.Obligation;
import org.objectstyle.wolips.bindings.api.ApiextGenerator.FoundBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableChoose;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableConstraint;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableRequires;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableType;

/**
 * Starts a component's first {@code .apiext} from its hand-written WebObjects {@code .api}, so
 * "Generate API" carries over what someone wrote there: its bindings with their {@code defaults}
 * presets, which are required and which must be settable, whether it wraps content, and the
 * validation rules that have a typed {@code .apiext} form. The {@code .apiext} is the format; this
 * is a one-way bridge in, used once per component.
 *
 * <p>A rule describes a violation (it fires when all its conditions hold), and translates when it's
 * one of the shapes the {@code .apiext} constraints cover:
 * <ul>
 *   <li>a single {@code <unbound>} or {@code <unsettable>}: the binding is required, or must be
 *       settable (it's pushed to);</li>
 *   <li>an {@code <or>} of {@code <unbound>}s: each of those bindings is required;</li>
 *   <li>a {@code <count>} of alternatives (each a binding, or an {@code <or>} of bindings) against a
 *       bound, or two counts joined by {@code <or>}: a {@code <choose min max>};</li>
 *   <li>several alternatives all bound ("only one of"): a {@code <choose>} allowing all but one;
 *       several bindings all unbound ("one of them must be"): a {@code <choose min="1">};</li>
 *   <li>an antecedent (a binding, or an {@code <or>} of bindings) and a consequent that's
 *       {@code <unbound>}, {@code <unsettable>} or {@code <ungettable>}: a {@code <requires>}.</li>
 * </ul>
 * Anything else has no typed form. Rather than drop a rule someone wrote, nothing is converted
 * when one doesn't translate, and the rule is named.
 */
public final class ApiToApiext {

	private ApiToApiext() {
	}

	/**
	 * Fills a new, empty model from the {@code .api}: its bindings typed from {@code found} (the
	 * component's keys, by reflection), its content policy and its constraints.
	 *
	 * @return the messages (or descriptions) of the rules that don't translate; when not empty, the
	 *         model is left untouched
	 */
	public static List<String> seed(MutableApiextModel model, ApiSnapshot api, List<FoundBinding> found) {
		final List<String> required = new ArrayList<>();
		final List<String> settable = new ArrayList<>();
		final List<MutableConstraint> constraints = new ArrayList<>();
		final List<String> untranslatable = new ArrayList<>();
		for (final ApiValidation validation : api.getValidations()) {
			final List<ApiValidation> conditions = flatten(validation.getChildren());
			if (conditions.size() == 1 && conditions.get(0).getKind() == ApiValidation.Kind.UNBOUND) {
				required.add(conditions.get(0).getBindingName());
				continue;
			}
			if (conditions.size() == 1 && conditions.get(0).getKind() == ApiValidation.Kind.UNSETTABLE) {
				settable.add(conditions.get(0).getBindingName());
				continue;
			}
			final List<String> eachRequired = conditions.size() == 1 ? unboundNames(conditions.get(0)) : null;
			if (eachRequired != null) {
				required.addAll(eachRequired);
				continue;
			}
			final MutableConstraint constraint = conditions.size() == 1 ? choose(conditions.get(0)) : combination(conditions);
			if (constraint == null) {
				untranslatable.add(validation.getMessage() != null && !validation.getMessage().isEmpty() ? validation.getMessage() : "an unnamed rule on " + bindingNames(validation));
				continue;
			}
			if (validation.getMessage() != null && !validation.getMessage().isEmpty()) {
				constraint.message = validation.getMessage();
			}
			constraints.add(constraint);
		}
		if (!untranslatable.isEmpty()) {
			return untranslatable;
		}

		// The declared bindings, then any a rule names without declaring.
		final List<String> names = new ArrayList<>();
		for (final IApiBinding apiBinding : api.getBindings()) {
			names.add(apiBinding.getName());
		}
		for (final String name : concat(required, settable)) {
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		for (final String name : names) {
			final IApiBinding apiBinding = api.getBinding(name);
			final MutableBinding binding = new MutableBinding();
			binding.name = name;
			binding.required = apiBinding != null && apiBinding.isRequired() || required.contains(name);
			binding.defaults = apiBinding != null && apiBinding.getDefaults() != null && !apiBinding.getDefaults().isEmpty() ? apiBinding.getDefaults() : null;
			final FoundBinding reflected = found.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
			final String type = reflected != null && reflected.type() != null && !reflected.type().isEmpty() ? reflected.type() : "java.lang.Object";
			binding.pull.add(new MutableType(type, null));
			if (apiBinding != null && apiBinding.isWillSet() || settable.contains(name) || reflected != null && reflected.pushed()) {
				binding.push.add(new MutableType(type, null));
			}
			model.bindings.add(binding);
		}
		model.content = api.isComponentContent() ? Content.ALLOWED : null;
		model.constraints.addAll(constraints);
		model.markAsDirty();
		return untranslatable;
	}

	private static List<String> concat(List<String> a, List<String> b) {
		final List<String> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	/** The conditions of a rule, with nested {@code <and>}s (and {@code <validation>}s) opened up. */
	private static List<ApiValidation> flatten(List<ApiValidation> nodes) {
		final List<ApiValidation> flat = new ArrayList<>();
		for (final ApiValidation node : nodes) {
			if (node.getKind() == ApiValidation.Kind.AND || node.getKind() == ApiValidation.Kind.VALIDATION) {
				flat.addAll(flatten(node.getChildren()));
			}
			else {
				flat.add(node);
			}
		}
		return flat;
	}

	/** A {@code <count>}, or an {@code <or>} of a too-few and a too-many count, as a choose; else null. */
	private static MutableChoose choose(ApiValidation condition) {
		if (condition.getKind() == ApiValidation.Kind.COUNT) {
			final int[] bounds = allowedRange(condition.getCountTest());
			return bounds == null ? null : choose(condition.getChildren(), bounds[0], bounds[1]);
		}
		if (condition.getKind() == ApiValidation.Kind.OR && condition.getChildren().size() == 2) {
			final ApiValidation first = condition.getChildren().get(0);
			final ApiValidation second = condition.getChildren().get(1);
			if (first.getKind() != ApiValidation.Kind.COUNT || second.getKind() != ApiValidation.Kind.COUNT || !same(first.getChildren(), second.getChildren())) {
				return null;
			}
			final int[] a = allowedRange(first.getCountTest());
			final int[] b = allowedRange(second.getCountTest());
			if (a == null || b == null) {
				return null;
			}
			return choose(first.getChildren(), Math.max(a[0], b[0]), Math.min(a[1], b[1]));
		}
		return null;
	}

	private static MutableChoose choose(List<ApiValidation> alternatives, int min, int max) {
		final MutableChoose choose = new MutableChoose();
		for (final ApiValidation alternative : alternatives) {
			final List<String> names = alternativeNames(alternative);
			if (names == null) {
				return null;
			}
			choose.alternatives.add(names);
		}
		choose.min = min > 0 ? min : null;
		choose.max = max == Integer.MAX_VALUE ? null : max;
		return choose.min == null && choose.max == null ? null : choose;
	}

	/**
	 * The number of satisfied alternatives a count test allows, as {@code {min, max}} (max
	 * {@link Integer#MAX_VALUE} for unbounded): the rule fires on the count the test matches, so the
	 * allowed range is its complement. Null when that isn't a single range.
	 */
	static int[] allowedRange(String test) {
		if (test == null) {
			return null;
		}
		final String t = test.replace(" ", "");
		int i = 0;
		while (i < t.length() && "<>=!".indexOf(t.charAt(i)) >= 0) {
			i++;
		}
		final String operator = i == 0 ? "==" : t.substring(0, i);
		final int n;
		try {
			n = Integer.parseInt(t.substring(i));
		}
		catch (NumberFormatException e) {
			return null;
		}
		return switch (operator) {
			case "!=" -> new int[] { n, n };
			case "<" -> new int[] { n, Integer.MAX_VALUE };
			case "<=", "=<" -> new int[] { n + 1, Integer.MAX_VALUE };
			case ">" -> new int[] { 0, n };
			case ">=", "=>" -> n > 0 ? new int[] { 0, n - 1 } : null;
			case "==", "=" -> n == 0 ? new int[] { 1, Integer.MAX_VALUE } : null;
			default -> null;
		};
	}

	/**
	 * Several conditions: all alternatives bound (at most all but one of them may be), all bindings
	 * unbound (at least one must be), or an antecedent with a consequent; else null.
	 */
	private static MutableConstraint combination(List<ApiValidation> conditions) {
		final List<List<String>> alternatives = new ArrayList<>();
		for (final ApiValidation condition : conditions) {
			final List<String> names = alternativeNames(condition);
			if (names == null) {
				alternatives.clear();
				break;
			}
			alternatives.add(names);
		}
		if (alternatives.size() == conditions.size()) {
			final MutableChoose choose = new MutableChoose();
			choose.alternatives.addAll(alternatives);
			choose.max = conditions.size() - 1;
			return choose;
		}
		if (conditions.stream().allMatch(c -> c.getKind() == ApiValidation.Kind.UNBOUND)) {
			final MutableChoose choose = new MutableChoose();
			for (final ApiValidation condition : conditions) {
				choose.alternatives.add(new ArrayList<>(List.of(condition.getBindingName())));
			}
			choose.min = 1;
			return choose;
		}
		return requires(conditions);
	}

	/** An {@code <or>} of {@code <unbound>}s (each binding required) as the names; else null. */
	private static List<String> unboundNames(ApiValidation node) {
		if (node.getKind() != ApiValidation.Kind.OR || node.getChildren().isEmpty()) {
			return null;
		}
		final List<String> names = new ArrayList<>();
		for (final ApiValidation child : node.getChildren()) {
			if (child.getKind() != ApiValidation.Kind.UNBOUND) {
				return null;
			}
			names.add(child.getBindingName());
		}
		return names;
	}

	/** One antecedent and one consequent, as a requires; else null. */
	private static MutableRequires requires(List<ApiValidation> conditions) {
		ApiValidation consequent = null;
		Obligation must = null;
		for (final ApiValidation condition : conditions) {
			final Obligation obligation = switch (condition.getKind()) {
				case UNBOUND -> Obligation.BOUND;
				case UNSETTABLE -> Obligation.SETTABLE;
				case UNGETTABLE -> Obligation.GETTABLE;
				default -> null;
			};
			if (obligation != null) {
				if (consequent != null) {
					return null;
				}
				consequent = condition;
				must = obligation;
			}
		}
		if (consequent == null) {
			return null;
		}
		final String binding = consequent.getBindingName();
		final List<ApiValidation> antecedents = new ArrayList<>();
		for (final ApiValidation condition : conditions) {
			// A <bound> on the consequent itself only restates that an unsettable value is a bound one.
			if (condition != consequent && !(must != Obligation.BOUND && condition.getKind() == ApiValidation.Kind.BOUND && binding.equals(condition.getBindingName()))) {
				antecedents.add(condition);
			}
		}
		final MutableRequires requires = new MutableRequires();
		requires.binding = binding;
		requires.must = must;
		if (antecedents.isEmpty()) {
			return must == Obligation.BOUND ? null : requires;
		}
		if (antecedents.size() != 1) {
			return null;
		}
		final List<String> when = alternativeNames(antecedents.get(0));
		if (when == null) {
			return null;
		}
		requires.when.addAll(when);
		return requires;
	}

	/** Whether two lists of rule nodes are the same tree (ApiValidation has no value equality). */
	private static boolean same(List<ApiValidation> a, List<ApiValidation> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			final ApiValidation x = a.get(i);
			final ApiValidation y = b.get(i);
			if (x.getKind() != y.getKind() || !java.util.Objects.equals(x.getBindingName(), y.getBindingName()) || !java.util.Objects.equals(x.getCountTest(), y.getCountTest()) || !same(x.getChildren(), y.getChildren())) {
				return false;
			}
		}
		return true;
	}

	/** A {@code <bound>} binding, or an {@code <or>} of them, as names; else null. */
	private static List<String> alternativeNames(ApiValidation node) {
		if (node.getKind() == ApiValidation.Kind.BOUND) {
			return new ArrayList<>(List.of(node.getBindingName()));
		}
		if (node.getKind() != ApiValidation.Kind.OR || node.getChildren().size() < 2) {
			return null;
		}
		final List<String> names = new ArrayList<>();
		for (final ApiValidation child : node.getChildren()) {
			if (child.getKind() != ApiValidation.Kind.BOUND) {
				return null;
			}
			names.add(child.getBindingName());
		}
		return names;
	}

	/** The bindings a rule mentions, for naming a rule that has no message. */
	private static String bindingNames(ApiValidation validation) {
		final List<String> names = new ArrayList<>();
		collect(validation, names);
		return names.isEmpty() ? "no binding" : "'" + String.join("', '", names) + "'";
	}

	private static void collect(ApiValidation node, List<String> names) {
		if (node.getBindingName() != null && !names.contains(node.getBindingName())) {
			names.add(node.getBindingName());
		}
		for (final ApiValidation child : node.getChildren()) {
			collect(child, names);
		}
	}
}

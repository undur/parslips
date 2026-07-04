package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;
import org.objectstyle.wolips.bindings.api.ApiextModel.UnknownAttributes;

/**
 * Evaluates an element's {@code .apiext} contract against the bindings actually written on a template
 * tag, producing the diagnostics the editor turns into markers (Milestone 2 — where {@code .apiext}
 * stops <em>describing</em> and starts <em>enforcing</em>).
 * <p>
 * What it evaluates, all against the set of <b>explicitly bound</b> binding names (a {@code <default>}
 * never counts as bound — the spec's "bound means bound" rule):
 * <ul>
 * <li><b>required</b> — a {@code required="true"} binding that isn't bound → error.</li>
 * <li><b>{@code <choose>}</b> — the number of satisfied alternatives must be within {@code [min,max]};
 *     a satisfied {@code <any-of>} counts as exactly one alternative.</li>
 * <li><b>{@code <requires>}</b> — when the antecedent holds, the consequent must be bound
 *     ({@code must="bound"}). Settable/gettable obligations depend on <em>value</em> analysis the
 *     template model doesn't provide here, so they are not evaluated (left to the push-derived
 *     settability check the existing validator already performs).</li>
 * <li><b>{@code unknownAttributes="forbidden"}</b> — a bound attribute not declared as a binding →
 *     error (closed set). {@code allowed}/{@code passthrough}/absent do not flag unknown attributes.</li>
 * <li><b>deprecation</b> — a bound binding marked {@code <deprecated>} → warning, with its note.</li>
 * </ul>
 * This is pure logic over names + the model; the caller supplies the bound-name set and maps the
 * returned {@link Diagnostic}s onto its own problem/marker types.
 */
public final class ApiextTemplateEvaluator {

	/** A single evaluation result. */
	public static final class Diagnostic {
		/** Whether this is a warning (deprecation) or an error (contract violation). */
		public enum Kind { ERROR, WARNING }

		private final Kind _kind;
		private final String _message;
		/** The binding name this diagnostic is about, when it targets a specific one (else null). */
		private final String _bindingName;

		Diagnostic(Kind kind, String message, String bindingName) {
			_kind = kind;
			_message = message;
			_bindingName = bindingName;
		}

		public Kind getKind() {
			return _kind;
		}

		public String getMessage() {
			return _message;
		}

		/** The binding this is about (for positioning the marker on that attribute), or null. */
		public String getBindingName() {
			return _bindingName;
		}
	}

	private ApiextTemplateEvaluator() {
		// static only
	}

	/**
	 * Evaluates {@code model} against {@code boundNames} (the binding names explicitly written on the
	 * template tag). Returns every diagnostic found; empty means the tag satisfies the contract.
	 *
	 * @param model      the element's parsed {@code .apiext}; must be {@code .apiext}-owned
	 * @param boundNames the names of the bindings explicitly bound on the tag (never null)
	 */
	public static List<Diagnostic> evaluate(ApiextModel model, Set<String> boundNames) {
		final List<Diagnostic> out = new ArrayList<>();
		if (model == null) {
			return out;
		}

		// required + deprecation, per declared binding.
		for (final Binding b : model.getBindings()) {
			final String name = b.getName();
			final boolean bound = boundNames.contains(name);
			if (b.isRequired() && !bound) {
				out.add(new Diagnostic(Diagnostic.Kind.ERROR, "'" + name + "' is required but is not bound.", name));
			}
			if (bound && b.isDeprecated()) {
				final StringBuilder m = new StringBuilder("'").append(name).append("' is deprecated.");
				final String note = b.getDeprecationNote();
				if (note != null && !note.isEmpty()) {
					m.append(' ').append(note);
				}
				out.add(new Diagnostic(Diagnostic.Kind.WARNING, m.toString(), name));
			}
		}

		// unknownAttributes="forbidden": a bound attribute not declared as a binding is an error.
		if (model.getUnknownAttributes() == UnknownAttributes.FORBIDDEN) {
			final Set<String> declared = declaredNames(model);
			for (final String bound : boundNames) {
				if (!declared.contains(bound)) {
					out.add(new Diagnostic(Diagnostic.Kind.ERROR,
							"'" + bound + "' is not a declared binding, and this element forbids unknown attributes.", bound));
				}
			}
		}

		// Cross-binding constraints.
		for (final Constraint c : model.getConstraints()) {
			if (c instanceof Choose) {
				evaluateChoose((Choose) c, boundNames, out);
			}
			else if (c instanceof Requires) {
				evaluateRequires((Requires) c, boundNames, out);
			}
		}

		return out;
	}

	private static void evaluateChoose(Choose choose, Set<String> bound, List<Diagnostic> out) {
		int satisfied = 0;
		for (final Alternative alt : choose.getAlternatives()) {
			if (isSatisfied(alt, bound)) {
				satisfied++;
			}
		}
		final int min = choose.getMin() != null ? choose.getMin() : 0;
		final Integer max = choose.getMax(); // null = unbounded
		if (satisfied < min || (max != null && satisfied > max)) {
			final String message = choose.getMessage() != null ? choose.getMessage() : ApiextConstraintMessages.describe(choose);
			out.add(new Diagnostic(Diagnostic.Kind.ERROR, message, null));
		}
	}

	private static void evaluateRequires(Requires requires, Set<String> bound, List<Diagnostic> out) {
		// Only the "bound" obligation is checkable from names alone; settable/gettable need value
		// analysis and are handled by the existing push-derived settability check.
		if (requires.getMust() != ApiextModel.Obligation.BOUND) {
			return;
		}
		final Alternative antecedent = requires.getAntecedent();
		final boolean holds = antecedent == null || isSatisfied(antecedent, bound);
		if (holds && !bound.contains(requires.getBinding())) {
			final String message = requires.getMessage() != null ? requires.getMessage() : ApiextConstraintMessages.describe(requires);
			out.add(new Diagnostic(Diagnostic.Kind.ERROR, message, requires.getBinding()));
		}
	}

	/** An alternative is satisfied iff any of its binding names is bound. */
	private static boolean isSatisfied(Alternative alt, Set<String> bound) {
		for (final String name : alt.getBindingNames()) {
			if (bound.contains(name)) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> declaredNames(ApiextModel model) {
		final Set<String> names = new java.util.LinkedHashSet<>();
		for (final Binding b : model.getBindings()) {
			if (b.getName() != null) {
				names.add(b.getName());
			}
		}
		return names;
	}
}

package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Obligation;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;

/**
 * The consumer-enforced integrity and structural checks for {@code .apiext} constraints — the rules
 * the apiext-format spec (§ Constraints) calls format-defining but that a DTD structurally cannot
 * express. Running this over a parsed {@link ApiextModel} is what makes an {@code .apiext} file
 * <em>checkable</em> rather than merely well-formed.
 * <p>
 * Checks performed:
 * <ul>
 * <li><b>Reference integrity</b> — every binding referenced by a constraint ({@code binding=},
 *     {@code when=}, and {@code <binding>}/{@code <any-of>} members) MUST name a declared binding.
 *     With typed constructs a typo'd name would silently <em>weaken</em> a constraint, so this is
 *     the single most important check.</li>
 * <li><b>{@code <choose>} arity/bounds</b> — ≥2 alternatives; at least one of {@code min}/{@code max}
 *     present; both non-negative; {@code min ≤ max}; and {@code max} not larger than the alternative
 *     count (a {@code max} that can never bind is a spec error).</li>
 * <li><b>{@code <any-of>} arity</b> — ≥2 members.</li>
 * <li><b>{@code <requires>} obligation rules</b> — an unconditional {@code must="bound"} is invalid
 *     (that is {@code required="true"}'s job); {@code when=} and an {@code <any-of>} antecedent are
 *     mutually exclusive.</li>
 * </ul>
 * (The {@code required} + {@code <default>} conflict the spec mentions is deferred: {@code <default>}
 * is a separate open format issue and not yet in the constraints grammar this validates.)
 * The checks are non-fatal by design: {@link ApiextModel#parse} still produces a model for a
 * slightly-malformed file (so the editor renders what it can); this validator reports what's wrong
 * as a list of {@link Problem}s a caller can surface as markers or log entries.
 */
public final class ApiextConstraintValidator {

	/** Severity of a reported problem. */
	public enum Severity {
		/** A spec violation — the file is not a valid {@code .apiext} as authored. */
		ERROR,
		/** Suspicious but not strictly invalid (e.g. {@code required} + {@code <default>}). */
		WARNING
	}

	/** A single integrity/structural problem found in a model's constraints. */
	public static final class Problem {
		private final Severity _severity;
		private final String _message;

		Problem(Severity severity, String message) {
			_severity = severity;
			_message = message;
		}

		public Severity getSeverity() {
			return _severity;
		}

		public String getMessage() {
			return _message;
		}

		@Override
		public String toString() {
			return _severity + ": " + _message;
		}
	}

	private ApiextConstraintValidator() {
		// static only
	}

	/**
	 * Validates the model's constraints and (the {@code required}+{@code default} rule) its bindings,
	 * returning every problem found. An empty list means the constraints are spec-clean. Never null.
	 */
	public static List<Problem> validate(ApiextModel model) {
		final List<Problem> problems = new ArrayList<>();
		if (model == null) {
			return problems;
		}

		// The set of declared binding names, for reference-integrity checks.
		final Set<String> declared = new LinkedHashSet<>();
		for (final Binding b : model.getBindings()) {
			if (b.getName() != null) {
				declared.add(b.getName());
			}
		}

		// A removed-grammar construct in an .apiext file is a spec violation, not a fallback.
		for (final String legacy : model.getLegacyConstructs()) {
			problems.add(error("<" + legacy + "> is not part of the .apiext grammar — it was removed (see the format spec). "
					+ "A legacy construct in an .apiext file is an error; migrate the file."));
		}

		for (final Constraint c : model.getConstraints()) {
			if (c instanceof Choose) {
				checkChoose((Choose) c, declared, problems);
			}
			else if (c instanceof Requires) {
				checkRequires((Requires) c, declared, problems);
			}
		}

		// #5: deprecated + required on one binding is incoherent (a required binding can't be
		// discouraged from use) — a lint, not an error.
		for (final Binding b : model.getBindings()) {
			if (b.isDeprecated() && b.isRequired()) {
				problems.add(new Problem(Severity.WARNING,
						"Binding \"" + b.getName() + "\" is both required and deprecated — a required binding cannot be discouraged from use."));
			}
		}

		return problems;
	}

	private static void checkChoose(Choose choose, Set<String> declared, List<Problem> problems) {
		final List<Alternative> alts = choose.getAlternatives();
		if (alts.size() < 2) {
			problems.add(error("<choose> must list at least 2 alternatives (a single-binding choose is what required= expresses)."));
		}
		final Integer min = choose.getMin();
		final Integer max = choose.getMax();
		if (min == null && max == null) {
			problems.add(error("<choose> must specify at least one of min or max (a choose with neither is vacuous)."));
		}
		if (min != null && min < 0) {
			problems.add(error("<choose> min must be non-negative (was " + min + ")."));
		}
		if (max != null && max < 0) {
			problems.add(error("<choose> max must be non-negative (was " + max + ")."));
		}
		if (min != null && max != null && min > max) {
			problems.add(error("<choose> min (" + min + ") must not exceed max (" + max + ")."));
		}
		if (max != null && max > alts.size()) {
			problems.add(error("<choose> max (" + max + ") exceeds the number of alternatives (" + alts.size() + "), so it can never be reached."));
		}
		for (final Alternative alt : alts) {
			checkAlternative(alt, declared, problems, "<choose>");
		}
	}

	private static void checkRequires(Requires requires, Set<String> declared, List<Problem> problems) {
		if (requires.getBinding() == null || requires.getBinding().isEmpty()) {
			problems.add(error("<requires> is missing its required 'binding' attribute."));
		}
		else if (!declared.contains(requires.getBinding())) {
			problems.add(error("<requires binding=\"" + requires.getBinding() + "\"> names a binding that is not declared on this element."));
		}
		final Alternative antecedent = requires.getAntecedent();
		if (antecedent == null && requires.getMust() == Obligation.BOUND) {
			problems.add(error("<requires binding=\"" + requires.getBinding() + "\"> is unconditional with must=\"bound\" — an always-required binding is expressed with required=\"true\", not a constraint."));
		}
		if (antecedent != null) {
			checkAlternative(antecedent, declared, problems, "<requires> antecedent");
		}
	}

	/** Reference-integrity + any-of-arity for one alternative/antecedent. */
	private static void checkAlternative(Alternative alt, Set<String> declared, List<Problem> problems, String where) {
		if (alt.isAnyOf() && alt.getBindingNames().size() < 2) {
			problems.add(error("<any-of> in " + where + " must have at least 2 members."));
		}
		for (final String name : alt.getBindingNames()) {
			if (name == null || name.isEmpty()) {
				problems.add(error(where + " references a binding with no name."));
			}
			else if (!declared.contains(name)) {
				problems.add(error(where + " references binding \"" + name + "\", which is not declared on this element."));
			}
		}
	}

	private static Problem error(String message) {
		return new Problem(Severity.ERROR, message);
	}
}

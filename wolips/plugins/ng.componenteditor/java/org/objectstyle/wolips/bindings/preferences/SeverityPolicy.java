package org.objectstyle.wolips.bindings.preferences;

/**
 * The single place that interprets a validation-severity preference value.
 *
 * <p>Validation sites used to hand-roll this interpretation inline — read the
 * severity string, gate problem creation on {@code !IGNORE.equals(...)}, and pass
 * {@code WARNING.equals(...)} as the problem's warning flag. That pattern was
 * copy-pasted at ~15 sites; one copy drifted (the "no key in component" keypath
 * error hardcoded its flag and skipped the IGNORE gate, so it could never be
 * ignored). Routing every site through this class makes that class of bug
 * impossible: no site re-implements the interpretation, none can forget the
 * IGNORE gate or hardcode the flag.
 *
 * <p>The interpretation is faithful to the historical inline logic:
 * {@link PreferenceConstants#WARNING} maps to "warning", anything else that isn't
 * {@link PreferenceConstants#IGNORE} maps to "error" — i.e. {@link #isWarning} is
 * exactly the old {@code WARNING.equals(severity)} check, not a stricter
 * three-valued parse. (No exceptions on unrecognized values; that would change
 * behavior.)
 *
 * <p>Two flavours of method:
 * <ul>
 *   <li><b>String-based</b> ({@link #isIgnored(String)} etc.) — pure functions of a
 *       severity value, with no preference-store access. These hold the logic and
 *       are unit-testable without a running workbench.</li>
 *   <li><b>Key-based</b> ({@link #isIgnoredKey(String)} etc.) — read the configured
 *       value for a {@code *_SEVERITY_KEY} via {@link BindingValidationPreferences}
 *       and delegate to the string-based core. These are what validation sites call.</li>
 * </ul>
 */
public final class SeverityPolicy {

	private SeverityPolicy() {
		// Static methods only.
	}

	// ---- Pure interpretation of a severity value (unit-testable) ------------

	/** @return true if {@code severity} means "ignore" (suppress the problem). */
	public static boolean isIgnored(String severity) {
		return PreferenceConstants.IGNORE.equals(severity);
	}

	/**
	 * @return true if {@code severity} means "warning", false otherwise (i.e.
	 *         "error"). Mirrors the historical {@code WARNING.equals(severity)}
	 *         check exactly. Callers should rule out {@link #isIgnored} first.
	 */
	public static boolean isWarning(String severity) {
		return PreferenceConstants.WARNING.equals(severity);
	}

	/**
	 * The OR-combine used by wrapped/mirrored problems (an OGNL sub-problem, or a
	 * WOD problem mirrored into the HTML view): warning if the inner problem is
	 * already a warning <em>or</em> this severity says warning. Preserves the
	 * existing behaviour of those sites.
	 */
	public static boolean isWarningOr(String severity, boolean innerIsWarning) {
		return innerIsWarning || isWarning(severity);
	}

	// ---- Key-based convenience (read the configured value, then interpret) --

	/** @return true if the configured severity for {@code severityKey} is "ignore". */
	public static boolean isIgnoredKey(String severityKey) {
		return isIgnored(BindingValidationPreferences.severity(severityKey));
	}

	/** @return true if the configured severity for {@code severityKey} is "warning". */
	public static boolean isWarningKey(String severityKey) {
		return isWarning(BindingValidationPreferences.severity(severityKey));
	}

	/** Key-based form of {@link #isWarningOr(String, boolean)}. */
	public static boolean isWarningOrKey(String severityKey, boolean innerIsWarning) {
		return isWarningOr(BindingValidationPreferences.severity(severityKey), innerIsWarning);
	}
}

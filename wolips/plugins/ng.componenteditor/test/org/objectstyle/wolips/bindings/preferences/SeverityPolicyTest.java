package org.objectstyle.wolips.bindings.preferences;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link SeverityPolicy} — the pure interpretation of a validation
 * severity value into ignore / warning / error decisions. Exercises the
 * string-based core (no preference store needed).
 */
public class SeverityPolicyTest {

	// ---- isIgnored ----------------------------------------------------------

	@Test
	public void isIgnored_trueOnlyForIgnore() {
		assertTrue(SeverityPolicy.isIgnored(PreferenceConstants.IGNORE));
		assertFalse(SeverityPolicy.isIgnored(PreferenceConstants.WARNING));
		assertFalse(SeverityPolicy.isIgnored(PreferenceConstants.ERROR));
	}

	@Test
	public void isIgnored_falseForUnknownOrNull() {
		// Faithful to the old WARNING.equals/IGNORE.equals checks: unknown values
		// are simply "not ignore" (and elsewhere "not warning" => error).
		assertFalse(SeverityPolicy.isIgnored("default"));
		assertFalse(SeverityPolicy.isIgnored(null));
		assertFalse(SeverityPolicy.isIgnored("nonsense"));
	}

	// ---- isWarning ----------------------------------------------------------

	@Test
	public void isWarning_trueOnlyForWarning() {
		assertTrue(SeverityPolicy.isWarning(PreferenceConstants.WARNING));
		assertFalse(SeverityPolicy.isWarning(PreferenceConstants.ERROR));
		assertFalse(SeverityPolicy.isWarning(PreferenceConstants.IGNORE));
	}

	@Test
	public void isWarning_falseForUnknownOrNull() {
		// Anything that isn't WARNING is treated as error (false) — matches the
		// historical WARNING.equals(severity) semantics exactly.
		assertFalse(SeverityPolicy.isWarning("default"));
		assertFalse(SeverityPolicy.isWarning(null));
		assertFalse(SeverityPolicy.isWarning("nonsense"));
	}

	// ---- isWarningOr (wrapped/mirrored problems) ----------------------------

	@Test
	public void isWarningOr_trueIfInnerWarning() {
		// Inner problem already a warning => warning regardless of this severity.
		assertTrue(SeverityPolicy.isWarningOr(PreferenceConstants.ERROR, true));
		assertTrue(SeverityPolicy.isWarningOr(PreferenceConstants.WARNING, true));
	}

	@Test
	public void isWarningOr_trueIfSeverityWarning() {
		// This severity says warning => warning even if the inner problem is error.
		assertTrue(SeverityPolicy.isWarningOr(PreferenceConstants.WARNING, false));
	}

	@Test
	public void isWarningOr_falseWhenNeitherWarning() {
		assertFalse(SeverityPolicy.isWarningOr(PreferenceConstants.ERROR, false));
	}
}

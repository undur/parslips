package org.objectstyle.wolips.bindings.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.objectstyle.wolips.bindings.Activator;

/**
 * Typed accessors for the binding/template <em>validation</em> preferences —
 * the on/off validation toggles and the per-problem severity levels.
 *
 * <p>These were previously read with scattered raw-key calls like
 * {@code Activator.getDefault().getPluginPreferences().getString(
 * PreferenceConstants.MISSING_COMPONENT_SEVERITY_KEY)} across the validators
 * ({@code AbstractWodBinding}, {@code AbstractWodElement}, {@code TemplateValidator},
 * {@code WooModel}, …). This class is the single place that knows how to reach the
 * store, so callers ask for intent rather than juggling store lookups.
 *
 * <p>Scope is deliberately limited to the simple toggles + severities. The
 * following stay at the key/{@link PreferenceConstants} level on purpose:
 * <ul>
 *   <li>The severity <em>value vocabulary</em> ({@code ERROR}/{@code WARNING}/
 *       {@code IGNORE}) and the page option tables — shared with the preference
 *       pages; key-centric is the right form.</li>
 *   <li>{@code TAG_SHORTCUTS_KEY} and {@code BINDING_VALIDATION_RULES_KEY} — these
 *       are serialized complex values that {@code ApiCache} parses into objects and
 *       listens to; that's a subsystem, not a simple read, so it keeps its own
 *       handling.</li>
 * </ul>
 *
 * <p>Defaults remain owned by {@code PreferenceInitializer} (the severity defaults
 * reference the {@link PreferenceConstants} value vocabulary and the complex values
 * need serialization, so the initializer is their honest home).
 */
public final class BindingValidationPreferences {

	private BindingValidationPreferences() {
		// Static accessors only.
	}

	private static IPreferenceStore store() {
		return Activator.getDefault().getPreferenceStore();
	}

	/** @return true if binding values should be validated. */
	public static boolean validateBindingValues() {
		return store().getBoolean(PreferenceConstants.VALIDATE_BINDING_VALUES);
	}

	/** @return true if templates should be validated. */
	public static boolean validateTemplates() {
		return store().getBoolean(PreferenceConstants.VALIDATE_TEMPLATES_KEY);
	}

	/** @return true if WOO encodings should be validated. */
	public static boolean validateWooEncodings() {
		return store().getBoolean(PreferenceConstants.VALIDATE_WOO_ENCODINGS_KEY);
	}

	/** @return true if new bindings should be created as inline bindings. */
	public static boolean useInlineBindings() {
		return store().getBoolean(PreferenceConstants.USE_INLINE_BINDINGS_KEY);
	}

	/** Sets the "create inline bindings" preference. */
	public static void setUseInlineBindings(boolean useInlineBindings) {
		store().setValue(PreferenceConstants.USE_INLINE_BINDINGS_KEY, useInlineBindings);
	}

	/**
	 * @param severityKey one of the {@code *_SEVERITY_KEY} constants in
	 *                    {@link PreferenceConstants}
	 * @return the configured severity value ({@code ERROR}/{@code WARNING}/
	 *         {@code IGNORE}) for that problem kind
	 */
	public static String severity(String severityKey) {
		return store().getString(severityKey);
	}
}

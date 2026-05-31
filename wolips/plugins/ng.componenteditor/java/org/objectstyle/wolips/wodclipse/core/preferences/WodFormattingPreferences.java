package org.objectstyle.wolips.wodclipse.core.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.objectstyle.wolips.wodclipse.core.Activator;

/**
 * Typed accessors for the WOD/template <em>formatting</em> preferences.
 *
 * <p>These preferences were previously read with scattered raw-key calls like
 * {@code Activator.getDefault().getPreferenceStore().getBoolean(
 * PreferenceConstants.INDENT_TABS)} across actions, refactorings and assist
 * processors. This class is the single place that knows the keys, their types,
 * and their defaults — callers ask for intent ({@link #indentTabs()}) rather
 * than juggling key strings and store lookups.
 *
 * <p>Defaults are co-located here (the {@code DEFAULT_*} constants) and also seeded
 * into the store by {@code PreferenceInitializer}, so reads work even before a user
 * has visited the preference page.
 *
 * <p>The <em>preference page</em> itself ({@code XMLPreferencePage}) deliberately
 * still reads/writes the store directly via {@link PreferenceConstants} — a page is
 * the thing that edits these values, so it works at the key level. This accessor is
 * for the <em>consumers</em> of the values.
 *
 * <p>The syntax-colouring preferences (element/binding colours) are intentionally
 * <em>not</em> wrapped here: they're consumed by {@code WodScanner} as a set of
 * token keys it iterates over, where the key-centric form is the natural fit.
 */
public final class WodFormattingPreferences {

	private WodFormattingPreferences() {
		// Static accessors only.
	}

	/** Default for {@link #spacesAroundEquals()}. */
	public static final boolean DEFAULT_SPACES_AROUND_EQUALS = false;

	/** Default for {@link #indentSize()}. */
	public static final int DEFAULT_INDENT_SIZE = 2;

	/** Default for {@link #indentTabs()}. */
	public static final boolean DEFAULT_INDENT_TABS = false;

	/** Default for {@link #lowercaseAttributes()}. */
	public static final boolean DEFAULT_LOWERCASE_ATTRIBUTES = true;

	/** Default for {@link #lowercaseTags()}. */
	public static final boolean DEFAULT_LOWERCASE_TAGS = true;

	/** Default for {@link #stickyWOTags()}. */
	public static final boolean DEFAULT_STICKY_WOTAGS = false;

	private static IPreferenceStore store() {
		return Activator.getDefault().getPreferenceStore();
	}

	/** @return true if a space should surround the {@code =} in bindings. */
	public static boolean spacesAroundEquals() {
		return store().getBoolean(PreferenceConstants.SPACES_AROUND_EQUALS);
	}

	/** @return the number of spaces per indent level (when not indenting with tabs). */
	public static int indentSize() {
		return store().getInt(PreferenceConstants.INDENT_SIZE);
	}

	/** @return true if indentation uses tabs rather than spaces. */
	public static boolean indentTabs() {
		return store().getBoolean(PreferenceConstants.INDENT_TABS);
	}

	/** @return true if HTML attribute names should be lower-cased on format. */
	public static boolean lowercaseAttributes() {
		return store().getBoolean(PreferenceConstants.LOWERCASE_ATTRIBUTES);
	}

	/** @return true if HTML tag names should be lower-cased on format. */
	public static boolean lowercaseTags() {
		return store().getBoolean(PreferenceConstants.LOWERCASE_TAGS);
	}

	/** @return true if {@code <wo:...>} tags should "stick" to their content on format. */
	public static boolean stickyWOTags() {
		return store().getBoolean(PreferenceConstants.STICKY_WOTAGS);
	}
}

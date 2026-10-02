package org.objectstyle.wolips.bindings.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/**
 * The binding names a component reads or writes by name — {@code valueForBinding("team")},
 * {@code booleanValueForBinding("showName", true)}, {@code hasBinding("limit")},
 * {@code setValueForBinding(value, "selection")} and their kin.
 *
 * <p>A non-synchronizing component (the idiom for display components: it reads its bindings
 * on demand instead of having them pushed into its variables) has no settable keys, so
 * reflection — what completion and the dev server fall back to when a component declares no
 * API — finds nothing. These string literals ARE its API: they name exactly the bindings it
 * reads. They are also what a rename of such a binding must change, or the renamed call sites
 * pass a binding the component no longer reads.
 *
 * <p>Matching is textual over the component's own compilation unit: a literal first argument
 * (or second, for {@code setValueForBinding(value, "name")}). A name computed at runtime isn't
 * found — there is nothing static to find.
 */
public final class BindingNameLiterals {

	/**
	 * The by-name binding accessors of WOComponent and ERXComponent. The name is the first
	 * argument, except for setValueForBinding, where it's the second.
	 */
	private static final Pattern CALL = Pattern.compile(
			"\\b(?:valueForBinding|booleanValueForBinding|stringValueForBinding|integerValueForBinding|intValueForBinding|floatValueForBinding|doubleValueForBinding|objectValueForBinding|arrayValueForBinding|hasBinding|canGetValueForBinding|canSetValueForBinding)\\s*\\(\\s*\"([A-Za-z_][\\w]*)\""
					+ "|\\bsetValueForBinding\\s*\\([^,()]*(?:\\([^()]*\\))?[^,()]*,\\s*\"([A-Za-z_][\\w]*)\"" );

	private BindingNameLiterals() {
	}

	/** One occurrence: the binding name and the offset of the name's text (inside the quotes). */
	public record Occurrence(String name, int offset) {
	}

	/** Every by-name binding reference in the source, in order. */
	public static List<Occurrence> find(String source) {
		final List<Occurrence> occurrences = new ArrayList<>();
		if (source == null) {
			return occurrences;
		}
		final Matcher matcher = CALL.matcher(source);
		while (matcher.find()) {
			final int group = matcher.group(1) != null ? 1 : 2;
			occurrences.add(new Occurrence(matcher.group(group), matcher.start(group)));
		}
		return occurrences;
	}

	/** The distinct binding names the source reads or writes by name, in order of first use. */
	public static Set<String> names(String source) {
		final Set<String> names = new LinkedHashSet<>();
		for (final Occurrence occurrence : find(source)) {
			names.add(occurrence.name());
		}
		return names;
	}

	/** The distinct binding names a component's own source uses by name; empty for a binary type. */
	public static Set<String> names(IType component) {
		return names(sourceOf(component));
	}

	/** The source of the type's compilation unit, or null (binary, or unreadable). */
	public static String sourceOf(IType type) {
		if (type == null || type.isBinary()) {
			return null;
		}
		try {
			final ICompilationUnit unit = type.getCompilationUnit();
			return unit == null ? null : unit.getSource();
		}
		catch (final JavaModelException e) {
			return null;
		}
	}
}

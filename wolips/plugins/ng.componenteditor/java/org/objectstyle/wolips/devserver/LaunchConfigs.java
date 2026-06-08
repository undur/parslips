package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Resolves Eclipse launch configurations for the dev server's {@code /launch}
 * endpoint, and encapsulates the "which config did you mean?" logic.
 *
 * <p>The tricky part is that one project often has several configs pointing at
 * different environments — e.g. {@code Strimillinn - Local} and
 * {@code Strimillinn - Production}. Firing the wrong one (Production while you're
 * working locally) is a genuine footgun, so resolution refuses to guess when the
 * choice is ambiguous; it returns the candidates instead and lets the caller pick.
 *
 * <p>Resolution order for a query string:
 * <ol>
 *   <li><b>Exact config name</b> (case-insensitive) → that config.</li>
 *   <li>Otherwise treat the query as a <b>project name</b> and gather that project's
 *       configs. One → use it. Several → prefer the ones whose name contains
 *       "local" or "dev" (case-insensitive); if that narrows to exactly one, use it.
 *       Still ambiguous (or none match) → return the candidates, choosing nothing.</li>
 * </ol>
 */
final class LaunchConfigs {

	/** Outcome of a resolve: either a single chosen config, or several candidates to disambiguate. */
	static final class Resolution {
		final ILaunchConfiguration chosen; // non-null when unambiguous
		final List<ILaunchConfiguration> candidates; // the options when ambiguous (or empty when none found)

		private Resolution(ILaunchConfiguration chosen, List<ILaunchConfiguration> candidates) {
			this.chosen = chosen;
			this.candidates = candidates;
		}

		static Resolution chosen(ILaunchConfiguration c) {
			return new Resolution(c, List.of(c));
		}

		static Resolution ambiguous(List<ILaunchConfiguration> candidates) {
			return new Resolution(null, candidates);
		}
	}

	private LaunchConfigs() {
	}

	static List<ILaunchConfiguration> all() {
		try {
			final ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			final ILaunchConfiguration[] configs = manager.getLaunchConfigurations();
			final List<ILaunchConfiguration> result = new ArrayList<>(configs.length);
			for (final ILaunchConfiguration c : configs) {
				result.add(c);
			}
			return result;
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
			return List.of();
		}
	}

	/**
	 * Resolves a query (a config name, or a project name) to a single config when it
	 * can do so safely, or to a set of candidates when the choice is ambiguous.
	 */
	static Resolution resolve(String query) {
		final List<ILaunchConfiguration> configs = all();

		// 1. Exact config name (case-insensitive).
		for (final ILaunchConfiguration c : configs) {
			if (c.getName().equalsIgnoreCase(query)) {
				return Resolution.chosen(c);
			}
		}

		// 2. Treat the query as a project name and gather that project's configs.
		final List<ILaunchConfiguration> forProject = new ArrayList<>();
		for (final ILaunchConfiguration c : configs) {
			if (query.equalsIgnoreCase(projectNameOf(c))) {
				forProject.add(c);
			}
		}

		if (forProject.isEmpty()) {
			return Resolution.ambiguous(List.of()); // nothing matched the query at all
		}
		if (forProject.size() == 1) {
			return Resolution.chosen(forProject.get(0));
		}

		// Several configs for the project — prefer local/dev to avoid firing Production.
		final List<ILaunchConfiguration> localish = new ArrayList<>();
		for (final ILaunchConfiguration c : forProject) {
			final String name = c.getName().toLowerCase(Locale.ROOT);
			if (name.contains("local") || name.contains("dev")) {
				localish.add(c);
			}
		}
		if (localish.size() == 1) {
			return Resolution.chosen(localish.get(0));
		}

		// Still ambiguous: refuse to guess (a wrong guess might launch the wrong
		// environment). Return the narrowed-down candidates if local/dev narrowed at
		// all, otherwise all of the project's configs.
		return Resolution.ambiguous(localish.isEmpty() ? forProject : localish);
	}

	/** The project name attribute of a config, or empty string if absent. */
	static String projectNameOf(ILaunchConfiguration c) {
		try {
			return c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
		}
		catch (Exception e) {
			return "";
		}
	}
}

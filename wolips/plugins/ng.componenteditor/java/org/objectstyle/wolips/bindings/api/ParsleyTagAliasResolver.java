package org.objectstyle.wolips.bindings.api;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Resolves Parsley tag aliases for a project by reading the {@code parsley-tag-aliases.properties}
 * resources on its classpath &mdash; the same files Parsley's runtime ({@code ParsleyTagRegistry})
 * reads. This is how the editor learns the <em>actual</em> tag vocabulary a project uses (its
 * shortcuts and element replacements), declared by the app and its frameworks.
 *
 * <p><b>Transitional double mechanism.</b> Projects that have not yet adopted the Parsley
 * template parser / wonder-slim still rely on the legacy WOLips tag-shortcut preference. So:
 * if a project has <em>any</em> {@code parsley-tag-aliases.properties} on its classpath, the new
 * mechanism takes over shortcut resolution <em>entirely</em> for that project (the legacy
 * preference is not consulted at all); otherwise the legacy mechanism is used unchanged. The
 * switch is per-project and all-or-nothing &mdash; see {@link #isActiveFor(IJavaProject)}.
 *
 * <p>Resolution mirrors the runtime exactly: a flat {@code alias -> target} map resolved
 * <b>recursively</b> to a fixed point ({@code str -> WOString -> ERXWOString}), cycle-guarded.
 *
 * <p>Results are cached per project. The cache is cleared via {@link #clearCache()} when the
 * classpath may have changed.
 */
public final class ParsleyTagAliasResolver {

	/** The resource name frameworks/apps drop on the classpath; matches Parsley's runtime. */
	public static final String ALIASES_RESOURCE = "parsley-tag-aliases.properties";

	/** Per-project alias map cache (alias -> target), or an empty map when none are present. */
	private static final Map<IJavaProject, Map<String, String>> _cache = new HashMap<>();

	private ParsleyTagAliasResolver() {
	}

	/**
	 * @return true if this project declares Parsley tag aliases (i.e. at least one
	 *         {@code parsley-tag-aliases.properties} is on its classpath), meaning the new
	 *         mechanism should be used for it instead of the legacy tag-shortcut preference.
	 */
	public static boolean isActiveFor(IJavaProject project) {
		return !aliasMap(project).isEmpty();
	}

	/**
	 * Resolves a tag/element name to its final target by following the project's alias chains
	 * recursively to a fixed point. A name with no alias resolves to itself. Cycles are broken
	 * defensively. Mirrors {@code ParsleyTagRegistry.resolve}.
	 *
	 * @param project the project whose aliases to use
	 * @param name    the tag/element name as written in the template
	 * @return the fully-resolved element name (never null; returns {@code name} if unmapped)
	 */
	public static String resolve(IJavaProject project, String name) {
		final java.util.List<String> chain = resolveChain( project, name );
		return chain.get( chain.size() - 1 );
	}

	/**
	 * Resolves the full alias chain from {@code name} to its fixed point, in order. For
	 * {@code str} this is {@code [str, WOString, ERXWOString]}. A name with no alias yields a
	 * single-element list. Cycles are broken defensively. Used both for normal resolution
	 * (the last element) and for "walk back up the chain" doc fallback: if the resolved
	 * element has no documentation, an earlier element in the chain (what it replaces) may.
	 *
	 * @return the resolution chain, oldest (the typed name) first, never empty
	 */
	public static java.util.List<String> resolveChain(IJavaProject project, String name) {
		final Map<String, String> aliases = aliasMap( project );
		final java.util.List<String> chain = new java.util.ArrayList<>();
		String current = name;
		final Set<String> seen = new HashSet<>();
		chain.add( current );
		seen.add( current );
		String next;
		while ((next = aliases.get( current )) != null) {
			if (!seen.add( next )) {
				break; // cycle
			}
			chain.add( next );
			current = next;
		}
		return chain;
	}

	/**
	 * Resolves a tag to the element whose <b>bindings/documentation</b> should be used, which
	 * may differ from {@link #resolve}. A replacement element (e.g. ERXWOString) often has no
	 * {@code .api}/{@code .apiext} of its own; discovering its bindings by reflection is slow
	 * and yields little, while the element it replaces (WOString) has a cheap, cached
	 * definition that is the correct binding contract anyway. So this walks the chain to the
	 * end, then back up to the nearest element with a definition. Falls back to the fully
	 * resolved name when nothing in the chain is documented.
	 *
	 * <p>Important for performance: it lets binding completion and validation work against the
	 * cheaply-cached ancestor instead of reflecting over the heavyweight replacement per tag.
	 */
	public static String resolveForBindings(IJavaProject project, String name) {
		final String resolved = resolve( project, name );
		if (!isActiveFor( project )) {
			return resolved;
		}
		final java.util.List<String> chain = resolveChain( project, name );
		for (int i = chain.size() - 1; i >= 0; i--) {
			if (hasBindingDefinition( chain.get( i ) )) {
				return chain.get( i );
			}
		}
		return resolved;
	}

	/** @return true if the named element has a global API, a bundled {@code .apiext}, or a global {@code .apiext}. */
	private static boolean hasBindingDefinition(String name) {
		try {
			if (ApiUtils.findGlobalApiSnapshotByClassName( name ) != null) {
				return true;
			}
			return ApiUtils.findGlobalApiextBytes( name ) != null;
		}
		catch (Throwable t) {
			return false;
		}
	}

	/**
	 * @return the project's raw alias map ({@code alias -> target}), in declaration order;
	 *         empty if the project declares no Parsley aliases. The returned map is unmodifiable.
	 */
	public static Map<String, String> aliasMap(IJavaProject project) {
		if (project == null) {
			return Collections.emptyMap();
		}
		synchronized (_cache) {
			Map<String, String> cached = _cache.get( project );
			if (cached == null) {
				cached = load( project );
				_cache.put( project, cached );
			}
			return cached;
		}
	}

	/** Clears the per-project cache (call when a project's classpath may have changed). */
	public static void clearCache() {
		synchronized (_cache) {
			_cache.clear();
		}
	}

	/**
	 * Loads and merges every {@code parsley-tag-aliases.properties} reachable from the project's
	 * <b>resolved classpath</b> — mirroring what the runtime classloader sees. Walks each
	 * classpath entry:
	 * <ul>
	 *   <li>library jar &rarr; read the entry from inside the jar;</li>
	 *   <li>library folder (a {@code target/classes}-style dir on the path) &rarr; read the file;</li>
	 *   <li>referenced workspace project &rarr; read from <em>its</em> output folder (where
	 *       {@code src/main/resources} compiles to) — this is the key case, since Parsley and
	 *       ERExtensions are workspace projects whose resources live in their own output, not in
	 *       this project's package-fragment roots;</li>
	 * </ul>
	 * plus this project's own output folder. First declaration of an alias wins on conflict.
	 */
	private static Map<String, String> load(IJavaProject project) {
		final Map<String, String> merged = new LinkedHashMap<>();
		try {
			final IJavaModel javaModel = project.getJavaModel();

			// This project's own output (its src/main/resources lands here).
			readFromOutput( project, merged );

			for (final IClasspathEntry entry : project.getResolvedClasspath( true )) {
				switch (entry.getEntryKind()) {
				case IClasspathEntry.CPE_LIBRARY:
					mergeFirstWins( merged, readFromLibraryPath( entry.getPath() ) );
					break;
				case IClasspathEntry.CPE_PROJECT:
					// A referenced workspace project — read its output folder.
					final IProject refProject = ResourcesPlugin.getWorkspace().getRoot().getProject( entry.getPath().lastSegment() );
					if (refProject != null && refProject.exists()) {
						final IJavaProject refJava = javaModel.getJavaProject( refProject.getName() );
						if (refJava.exists()) {
							readFromOutput( refJava, merged );
						}
					}
					break;
				default:
					break;
				}
			}
		} catch (Throwable t) {
			// Best-effort: a classpath problem just means "no aliases".
		}

		// Temporary bridge: an ng project whose ng-appserver predates the shipped
		// parsley-tag-aliases.properties (e.g. the 0.1.1 release) declares nothing — and the
		// alternative fallback, the legacy WebObjects shortcut table, is the wrong vocabulary
		// for it. So use the bundled copy of ng-objects' own registry instead. A real file on
		// the classpath always wins (we only get here when none was found); the copy is synced
		// from ng-objects (see apiext/ng/README.md) and goes away once an ng-appserver that
		// ships its own is the floor.
		if (merged.isEmpty() && isNGProject( project )) {
			mergeFirstWins( merged, bundledNGAliases() );
		}
		return Collections.unmodifiableMap( merged );
	}

	private static boolean isNGProject(IJavaProject project) {
		try {
			final org.objectstyle.wolips.variables.ParsleyProject parsleyProject = (org.objectstyle.wolips.variables.ParsleyProject) project.getProject().getAdapter( org.objectstyle.wolips.variables.ParsleyProject.class );
			return parsleyProject != null && parsleyProject.isNGProject();
		} catch (Throwable t) {
			return false;
		}
	}

	/** The bundled copy of ng-appserver's tag registry ({@code /apiext/ng/parsley-tag-aliases.properties}), or empty. */
	private static Properties bundledNGAliases() {
		final Properties props = new Properties();
		try {
			final java.net.URL url = tk.eclipse.plugin.htmleditor.HTMLPlugin.getDefault().getBundle().getEntry( "/apiext/ng/" + ALIASES_RESOURCE );
			if (url != null) {
				try (InputStream in = url.openStream()) {
					props.load( in );
				}
			}
		} catch (Throwable t) {
			// no bundled copy — nothing to bridge with
		}
		return props;
	}

	/** Reads the alias resource from a project's default output folder (e.g. target/classes). */
	private static void readFromOutput(IJavaProject project, Map<String, String> merged) {
		try {
			final IPath output = project.getOutputLocation(); // workspace-relative
			if (output != null) {
				final IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember( output.append( ALIASES_RESOURCE ) );
				if (res instanceof IFile && res.exists()) {
					try (InputStream in = ((IFile) res).getContents()) {
						final Properties props = new Properties();
						props.load( in );
						mergeFirstWins( merged, props );
					}
				}
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	/** Reads the alias resource from a library classpath path (a jar file, or a folder on disk). */
	private static Properties readFromLibraryPath(IPath path) {
		if (path == null) {
			return null;
		}
		try {
			// A workspace-relative folder library (e.g. another project's classes folder).
			final IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember( path );
			if (res != null && res.getType() == IResource.FOLDER) {
				final IFile file = ((org.eclipse.core.resources.IFolder) res).getFile( ALIASES_RESOURCE );
				if (file.exists()) {
					try (InputStream in = file.getContents()) {
						final Properties props = new Properties();
						props.load( in );
						return props;
					}
				}
				return null;
			}
			// An absolute filesystem path: a jar, or a plain folder.
			final File f = path.toFile();
			if (f.isFile()) {
				try (JarFile jar = new JarFile( f )) {
					final JarEntry entry = jar.getJarEntry( ALIASES_RESOURCE );
					if (entry != null) {
						try (InputStream in = jar.getInputStream( entry )) {
							final Properties props = new Properties();
							props.load( in );
							return props;
						}
					}
				}
			} else if (f.isDirectory()) {
				final File propsFile = new File( f, ALIASES_RESOURCE );
				if (propsFile.isFile()) {
					try (InputStream in = new java.io.FileInputStream( propsFile )) {
						final Properties props = new Properties();
						props.load( in );
						return props;
					}
				}
			}
		} catch (Throwable t) {
			// ignore
		}
		return null;
	}

	/** Merges props into target, keeping the first-registered value on conflict. No-op if null. */
	private static void mergeFirstWins(Map<String, String> target, Properties props) {
		if (props == null) {
			return;
		}
		for (final String alias : props.stringPropertyNames()) {
			final String value = props.getProperty( alias );
			if (alias.isBlank() || value == null || value.isBlank()) {
				continue;
			}
			target.putIfAbsent( alias.trim(), value.trim() );
		}
	}
}

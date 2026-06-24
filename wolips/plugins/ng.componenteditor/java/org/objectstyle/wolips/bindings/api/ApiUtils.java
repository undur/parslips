package org.objectstyle.wolips.bindings.api;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchPattern;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.osgi.framework.Bundle;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Static utility methods for looking up component API definitions.
 *
 * <p>The read path returns immutable {@link ApiSnapshot} objects. API lookups
 * are cached in {@link ApiCache} with timestamp-based invalidation for
 * project-specific {@code .api} files. The global {@code WebObjectDefinitions.xml}
 * is parsed once and cached for the lifetime of the plugin.
 */
public class ApiUtils {

	/**
	 * Global API snapshots from WebObjectDefinitions.xml, keyed by fully-qualified
	 * class name. Parsed lazily on first access; never reparsed (bundled resource).
	 */
	private static Map<String, ApiSnapshot> _globalApiSnapshots;

	/**
	 * Returns true if the given binding is an action binding, determined by
	 * either having "Actions" as its defaults category, or (when no defaults
	 * are defined) having a name that matches action naming conventions.
	 */
	public static boolean isActionBinding(IApiBinding binding) {
		String defaults = binding.getDefaults();
		if (IApiBinding.ACTIONS_DEFAULT.equals(defaults)) {
			return true;
		}
		if (defaults == null) {
			return isActionBindingName(binding.getName());
		}
		return false;
	}

	/**
	 * Returns true if the given binding name follows action naming conventions:
	 * either exactly "action" or ending with "Action" (e.g. "submitAction").
	 *
	 * <p>This is the name-only heuristic used when no {@code .api} metadata
	 * is available — for example, when a WOD binding is not defined in any API.
	 */
	public static boolean isActionBindingName(String name) {
		return "action".equals(name) || name.endsWith("Action");
	}

	public static int getSelectedDefaults(IApiBinding binding) {
		String defaults = binding.getDefaults();
		if (defaults == null) {
			return 0;
		}
		for (int i = 0; i < IApiBinding.ALL_DEFAULTS.length; i++) {
			if (IApiBinding.ALL_DEFAULTS[i].equals(defaults)) {
				return i;
			}
		}
		return 0;
	}

	// ---- Global API lookup (WebObjectDefinitions.xml) ----------------------

	/**
	 * Looks up a component API definition by class name from the global
	 * {@code WebObjectDefinitions.xml} bundled in the plugin. This is used
	 * for framework-provided elements that don't have project-specific
	 * {@code .api} files.
	 *
	 * <p>The lookup tries the given name first, then falls back to the simple
	 * (unqualified) class name. This is necessary because the global
	 * {@code WebObjectDefinitions.xml} uses short class names (e.g.
	 * {@code "WOString"}) while callers may pass fully-qualified names
	 * (e.g. {@code "com.webobjects.appserver._private.WOString"}).
	 *
	 * @param className the class name to look up (fully-qualified or simple)
	 * @return the matching API snapshot, or null if not found
	 */
	public static ApiSnapshot findGlobalApiSnapshotByClassName(String className) {
		try {
			ensureGlobalApiSnapshots();
			if (_globalApiSnapshots != null) {
				// Try exact match first (handles both FQN-keyed and short-keyed entries)
				ApiSnapshot snapshot = _globalApiSnapshots.get(className);
				if (snapshot != null) {
					return snapshot;
				}

				// Fall back to simple class name — WebObjectDefinitions.xml uses
				// short names like "WOString", not FQN like "com.webobjects...WOString"
				String simpleName = className;
				int dotIndex = className.lastIndexOf('.');
				if (dotIndex >= 0) {
					simpleName = className.substring(dotIndex + 1);
					snapshot = _globalApiSnapshots.get(simpleName);
					if (snapshot != null) {
						return snapshot;
					}
				}

				// NG elements share the same bindings as their WO counterparts.
				// If "NGConditional" isn't found, try "WOConditional". This is a
				// temporary bridge until NG has its own API definitions.
				if (simpleName.startsWith("NG")) {
					return _globalApiSnapshots.get("WO" + simpleName.substring(2));
				}
			}
		} catch (Throwable t) {
			// ignore
		}
		return null;
	}

	/**
	 * Lazily parses the global WebObjectDefinitions.xml into immutable
	 * snapshots. Called once; the result is cached permanently since the
	 * bundled resource never changes.
	 */
	private static synchronized void ensureGlobalApiSnapshots() {
		if (_globalApiSnapshots == null) {
			try {
				Bundle bundle = HTMLPlugin.getDefault().getBundle();
				if (bundle != null) {
					URL woDefinitionsURL = bundle.getEntry("/WebObjectDefinitions.xml");
					if (woDefinitionsURL != null) {
						_globalApiSnapshots = ApiParser.parseAllFromUrl(woDefinitionsURL);
					}
				}
			} catch (Throwable t) {
				// ignore
			}
		}
	}

	/**
	 * Looks up a bundled {@code .apiext} definition for a framework-provided element
	 * by class name. These live in an {@code apiext/} folder next to the global
	 * {@code WebObjectDefinitions.xml} in the plugin, and let us enrich the documentation
	 * for built-in elements (Markdown role doc, accepted binding types, per-binding docs)
	 * beyond what the terse {@code WebObjectDefinitions.xml} carries.
	 *
	 * <p>This is the staging ground for moving element documentation out of the plugin and
	 * into the frameworks that serve the elements: today we keep a small curated set here;
	 * eventually a framework would ship its own {@code .apiext} files. Callers consult this
	 * <em>before</em> {@link #findGlobalApiSnapshotByClassName} so a bundled {@code .apiext}
	 * takes precedence over the plain {@code WebObjectDefinitions.xml} entry.
	 *
	 * <p>Name resolution mirrors the global XML lookup: the given name is tried first, then
	 * the simple (unqualified) name (the files are named by short class name, e.g.
	 * {@code WOString.apiext}), then the NG&rarr;WO bridge ({@code NGFoo} &rarr; {@code WOFoo}).
	 *
	 * @param className the class name to look up (fully-qualified or simple)
	 * @return the raw {@code .apiext} bytes, or null if there's no bundled file for it
	 */
	public static byte[] findGlobalApiextBytes(String className) {
		if (className == null || className.isEmpty()) {
			return null;
		}
		try {
			Bundle bundle = HTMLPlugin.getDefault().getBundle();
			if (bundle == null) {
				return null;
			}

			// Try the name as given, then the simple name, then the NG->WO bridge name.
			byte[] bytes = readBundledApiext(bundle, className);
			if (bytes != null) {
				return bytes;
			}

			String simpleName = className;
			int dotIndex = className.lastIndexOf('.');
			if (dotIndex >= 0) {
				simpleName = className.substring(dotIndex + 1);
				bytes = readBundledApiext(bundle, simpleName);
				if (bytes != null) {
					return bytes;
				}
			}

			// NG elements share their WO counterpart's definition (see the global XML
			// lookup) — fall back to the WO name so a bundled WOFoo.apiext also documents NGFoo.
			if (simpleName.startsWith("NG")) {
				return readBundledApiext(bundle, "WO" + simpleName.substring(2));
			}
		} catch (Throwable t) {
			// Any failure (missing folder, IO, etc.) just means "no bundled .apiext".
		}
		return null;
	}

	/**
	 * Reads {@code apiext/<name>.apiext} from the plugin bundle, or returns null if the
	 * entry doesn't exist or can't be read.
	 */
	private static byte[] readBundledApiext(Bundle bundle, String name) {
		URL url = bundle.getEntry("/apiext/" + name + ".apiext");
		if (url == null) {
			return null;
		}
		try (InputStream in = url.openStream()) {
			return in.readAllBytes();
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * Derives a human-readable origin (framework/bundle/project name) for an element type —
	 * where it comes from. Used by the element-help list and the rendered card so the user
	 * can see which framework an element belongs to.
	 *
	 * <p>Resolution:
	 * <ul>
	 *   <li>Binary types (from a jar or a {@code .framework}) &rarr; the archive's base name,
	 *       e.g. {@code "JavaWebObjects.jar"} &rarr; {@code "JavaWebObjects"}.</li>
	 *   <li>Source types (in the workspace) &rarr; the containing project's name.</li>
	 * </ul>
	 *
	 * @return the origin name, or null if it can't be determined
	 */
	public static String resolveOrigin(IType elementType) {
		if (elementType == null) {
			return null;
		}
		try {
			IPackageFragmentRoot root = (IPackageFragmentRoot) elementType.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (root != null) {
				if (root.getKind() == IPackageFragmentRoot.K_BINARY) {
					// Jar / .framework: use the archive's base file name (strip path + extension).
					IPath path = root.getPath();
					if (path != null && path.lastSegment() != null) {
						String last = path.lastSegment();
						final int dot = last.lastIndexOf('.');
						return dot > 0 ? last.substring(0, dot) : last;
					}
				}
				// Source root: the element lives in this project.
				if (root.getJavaProject() != null) {
					return root.getJavaProject().getElementName();
				}
			}
			// Fallback: the element type's own project.
			if (elementType.getJavaProject() != null) {
				return elementType.getJavaProject().getElementName();
			}
		} catch (Throwable t) {
			// Best-effort only — origin is informational.
		}
		return null;
	}

	// ---- Per-type API lookup (project .api files) --------------------------

	/**
	 * How long (in milliseconds) before a "no .api file" negative cache entry
	 * is re-checked. Keeps the negative cache from permanently hiding a newly
	 * created {@code .api} file.
	 */
	private static final long MISSING_API_TTL_MS = 10_000;

	/**
	 * Lightweight holder for a located {@code .api} file and its modification
	 * timestamp. Used to validate cached snapshots without re-parsing the XML.
	 *
	 * <p>Exactly one of {@link #_file}, {@link #_eclipseFile}, or {@link #_url}
	 * is non-null, depending on how the file was found.
	 */
	private static class ApiFileInfo {
		/** Non-null for plain disk files (e.g. folder-style frameworks). */
		final File _file;
		/** Non-null for Eclipse workspace files (source projects). */
		final IFile _eclipseFile;
		/** Non-null for jar entries. */
		final URL _url;
		/** File modification timestamp ({@code lastModified()} or {@code getModificationStamp()}). */
		final long _timestamp;

		ApiFileInfo(File file, long timestamp) {
			_file = file;
			_eclipseFile = null;
			_url = null;
			_timestamp = timestamp;
		}

		ApiFileInfo(IFile eclipseFile) {
			_file = null;
			_eclipseFile = eclipseFile;
			_url = null;
			_timestamp = eclipseFile.getModificationStamp();
		}

		ApiFileInfo(URL url, long timestamp) {
			_file = null;
			_eclipseFile = null;
			_url = url;
			_timestamp = timestamp;
		}

		/**
		 * Parses the located {@code .api} file into an {@link ApiSnapshot}.
		 */
		ApiSnapshot parse() throws Exception {
			if (_eclipseFile != null) {
				return ApiParser.parseFile(_eclipseFile.getLocation().toFile());
			} else if (_file != null) {
				return ApiParser.parseFile(_file);
			} else {
				return ApiParser.parseUrl(_url);
			}
		}
	}

	/**
	 * Finds the API snapshot for a given element type by searching for its
	 * {@code .api} file in the project classpath (jar files, source folders,
	 * or component bundles).
	 *
	 * <p>Results are cached in the provided {@link ApiCache} with timestamp-based
	 * invalidation: on every cache hit, the {@code .api} file's current
	 * modification timestamp is compared with the cached timestamp. If the file
	 * has been modified, the snapshot is reparsed. This ensures changes in
	 * dependency projects are picked up immediately without requiring an Eclipse
	 * restart.
	 *
	 * <p>For framework-private classes (in {@code ng.appserver.templating._private}
	 * or {@code com.webobjects.appserver._private}), the global
	 * {@code WebObjectDefinitions.xml} is consulted instead.
	 *
	 * @param elementType the JDT type to look up
	 * @param cache the API cache to use
	 * @return the matching API snapshot, or null if no {@code .api} file was found
	 */
	public static ApiSnapshot findApiSnapshot(IType elementType, ApiCache cache) throws ApiModelException {
		if (elementType == null) {
			return null;
		}

		String fqn = elementType.getFullyQualifiedName();

		// Framework-private classes use the global WebObjectDefinitions.xml,
		// which is a bundled resource that never changes at runtime.
		if (fqn.startsWith("ng.appserver.templating._private.") || fqn.startsWith("ng.appserver.templating.elements.") || fqn.startsWith("com.webobjects.appserver._private.")) {
			ApiSnapshot snapshot = cache.getApiSnapshotForType(elementType);
			if (snapshot != null) {
				return snapshot;
			}
			snapshot = findGlobalApiSnapshotByClassName(fqn);
			if (snapshot != null) {
				cache.setApiSnapshotForType(snapshot, elementType);
			}
			return snapshot;
		}

		// For project/classpath .api files: validate cached entries against
		// the file's current modification timestamp.
		try {
			// Check positive cache — validate timestamp if present
			ApiSnapshot snapshot = cache.getApiSnapshotForType(elementType);
			if (snapshot != null) {
				Long cachedTimestamp = cache.getApiTimestampForType(elementType);
				if (cachedTimestamp != null) {
					ApiFileInfo fileInfo = findApiFile(elementType);
					if (fileInfo != null && fileInfo._timestamp == cachedTimestamp.longValue()) {
						// File hasn't changed — cache hit
						return snapshot;
					}
				}
				// Timestamp missing, mismatched, or file disappeared — invalidate
				cache.clearApiForElementType(elementType);
				snapshot = null;
			}

			// Check negative cache — re-check after TTL expires
			Boolean apiMissing = cache.apiMissingForElementType(elementType);
			if (apiMissing != null && apiMissing.booleanValue()) {
				Long missingTimestamp = cache.getApiMissingTimestampForType(elementType);
				if (missingTimestamp != null) {
					long age = System.currentTimeMillis() - missingTimestamp.longValue();
					if (age < MISSING_API_TTL_MS) {
						// Still within TTL — trust the negative cache
						return null;
					}
				}
				// TTL expired — clear and re-check
				cache.clearApiForElementType(elementType);
			}

			// Full lookup: locate the .api file and parse it
			ApiFileInfo fileInfo = findApiFile(elementType);
			if (fileInfo != null) {
				snapshot = fileInfo.parse();
			}

			// Update cache with result and timestamp
			if (snapshot == null) {
				cache.setApiMissingForElementType(true, elementType);
			} else {
				cache.setApiSnapshotForType(snapshot, elementType);
				if (fileInfo != null) {
					cache.setApiTimestampForType(fileInfo._timestamp, elementType);
				}
			}

			return snapshot;
		} catch (Throwable t) {
			Activator.getDefault().log("Failed to parse API for " + elementType.getElementName() + ".", t);
			return null;
		}
	}

	/**
	 * Locates the {@code .api} file for the given type on the classpath without
	 * parsing it. Returns file metadata (location + timestamp) for cache
	 * validation, or null if no {@code .api} file was found.
	 *
	 * <p>This is intentionally cheap — it only stats the file system, without
	 * reading or parsing XML.
	 *
	 * <p>Handles three cases:
	 * <ol>
	 *   <li>Class file in a jar — looks for {@code Resources/TypeName.api} in the jar</li>
	 *   <li>Class file on disk — looks for {@code TypeName.api} as a sibling resource</li>
	 *   <li>Source file — uses the component locate infrastructure to find the {@code .api} file</li>
	 * </ol>
	 */
	private static ApiFileInfo findApiFile(IType elementType) throws Exception {
		IOpenable typeContainer = elementType.getOpenable();

		if (typeContainer instanceof IClassFile) {
			IClassFile classFile = (IClassFile) typeContainer;
			IJavaElement parent = classFile.getParent();
			if (parent instanceof IPackageFragment) {
				IPackageFragment parentPackage = (IPackageFragment) parent;
				IJavaElement parentParent = parentPackage.getParent();

				// Check for jar-style framework: look inside the jar for Resources/TypeName.api
				if (parentParent instanceof IPackageFragmentRoot && "jar".equalsIgnoreCase(parentParent.getPath().getFileExtension())) {
					String jarResourcePath = "Resources/" + elementType.getElementName() + ".api";
					String jarOSPath = parentParent.getPath().toOSString();
					File jarOSFile = new File(jarOSPath);

					if (jarOSFile.exists()) {
						JarFile jarFile = new JarFile(jarOSFile);
						try {
							JarEntry je = jarFile.getJarEntry(jarResourcePath);
							if (je != null && je.getSize() != 0) {
								// Use the JAR file's own modification time — when the JAR is
								// rebuilt, this changes even if the entry timestamp doesn't.
								long timestamp = jarOSFile.lastModified();
								URL url = new URL("jar:file:" + jarOSPath + "!/" + jarResourcePath);
								return new ApiFileInfo(url, timestamp);
							}
						} finally {
							jarFile.close();
						}
					}
				}

				// Check for folder-style: look for TypeName.api as a sibling file
				IPath packagePath = parentPackage.getPath();
				IPath apiPath = packagePath.removeLastSegments(2).append(elementType.getElementName()).addFileExtension("api");
				File apiFile = apiPath.toFile();
				if (apiFile.exists()) {
					return new ApiFileInfo(apiFile, apiFile.lastModified());
				}
			}
		} else if (typeContainer instanceof ICompilationUnit) {
			// Source file — use the component locate infrastructure
			LocalizedComponentsLocateResult componentsLocateResults = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(elementType.getJavaProject().getProject(), elementType.getElementName());
			IFile apiFile = componentsLocateResults.getDotApi();
			if (apiFile != null && apiFile.exists()) {
				return new ApiFileInfo(apiFile);
			}
		}

		return null;
	}

	/**
	 * Reads the raw bytes of the {@code .apiext} sibling for an element, if one exists
	 * next to its {@code .api} file. The {@code .apiext} format is the in-flux extended
	 * element API AjaxSlim is prototyping; this just <em>locates and reads</em> it — the
	 * caller parses (and decides parsability, the in-flux gate).
	 *
	 * <p>Resolution mirrors {@link #findApiFile}: an {@code .apiext} lives in the same
	 * place as the {@code .api} (jar {@code Resources/}, folder-style sibling, or source
	 * sibling), with the extension swapped.
	 *
	 * @return the file bytes, or null if there is no readable {@code .apiext} sibling
	 */
	public static byte[] findApiextBytes(IType elementType) {
		if (elementType == null) {
			return null;
		}
		try {
			final IOpenable typeContainer = elementType.getOpenable();

			if (typeContainer instanceof IClassFile) {
				final IJavaElement parent = ((IClassFile) typeContainer).getParent();
				if (parent instanceof IPackageFragment) {
					final IJavaElement parentParent = ((IPackageFragment) parent).getParent();

					// jar-style framework: Resources/TypeName.apiext inside the jar
					if (parentParent instanceof IPackageFragmentRoot && "jar".equalsIgnoreCase(parentParent.getPath().getFileExtension())) {
						final String jarResourcePath = "Resources/" + elementType.getElementName() + ".apiext";
						final File jarOSFile = new File(parentParent.getPath().toOSString());
						if (jarOSFile.exists()) {
							try (JarFile jarFile = new JarFile(jarOSFile)) {
								final JarEntry je = jarFile.getJarEntry(jarResourcePath);
								if (je != null && je.getSize() != 0) {
									try (InputStream is = jarFile.getInputStream(je)) {
										return is.readAllBytes();
									}
								}
							}
						}
					}

					// folder-style: TypeName.apiext sibling
					final IPath packagePath = ((IPackageFragment) parent).getPath();
					final IPath apiextPath = packagePath.removeLastSegments(2).append(elementType.getElementName()).addFileExtension("apiext");
					final File apiextFile = apiextPath.toFile();
					if (apiextFile.exists()) {
						return Files.readAllBytes(apiextFile.toPath());
					}
				}
			} else if (typeContainer instanceof ICompilationUnit) {
				// Source file: the .apiext sits next to the located .api.
				final LocalizedComponentsLocateResult locate = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(elementType.getJavaProject().getProject(), elementType.getElementName());
				final IFile apiFile = locate.getDotApi();
				if (apiFile != null) {
					final IFile apiextFile = apiFile.getParent().getFile(new org.eclipse.core.runtime.Path(elementType.getElementName() + ".apiext"));
					if (apiextFile.exists() && apiextFile.getLocation() != null) {
						return Files.readAllBytes(apiextFile.getLocation().toFile().toPath());
					}
				}
			}
		}
		catch (Exception e) {
			// Locating/reading failed — treat as "no .apiext", caller falls back to .api.
		}
		return null;
	}

	// ---- Valid values lookup ------------------------------------------------

	/**
	 * Looks up valid values for a binding by name, resolving the API type and
	 * finding the matching binding definition.
	 */
	public static String[] getValidValues(String partialValue, IJavaProject javaProject, IType componentType, IType apiType, String bindingName, TypeCache typeCache) throws JavaModelException, ApiModelException {
		String[] validValues = null;
		ApiSnapshot snapshot = ApiUtils.findApiSnapshot(apiType, typeCache.getApiCache(javaProject));
		if (snapshot != null) {
			IApiBinding matchingBinding = snapshot.getBinding(bindingName);
			if (matchingBinding != null) {
				validValues = matchingBinding.getValidValues(partialValue, javaProject, componentType, typeCache);
			}
		}
		return validValues;
	}

	/**
	 * Returns valid values for a binding based on its defaults type (Boolean,
	 * Actions, Date Format Strings, etc.).
	 */
	public static String[] getValidValues(IApiBinding binding, String partialValue, IJavaProject javaProject, IType componentType, TypeCache typeCache) throws JavaModelException {
		Set<String> validValues = new HashSet<>();
		int selectedDefaults = binding.getSelectedDefaults();
		String defaultsName = IApiBinding.ALL_DEFAULTS[selectedDefaults];
		if ("Boolean".equals(defaultsName)) {
			validValues.add("true");
			validValues.add("false");
		} else if ("YES/NO".equals(defaultsName)) {
			validValues.add("yes");
			validValues.add("no");
		} else if ("Date Format Strings".equals(defaultsName)) {
			validValues.add("\"%m/%d/%y\"");
			validValues.add("\"%B %d, %Y\"");
			validValues.add("\"%b %d, %Y\"");
			validValues.add("\"%A, %B %d, %Y\"");
			validValues.add("\"%A, %b %d, %Y\"");
			validValues.add("\"%d.%m.%y\"");
			validValues.add("\"%d %B %y\"");
			validValues.add("\"%d %b %y\"");
			validValues.add("\"%A %d %B %Y\"");
			validValues.add("\"%A %d %b %Y\"");
			validValues.add("\"%x\"");
			validValues.add("\"%H:%M:%S\"");
			validValues.add("\"%I:%M:%S %p\"");
			validValues.add("\"%H:%M\"");
			validValues.add("\"%I:%M %p\"");
			validValues.add("\"%X\"");
		} else if ("Number Format Strings".equals(defaultsName)) {
			validValues.add("\"0\"");
			validValues.add("\"0.00\"");
			validValues.add("\"0.##\"");
			validValues.add("\"#,##0\"");
			validValues.add("\"_,__0\"");
			validValues.add("\"#,##0.00\"");
			validValues.add("\"$#,##0\"");
			validValues.add("\"$#,##0.00\"");
			validValues.add("\"$#,##0.##\"");
		} else if ("MIME Types".equals(defaultsName)) {
			validValues.add("\"image/gif\"");
			validValues.add("\"image/jpeg\"");
			validValues.add("\"image/png\"");
		} else if ("Direct Actions".equals(defaultsName)) {
			// ng-objects does not have WODirectAction; direct action completion disabled
		} else if ("Direct Action Classes".equals(defaultsName)) {
			// ng-objects does not have WODirectAction; direct action class completion disabled
		} else if ("Page Names".equals(defaultsName)) {
			if (partialValue != null && partialValue.startsWith("\"")) {
				TypeNameCollector typeNameCollector = new TypeNameCollector(javaProject, false);
				BindingReflectionUtils.findMatchingElementClassNames(partialValue.substring(1), SearchPattern.R_PREFIX_MATCH, typeNameCollector, new NullProgressMonitor());
				for (String typeName : typeNameCollector.getTypeNames()) {
					int dotIndex = typeName.lastIndexOf('.');
					if (dotIndex != -1) {
						typeName = typeName.substring(dotIndex + 1);
					}
					validValues.add("\"" + typeName + "\"");
				}
			}
		} else if ("Frameworks".equals(defaultsName)) {
			if (partialValue != null && partialValue.startsWith("\"")) {
				validValues.add("\"app\"");
			}
		} else if ("Actions".equals(defaultsName)) {
			List<BindingValueKey> bindingKeysList = BindingReflectionUtils.getBindingKeys(javaProject, componentType, "", false, BindingReflectionUtils.VOID_ONLY, false, typeCache);
			for (BindingValueKey key : bindingKeysList) {
				validValues.add(key.getBindingName());
			}
		}
		return validValues.toArray(new String[validValues.size()]);
	}

}

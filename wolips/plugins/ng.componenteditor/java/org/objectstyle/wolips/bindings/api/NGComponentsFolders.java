package org.objectstyle.wolips.bindings.api;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds files in ng-objects' namespace components folders: {@code ng/<namespace>/components/}
 * on the classpath, where an ng framework or app keeps its component templates and the
 * {@code .apiext} files of its elements (ng-appserver ships e.g.
 * {@code ng/ng/components/NGString.apiext}). This mirrors WebObjects, where a framework's
 * {@code .api} files sit in its {@code Resources/} with the components.
 *
 * <p>The element's namespace isn't known from its class, so every namespace in the classpath
 * root that holds the class is searched. A root holding the same file in two namespaces is a
 * mistake in that framework; the first namespace in name order wins, so the answer is stable.
 */
final class NGComponentsFolders {

	/** The folder holding the namespaces, at the root of a jar or class folder. */
	static final String NG_FOLDER = "ng";

	/** The folder inside a namespace holding its components. */
	static final String COMPONENTS_FOLDER = "components";

	private NGComponentsFolders() {
	}

	/**
	 * The jar entry name of a file in a namespace components folder ({@code ng/<namespace>/components/<file>}),
	 * keyed by file name, for every such entry among {@code entryNames}. Nested folders inside
	 * {@code components/} are ignored; a file name present in several namespaces maps to the
	 * entry of the first namespace in name order.
	 */
	static Map<String, String> indexEntries(Iterable<String> entryNames) {
		final Map<String, String> index = new HashMap<>();
		for (final String entryName : entryNames) {
			final String[] segments = entryName.split("/");
			if (segments.length != 4 || !NG_FOLDER.equals(segments[0]) || !COMPONENTS_FOLDER.equals(segments[2]) || segments[1].isEmpty() || segments[3].isEmpty()) {
				continue;
			}
			index.merge(segments[3], entryName, (existing, candidate) -> namespaceOf(candidate).compareTo(namespaceOf(existing)) < 0 ? candidate : existing);
		}
		return index;
	}

	/**
	 * The file {@code ng/<namespace>/components/<fileName>} under the directory {@code root}
	 * (a class folder, a source folder or an output folder), or null when no namespace has it.
	 */
	static File findInDirectory(File root, String fileName) {
		final File[] namespaces = new File(root, NG_FOLDER).listFiles(File::isDirectory);
		if (namespaces == null) {
			return null;
		}
		Arrays.sort(namespaces, Comparator.comparing(File::getName));
		for (final File namespace : namespaces) {
			final File file = new File(new File(namespace, COMPONENTS_FOLDER), fileName);
			if (file.isFile()) {
				return file;
			}
		}
		return null;
	}

	private static String namespaceOf(String entryName) {
		return entryName.split("/")[1];
	}
}

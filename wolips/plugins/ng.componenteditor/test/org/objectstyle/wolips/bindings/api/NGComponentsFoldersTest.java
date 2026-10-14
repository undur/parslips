package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link NGComponentsFolders}: finding files in ng-objects' namespace components
 * folders ({@code ng/<namespace>/components/}), in jar entry lists and in directories.
 */
public class NGComponentsFoldersTest {

	@Rule
	public TemporaryFolder _temp = new TemporaryFolder();

	// ---- indexEntries (jars) ---------------------------------------------------

	@Test
	public void indexEntries_findsFilesInAnyNamespace() {
		final Map<String, String> index = NGComponentsFolders.indexEntries(List.of(
				"ng/ng/components/NGString.apiext",
				"ng/control/components/NGControlLogin.apiext",
				"ng/appserver/templating/elements/NGString.class"));
		assertEquals("ng/ng/components/NGString.apiext", index.get("NGString.apiext"));
		assertEquals("ng/control/components/NGControlLogin.apiext", index.get("NGControlLogin.apiext"));
		assertEquals(2, index.size());
	}

	@Test
	public void indexEntries_ignoresOtherFoldersAndNesting() {
		final Map<String, String> index = NGComponentsFolders.indexEntries(List.of(
				"ng/ng/components/",
				"ng/ng/components/NGExceptionPage.wo/NGExceptionPage.html",
				"ng/ng/app-resources/NGString.apiext",
				"Resources/NGString.apiext",
				"other/ng/components/NGString.apiext"));
		assertTrue(index.isEmpty());
	}

	@Test
	public void indexEntries_firstNamespaceInNameOrderWins() {
		final Map<String, String> index = NGComponentsFolders.indexEntries(List.of(
				"ng/zeta/components/Shared.apiext",
				"ng/alpha/components/Shared.apiext",
				"ng/mid/components/Shared.apiext"));
		assertEquals("ng/alpha/components/Shared.apiext", index.get("Shared.apiext"));
	}

	// ---- findInDirectory (class, source and output folders) -------------------

	@Test
	public void findInDirectory_findsFileInANamespace() throws IOException {
		final File root = _temp.newFolder("classes");
		final File file = write(root, "ng/app/components/MyElement.apiext");
		assertEquals(file, NGComponentsFolders.findInDirectory(root, "MyElement.apiext"));
	}

	@Test
	public void findInDirectory_missing_returnsNull() throws IOException {
		final File root = _temp.newFolder("classes");
		assertNull(NGComponentsFolders.findInDirectory(root, "MyElement.apiext"));
		write(root, "ng/app/components/Other.apiext");
		assertNull(NGComponentsFolders.findInDirectory(root, "MyElement.apiext"));
	}

	@Test
	public void findInDirectory_ignoresAFolderOfThatName() throws IOException {
		final File root = _temp.newFolder("classes");
		new File(root, "ng/app/components/MyElement.apiext").mkdirs();
		assertNull(NGComponentsFolders.findInDirectory(root, "MyElement.apiext"));
	}

	@Test
	public void findInDirectory_firstNamespaceInNameOrderWins() throws IOException {
		final File root = _temp.newFolder("classes");
		write(root, "ng/zeta/components/Shared.apiext");
		final File alpha = write(root, "ng/alpha/components/Shared.apiext");
		assertEquals(alpha, NGComponentsFolders.findInDirectory(root, "Shared.apiext"));
	}

	private static File write(File root, String path) throws IOException {
		final File file = new File(root, path);
		file.getParentFile().mkdirs();
		Files.writeString(file.toPath(), "<wo/>");
		return file;
	}
}

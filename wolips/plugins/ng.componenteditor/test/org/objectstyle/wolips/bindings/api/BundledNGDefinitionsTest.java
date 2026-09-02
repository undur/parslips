package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

/**
 * Guards the bundled copies of ng-objects' tag registry and element definitions under
 * {@code apiext/ng/} — the temporary bridge for projects on an ng-appserver that doesn't ship
 * them yet (see {@code apiext/ng/README.md}). They're copies, so the thing that can go wrong is
 * drift: a file that no longer parses, an alias pointing at an element with no definition, or a
 * definition no tag reaches. Surefire's CWD is the plugin base dir, so the folder resolves directly.
 */
public class BundledNGDefinitionsTest {

	private static final File DIR = new File("apiext/ng");

	@Test
	public void everyBundledNGApiextParses() throws Exception {
		final File[] files = DIR.listFiles((d, name) -> name.endsWith(".apiext"));
		assertNotNull("apiext/ng/ should exist", files);
		assertTrue("expected the ng element definitions", files.length >= 26);
		for (final File file : files) {
			final ApiextModel model = ApiextModel.parse(Files.readAllBytes(file.toPath()));
			assertNotNull("failed to parse " + file.getName(), model);
			assertEquals("class attribute must match the file name in " + file.getName(),
					file.getName().replace(".apiext", ""), model.getClassName());
		}
	}

	@Test
	public void everyAliasTargetHasADefinitionAndViceVersa() throws Exception {
		final Properties aliases = new Properties();
		try (InputStream in = new FileInputStream(new File(DIR, "parsley-tag-aliases.properties"))) {
			aliases.load(in);
		}
		assertTrue("the tag registry should not be empty", aliases.size() >= 26);

		final Set<String> defined = new HashSet<>();
		for (final File file : DIR.listFiles((d, name) -> name.endsWith(".apiext"))) {
			defined.add(file.getName().replace(".apiext", ""));
		}
		final Set<String> targeted = new HashSet<>();
		for (final String alias : aliases.stringPropertyNames()) {
			final String target = aliases.getProperty(alias).trim();
			assertTrue("tag '" + alias + "' points at '" + target + "', which has no bundled .apiext", defined.contains(target));
			targeted.add(target);
		}
		for (final String element : defined) {
			assertTrue("bundled definition '" + element + "' is reached by no tag in the registry", targeted.contains(element));
		}
	}
}

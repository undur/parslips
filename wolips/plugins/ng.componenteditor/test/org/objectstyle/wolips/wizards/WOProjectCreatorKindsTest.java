package org.objectstyle.wolips.wizards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** The generator's name helpers and the three template kinds, written to a temp directory (no Eclipse). */
public class WOProjectCreatorKindsTest {

	@Test
	public void packageNameIsDerivedFromTheProjectName() {
		assertEquals("my.cool.app", WOProjectCreator.derivePackageName("my-cool_app"));
		assertEquals("myapp", WOProjectCreator.derivePackageName("MyApp"));
		assertEquals("shop._2go", WOProjectCreator.derivePackageName("shop-2go"));
		assertEquals("a.b", WOProjectCreator.derivePackageName("a--b"));
	}

	@Test
	public void namesAreValidated() {
		assertNull(WOProjectCreator.validateProjectName("my-app_2.0"));
		assertNotNull(WOProjectCreator.validateProjectName(""));
		assertNotNull(WOProjectCreator.validateProjectName("../evil"));
		assertNotNull(WOProjectCreator.validateProjectName("has space"));
		assertNull(WOProjectCreator.validatePackageName("is.rebbi.thing"));
		assertNotNull(WOProjectCreator.validatePackageName("is..thing"));
		assertNotNull(WOProjectCreator.validatePackageName("2fast.app"));
	}

	@Test
	public void mavenKindWritesAPlainJarProject() throws Exception {
		final Path dir = Files.createTempDirectory("parslips-maven").resolve("my-lib");
		final WOProjectCreator creator = new WOProjectCreator("my-lib", "my.lib", WOProjectCreator.Kind.MAVEN, dir);
		final Path entry = creator.createProject();

		assertEquals(dir.resolve("pom.xml"), entry);
		final String pom = Files.readString(entry);
		assertTrue(pom.contains("<artifactId>my-lib</artifactId>"));
		assertTrue(pom.contains("<groupId>my.lib</groupId>"));
		assertTrue(pom.contains("<packaging>jar</packaging>"));
		assertTrue(Files.isDirectory(dir.resolve("src/main/java/my/lib")));
		assertTrue(Files.isDirectory(dir.resolve("src/test/java/my/lib")));
		assertFalse("a library has no build.properties / principalClass", Files.exists(dir.resolve("build.properties")));
		assertNull(creator.mainClassName());
	}

	@Test
	public void appKindsNameTheirMainClassAndDeclareIt() throws Exception {
		final Path dir = Files.createTempDirectory("parslips-ng").resolve("my-app");
		final WOProjectCreator creator = new WOProjectCreator("my-app", "my.app", WOProjectCreator.Kind.NG_APP, dir);
		creator.createProject();

		assertEquals("my.app.Application", creator.mainClassName());
		assertTrue(Files.readString(dir.resolve("build.properties")).contains("principalClass=my.app.Application"));
		assertTrue(Files.exists(dir.resolve("src/main/java/my/app/Application.java")));
		assertTrue(Files.exists(dir.resolve("src/main/resources/ng/app/components/Main.html")));
	}

	@Test
	public void woAppDoesNotPickAnAdaptor() throws Exception {
		// The template stays adaptor-neutral: no adaptor dependency, no WOAdaptor setting. Which
		// adaptor runs is the machine's or the project's later choice, not the template's.
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		assertFalse(Files.readString(dir.resolve("pom.xml")).contains("wo-adaptor-jetty"));
		assertFalse(Files.readString(dir.resolve("src/main/woresources/Properties")).contains("WOAdaptor"));
	}
}

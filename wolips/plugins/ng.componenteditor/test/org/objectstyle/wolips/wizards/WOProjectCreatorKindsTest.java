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
	public void woAppRunsOnWoAdaptorJetty() throws Exception {
		// wo-adaptor-jetty selects itself when it's a dependency, so nothing names an adaptor.
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		final String pom = Files.readString(dir.resolve("pom.xml"));
		assertTrue(pom, pom.contains("<artifactId>wo-adaptor-jetty</artifactId>"));
		assertEquals("", Files.readString(dir.resolve("src/main/woresources/Properties")));
	}

	@Test
	public void woAppIsACurrentWonderSlimApp() throws Exception {
		// What a new wonder-slim app is today: routes through ERXRouter, an ERXSession, no DirectAction.
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		final String application = Files.readString(dir.resolve("src/main/java/my/wo/app/Application.java"));
		assertTrue(application, application.contains("import er.extensions.routing.ERXRouter;"));
		assertTrue(application, application.contains("routes.map( \"/\", Main.class );"));
		assertTrue(application, application.contains("ERXApplication.main( args, Application.class );"));
		assertTrue(Files.readString(dir.resolve("src/main/java/my/wo/app/Session.java")).contains("public class Session extends ERXSession"));
		// The router is the default request handler, and the .wo needs only its .html.
		assertFalse(Files.exists(dir.resolve("src/main/java/my/wo/app/DirectAction.java")));
		assertTrue(Files.readString(dir.resolve("src/main/components/Main.wo/Main.html")).contains("<title>my-wo-app</title>"));
		assertFalse(Files.exists(dir.resolve("src/main/components/Main.wo/Main.wod")));
		assertFalse(Files.exists(dir.resolve("src/main/components/Main.wo/Main.woo")));
		assertTrue(Files.readString(dir.resolve("build.properties")).contains("project.base=wo"));
	}

	@Test
	public void woAppPomBuildsAnywhere() throws Exception {
		// ERExtensions and wo-adaptor-jetty aren't on Maven Central, so the pom names WOCommunity.
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		final String pom = Files.readString(dir.resolve("pom.xml"));
		assertTrue(pom, pom.contains("<url>https://maven.wocommunity.org/content/groups/public</url>"));
		assertTrue(pom, pom.contains("<maven.compiler.release>25</maven.compiler.release>"));
		assertFalse(pom, pom.contains("JavaWebObjects"));
		assertTrue(pom, pom.contains("<groupId>my.wo.app</groupId>"));
		assertTrue(pom, pom.contains("<artifactId>my-wo-app</artifactId>"));
	}

	@Test
	public void woAppDependsOnNoAjaxFramework() throws Exception {
		// The generated app uses no Ajax elements, so it pulls in neither Ajax nor AjaxSlim.
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		final String pom = Files.readString(dir.resolve("pom.xml"));
		assertTrue(pom, pom.contains("<artifactId>ERExtensions</artifactId>"));
		assertFalse(pom, pom.contains("<artifactId>Ajax"));
	}

	@Test
	public void woAppMainIsAnErxComponent() throws Exception {
		// New components copy Main; plain WOComponent lacks the typed pageWithName(Class).
		final Path dir = Files.createTempDirectory("parslips-wo").resolve("my-wo-app");
		new WOProjectCreator("my-wo-app", "my.wo.app", WOProjectCreator.Kind.WO_APP, dir).createProject();

		final String main = Files.readString(dir.resolve("src/main/java/my/wo/app/components/Main.java"));
		assertTrue(main, main.contains("public class Main extends ERXComponent"));
		assertTrue(main, main.contains("import er.extensions.components.ERXComponent;"));
		assertFalse(main, main.contains("WOComponent;"));
	}
}

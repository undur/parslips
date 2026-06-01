package org.objectstyle.wolips.wizards;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes all files and folders for a new ng-objects or WebObjects Maven project
 * to a directory on disk.
 *
 * <p>This class has zero Eclipse dependencies — it works purely with
 * {@link java.nio.file.Path}. The caller (typically {@link WOProjectCreationPage})
 * is responsible for importing the result into Eclipse as a Maven project via m2e.
 *
 * <p>This separation means the same file generation logic could later be reused
 * by a Maven archetype or a command-line scaffolding tool.
 *
 * <p>The generated project follows standard Maven conventions:
 * <pre>
 *   pom.xml                              — Maven project descriptor
 *   build.properties                      — Parsley framework detection (project.base=ng or project.base=wo)
 *   src/main/java/{package}/              — Application, Session, DirectAction
 *   src/main/java/{package}/components/   — Main.java
 *
 *   ng-objects:
 *   src/main/resources/ng/app/components/          — Main.html (standalone template)
 *   src/main/resources/ng/app/app-resources/        — application resources
 *   src/main/resources/ng/app/webserver-resources/  — static web assets
 *
 *   WebObjects:
 *   src/main/components/Main.wo/          — Main.html, Main.wod, Main.woo (bundle template)
 *   src/main/woresources/                 — Properties file
 *   src/main/webserver-resources/         — static web assets
 * </pre>
 *
 * <p>File contents use Java text blocks with {@link String#format} — no Velocity templates.
 */
public class WOProjectCreator {

	private final String _projectName;
	private final String _packageName;
	private final boolean _isNG;
	private final Path _projectDir;

	/**
	 * @param projectName  the project name (also used as Maven artifactId)
	 * @param packageName  the Java package for generated classes
	 * @param isNG         true for ng-objects, false for WebObjects
	 * @param projectDir   the directory where files will be written
	 */
	public WOProjectCreator(String projectName, String packageName, boolean isNG, Path projectDir) {
		_projectName = projectName;
		_packageName = packageName;
		_isNG = isNG;
		_projectDir = projectDir;
	}

	/**
	 * Writes the complete project structure to disk.
	 *
	 * <p>Creates the project directory if it doesn't exist. Does not create
	 * any Eclipse project metadata — the caller should import via m2e.
	 *
	 * @return the path to Main.html (for revealing in an editor after import)
	 */
	public Path createProject() throws IOException {
		Files.createDirectories(_projectDir);

		// 1. Create folder structure
		String packagePath = _packageName.replace('.', '/');

		Files.createDirectories(_projectDir.resolve("src/main/java/" + packagePath + "/components"));
		if (_isNG) {
			// ng-objects: resources live under src/main/resources/ng/app/
			Files.createDirectories(_projectDir.resolve("src/main/resources/ng/app/components"));
			Files.createDirectories(_projectDir.resolve("src/main/resources/ng/app/app-resources"));
			Files.createDirectories(_projectDir.resolve("src/main/resources/ng/app/webserver-resources"));
		}
		else {
			// WebObjects: traditional WO folder layout under src/main/
			Files.createDirectories(_projectDir.resolve("src/main/components/Main.wo"));
			Files.createDirectories(_projectDir.resolve("src/main/resources"));
			Files.createDirectories(_projectDir.resolve("src/main/woresources"));
			Files.createDirectories(_projectDir.resolve("src/main/webserver-resources"));
		}

		// 2. Write files
		writeFile("pom.xml", generatePomXml());
		writeFile("build.properties", generateBuildProperties());

		// Java sources
		String javaBase = "src/main/java/" + packagePath + "/";
		writeFile(javaBase + "Application.java", generateApplicationJava());
		writeFile(javaBase + "Session.java", generateSessionJava());
		writeFile(javaBase + "DirectAction.java", generateDirectActionJava());
		writeFile(javaBase + "components/Main.java", generateMainJava());

		// Component template files
		String componentBase = _isNG
				? "src/main/resources/ng/app/components/"
				: "src/main/components/Main.wo/";
		writeFile(componentBase + "Main.html", generateMainHtml());

		// Bundle templates (.wo folders) need .wod and .woo files.
		// Standalone templates (ng-objects) are single .html files — no .wod/.woo.
		if (!_isNG) {
			writeFile(componentBase + "Main.wod", "");
			writeFile(componentBase + "Main.woo", generateMainWoo());
		}

		// WO-specific files
		if (!_isNG) {
			writeFile("src/main/woresources/Properties", generateWOProperties());
		}

		return _projectDir.resolve(componentBase + "Main.html");
	}

	/**
	 * Writes a UTF-8 file relative to the project directory.
	 */
	private void writeFile(String relativePath, String content) throws IOException {
		Path file = _projectDir.resolve(relativePath);
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	// ---- Content generators ----

	private String generatePomXml() {
		if (_isNG) {
			return String.format("""
					<?xml version="1.0" encoding="UTF-8"?>
					<project xmlns="http://maven.apache.org/POM/4.0.0"
					\t\txmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
					\t\txsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
					\t<modelVersion>4.0.0</modelVersion>

					\t<groupId>%s</groupId>
					\t<artifactId>%s</artifactId>
					\t<version>1.0.0-SNAPSHOT</version>
					\t<packaging>woapplication</packaging>

					\t<properties>
					\t\t<maven.compiler.source>21</maven.compiler.source>
					\t\t<maven.compiler.target>21</maven.compiler.target>
					\t\t<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
					\t</properties>

					\t<dependencies>
					\t\t<dependency>
					\t\t\t<groupId>is.rebbi.ng</groupId>
					\t\t\t<artifactId>ng-appserver</artifactId>
					\t\t\t<version>0.1.1</version>
					\t\t</dependency>
					\t\t<dependency>
					\t\t\t<groupId>is.rebbi.ng</groupId>
					\t\t\t<artifactId>ng-adaptor-jetty</artifactId>
					\t\t\t<version>0.1.1</version>
					\t\t</dependency>
					\t\t<dependency>
					\t\t\t<groupId>org.slf4j</groupId>
					\t\t\t<artifactId>slf4j-simple</artifactId>
					\t\t\t<version>2.0.16</version>
					\t\t</dependency>
					\t</dependencies>

					\t<build>
					\t\t<plugins>
					\t\t\t<plugin>
					\t\t\t\t<groupId>is.rebbi</groupId>
					\t\t\t\t<artifactId>vermilingua-maven-plugin</artifactId>
					\t\t\t\t<version>1.1.4</version>
					\t\t\t\t<extensions>true</extensions>
					\t\t\t</plugin>
					\t\t</plugins>
					\t</build>
					</project>
					""", _packageName, _projectName);
		}

		// WebObjects pom.xml
		return String.format("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0"
				\t\txmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				\t\txsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				\t<modelVersion>4.0.0</modelVersion>

				\t<groupId>%s</groupId>
				\t<artifactId>%s</artifactId>
				\t<version>1.0.0-SNAPSHOT</version>
				\t<packaging>woapplication</packaging>

				\t<properties>
				\t\t<maven.compiler.source>25</maven.compiler.source>
				\t\t<maven.compiler.target>25</maven.compiler.target>
				\t\t<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
				\t</properties>

				\t<dependencies>
				\t\t<dependency>
				\t\t\t<groupId>is.rebbi.slim</groupId>
				\t\t\t<artifactId>ERExtensions</artifactId>
				\t\t\t<version>8.0.1</version>
				\t\t</dependency>
				\t\t<dependency>
				\t\t\t<groupId>is.rebbi.slim</groupId>
				\t\t\t<artifactId>ERLoggingReload4j</artifactId>
				\t\t\t<version>8.0.1</version>
				\t\t</dependency>
				\t\t<dependency>
				\t\t\t<groupId>is.rebbi.slim</groupId>
				\t\t\t<artifactId>Ajax</artifactId>
				\t\t\t<version>8.0.1</version>
				\t\t</dependency>
				\t\t<dependency>
				\t\t\t<groupId>com.webobjects</groupId>
				\t\t\t<artifactId>JavaWebObjects</artifactId>
				\t\t\t<version>5.4.3</version>
				\t\t\t<exclusions>
				\t\t\t\t<exclusion>
				\t\t\t\t\t<groupId>com.webobjects</groupId>
				\t\t\t\t\t<artifactId>JavaXML</artifactId>
				\t\t\t\t</exclusion>
				\t\t\t</exclusions>
				\t\t</dependency>
				\t</dependencies>

				\t<build>
				\t\t<plugins>
				\t\t\t<plugin>
				\t\t\t\t<groupId>is.rebbi</groupId>
				\t\t\t\t<artifactId>vermilingua-maven-plugin</artifactId>
				\t\t\t\t<version>1.1.4</version>
				\t\t\t\t<extensions>true</extensions>
				\t\t\t</plugin>
				\t\t</plugins>
				\t</build>
				</project>
				""", _packageName, _projectName);
	}

	private String generateBuildProperties() {
		return String.format("""
				project.base=%s
				project.name=%s
				project.type=application
				principalClass=%s.Application
				""", _isNG ? "ng" : "wo", _projectName, _packageName);
	}

	private String generateApplicationJava() {
		if (_isNG) {
			return String.format("""
					package %1$s;

					import ng.appserver.NGApplication;
					import ng.plugins.Routes;
					import %1$s.components.Main;

					public class Application extends NGApplication {

					\tpublic static void main(String[] args) {
					\t\tNGApplication.run(args, Application.class);
					\t}

					\t@Override
					\tpublic Routes routes() {
					\t\treturn super
					\t\t\t\t.routes()
					\t\t\t\t.map( "/", Main.class );
					\t}
					}
					""", _packageName);
		}

		return String.format("""
				package %1$s;

				import er.extensions.appserver.ERXApplication;
				import er.extensions.routes.RouteTable;
				import %1$s.components.Main;

				public class Application extends ERXApplication {

				\tpublic static void main(String[] args) {
				\t\tERXApplication.main(args, Application.class);
				\t}

				\tpublic Application() {
				\t\t// Maps the default route "/" to the Main component
				\t\tRouteTable.defaultRouteTable().map( "/", Main.class );

				\t\t// ...and the default direct connect URL (something like "/cgi-bin/WebObjects/MyApp.woa")
				\t\tRouteTable.defaultRouteTable().map( "%%s/%%s.woa".formatted( adaptorPath(), name() ), Main.class );
				\t}
				}
				""", _packageName);
	}

	private String generateSessionJava() {
		if (_isNG) {
			return String.format("""
					package %s;

					import ng.appserver.NGSession;

					public class Session extends NGSession {

					}
					""", _packageName);
		}

		return String.format("""
				package %s;

				import er.extensions.appserver.ERXSession;

				public class Session extends ERXSession {

				}
				""", _packageName);
	}

	private String generateDirectActionJava() {
		if (_isNG) {
			return String.format("""
					package %1$s;

					import ng.appserver.NGActionResults;
					import ng.appserver.directactions.NGDirectAction;
					import ng.appserver.NGRequest;

					public class DirectAction extends NGDirectAction {

					\tpublic DirectAction(NGRequest request) {
					\t\tsuper(request);
					\t}

					\tpublic NGActionResults defaultAction() {
					\t\treturn pageWithName(%1$s.components.Main.class);
					\t}
					}
					""", _packageName);
		}

		return String.format("""
				package %1$s;

				import com.webobjects.appserver.WOActionResults;
				import com.webobjects.appserver.WORequest;

				import er.extensions.appserver.ERXDirectAction;

				public class DirectAction extends ERXDirectAction {

				\tpublic DirectAction(WORequest request) {
				\t\tsuper(request);
				\t}

				\tpublic WOActionResults defaultAction() {
				\t\treturn pageWithName(%1$s.components.Main.class);
				\t}
				}
				""", _packageName);
	}

	private String generateMainJava() {
		if (_isNG) {
			return String.format("""
					package %s.components;

					import ng.appserver.NGContext;
					import ng.appserver.templating.NGComponent;

					public class Main extends NGComponent {

					\tpublic Main(NGContext context) {
					\t\tsuper(context);
					\t}
					}
					""", _packageName);
		}

		return String.format("""
				package %s.components;

				import com.webobjects.appserver.WOComponent;
				import com.webobjects.appserver.WOContext;

				public class Main extends WOComponent {

				\tpublic Main(WOContext context) {
				\t\tsuper(context);
				\t}
				}
				""", _packageName);
	}

	private String generateMainHtml() {
		return """
				<!DOCTYPE html>
				<html>
				\t<head>
				\t\t<meta charset="UTF-8">
				\t\t<title>Main</title>
				\t</head>
				\t<body>
				\t\t<h1>Hello World!</h1>
				\t</body>
				</html>
				""";
	}

	private String generateMainWoo() {
		return """
				{
				\t"WebObjects Release" = "WebObjects 5.0";
				\tencoding = "UTF-8";
				}
				""";
	}

	/**
	 * Generates the Properties file for WebObjects projects (log4j configuration).
	 * Note: {@code %%} in the format string produces a literal {@code %} for log4j patterns.
	 */
	private String generateWOProperties() {
		return String.format("""
				log4j.rootCategory=INFO, stdout

				log4j.appender.stdout=org.apache.log4j.ConsoleAppender
				log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
				log4j.appender.stdout.layout.ConversionPattern=%%d{ISO8601} [%%t] %%p %%c - %%m%%n

				%s.Application.er.migration.migrateAtStartup=false
				""", _projectName);
	}
}

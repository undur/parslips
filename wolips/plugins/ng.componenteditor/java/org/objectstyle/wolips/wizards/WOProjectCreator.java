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
 *   pom.xml                           — Maven project descriptor
 *   build.properties                   — Parsley framework detection (project.base=ng or project.base=wo)
 *   src/main/java/{package}/           — Application, Session, DirectAction
 *   src/main/java/{package}/components/ — Main.java
 *   src/main/components/              — Main.html, Main.wod, Main.woo (ng: standalone; WO: inside Main.wo/)
 *   src/main/resources/               — (Properties file for WO)
 *   src/main/webserver-resources/     — static web assets
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
			Files.createDirectories(_projectDir.resolve("src/main/components"));
		}
		else {
			Files.createDirectories(_projectDir.resolve("src/main/components/Main.wo"));
		}
		Files.createDirectories(_projectDir.resolve("src/main/resources"));
		if (!_isNG) {
			Files.createDirectories(_projectDir.resolve("src/main/woresources"));
		}
		Files.createDirectories(_projectDir.resolve("src/main/webserver-resources"));

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
		String componentBase = _isNG ? "src/main/components/" : "src/main/components/Main.wo/";
		writeFile(componentBase + "Main.html", generateMainHtml());
		writeFile(componentBase + "Main.wod", "");
		writeFile(componentBase + "Main.woo", generateMainWoo());

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
					         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
					         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
					    <modelVersion>4.0.0</modelVersion>

					    <groupId>%s</groupId>
					    <artifactId>%s</artifactId>
					    <version>1.0.0-SNAPSHOT</version>
					    <packaging>jar</packaging>

					    <properties>
					        <maven.compiler.source>21</maven.compiler.source>
					        <maven.compiler.target>21</maven.compiler.target>
					        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
					    </properties>

					    <dependencies>
					        <dependency>
					            <groupId>is.rebbi.ng</groupId>
					            <artifactId>ng-appserver</artifactId>
					            <version>0.1.0</version>
					        </dependency>
					        <dependency>
					            <groupId>is.rebbi.ng</groupId>
					            <artifactId>ng-adaptor-jetty</artifactId>
					            <version>0.1.0</version>
					        </dependency>
					        <dependency>
					            <groupId>org.slf4j</groupId>
					            <artifactId>slf4j-simple</artifactId>
					            <version>2.0.16</version>
					        </dependency>
					    </dependencies>
					</project>
					""", _packageName, _projectName);
		}

		// WebObjects pom.xml
		return String.format("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0"
				         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>

				    <groupId>%s</groupId>
				    <artifactId>%s</artifactId>
				    <version>1.0.0-SNAPSHOT</version>
				    <packaging>woapplication</packaging>

				    <properties>
				        <maven.compiler.source>25</maven.compiler.source>
				        <maven.compiler.target>25</maven.compiler.target>
				        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
				    </properties>

				    <dependencies>
				        <dependency>
				            <groupId>wonder.core</groupId>
				            <artifactId>ERExtensions</artifactId>
				            <version>8.0.0.slim-SNAPSHOT</version>
				        </dependency>
				        <dependency>
				            <groupId>wonder.core</groupId>
				            <artifactId>ERLoggingReload4j</artifactId>
				            <version>8.0.0.slim-SNAPSHOT</version>
				        </dependency>
				        <dependency>
				            <groupId>wonder.ajax</groupId>
				            <artifactId>Ajax</artifactId>
				            <version>8.0.0.slim-SNAPSHOT</version>
				        </dependency>
				        <dependency>
				            <groupId>com.webobjects</groupId>
				            <artifactId>JavaWebObjects</artifactId>
				            <version>5.4.3</version>
				            <exclusions>
				                <exclusion>
				                    <groupId>com.webobjects</groupId>
				                    <artifactId>JavaXML</artifactId>
				                </exclusion>
				            </exclusions>
				        </dependency>
				    </dependencies>

				    <build>
				        <plugins>
				            <plugin>
				                <groupId>is.rebbi</groupId>
				                <artifactId>vermilingua-maven-plugin</artifactId>
				                <version>1.0.5</version>
				                <extensions>true</extensions>
				            </plugin>
				        </plugins>
				    </build>
				</project>
				""", _packageName, _projectName);
	}

	private String generateBuildProperties() {
		return String.format("""
				project.base=%s
				project.name=%s
				project.type=application
				""", _isNG ? "ng" : "wo", _projectName);
	}

	private String generateApplicationJava() {
		if (_isNG) {
			return String.format("""
					package %s;

					import ng.appserver.NGApplication;

					public class Application extends NGApplication {

					    public static void main(String[] args) {
					        NGApplication.run(args, Application.class);
					    }
					}
					""", _packageName);
		}

		return String.format("""
				package %1$s;

				import er.extensions.appserver.ERXApplication;
				import er.extensions.routes.RouteTable;
				import %1$s.components.Main;

				public class Application extends ERXApplication {

				    public static void main(String[] args) {
				        ERXApplication.main(args, Application.class);
				    }

				    public Application() {
				        RouteTable.defaultRouteTable().map( "/", Main.class );
				    }
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

					    public DirectAction(NGRequest request) {
					        super(request);
					    }

					    public NGActionResults defaultAction() {
					        return pageWithName(%1$s.components.Main.class);
					    }
					}
					""", _packageName);
		}

		return String.format("""
				package %1$s;

				import com.webobjects.appserver.WOActionResults;
				import com.webobjects.appserver.WORequest;

				import er.extensions.appserver.ERXDirectAction;

				public class DirectAction extends ERXDirectAction {

				    public DirectAction(WORequest request) {
				        super(request);
				    }

				    public WOActionResults defaultAction() {
				        return pageWithName(%1$s.components.Main.class);
				    }
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

					    public Main(NGContext context) {
					        super(context);
					    }
					}
					""", _packageName);
		}

		return String.format("""
				package %s.components;

				import com.webobjects.appserver.WOComponent;
				import com.webobjects.appserver.WOContext;

				public class Main extends WOComponent {

				    public Main(WOContext context) {
				        super(context);
				    }
				}
				""", _packageName);
	}

	private String generateMainHtml() {
		return """
				<!DOCTYPE html>
				<html>
				<head>
				    <meta charset="UTF-8">
				    <title>Main</title>
				</head>
				<body>
				    <h1>Hello World!</h1>
				</body>
				</html>
				""";
	}

	private String generateMainWoo() {
		return """
				{
				    "WebObjects Release" = "WebObjects 5.0";
				    encoding = "UTF-8";
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

package org.objectstyle.wolips.wizards;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaCore;

/**
 * Creates all files and folders for a new ng-objects or WebObjects Maven project.
 *
 * <p>Invoked from {@link WOProjectCreationPage} during the wizard's performFinish().
 * Runs inside a {@link org.eclipse.jface.dialogs.ProgressMonitorDialog}.
 *
 * <p>The generated project follows standard Maven conventions:
 * <pre>
 *   pom.xml                           — Maven project descriptor
 *   build.properties                   — Parsley framework detection (base=ng or base=wo)
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
	private final URI _locationURI;

	/**
	 * @param projectName  the Eclipse project name (also used as Maven artifactId)
	 * @param packageName  the Java package for generated classes
	 * @param isNG         true for ng-objects, false for WebObjects
	 * @param locationURI  custom project location, or null for workspace default
	 */
	public WOProjectCreator(String projectName, String packageName, boolean isNG, URI locationURI) {
		_projectName = projectName;
		_packageName = packageName;
		_isNG = isNG;
		_locationURI = locationURI;
	}

	/**
	 * Creates the project and all its contents.
	 *
	 * @return the Main.html IFile to reveal in the editor
	 */
	public IFile createProject(IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, "Creating project " + _projectName, 10);

		try {
			// 1. Create the Eclipse project with Java nature
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IProject project = root.getProject(_projectName);
			IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(_projectName);

			// Set custom location if specified (null means workspace default)
			if (_locationURI != null) {
				description.setLocationURI(_locationURI);
			}

			// Add Java nature so Eclipse recognizes this as a Java project immediately
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });

			// Add the Java builder
			ICommand javaBuildCommand = description.newCommand();
			javaBuildCommand.setBuilderName(JavaCore.BUILDER_ID);
			description.setBuildSpec(new ICommand[] { javaBuildCommand });

			project.create(description, subMonitor.split(1));
			project.open(subMonitor.split(1));

			// 2. Create the folder structure
			String packagePath = _packageName.replace('.', '/');

			createFolder(project, "src/main/java/" + packagePath, monitor);
			createFolder(project, "src/main/java/" + packagePath + "/components", monitor);
			if (_isNG) {
				createFolder(project, "src/main/components", monitor);
			}
			else {
				createFolder(project, "src/main/components/Main.wo", monitor);
			}
			createFolder(project, "src/main/resources", monitor);
			createFolder(project, "src/main/webserver-resources", monitor);
			monitor.worked(2);

			// 3. Create files
			createFile(project, "pom.xml", generatePomXml(), monitor);
			createFile(project, "build.properties", generateBuildProperties(), monitor);

			// Java sources
			String javaBase = "src/main/java/" + packagePath + "/";
			createFile(project, javaBase + "Application.java", generateApplicationJava(), monitor);
			createFile(project, javaBase + "Session.java", generateSessionJava(), monitor);
			createFile(project, javaBase + "DirectAction.java", generateDirectActionJava(), monitor);
			createFile(project, javaBase + "components/Main.java", generateMainJava(), monitor);
			monitor.worked(3);

			// Component template files
			String componentBase = _isNG ? "src/main/components/" : "src/main/components/Main.wo/";
			createFile(project, componentBase + "Main.html", generateMainHtml(), monitor);
			createFile(project, componentBase + "Main.wod", "", monitor);
			createFile(project, componentBase + "Main.woo", generateMainWoo(), monitor);
			monitor.worked(1);

			// WO-specific files
			if (!_isNG) {
				createFile(project, "src/main/resources/Properties", generateWOProperties(), monitor);
			}
			monitor.worked(1);

			// 4. Refresh so Eclipse sees all new files
			project.refreshLocal(IResource.DEPTH_INFINITE, subMonitor.split(1));

			return project.getFile(componentBase + "Main.html");
		}
		finally {
			monitor.done();
		}
	}

	// ---- Folder/file helpers ----

	/**
	 * Creates a folder and all its parent folders.
	 */
	private void createFolder(IProject project, String path, IProgressMonitor monitor) throws CoreException {
		String[] segments = path.split("/");
		IFolder current = null;

		for (String segment : segments) {
			current = (current == null) ? project.getFolder(segment) : current.getFolder(segment);
			if (!current.exists()) {
				current.create(false, true, monitor);
			}
		}
	}

	/**
	 * Creates a file with the given UTF-8 content.
	 */
	private void createFile(IProject project, String path, String content, IProgressMonitor monitor) throws CoreException {
		IFile file = project.getFile(path);
		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		file.create(new ByteArrayInputStream(bytes), false, monitor);
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
					            <groupId>is.rebbi</groupId>
					            <artifactId>ng-appserver</artifactId>
					            <version>0.1.0-SNAPSHOT</version>
					        </dependency>
					        <dependency>
					            <groupId>is.rebbi</groupId>
					            <artifactId>ng-adaptor-jetty</artifactId>
					            <version>0.1.0-SNAPSHOT</version>
					        </dependency>
					    </dependencies>
					</project>
					""", _packageName, _projectName);
		}

		// WebObjects pom.xml — uses Maven ${property} references (pass through String.format safely)
		return String.format("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0"
				         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>

				    <groupId>%s</groupId>
				    <artifactId>%s</artifactId>
				    <version>1.0.0-SNAPSHOT</version>
				    <packaging>war</packaging>

				    <properties>
				        <maven.compiler.source>21</maven.compiler.source>
				        <maven.compiler.target>21</maven.compiler.target>
				        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
				        <wonder.core.group>wonder.core</wonder.core.group>
				        <wonder.ajax.group>wonder.ajax</wonder.ajax.group>
				        <wonder.version>7.4</wonder.version>
				        <webobjects.group>com.webobjects</webobjects.group>
				        <webobjects.version>5.4.3</webobjects.version>
				    </properties>

				    <dependencies>
				        <dependency>
				            <groupId>${wonder.core.group}</groupId>
				            <artifactId>ERExtensions</artifactId>
				            <version>${wonder.version}</version>
				        </dependency>
				        <dependency>
				            <groupId>${wonder.core.group}</groupId>
				            <artifactId>ERLoggingReload4j</artifactId>
				            <version>${wonder.version}</version>
				        </dependency>
				        <dependency>
				            <groupId>${wonder.ajax.group}</groupId>
				            <artifactId>Ajax</artifactId>
				            <version>${wonder.version}</version>
				        </dependency>
				        <dependency>
				            <groupId>${webobjects.group}</groupId>
				            <artifactId>JavaWebObjects</artifactId>
				            <version>${webobjects.version}</version>
				            <exclusions>
				                <exclusion>
				                    <groupId>${webobjects.group}</groupId>
				                    <artifactId>JavaXML</artifactId>
				                </exclusion>
				            </exclusions>
				        </dependency>
				    </dependencies>
				</project>
				""", _packageName, _projectName);
	}

	private String generateBuildProperties() {
		return String.format("""
				base=%s
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
				package %s;

				import er.extensions.appserver.ERXApplication;

				public class Application extends ERXApplication {

				    public static void main(String[] args) {
				        ERXApplication.main(args, Application.class);
				    }

				    @Override
				    public void finishInitialization() {
				        super.finishInitialization();
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
					package %s;

					import ng.appserver.NGActionResults;
					import ng.appserver.NGDirectAction;
					import ng.appserver.NGRequest;

					public class DirectAction extends NGDirectAction {

					    public DirectAction(NGRequest request) {
					        super(request);
					    }

					    public NGActionResults defaultAction() {
					        return pageWithName(components.Main.class);
					    }
					}
					""", _packageName);
		}

		return String.format("""
				package %s;

				import com.webobjects.appserver.WOActionResults;
				import com.webobjects.appserver.WORequest;

				import er.extensions.appserver.ERXDirectAction;

				public class DirectAction extends ERXDirectAction {

				    public DirectAction(WORequest request) {
				        super(request);
				    }

				    public WOActionResults defaultAction() {
				        return pageWithName(components.Main.class);
				    }
				}
				""", _packageName);
	}

	private String generateMainJava() {
		if (_isNG) {
			return String.format("""
					package %s.components;

					import ng.appserver.templating.NGComponent;

					public class Main extends NGComponent {

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

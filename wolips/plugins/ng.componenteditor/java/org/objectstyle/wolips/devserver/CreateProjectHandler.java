package org.objectstyle.wolips.devserver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.objectstyle.wolips.wizards.MavenProjectImporter;
import org.objectstyle.wolips.wizards.WOProjectCreator;

/**
 * {@code /createProject} — generates a new project from one of the bundled templates, imports
 * it into the workspace through m2e, gives an application a launch configuration, and
 * (optionally) launches it. The point is to close the loop for an external agent: from "there
 * is no project" to "the app is running in Eclipse, ready for the edit loop" without a human
 * clicking through an import wizard.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code name} — the project name (also the Maven artifactId). Required.</li>
 *   <li>{@code template} — {@code ng-objects-app}, {@code wonder-slim-app} or {@code maven}
 *       (a plain jar project for supporting logic). Required; {@code ng}/{@code wo} are accepted
 *       as short forms.</li>
 *   <li>{@code package} — the Java package (and Maven groupId). Default: derived from the name
 *       the way the New Project wizard does ({@code my-cool-app} → {@code my.cool.app}).</li>
 *   <li>{@code location} — the PARENT directory to create the project folder in. Default: the
 *       workspace directory.</li>
 *   <li>{@code launch} — {@code true} to launch the new application right away; every
 *       {@code /launch} parameter ({@code waitForPort}, {@code stopOthers}, {@code port}…)
 *       passes through.</li>
 * </ul>
 *
 * <p>It refuses rather than overwrite: an existing workspace project of that name, or a
 * non-empty target directory, is an error naming the conflict.
 */
class CreateProjectHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String name = params.get("name");
		final String nameProblem = WOProjectCreator.validateProjectName(name);
		if (nameProblem != null) {
			return refusal(nameProblem);
		}

		final WOProjectCreator.Kind kind = kindOf(params.get("template"));
		if (kind == null) {
			return refusal("missing or unknown template - use ng-objects-app, wonder-slim-app or maven");
		}

		final String packageName = params.get("package") != null && !params.get("package").isBlank()
				? params.get("package").trim()
				: WOProjectCreator.derivePackageName(name);
		final String packageProblem = WOProjectCreator.validatePackageName(packageName);
		if (packageProblem != null) {
			return refusal(packageProblem);
		}

		final IProject existing = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (existing.exists()) {
			return refusal("a project named \"" + name + "\" already exists in the workspace (" + (existing.isOpen() ? "open" : "closed") + ")");
		}

		final Path parent = params.get("location") != null && !params.get("location").isBlank()
				? Path.of(expandHome(params.get("location").trim()))
				: ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
		if (!Files.isDirectory(parent)) {
			return refusal("location \"" + parent + "\" is not an existing directory (it is the PARENT the project folder is created in)");
		}
		final Path projectDir = parent.resolve(name);
		if (Files.exists(projectDir) && !isEmptyDirectory(projectDir)) {
			return refusal("\"" + projectDir + "\" already exists and is not empty - use /importProject?path=... to bring an existing project into the workspace");
		}

		// 1. Files on disk (no Eclipse involved), 2. m2e import, 3. let the workspace settle.
		final WOProjectCreator creator = new WOProjectCreator(name, packageName, kind, projectDir);
		final Path entryFile = creator.createProject();
		final IProject project = MavenProjectImporter.importProject(projectDir.toFile());
		if (project == null) {
			return "{\"created\":false,\"reason\":\"the files were written to " + DevServerJson.escape(projectDir.toString())
					+ " but m2e found no Maven project to import there\"}";
		}
		settle();

		final StringBuilder b = new StringBuilder();
		b.append("{\"created\":true,\"project\":\"").append(DevServerJson.escape(project.getName()))
				.append("\",\"template\":\"").append(templateName(kind))
				.append("\",\"path\":\"").append(DevServerJson.escape(projectDir.toString()))
				.append("\",\"package\":\"").append(DevServerJson.escape(packageName))
				.append("\",\"entryFile\":\"").append(DevServerJson.escape(projectDir.relativize(entryFile).toString())).append('"');

		final List<WorkspaceProblems.Problem> errors = WorkspaceProblems.javaErrors(project, 10);
		b.append(",\"compileErrors\":").append(errors.size());
		if (!errors.isEmpty()) {
			b.append(",\"problems\":").append(WorkspaceProblems.toJsonArray(errors));
		}

		final String mainClass = creator.mainClassName();
		if (mainClass != null) {
			final String launchConfig = LaunchConfigCreator.ensure(project.getName(), mainClass);
			b.append(",\"mainClass\":\"").append(DevServerJson.escape(mainClass))
					.append("\",\"launchConfig\":\"").append(DevServerJson.escape(launchConfig)).append('"');

			if ("true".equalsIgnoreCase(params.get("launch"))) {
				final Map<String, String> launchParams = new HashMap<>(params);
				launchParams.put("config", launchConfig);
				launchParams.remove("app");
				b.append(",\"launch\":").append(new LaunchHandler().handle(launchParams));
			}
			else {
				b.append(",\"hint\":\"start it with /launch?config=").append(DevServerJson.escape(launchConfig)).append("&waitForPort=1200 (add stopOthers=true if another app holds the port)\"");
			}
		}
		return b.append('}').toString();
	}

	/** Waits for m2e's post-import classpath work and the first build, so the reported errors are real. */
	static void settle() {
		LaunchClosure.awaitClasspathJobs();
		RefreshProjectHandler.waitForBuildToSettle();
		LaunchClosure.awaitClasspathJobs();
		RefreshProjectHandler.waitForBuildToSettle();
	}

	static WOProjectCreator.Kind kindOf(String template) {
		if (template == null) {
			return null;
		}
		switch (template.trim().toLowerCase()) {
		case "ng-objects-app":
		case "ng":
			return WOProjectCreator.Kind.NG_APP;
		case "wonder-slim-app":
		case "wo":
			return WOProjectCreator.Kind.WO_APP;
		case "maven":
			return WOProjectCreator.Kind.MAVEN;
		default:
			return null;
		}
	}

	static String templateName(WOProjectCreator.Kind kind) {
		switch (kind) {
		case NG_APP:
			return "ng-objects-app";
		case WO_APP:
			return "wonder-slim-app";
		default:
			return "maven";
		}
	}

	static String expandHome(String path) {
		return path.equals("~") || path.startsWith("~/") ? System.getProperty("user.home") + path.substring(1) : path;
	}

	private static boolean isEmptyDirectory(Path dir) throws Exception {
		if (!Files.isDirectory(dir)) {
			return false;
		}
		try (Stream<Path> entries = Files.list(dir)) {
			return entries.findAny().isEmpty();
		}
	}

	private static String refusal(String reason) {
		return "{\"created\":false,\"reason\":\"" + DevServerJson.escape(reason) + "\"}";
	}

	/** For {@link ImportProjectHandler}: the project whose location is this directory, or null. */
	static IProject projectAt(File dir) {
		for (final IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.getLocation() != null && project.getLocation().toFile().equals(dir)) {
				return project;
			}
		}
		return null;
	}
}

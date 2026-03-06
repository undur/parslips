package org.objectstyle.wolips.wizards;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.objectstyle.wolips.baseforuiplugins.utils.WorkbenchUtilities;

/**
 * Single wizard page for the "New Project" wizard.
 *
 * <p>Extends {@link WizardNewProjectCreationPage}, which provides:
 * <ul>
 *   <li>Project name text field with validation (duplicate names, invalid characters)</li>
 *   <li>Location controls (default workspace location or custom path)</li>
 * </ul>
 *
 * <p>We add a framework selection group with radio buttons for ng-objects
 * and WebObjects. The project name is also used to derive the Java package
 * name via {@link #derivePackageName()}.
 *
 * <p>Project creation works in two phases:
 * <ol>
 *   <li>{@link WOProjectCreator} writes all files to disk (pure I/O, no Eclipse APIs)</li>
 *   <li>m2e's {@link IProjectConfigurationManager#importProjects} imports the result
 *       as a fully-configured Maven project (natures, classpath, dependencies)</li>
 * </ol>
 *
 * <p>This means the file generation logic is portable — the same templates
 * could be reused by Maven archetypes or other scaffolding tools.
 *
 * <p>Note: {@code WizardNewProjectCreationPage} handles project name validation
 * automatically — we don't need to override {@code validatePage()}.
 */
public class WOProjectCreationPage extends WizardNewProjectCreationPage {

	/** Dialog settings key for persisting the last-selected framework. */
	private static final String FRAMEWORK_KEY = "WOProjectCreationPage.framework";

	private Button _ngRadio;
	private Button _woRadio;

	public WOProjectCreationPage() {
		super("createProjectPage");
		setTitle(Messages.getString("WOProjectCreationPage.title"));
		setDescription(Messages.getString("WOProjectCreationPage.description"));
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);

		Composite composite = (Composite) getControl();

		// --- Framework selection group ---
		Group frameworkGroup = new Group(composite, SWT.NONE);
		frameworkGroup.setText(Messages.getString("WOProjectCreationPage.framework.group"));
		frameworkGroup.setLayout(new GridLayout(1, false));
		frameworkGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		_ngRadio = new Button(frameworkGroup, SWT.RADIO);
		_ngRadio.setText(Messages.getString("WOProjectCreationPage.framework.ng"));

		_woRadio = new Button(frameworkGroup, SWT.RADIO);
		_woRadio.setText(Messages.getString("WOProjectCreationPage.framework.wo"));

		// Restore previous selection, defaulting to ng-objects
		IDialogSettings settings = getDialogSettings();
		String previousFramework = (settings != null) ? settings.get(FRAMEWORK_KEY) : null;
		if ("wo".equals(previousFramework)) {
			_woRadio.setSelection(true);
		}
		else {
			_ngRadio.setSelection(true);
		}
	}

	/**
	 * Returns {@code true} if the user selected ng-objects, {@code false} for WebObjects.
	 */
	private boolean isNGProject() {
		return _ngRadio.getSelection();
	}

	/**
	 * Derives a Java package name from the project name.
	 *
	 * <p>Converts to lowercase, replaces hyphens/underscores with dots,
	 * strips characters not valid in Java identifiers, collapses consecutive
	 * dots, and ensures each segment starts with a letter.
	 *
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code "MyApp"} → {@code "myapp"}</li>
	 *   <li>{@code "my-cool-app"} → {@code "my.cool.app"}</li>
	 *   <li>{@code "My App 2"} → {@code "myapp2"}</li>
	 * </ul>
	 */
	private String derivePackageName() {
		String name = getProjectName().toLowerCase();

		// Replace hyphens and underscores with dots (common Maven convention)
		name = name.replace('-', '.').replace('_', '.');

		// Strip characters not valid in Java identifiers (keeping dots as separators)
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '.' || Character.isJavaIdentifierPart(c)) {
				sb.append(c);
			}
		}
		name = sb.toString();

		// Collapse consecutive dots and trim leading/trailing dots
		name = name.replaceAll("\\.{2,}", ".").replaceAll("^\\.|\\.$", "");

		// Ensure each segment starts with a letter (Java requirement)
		String[] segments = name.split("\\.");
		sb = new StringBuilder();
		for (int i = 0; i < segments.length; i++) {
			if (segments[i].isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('.');
			}
			if (Character.isDigit(segments[i].charAt(0))) {
				sb.append('_');
			}
			sb.append(segments[i]);
		}

		return sb.toString();
	}

	/**
	 * Creates the project. Called from {@link WOProjectCreationWizard#performFinish()}.
	 *
	 * <p>Phase 1: {@link WOProjectCreator} writes all files to disk.
	 * Phase 2: m2e imports the directory as a fully-configured Maven project.
	 *
	 * @return the Main.html file to reveal in the editor, or {@code null} if creation failed
	 */
	public IFile createProject() {
		// Persist the framework selection for next time
		IDialogSettings settings = getDialogSettings();
		if (settings != null) {
			settings.put(FRAMEWORK_KEY, isNGProject() ? "ng" : "wo");
		}

		// Capture all SWT widget values on the UI thread before entering the
		// background thread. SWT widgets can only be accessed from the UI thread.
		final String projectName = getProjectName();
		final String packageName = derivePackageName();
		final boolean isNG = isNGProject();
		final URI locationURI = getLocationURI();

		try {
			IFile[] result = new IFile[1];
			new ProgressMonitorDialog(getShell()).run(true, false, monitor -> {
				try {
					// Determine the project directory on disk.
					// getLocationURI() returns null for the default workspace location.
					Path projectDir;
					if (locationURI != null) {
						projectDir = Path.of(locationURI).resolve(projectName);
					}
					else {
						projectDir = ResourcesPlugin.getWorkspace().getRoot()
								.getLocation().toFile().toPath()
								.resolve(projectName);
					}

					// Phase 1: Write files to disk (no Eclipse APIs)
					monitor.beginTask("Creating project " + projectName, 3);
					WOProjectCreator creator = new WOProjectCreator(
						projectName,
						packageName,
						isNG,
						projectDir
					);
					Path mainTemplatePath = creator.createProject();
					monitor.worked(1);

					// Phase 2: Import as Maven project via m2e
					monitor.subTask("Importing Maven project...");
					IProject project = importMavenProject(projectDir.toFile());
					monitor.worked(1);

					// Resolve the Main.html IFile within the imported project
					if (project != null) {
						String componentBase = isNG
								? "src/main/components/"
								: "src/main/components/Main.wo/";
						result[0] = project.getFile(componentBase + "Main.html");
					}
					monitor.worked(1);
					monitor.done();
				}
				catch (Exception e) {
					throw new java.lang.reflect.InvocationTargetException(e);
				}
			});
			return result[0];
		}
		catch (Exception e) {
			WizardsPlugin.getDefault().log(e);
			WorkbenchUtilities.errorDialog(getShell(), "Error", "Error creating project", e);
			return null;
		}
	}

	/**
	 * Imports a directory containing a pom.xml as a Maven project using m2e.
	 *
	 * <p>This is equivalent to the user doing File → Import → Existing Maven Projects.
	 * m2e handles all Eclipse project configuration: natures, builders, classpath,
	 * source folders, dependency resolution.
	 *
	 * @param projectDir  the directory containing the pom.xml
	 * @return the imported IProject, or null if import failed
	 */
	private IProject importMavenProject(File projectDir) throws CoreException, InterruptedException {
		IProjectConfigurationManager configManager = MavenPlugin.getProjectConfigurationManager();

		// Scan for pom.xml in the project directory
		LocalProjectScanner scanner = new LocalProjectScanner(
				Collections.singletonList(projectDir.getAbsolutePath()),
				false,                                              // don't search for nested projects
				MavenPlugin.getMavenModelManager());
		scanner.run(new NullProgressMonitor());

		// Collect found Maven project(s)
		Collection<MavenProjectInfo> projects = collectProjects(scanner.getProjects());
		if (projects.isEmpty()) {
			return null;
		}

		// Import into Eclipse workspace
		ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
		List<IMavenProjectImportResult> results = configManager.importProjects(
				projects,
				importConfig,
				new NullProgressMonitor());

		// Return the first successfully imported project
		for (IMavenProjectImportResult result : results) {
			IProject project = result.getProject();
			if (project != null && project.exists()) {
				return project;
			}
		}

		return null;
	}

	/**
	 * Recursively collects all {@link MavenProjectInfo} from the scanner results,
	 * flattening any nested project hierarchies.
	 */
	private Collection<MavenProjectInfo> collectProjects(Collection<MavenProjectInfo> input) {
		List<MavenProjectInfo> result = new ArrayList<>();
		for (MavenProjectInfo info : input) {
			result.add(info);
			result.addAll(collectProjects(info.getProjects()));
		}
		return result;
	}
}

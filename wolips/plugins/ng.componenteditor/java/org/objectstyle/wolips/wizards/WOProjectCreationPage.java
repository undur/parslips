package org.objectstyle.wolips.wizards;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
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
		return WOProjectCreator.derivePackageName(getProjectName());
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
					// using the path returned by WOProjectCreator, which
					// already knows the correct location for each framework.
					if (project != null) {
						Path relativeMainPath = projectDir.relativize(mainTemplatePath);
						result[0] = project.getFile(relativeMainPath.toString());
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

	/** Imports the generated directory as a Maven project (shared with the dev server). */
	private IProject importMavenProject(File projectDir) throws CoreException, InterruptedException {
		return MavenProjectImporter.importProject(projectDir);
	}
}

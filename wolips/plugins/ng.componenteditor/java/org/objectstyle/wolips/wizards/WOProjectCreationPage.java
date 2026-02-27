package org.objectstyle.wolips.wizards;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
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
	public boolean isNGProject() {
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
	public String derivePackageName() {
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
	 * @return the Main.html file to reveal in the editor, or {@code null} if creation failed
	 */
	public IFile createProject() {
		// Persist the framework selection for next time
		IDialogSettings settings = getDialogSettings();
		if (settings != null) {
			settings.put(FRAMEWORK_KEY, isNGProject() ? "ng" : "wo");
		}

		try {
			IFile[] result = new IFile[1];
			new ProgressMonitorDialog(getShell()).run(false, false, monitor -> {
				try {
					WOProjectCreator creator = new WOProjectCreator(
						getProjectName(),
						derivePackageName(),
						isNGProject(),
						getLocationURI()
					);
					result[0] = creator.createProject(monitor);
				}
				catch (CoreException e) {
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
}

package org.objectstyle.wolips.wizards;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

/**
 * Wizard for creating a new ng-objects or WebObjects Maven project.
 *
 * <p>Registered in plugin.xml under the "NG Objects" category with
 * {@code project="true"} so it appears in Eclipse's "New Project" dialog.
 *
 * <p>Creates a complete Maven project structure with a sample Main component,
 * ready for import and execution. After creation, reveals the Main.html
 * template in the editor.
 *
 * <p>Follows the same pattern as {@link WOComponentCreationWizard}.
 */
public class WOProjectCreationWizard extends Wizard implements INewWizard {

	private WOProjectCreationPage _mainPage;

	public WOProjectCreationWizard() {
		super();
	}

	@Override
	public void addPages() {
		addPage(_mainPage);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		setDialogSettings(WizardsPlugin.getDefault().getDialogSettings());
		_mainPage = new WOProjectCreationPage();
		setWindowTitle(Messages.getString("WOProjectCreationWizard.title"));
		setDefaultPageImageDescriptor(WizardsPlugin.WOCOMPONENT_WIZARD_BANNER());
	}

	@Override
	public boolean performFinish() {
		IFile mainTemplate = _mainPage.createProject();

		if (mainTemplate != null) {
			WizardsPlugin.selectAndReveal(new IResource[] { mainTemplate });
		}

		WizardsPlugin.getDefault().publicSaveDialogSettings();
		return mainTemplate != null;
	}
}

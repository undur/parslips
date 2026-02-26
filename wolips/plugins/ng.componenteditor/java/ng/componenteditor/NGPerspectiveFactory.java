package ng.componenteditor;

import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * The Parsley perspective — a streamlined layout for WebObjects / ng-objects
 * component development.
 * <p>
 * Layout:
 * <pre>
 *  ┌──────────┬──────────────────────┬──────────┐
 *  │ Parsley  │                      │          │
 *  │ Explorer │      Editor Area     │ Outline  │
 *  │          │                      │          │
 *  │          ├──────────────────────┤          │
 *  │          │ Problems │ Console   │          │
 *  └──────────┴──────────────────────┴──────────┘
 * </pre>
 */
public class NGPerspectiveFactory implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "ng.componenteditor.ParsleyPerspective";

	private static final String ID_PARSLEY_EXPLORER = "ng.componenteditor.explorer.NGPackageExplorer";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		// Left: Parsley Explorer + Type Hierarchy
		IFolderLayout leftFolder = layout.createFolder("left", IPageLayout.LEFT, 0.25f, editorArea);
		leftFolder.addView(ID_PARSLEY_EXPLORER);
		leftFolder.addView(JavaUI.ID_TYPE_HIERARCHY);

		// Bottom: Problems, Console, Javadoc, Source, Search placeholder
		IFolderLayout bottomFolder = layout.createFolder("bottom", IPageLayout.BOTTOM, 0.75f, editorArea);
		bottomFolder.addView(IPageLayout.ID_PROBLEM_VIEW);
		bottomFolder.addView(IConsoleConstants.ID_CONSOLE_VIEW);
		bottomFolder.addView(JavaUI.ID_JAVADOC_VIEW);
		bottomFolder.addView(JavaUI.ID_SOURCE_VIEW);
		bottomFolder.addPlaceholder(NewSearchUI.SEARCH_VIEW_ID);
		bottomFolder.addPlaceholder(IPageLayout.ID_BOOKMARKS);
		bottomFolder.addPlaceholder(IProgressConstants.PROGRESS_VIEW_ID);

		// Right: Outline
		layout.addView(IPageLayout.ID_OUTLINE, IPageLayout.RIGHT, 0.75f, editorArea);

		// Action sets
		layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET);
		layout.addActionSet(JavaUI.ID_ACTION_SET);
		layout.addActionSet(JavaUI.ID_ELEMENT_CREATION_ACTION_SET);
		layout.addActionSet(IPageLayout.ID_NAVIGATE_ACTION_SET);
		layout.addActionSet("org.eclipse.team.ui.actionSet");
		layout.addActionSet("org.eclipse.egit.ui.gitActionSet");
		layout.addActionSet("org.eclipse.egit.ui.navigationActionSet");

		// Show View shortcuts
		layout.addShowViewShortcut(ID_PARSLEY_EXPLORER);
		layout.addShowViewShortcut(JavaUI.ID_PACKAGES);
		layout.addShowViewShortcut(JavaUI.ID_TYPE_HIERARCHY);
		layout.addShowViewShortcut(JavaUI.ID_SOURCE_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_JAVADOC_VIEW);
		layout.addShowViewShortcut(NewSearchUI.SEARCH_VIEW_ID);
		layout.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);
		layout.addShowViewShortcut(IPageLayout.ID_OUTLINE);
		layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);

		// New wizard shortcuts — Java basics
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewPackageCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewClassCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewInterfaceCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewEnumCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewAnnotationCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewSourceFolderCreationWizard");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");
		layout.addNewWizardShortcut("org.eclipse.ui.editors.wizards.UntitledTextFileWizard");

		// Perspective shortcuts
		layout.addPerspectiveShortcut("org.eclipse.debug.ui.DebugPerspective");
		layout.addPerspectiveShortcut("org.eclipse.jdt.ui.JavaPerspective");
		layout.addPerspectiveShortcut("org.eclipse.ui.resourcePerspective");
	}
}

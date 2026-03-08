/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2002 - 2006 The ObjectStyle Group
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */
package org.objectstyle.wolips.wizards;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonStatusDialogField;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.core.resources.types.TypeNameCollector;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Second page of the New WO Component wizard. Handles choosing the destination
 * folder, java package, superclass, HTML doctype, encoding, and optional files.
 *
 * Lifecycle overview (important for understanding the code flow):
 *
 * 1. Constructor is called with the user's current Package Explorer selection.
 *    processSelection() sanitizes it (redirecting packages, source folders,
 *    classpath containers etc. to the project) and passes the result to super.
 *    We also store the original selection for use in initialPopulateContainerNameField().
 *
 * 2. Eclipse calls createControl(), which calls super.createControl(). Inside
 *    super.createControl(), the UI widgets are created and initialPopulateContainerNameField()
 *    is called to set the initial container path in the text widget.
 *
 * 3. After super.createControl() returns, we add our own controls (package,
 *    superclass, HTML options) and use getContainerFullPath() to read back
 *    the container path for initializing those fields.
 *
 * Key subtlety: getContainerFullPath() can return null if no valid container
 * was resolved (e.g. if an unrecognized selection type slipped through).
 * All callers must null-check it.
 *
 * @author mnolte
 * @author uli
 */
public class WOComponentCreationPage extends WizardNewWOResourcePage {

	/** Dialog settings keys — used to persist user preferences between wizard invocations */
	private static final String BODY_CHECKBOX_KEY = "WOComponentCreationWizardSection.bodyCheckbox";
	private static final String API_CHECKBOX_KEY = "WOComponentCreationWizardSection.apiCheckbox";
	private static final String HTML_DOCTYPE_KEY = "WOComponentCreationWizardSection.htmlDocType";
	private static final String NSSTRING_ENCODING_KEY = "WOComponentCreationWizardSection.encoding";

	private static final String COMPONENT_FORMAT_KEY = "WOComponentCreationWizardSection.componentFormat";

	/** Component format values for dialog settings persistence */
	private static final String FORMAT_STANDALONE = "standalone";
	private static final String FORMAT_BUNDLE = "bundle";

	private Button _bodyCheckbox;
	private Combo _htmlCombo;
	private Combo _encodingCombo;
	private Button _apiCheckbox;
	private IResource[] _resourcesToReveal;
	private StringButtonStatusDialogField _packageDialogField;
	private StringButtonStatusDialogField _superclassDialogField;

	/** Radio buttons for choosing between standalone HTML and .wo bundle */
	private Button _standaloneRadio;
	private Button _bundleRadio;

	/**
	 * The Optional Files group — needs a reference so we can show/hide it
	 * based on the component format selection. Bundle components have
	 * optional files (body tag, doctype, API, encoding); standalone
	 * components don't.
	 */
	private Group _optionalFilesGroup;

	/**
	 * If the user selected a Java package folder, this holds the IPackageFragment.
	 * Used to pre-fill the package field and to find the right component folder.
	 * Null for all other selection types.
	 */
	private Object _currentSelection;

	/**
	 * The raw, unprocessed selection from the Package Explorer. We need this
	 * because processSelection() transforms the selection before passing it
	 * to super, but initialPopulateContainerNameField() needs access to the
	 * original to extract the project and find the components folder.
	 */
	private IStructuredSelection _originalSelection;


	enum HTML {
		STRICT_401("HTML 4.0.1 Strict", "4.0.1 strict doctype", 0), 
		TRANSITIONAL_401("HTML 4.0.1 Transitional", "4.0.1 transitional doctype", 1), 
		STRICT_XHTML10("XHTML 1.0 Strict", "XHTML 1.0 strict doctype", 2), 
		TRANSITIONAL_XHTML10("XHTML 1.0 Transitional", "XHTML 1.0 transitional doctype", 3), 
		FRAMESET_XHTML10("XHTML 1.0 Frameset", "XHTML 1.0 frameset doctype", 4), 
		XHTML11("XHTML 1.1", "XHTML 1.1 doctype", 5), 
		LAZY_OLD("Lazy Old HTML", "Lazy Old HTML", 6);

		private final String _displayString;

		private final String _html;

		private final int _templateIndex;

		// template index is just to make things easier in velocity engine
		HTML(String display, String html, int templateIndex) {
			_displayString = display;
			_html = html;
			_templateIndex = templateIndex;
		}

		String getDisplayString() {
			return _displayString;
		}

		String getHTML() {
			return _html;
		}

		int getTemplateIndex() {
			return _templateIndex;
		}

		String getDefaultDocType() {
			return TRANSITIONAL_XHTML10.getDisplayString();
		}
	}

	enum NSSTRINGENCODING {
		NSUTF8StringEncoding("NSUTF8StringEncoding"), 
		NSMacOSRomanStringEncoding("NSMacOSRomanStringEncoding"), 
		NSASCIIStringEncoding("NSASCIIStringEncoding"), 
		NSNEXTSTEPStringEncoding("NSNEXTSTEPStringEncoding"), 
		NSJapaneseEUCStringEncoding("NSJapaneseEUCStringEncoding"), 
		NSISOLatin1StringEncoding("NSISOLatin1StringEncoding"), 
		NSSymbolStringEncoding("NSSymbolStringEncoding"), 
		NSNonLossyASCIIStringEncoding("NSNonLossyASCIIStringEncoding"), 
		NSShiftJISStringEncoding("NSShiftJISStringEncoding"), 
		NSISOLatin2StringEncoding("NSISOLatin2StringEncoding"), 
		NSUnicodeStringEncoding("NSUnicodeStringEncoding"), 
		NSWindowsCP1251StringEncoding("NSWindowsCP1251StringEncoding"), 
		NSWindowsCP1252StringEncoding("NSWindowsCP1252StringEncoding"), 
		NSWindowsCP1253StringEncoding("NSWindowsCP1253StringEncoding"), 
		NSWindowsCP1254StringEncoding("NSWindowsCP1254StringEncoding"), 
		NSWindowsCP1250StringEncoding("NSWindowsCP1250StringEncoding"), 
		NSISO2022JPStringEncoding("NSISO2022JPStringEncoding"), 
		NSProprietaryStringEncoding("NSProprietaryStringEncoding");

		private final String _encoding;

		NSSTRINGENCODING(String encoding) {
			_encoding = encoding;
		}

		String getDisplayString() {
			return _encoding;
		}

		String getDefaultEncoding() {
			return NSSTRINGENCODING.NSUTF8StringEncoding.getDisplayString();
		}
	}

	/**
	 * Creates the page for the wocomponent creation wizard.
	 *
	 * @param selection the user's current selection in the Package Explorer
	 */
	public WOComponentCreationPage(IStructuredSelection selection) {
		// Pass the sanitized selection to super. processSelection() redirects
		// unsuitable targets (packages, source folders, classpath containers)
		// to the project, so the superclass can resolve a valid container path.
		super("createWOComponentPage1", WOComponentCreationPage.processSelection(selection));
		this.setTitle(Messages.getString("WOComponentCreationPage.title"));
		this.setDescription(Messages.getString("WOComponentCreationPage.description"));

		// Store the original (unprocessed) selection. We need it later in
		// initialPopulateContainerNameField() to extract the project and
		// find the "components" folder as the default location.
		_originalSelection = selection;

		// If the user selected a Java package folder, remember the IPackageFragment.
		// This is used later to pre-fill the package name field and to find
		// which folder the package's components live in.
		if (selection != null) {
			Object selectedObject = selection.getFirstElement();
			if (selectedObject instanceof IFolder) {
				IJavaElement parentJavaElement = JavaCore.create((IFolder) selectedObject);
				if (parentJavaElement instanceof IPackageFragment) {
					_currentSelection = parentJavaElement;
				}
			}
		}
	}

	/**
	 * Sanitizes the user's Package Explorer selection into something the
	 * superclass (WizardNewFileCreationPage) can use as a valid container.
	 *
	 * The Eclipse Package Explorer can return many different object types
	 * depending on what the user clicked:
	 *   - IProject / IJavaProject for the project root
	 *   - IFolder for regular folders
	 *   - IFile for files
	 *   - IPackageFragment for Java packages (looks like a folder)
	 *   - IPackageFragmentRoot for source folders (src/main/java, src/test/java)
	 *   - Classpath containers ("Maven Dependencies", "JRE System Library")
	 *   - Other IJavaElement subtypes
	 *
	 * Components shouldn't be created inside Java packages, source folders, or
	 * other components, so we redirect those selections to the project root.
	 * The actual smart default folder (components/) is handled separately in
	 * initialPopulateContainerNameField().
	 *
	 * @return a sanitized selection pointing to a valid container, or null
	 *         if the original selection is acceptable as-is
	 */
	private static IStructuredSelection processSelection(IStructuredSelection selection) {
		IStructuredSelection processedSelection = null;
		if (selection != null) {
			Object selectedObject = selection.getFirstElement();

			// If a file is selected, use its parent folder instead
			if (selectedObject instanceof IFile) {
				selectedObject = ((IFile)selectedObject).getParent();
				processedSelection = null;
			}

			if (selectedObject instanceof IFolder) {
				IFolder currentFolder = (IFolder) selectedObject;
				IJavaElement parentJavaElement = JavaCore.create(currentFolder);
				if (parentJavaElement instanceof IPackageFragment) {
					// Java package — don't create components inside packages, redirect to project
					processedSelection = new StructuredSelection(currentFolder.getProject());
				} else if (parentJavaElement instanceof IPackageFragmentRoot) {
					// Source folder (e.g. src/main/java) — redirect to project
					processedSelection = new StructuredSelection(currentFolder.getProject());
				} else if (currentFolder.getName().endsWith(".wo")) {
					// Another .wo component — go up one level to avoid nesting components
					processedSelection = new StructuredSelection(currentFolder.getParent());
				}
			} else if (selectedObject instanceof IJavaElement && !(selectedObject instanceof IJavaProject)) {
				// Classpath containers (e.g. "Maven Dependencies"), package fragment roots
				// shown as JDT elements rather than folders, and other JDT-specific nodes.
				// These aren't valid containers for files, so redirect to the project.
				// IJavaProject is excluded because it maps directly to IProject and is
				// already handled correctly by the superclass.
				try {
					IProject project = ((IJavaElement) selectedObject).getJavaProject().getProject();
					processedSelection = new StructuredSelection(project);
				} catch (Exception e) {
					// Some IJavaElement types might not have a java project (unlikely but defensive)
				}
			}
		}
		return processedSelection;
	}

	/**
	 * Called by the superclass during createControl() to set the initial value
	 * of the container (destination folder) text field.
	 *
	 * We override this to implement smart defaulting:
	 * 1. If a Java package was selected, find the folder where that package's
	 *    components already live (so new components go alongside existing ones).
	 * 2. Otherwise, find the "components" folder under src/main/ and default there.
	 * 3. If neither works, fall back to super (which uses the raw selection).
	 *
	 * Important: when we call setContainerFullPath() and return without calling
	 * super, it works because the text widget already exists at this point and
	 * setContainerFullPath() writes directly to it. If we called super after
	 * setting our path, super would overwrite it.
	 */
	@Override
	protected void initialPopulateContainerNameField() {
		if (_originalSelection != null) {
			Object selectedObject = _originalSelection.getFirstElement();

			// If the user selected a Java package, find where that package's
			// components are stored and default to that folder
			if (_currentSelection instanceof IPackageFragment) {
				IPath packagePath = componentPathForPackage((IPackageFragment) _currentSelection);
				if (packagePath != null) {
					setContainerFullPath(packagePath);
					return;
				}
			}

			// For any other selection, extract the project and look for
			// a "components" folder under src/main/. This handles all the
			// various selection types: IJavaElement (project root, source
			// folders, classpath containers) and IResource (folders, files).
			IProject project = null;
			if (selectedObject instanceof IJavaElement) {
				project = ((IJavaElement) selectedObject).getJavaProject().getProject();
			} else if (selectedObject instanceof IResource) {
				project = ((IResource) selectedObject).getProject();
			}
			if (project != null) {
				IPath componentsFolder = findComponentsFolder(project);
				if (componentsFolder != null) {
					setContainerFullPath(componentsFolder);
					return;
				}
			}
		}

		// No smart default found — let the superclass use its default logic
		super.initialPopulateContainerNameField();
	}

	/**
	 * Suppresses the "Advanced" linked resource controls from the superclass.
	 * We don't support creating linked WO components.
	 */
	@Override
	protected void createAdvancedControls(Composite parent) {
		// intentionally empty
	}

	/**
	 * Linked resources aren't applicable to WO components, so always return OK.
	 */
	@Override
	protected IStatus validateLinkedResource() {
		return Status.OK_STATUS;
	}

	/**
	 * Validates the wizard page state. Called whenever the user changes
	 * the container path or file name.
	 *
	 * getContainerFullPath() can return null if an unrecognized selection
	 * type was used, so we must guard against that before accessing segments.
	 */
	@Override
	protected boolean validatePage() {
		IPath containerPath = getContainerFullPath();
		if (containerPath == null || containerPath.segmentCount() == 0) {
			setErrorMessage("Please select a valid location");
			return false;
		}

		// Validate that the component name is a legal Java identifier
		// (since we also generate a .java file with this name)
		IStatus status = JavaConventions.validateCompilationUnitName(this.getFileName() + ".java",
				CompilerOptions.VERSION_1_3, CompilerOptions.VERSION_1_3);
		if (!status.isOK()) {
			setErrorMessage(status.getMessage());
			return false;
		}

		// Check for duplicate component names within the project.
		// The first path segment is always the project name in Eclipse workspace paths.
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(containerPath.segment(0));

		// This may need to change depending on how we want to deal with localized components in future.
		LocatePlugin locatePlugin = LocatePlugin.getDefault();
		try {
			LocalizedComponentsLocateResult result = locatePlugin.getLocalizedComponentsLocateResult(project, getFileName());
			if (result.getResources().length > 0) {
				setErrorMessage("A component by that name already exists");
				return false;
			}
		} catch (Exception e) {
			WizardsPlugin.getDefault().log(e);
		}

		// Prevent creating a component inside another component's .wo folder
		if (containerPath.lastSegment().endsWith(".wo")) {
			setErrorMessage("Cannot create a component within another component");
			return false;
		}

		return super.validatePage();
	}

	/**
	 * Creates the wizard page UI. Called by the wizard framework.
	 *
	 * super.createControl() creates the container selector and file name fields,
	 * and calls initialPopulateContainerNameField() to set the initial container.
	 * After that, we add our WO-specific controls: package, superclass, HTML
	 * doctype, encoding, and optional files (API, body tag).
	 *
	 * Note: getContainerFullPath() is used here to initialize the package and
	 * superclass fields. It can be null if an unrecognized selection type was
	 * used, so we null-check before using it.
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);

		Composite composite = (Composite) getControl();
		this.setFileName(Messages.getString("WOComponentCreationPage.newComponent.defaultName"));

		// --- Java settings group (package + superclass) ---
		Group javaGroup = new Group(composite, SWT.NONE);
		javaGroup.setText(Messages.getString("WOComponentCreationPage.creationOptions.javaFile.group"));
		GridLayout javaLayout = new GridLayout();
		javaLayout.numColumns = 4;
		javaGroup.setLayout(javaLayout);
		javaGroup.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));

		// Package field with browse button
		PackageButtonAdapter packageButtonAdapter = new PackageButtonAdapter();
		_packageDialogField = new StringButtonStatusDialogField(packageButtonAdapter);
		_packageDialogField.setDialogFieldListener(packageButtonAdapter);
		_packageDialogField.setLabelText(NewWizardMessages.NewTypeWizardPage_package_label);
		_packageDialogField.setButtonLabel(NewWizardMessages.NewTypeWizardPage_package_button);
		_packageDialogField.setStatusWidthHint(NewWizardMessages.NewTypeWizardPage_default);
		_packageDialogField.doFillIntoGrid(javaGroup, 4);
		Text packageText = _packageDialogField.getTextControl(null);
		LayoutUtil.setWidthHint(packageText, convertWidthInCharsToPixels(40));
		LayoutUtil.setHorizontalGrabbing(packageText);

		// Pre-fill the package name. If the user selected a Java package,
		// use that directly. Otherwise, try to infer the package from existing
		// components in the destination folder or from the "Main" component.
		if (_currentSelection instanceof IPackageFragment) {
			_packageDialogField.setText(((IPackageFragment) _currentSelection).getElementName());
		} else {
			String _package = null;
			IPath containerPath = this.getContainerFullPath();
			if (containerPath != null) {
				IResource _resource = ResourcesPlugin.getWorkspace().getRoot().findMember(containerPath);
				if (_resource instanceof IFolder) {
					_package = packageNameForComponentFolder((IFolder)_resource);
				}
				if (_package == null) {
					_package = packageNameForComponent("Main");
				}
			}
			if (_package == null) {
				_package = "";
			}
			_packageDialogField.setText(_package);
		}

		// Superclass field with browse button
		SuperclassButtonAdapter superclassButtonAdapter = new SuperclassButtonAdapter();
		_superclassDialogField = new StringButtonStatusDialogField(superclassButtonAdapter);
		_superclassDialogField.setDialogFieldListener(superclassButtonAdapter);
		_superclassDialogField.setLabelText(NewWizardMessages.NewTypeWizardPage_superclass_label);
		_superclassDialogField.setButtonLabel(NewWizardMessages.NewTypeWizardPage_superclass_button);
		_superclassDialogField.setStatusWidthHint(NewWizardMessages.NewTypeWizardPage_default);
		_superclassDialogField.doFillIntoGrid(javaGroup, 4);

		// Pre-fill superclass from the target project's framework type.
		// We always derive this from the project rather than using a saved
		// preference — the preference would be stale when switching between
		// ng-objects and WebObjects projects.
		IPath scContainerPath = getContainerFullPath();
		if (scContainerPath != null && scContainerPath.segmentCount() > 0) {
			IProject wizProject = ResourcesPlugin.getWorkspace().getRoot().getProject(scContainerPath.segment(0));
			_superclassDialogField.setText(ParsleyProject.getComponentClass(wizProject));
		} else {
			_superclassDialogField.setText("");
		}
		Text superclassText = _superclassDialogField.getTextControl(null);
		LayoutUtil.setWidthHint(superclassText, convertWidthInCharsToPixels(40));
		LayoutUtil.setHorizontalGrabbing(superclassText);

		new Label(composite, SWT.NONE); // vertical spacer

		// --- Component format group (standalone HTML vs .wo bundle) ---
		Group formatGroup = new Group(composite, SWT.NONE);
		formatGroup.setLayout(new GridLayout(2, false));
		formatGroup.setText(Messages.getString("WOComponentCreationPage.componentFormat.group"));
		formatGroup.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));

		_standaloneRadio = new Button(formatGroup, SWT.RADIO);
		_standaloneRadio.setText(Messages.getString("WOComponentCreationPage.componentFormat.standalone"));

		_bundleRadio = new Button(formatGroup, SWT.RADIO);
		_bundleRadio.setText(Messages.getString("WOComponentCreationPage.componentFormat.bundle"));

		// Default format: saved preference > project type detection.
		// ng-objects projects default to standalone; WO projects to bundles.
		boolean defaultToStandalone = resolveDefaultFormat();
		_standaloneRadio.setSelection(defaultToStandalone);
		_bundleRadio.setSelection(!defaultToStandalone);

		// When the format changes, show/hide the optional files group
		// (body tag, doctype, API, encoding are only relevant for bundles)
		SelectionListener formatListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateOptionalFilesVisibility();
			}
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				updateOptionalFilesVisibility();
			}
		};
		_standaloneRadio.addSelectionListener(formatListener);
		_bundleRadio.addSelectionListener(formatListener);

		// --- Component options group (body tag, HTML doctype, API file, encoding) ---
		// Only visible when .wo bundle format is selected.
		_optionalFilesGroup = new Group(composite, SWT.NONE);
		_optionalFilesGroup.setLayout(new GridLayout(3, false));
		_optionalFilesGroup.setText(Messages.getString("WOComponentCreationPage.creationOptions.group"));
		_optionalFilesGroup.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));

		ButtonSelectionAdaptor listener = new ButtonSelectionAdaptor();
		_bodyCheckbox = new Button(_optionalFilesGroup, SWT.CHECK);
		_bodyCheckbox.setText(Messages.getString("WOComponentCreationPage.creationOptions.bodyTag.button"));
		_bodyCheckbox.setSelection(this.getDialogSettings().getBoolean(BODY_CHECKBOX_KEY));
		_bodyCheckbox.setAlignment(SWT.CENTER);
		_bodyCheckbox.addListener(SWT.Selection, this);
		_bodyCheckbox.addSelectionListener(listener);

		Label htmlLabel = new Label(_optionalFilesGroup, SWT.RIGHT);
		htmlLabel.setText(Messages.getString("WOComponentCreationPage.creationOptions.bodyTag.label"));
		htmlLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		_htmlCombo = new Combo(_optionalFilesGroup, SWT.DROP_DOWN);
		_htmlCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		populateHTMLCombo(_htmlCombo);
		refreshButtonSettings(_bodyCheckbox);

		_apiCheckbox = new Button(_optionalFilesGroup, SWT.CHECK);
		GridData apiLayoutData = new GridData();
		_apiCheckbox.setLayoutData(apiLayoutData);
		_apiCheckbox.setText(Messages.getString("WOComponentCreationPage.creationOptions.apiFile.button"));
		_apiCheckbox.setSelection(this.getDialogSettings().getBoolean(API_CHECKBOX_KEY));
		_apiCheckbox.addListener(SWT.Selection, this);
		_apiCheckbox.addSelectionListener(listener);

		Label encodingLabel = new Label(_optionalFilesGroup, SWT.RIGHT);
		encodingLabel.setText(Messages.getString("WOComponentCreationPage.creationOptions.wooFile.label"));
		encodingLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		_encodingCombo = new Combo(_optionalFilesGroup, SWT.DROP_DOWN);
		_encodingCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		populateStringEncodingCombo(_encodingCombo);

		// Set initial visibility of the optional files group
		updateOptionalFilesVisibility();

		setPageComplete(validatePage());
	}
	
	/**
	 * Creates the component files. For .wo bundles, creates the folder with
	 * .html, .wod, .woo, .java, and optionally .api. For standalone, creates
	 * just the .html and .java files.
	 *
	 * <p>Called by WOComponentCreationWizard.performFinish() after the user
	 * clicks Finish.
	 *
	 * <p>Eclipse workspace paths always start with the project name as the
	 * first segment. If the path has only one segment, the component goes
	 * in the project root. Otherwise, we strip the project segment to get
	 * the project-relative subfolder path.
	 *
	 * @return true if creation was successful, false to keep the wizard open
	 */
	public boolean createComponent() {
		WOComponentCreator componentCreator;
		String componentName = getFileName();
		String packageName = _packageDialogField.getText();
		String superclassName = _superclassDialogField.getText();
		boolean standalone = isStandaloneSelected();

		// First path segment = project name (e.g. "myproject/src/main/components" -> "myproject")
		IProject actualProject = ResourcesPlugin.getWorkspace().getRoot().getProject(getContainerFullPath().segment(0));

		switch (getContainerFullPath().segmentCount()) {
		case 0:
			// Can't happen — validatePage() rejects empty paths
			setErrorMessage("unknown error");
			return false;
		case 1:
			// Path is just the project root (e.g. "/myproject")
			componentCreator = new WOComponentCreator(actualProject, componentName, packageName, superclassName, _bodyCheckbox.getSelection(), _apiCheckbox.getSelection(), standalone, this);
			break;
		default:
			// Path is a subfolder within the project — strip the project segment
			// to get the project-relative path (e.g. "src/main/components")
			IFolder subprojectFolder = actualProject.getFolder(getContainerFullPath().removeFirstSegments(1));
			componentCreator = new WOComponentCreator(subprojectFolder, componentName, packageName, superclassName, _bodyCheckbox.getSelection(), _apiCheckbox.getSelection(), standalone, this);
			break;
		}

		// Persist the user's choices so they're remembered next time
		// (superclass is not persisted — it's always derived from the project type)
		this.getDialogSettings().put(WOComponentCreationPage.BODY_CHECKBOX_KEY, _bodyCheckbox.getSelection());
		this.getDialogSettings().put(WOComponentCreationPage.HTML_DOCTYPE_KEY, _htmlCombo.getText());
		this.getDialogSettings().put(WOComponentCreationPage.NSSTRING_ENCODING_KEY, _encodingCombo.getText());
		this.getDialogSettings().put(WOComponentCreationPage.API_CHECKBOX_KEY, _apiCheckbox.getSelection());
		this.getDialogSettings().put(WOComponentCreationPage.COMPONENT_FORMAT_KEY,
				standalone ? FORMAT_STANDALONE : FORMAT_BUNDLE);

		IRunnableWithProgress op = new WorkspaceModifyDelegatingOperation(componentCreator);
		return createResourceOperation(op);
	}

	/**
	 * Populate a SWT Combo with HTML doctypes
	 * 
	 * @param c
	 */
	private void populateHTMLCombo(Combo c) {

		for (HTML entry : HTML.values()) {
			c.add(entry.getDisplayString());
		}

		selectHTMLDocTypePreference(c);
	}

	/**
	 * Pick the previous encoding preference else default to
	 * HTML.TRANSITIONAL_XHTML10
	 * 
	 * @param c
	 */
	private void selectHTMLDocTypePreference(Combo c) {
		String previousDocType = this.getDialogSettings().get(HTML_DOCTYPE_KEY);

		if (previousDocType != null && previousDocType.length() > 0) {
			int i = 0;
			for (HTML entry : HTML.values()) {
				if (previousDocType.equals(entry.getDisplayString())) {
					c.select(i);
					return;
				}
				i++;
			}
		}
		// default
		c.select(3);
	}

	/**
	 * Return the HTML for the selected html doc type
	 * 
	 * @return defaults to HTML.TRANSITIONAL_XHTML10
	 */
	public HTML getSelectedHTMLDocType() {
		if (_bodyCheckbox.getSelection()) {
			return getHTMLForDisplayString(_htmlCombo.getText());
		}

		return HTML.TRANSITIONAL_XHTML10;
	}

	/**
	 * Return HTML to insert for selected html/xhtml doc type
	 * 
	 * @param displayString
	 * @return selected doc type or HTML.TRANSITIONAL_XHTML10
	 */
	private HTML getHTMLForDisplayString(String displayString) {
		for (HTML entry : HTML.values()) {

			if (displayString.equals(entry.getDisplayString())) {
				return entry;
			}
		}

		return HTML.TRANSITIONAL_XHTML10;
	}

	/**
	 * Populate a SWT Combo with NSStringEncoding doctypes (See NSString.h)
	 * 
	 * @param c
	 */
	private void populateStringEncodingCombo(Combo c) {

		for (NSSTRINGENCODING entry : NSSTRINGENCODING.values()) {
			c.add(entry.getDisplayString());
		}

		selectNSStringEncodingPreference(c);
	}

	/**
	 * Pick the previous encoding preference else default to
	 * NSSTRINGENCODING.NSUTF8StringEncoding
	 * 
	 * @param c
	 */
	private void selectNSStringEncodingPreference(Combo c) {
		String previousEncoding = this.getDialogSettings().get(NSSTRING_ENCODING_KEY);

		if (previousEncoding != null && previousEncoding.length() > 0) {
			int i = 0;
			for (NSSTRINGENCODING entry : NSSTRINGENCODING.values()) {
				if (previousEncoding.equals(entry.getDisplayString())) {
					c.select(i);
					return;
				}
				i++;
			}
		}
		// default
		c.select(0);
	}

	/**
	 * Return current selected encoding
	 * 
	 * @return defaults to NSUTF8StringEncoding
	 */
	public String getSelectedEncoding() {
		return getEncodingForDisplayString(_encodingCombo.getText());

//		return NSSTRINGENCODING.NSUTF8StringEncoding.getDisplayString();
	}

	/**
	 * Return the encoding value to insert into the .woo file
	 * 
	 * @param displayString
	 * @return selected encoding or NSUTF8StringEncoding if not set
	 */
	private String getEncodingForDisplayString(String displayString) {
		for (NSSTRINGENCODING entry : NSSTRINGENCODING.values()) {

			if (displayString.equals(entry.getDisplayString())) {
				return displayString;
			}
		}

		return NSSTRINGENCODING.NSUTF8StringEncoding.getDefaultEncoding();
	}

	/**
	 * (non-Javadoc) Method declared on WizardNewFileCreationPage.
	 */
	protected String getNewFileLabel() {
		return Messages.getString("WOComponentCreationPage.newComponent.label");
	}

	public IResource[] getResourcesToReveal() {
		return _resourcesToReveal;
	}

	public void setResourcesToReveal(IResource[] resources) {
		this._resourcesToReveal = resources;
	}

	/**
	 * Looks up the Java package name for a given component by finding its .java
	 * file and reading the package from it. Used to pre-fill the package field
	 * based on existing components (e.g. if "Main" is in com.example.app,
	 * new components should probably go there too).
	 *
	 * @return the package name, or null if the component doesn't exist or has no .java file
	 */
	private String packageNameForComponent(String componentName) {
		IPath containerPath = getContainerFullPath();
		if (containerPath == null || containerPath.segmentCount() == 0) {
			return null;
		}
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(containerPath.segment(0));
		try {
			LocalizedComponentsLocateResult result = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
			IType javaType;
			if (result != null && (javaType = result.getDotJavaType()) != null) {
				return javaType.getPackageFragment().getElementName();
			}
		} catch (CoreException e) {
			WizardsPlugin.getDefault().log(e);
		} catch (LocateException e) {
			WizardsPlugin.getDefault().log(e);
		}
		return null;
	}

	/**
	 * Finds the Java package name used by components in the given folder.
	 * Looks for the first .wo subfolder and checks what package its .java
	 * file is in. This is used when the user selects a components folder
	 * directly, to pre-fill the package field.
	 *
	 * @return the package name, or null if no components are found in the folder
	 */
	private String packageNameForComponentFolder(IFolder folder) {
		try {
			for(IResource resource : folder.members()) {
				if ("wo".equals(resource.getLocation().getFileExtension())) {
					return packageNameForComponent(resource.getLocation().removeFileExtension().lastSegment());
				}
			}
		} catch (CoreException e) {
			WizardsPlugin.getDefault().log(e);
		}
		return null;
	}

	/**
	 * Searches for a "components" folder in the given project.
	 * Returns the full path of the first match, or null if none found.
	 *
	 * @see ParsleyProject#findComponentsFolder(IProject)
	 */
	private static IPath findComponentsFolder(IProject project) {
		IFolder folder = ParsleyProject.findComponentsFolder(project);
		return folder != null ? folder.getFullPath() : null;
	}

	/**
	 * Given a Java package, finds the folder where that package's components
	 * (.wo folders) are stored. This allows the wizard to default to the same
	 * folder when the user selects a Java package in the Package Explorer.
	 *
	 * Iterates through the Java files in the package and checks if any of them
	 * correspond to a WO component. If so, returns the parent folder of the
	 * first component found.
	 *
	 * @return the folder path where the package's components live, or null
	 */
	private IPath componentPathForPackage(IPackageFragment _selection) {
		try {
			LocatePlugin locate = LocatePlugin.getDefault();
			for (IJavaElement element : _selection.getChildren()) {
				String componentName = locate.fileNameWithoutExtension(element.getElementName());
				LocalizedComponentsLocateResult result = locate.getLocalizedComponentsLocateResult(
						_selection.getJavaProject().getProject(), componentName);
				IFolder[] components = result.getComponents();
				if (components.length > 0) {
					// Return the parent of the .wo folder (i.e. the components directory)
					IContainer selectionPath = components[0].getParent();
					return selectionPath.getFullPath();
				}
			}
		} catch (CoreException e) {
			WizardsPlugin.getDefault().log(e);
		} catch (LocateException e) {
			WizardsPlugin.getDefault().log(e);
		}
		return null;
	}

	/**
	 * Opens a dialog for the user to browse and select a Java package.
	 * Lists all packages from source folders in the project.
	 */
	private IPackageFragment choosePackage() {
		IPath containerPath = getContainerFullPath();
		if (containerPath == null || containerPath.segmentCount() == 0) {
			return null;
		}
		List<IJavaElement> packagesList = new LinkedList<IJavaElement>();
		try {
			IProject actualProject = ResourcesPlugin.getWorkspace().getRoot().getProject(containerPath.segment(0));
			IJavaProject javaProject = JavaModelManager.getJavaModelManager().getJavaModel().getJavaProject(actualProject);
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
			for (int k = 0; k < roots.length; k++) {
				if (roots[k].getKind() == IPackageFragmentRoot.K_SOURCE) {
					IJavaElement[] children = roots[k].getChildren();
					for (int i = 0; i < children.length; i++) {
						packagesList.add(children[i]);
					}
				}
			}
		} catch (JavaModelException e) {
			WizardsPlugin.getDefault().log(e);
		}
		IJavaElement[] packages = packagesList.toArray(new IJavaElement[packagesList.size()]);

		ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT));
		dialog.setIgnoreCase(false);
		dialog.setTitle(NewWizardMessages.NewTypeWizardPage_ChoosePackageDialog_title);
		dialog.setMessage(NewWizardMessages.NewTypeWizardPage_ChoosePackageDialog_description);
		dialog.setEmptyListMessage(NewWizardMessages.NewTypeWizardPage_ChoosePackageDialog_empty);
		dialog.setFilter(_packageDialogField.getText());
		dialog.setElements(packages);
		if (dialog.open() == Window.OK) {
			return (IPackageFragment) dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * Opens a dialog for the user to browse and select a superclass.
	 * Lists all WO element (component) classes available in the project.
	 */
	private String chooseSuperclass() {
		IPath containerPath = getContainerFullPath();
		if (containerPath == null || containerPath.segmentCount() == 0) {
			return null;
		}
		Set<String> superclasses = new HashSet<String>();
		try {
			IProject actualProject = ResourcesPlugin.getWorkspace().getRoot().getProject(containerPath.segment(0));
			IJavaProject javaProject = JavaModelManager.getJavaModelManager().getJavaModel().getJavaProject(actualProject);

			TypeNameCollector typeNameCollector = new TypeNameCollector(javaProject, false);
			BindingReflectionUtils.findMatchingElementClassNames("", SearchPattern.R_PREFIX_MATCH, typeNameCollector, new NullProgressMonitor());
			for (String typeName : typeNameCollector.getTypeNames()) {
				superclasses.add(typeName);
			}
		} catch (JavaModelException e) {
			WizardsPlugin.getDefault().log(e);
		}

		ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), new LabelProvider());
		dialog.setIgnoreCase(true);
		dialog.setTitle(NewWizardMessages.NewTypeWizardPage_SuperClassDialog_title);
		dialog.setMessage(NewWizardMessages.NewTypeWizardPage_SuperClassDialog_message);
		// dialog.setEmptyListMessage(NewWizardMessages.NewTypeWiz);
		dialog.setFilter(_superclassDialogField.getText());
		dialog.setElements(superclasses.toArray());
		if (dialog.open() == Window.OK) {
			return (String) dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * Returns true if the user has selected the standalone HTML format,
	 * false for .wo bundle format.
	 */
	public boolean isStandaloneSelected() {
		return _standaloneRadio.getSelection();
	}

	/**
	 * Determines the default component format based on project type,
	 * falling back to saved preference if the project type can't be
	 * detected.
	 *
	 * <p>Priority:
	 * <ol>
	 *   <li>Project type: ng-objects defaults to standalone, WO to bundle</li>
	 *   <li>Saved dialog setting from a previous wizard invocation
	 *       (only when project type detection fails)</li>
	 *   <li>Bundle as the ultimate fallback</li>
	 * </ol>
	 *
	 * <p>Project type is the primary source because the format choice is
	 * inherently project-specific — an ng-objects project should always
	 * default to standalone regardless of what the user last picked in
	 * a WO project, and vice versa.
	 *
	 * @return true for standalone, false for bundle
	 */
	private boolean resolveDefaultFormat() {
		// 1. Detect from project type — this is the primary source
		IPath containerPath = getContainerFullPath();
		if (containerPath != null && containerPath.segmentCount() > 0) {
			IProject project = ResourcesPlugin.getWorkspace().getRoot()
					.getProject(containerPath.segment(0));
			if (project != null && project.exists()) {
				ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
				if (pp != null) {
					return pp.isNGProject();
				}
			}
		}

		// 2. No project context — fall back to saved preference
		String savedFormat = this.getDialogSettings().get(COMPONENT_FORMAT_KEY);
		if (FORMAT_STANDALONE.equals(savedFormat)) {
			return true;
		}
		if (FORMAT_BUNDLE.equals(savedFormat)) {
			return false;
		}

		// 3. Fallback: bundle (traditional WO behavior)
		return false;
	}

	/**
	 * Shows or hides the Optional Files group based on the selected
	 * component format. Bundle components have optional files (body tag,
	 * doctype, API, encoding); standalone components don't need them.
	 *
	 * <p>Uses GridData.exclude to remove the group from the layout
	 * entirely when hidden, so the dialog doesn't have a blank gap.
	 */
	private void updateOptionalFilesVisibility() {
		boolean isBundle = _bundleRadio.getSelection();
		_optionalFilesGroup.setVisible(isBundle);
		((GridData) _optionalFilesGroup.getLayoutData()).exclude = !isBundle;

		// Re-layout the parent composite to adjust the dialog size
		_optionalFilesGroup.getParent().layout(true, true);
		getShell().pack(true);
	}

	/**
	 * Updates dependent UI controls when a checkbox is toggled.
	 * Currently, the HTML doctype combo is only enabled when the body checkbox is checked.
	 */
	private void refreshButtonSettings(Button button) {
		if (button.equals(_bodyCheckbox)) {
			_htmlCombo.setEnabled(_bodyCheckbox.getSelection());
		}
	}

	private void handleSelectionEvent(SelectionEvent event) {
		Widget w = event.widget;
		if (w instanceof Button) {
			refreshButtonSettings((Button) w);
		}
	}

	public StringButtonStatusDialogField getPackageDialogField() {
		return _packageDialogField;
	}

	public StringButtonStatusDialogField getSuperclassDialogField() {
		return _superclassDialogField;
	}

	/** Handles the "Browse..." button click for the package field */
	protected class PackageButtonAdapter implements IStringButtonAdapter, IDialogFieldListener {
		public void changeControlPressed(DialogField _field) {
			IPackageFragment pack = choosePackage();
			if (pack != null) {
				getPackageDialogField().setText(pack.getElementName());
			}
		}

		public void dialogFieldChanged(DialogField _field) {
			// Could be used for live validation of the package name
		}
	}

	/** Handles the "Browse..." button click for the superclass field */
	protected class SuperclassButtonAdapter implements IStringButtonAdapter, IDialogFieldListener {
		public void changeControlPressed(DialogField _field) {
			String superclass = chooseSuperclass();
			if (superclass != null) {
				getSuperclassDialogField().setText(superclass);
			}
		}

		public void dialogFieldChanged(DialogField _field) {
			// Could be used for live validation of the superclass name
		}
	}

	/** Forwards checkbox selection events to refreshButtonSettings() */
	protected class ButtonSelectionAdaptor implements SelectionListener {

		public void widgetDefaultSelected(SelectionEvent event) {
			handleSelectionEvent(event);
		}

		public void widgetSelected(SelectionEvent event) {
			handleSelectionEvent(event);
		}
	}
}

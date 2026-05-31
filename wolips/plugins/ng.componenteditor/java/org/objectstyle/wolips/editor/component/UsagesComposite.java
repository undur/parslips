package org.objectstyle.wolips.editor.component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Reusable composite that displays a "who uses this element?" reverse
 * dependency view. Contains a toolbar row (Refresh button + status label)
 * and a three-column {@link TableViewer} listing components that reference
 * the target element by name.
 *
 * <p>The scan is scoped to the element's own project and all projects that
 * transitively depend on it — a component in a library shows usages from
 * application projects that use that library, but not from unrelated projects.
 *
 * <p>This composite is shared by:
 * <ul>
 *   <li>{@link UsagesTab} — the Usages tab in the multi-tab component editor</li>
 *   <li>{@link org.objectstyle.wolips.editor.api.UsagesFormPage} —
 *       the Usages page in the standalone API editor</li>
 * </ul>
 *
 * <p>The scan runs lazily on first activation (via {@link #scanIfNeeded()})
 * or on manual refresh, using an Eclipse {@link Job} to avoid blocking
 * the UI thread. Results are delivered to the table via
 * {@link Display#asyncExec}.
 *
 * <p>The scanning logic reuses the regex patterns from
 * {@link RenameComponentProcessor} — the same patterns that power the
 * rename refactoring's cross-reference detection.
 */
public class UsagesComposite extends Composite {

	private final String _elementName;
	private final IProject _project;

	private TableViewer _tableViewer;
	private Label _statusLabel;
	private boolean _scanned;

	/**
	 * @param parent      the parent composite
	 * @param style       the SWT style bits
	 * @param elementName the name of the element/component to search for
	 * @param project     the project that owns this element — the scan covers
	 *                    this project and all projects that depend on it
	 */
	public UsagesComposite(Composite parent, int style, String elementName, IProject project) {
		super(parent, style);
		_elementName = elementName;
		_project = project;
		createContents();
	}

	/**
	 * Builds the UI: a toolbar row with Refresh button and status label,
	 * and a three-column table below (Component, Project, Count).
	 */
	private void createContents() {
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		// Toolbar row: Refresh button + status label
		Composite toolbar = new Composite(this, SWT.NONE);
		GridLayout toolbarLayout = new GridLayout(2, false);
		toolbarLayout.marginWidth = 5;
		toolbarLayout.marginHeight = 3;
		toolbar.setLayout(toolbarLayout);
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Button refreshButton = new Button(toolbar, SWT.PUSH);
		refreshButton.setText("Refresh");
		refreshButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				startScan();
			}
		});

		_statusLabel = new Label(toolbar, SWT.NONE);
		_statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		_statusLabel.setText("Click the Usages tab to scan for references.");

		// Table
		_tableViewer = new TableViewer(this,
				SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		Table table = _tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// Column: Component name
		TableViewerColumn componentColumn = new TableViewerColumn(_tableViewer, SWT.NONE);
		componentColumn.getColumn().setText("Component");
		componentColumn.getColumn().setWidth(250);
		componentColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ComponentUsage) element)._componentName;
			}
		});

		// Column: Project name
		TableViewerColumn projectColumn = new TableViewerColumn(_tableViewer, SWT.NONE);
		projectColumn.getColumn().setText("Project");
		projectColumn.getColumn().setWidth(200);
		projectColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ComponentUsage) element)._project.getName();
			}
		});

		// Column: Reference count (how many times the element appears in
		// that component's template)
		TableViewerColumn countColumn = new TableViewerColumn(_tableViewer, SWT.RIGHT);
		countColumn.getColumn().setText("Count");
		countColumn.getColumn().setWidth(60);
		countColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.valueOf(((ComponentUsage) element)._count);
			}
		});

		_tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		// Double-click opens the using component
		_tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				ComponentUsage usage = (ComponentUsage) selection.getFirstElement();
				if (usage != null && usage._file != null && usage._file.exists()) {
					try {
						IWorkbenchPage page = PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getActivePage();
						IDE.openEditor(page, usage._file, true);
					}
					catch (Exception e) {
						ComponenteditorPlugin.getDefault().log(e);
					}
				}
			}
		});
	}

	/**
	 * Triggers a scan on first call. Subsequent calls are no-ops —
	 * use {@link #startScan()} for manual refresh.
	 */
	public void scanIfNeeded() {
		if (!_scanned) {
			_scanned = true;
			startScan();
		}
	}

	/**
	 * Launches a background job to scan all workspace projects for references
	 * to this element. Results are posted to the table on the UI thread.
	 */
	public void startScan() {
		_statusLabel.setText("Scanning...");
		_tableViewer.setInput(new Object[0]);

		Job scanJob = new Job("Finding usages of " + _elementName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				List<ComponentUsage> usages = findUsages(monitor);
				if (!monitor.isCanceled()) {
					Display.getDefault().asyncExec(() -> {
						if (!_tableViewer.getTable().isDisposed()) {
							_tableViewer.setInput(usages);
							_statusLabel.setText(usages.size() + " usage"
									+ (usages.size() != 1 ? "s" : "") + " found");
						}
					});
				}
				return Status.OK_STATUS;
			}
		};
		scanJob.setSystem(false);
		scanJob.setPriority(Job.SHORT);
		scanJob.schedule();
	}

	/**
	 * Scans all open workspace projects for templates that reference this
	 * element. Reuses the regex matching from {@link RenameComponentProcessor}.
	 *
	 * <p>Should be called from a background thread.
	 */
	private List<ComponentUsage> findUsages(IProgressMonitor monitor) {
		List<ComponentUsage> usages = new ArrayList<>();

		// Scan the element's own project plus all projects that depend on
		// it (transitively). A component in a library can be used by any
		// application that depends on that library.
		Set<IProject> projectsToScan = new HashSet<>();
		collectDependentProjects(_project, projectsToScan);

		for (IProject project : projectsToScan) {
			if (monitor.isCanceled()) {
				break;
			}
			if (!project.isOpen() || !project.isAccessible()) {
				continue;
			}
			try {
				project.accept(new IResourceVisitor() {
					@Override
					public boolean visit(IResource resource) throws CoreException {
						if (monitor.isCanceled()) {
							return false;
						}
						if (resource.isDerived()) {
							return false;
						}
						if (resource.getType() != IResource.FILE) {
							return true;
						}

						IFile file = (IFile) resource;
						String extension = file.getFileExtension();
						if (extension == null) {
							return false;
						}

						try {
							boolean isHtml = "html".equalsIgnoreCase(extension);
							boolean isWod = "wod".equalsIgnoreCase(extension);

							if (!isHtml && !isWod) {
								return false;
							}

							// Skip this element's own files
							String ownerName = deriveComponentName(file);
							if (_elementName.equals(ownerName)) {
								return false;
							}

							String content = RenameComponentProcessor.readFileContent(file);
							if (content == null) {
								return false;
							}

							int count = 0;
							if (isHtml) {
								count = RenameComponentProcessor.htmlCountElementReferences(
										content, _elementName);
							}
							else if (isWod) {
								count = RenameComponentProcessor.wodCountElementReferences(
										content, _elementName);
							}

							if (count > 0 && ownerName != null) {
								// For WOD files, find the sibling HTML to open
								IFile fileToOpen = file;
								if (isWod) {
									fileToOpen = findSiblingHtml(file, ownerName);
									if (fileToOpen == null) {
										fileToOpen = file;
									}
								}
								usages.add(new ComponentUsage(
										ownerName, fileToOpen, project, count));
							}
						}
						catch (IOException e) {
							// Skip unreadable files
						}
						return false;
					}
				});
			}
			catch (CoreException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		// Sort by component name, then by project
		usages.sort((a, b) -> {
			int cmp = a._componentName.compareToIgnoreCase(b._componentName);
			if (cmp != 0) {
				return cmp;
			}
			return a._project.getName().compareToIgnoreCase(b._project.getName());
		});

		return usages;
	}

	/**
	 * Collects the given project and all projects that transitively depend on
	 * it (via {@link IProject#getReferencingProjects()}).
	 *
	 * <p>Uses a visited set for cycle detection — project dependency graphs
	 * can occasionally contain cycles due to misconfiguration.
	 */
	private static void collectDependentProjects(IProject project, Set<IProject> result) {
		if (!result.add(project)) {
			return; // already visited — prevents cycles
		}
		for (IProject dependent : project.getReferencingProjects()) {
			collectDependentProjects(dependent, result);
		}
	}

	/**
	 * Derives the component name from a template file.
	 *
	 * <p>For files inside a {@code .wo} folder (bundle templates), the component
	 * name is the folder name minus the {@code .wo} suffix. For standalone
	 * {@code .html} files, it's the file name minus the extension.
	 */
	private static String deriveComponentName(IFile file) {
		// Check if the file is inside a .wo folder
		if (file.getParent() != null
				&& file.getParent().getName().endsWith(".wo")) {
			String folderName = file.getParent().getName();
			return folderName.substring(0, folderName.length() - 3);
		}

		// Standalone template: strip extension
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			return name.substring(0, dot);
		}
		return name;
	}

	/**
	 * For a WOD file, finds the sibling HTML file in the same .wo folder,
	 * so double-click can open the template rather than the raw WOD file.
	 *
	 * @return the sibling HTML file, or null if not found
	 */
	private static IFile findSiblingHtml(IFile wodFile, String componentName) {
		if (wodFile.getParent() != null) {
			IFile html = wodFile.getParent().getFile(
					new Path(componentName + ".html"));
			if (html.exists()) {
				return html;
			}
		}
		return null;
	}

	/**
	 * A single usage result: identifies a component that references the
	 * target element, including how many times.
	 */
	static class ComponentUsage {
		final String _componentName;
		final IFile _file;
		final IProject _project;
		/** Number of references to the target element in this component. */
		final int _count;

		ComponentUsage(String componentName, IFile file, IProject project, int count) {
			_componentName = componentName;
			_file = file;
			_project = project;
			_count = count;
		}
	}
}

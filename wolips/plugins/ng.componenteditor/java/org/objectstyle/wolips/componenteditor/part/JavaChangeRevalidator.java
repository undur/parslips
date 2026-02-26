package org.objectstyle.wolips.componenteditor.part;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.ResourceUtil;
import org.objectstyle.wolips.componenteditor.part.ComponentEditor;
import org.objectstyle.wolips.wodclipse.core.builder.WodBuilder;

/**
 * Listens for Java file saves and re-validates any open component editors
 * in the same project. This ensures that validation markers are updated
 * when a referenced Java class is modified (e.g., adding a method that
 * resolves a missing binding keypath).
 */
public class JavaChangeRevalidator implements IResourceChangeListener {

	private static JavaChangeRevalidator instance;

	public static synchronized void install() {
		if (instance == null) {
			instance = new JavaChangeRevalidator();
			ResourcesPlugin.getWorkspace().addResourceChangeListener(instance, IResourceChangeEvent.POST_CHANGE);
		}
	}

	public static synchronized void uninstall() {
		if (instance != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(instance);
			instance = null;
		}
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}

		// Collect projects that had Java files changed
		Set<IProject> affectedProjects = new HashSet<>();
		try {
			delta.accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(IResourceDelta d) throws CoreException {
					IResource resource = d.getResource();
					if (resource instanceof IFile) {
						IFile file = (IFile) resource;
						if (d.getKind() == IResourceDelta.CHANGED
								&& (d.getFlags() & IResourceDelta.CONTENT) != 0
								&& "java".equals(file.getFileExtension())) {
							affectedProjects.add(file.getProject());
						}
						return false;
					}
					return true;
				}
			});
		} catch (CoreException e) {
			// Ignore
		}

		if (!affectedProjects.isEmpty()) {
			revalidateOpenComponents(affectedProjects);
		}
	}

	private void revalidateOpenComponents(Set<IProject> affectedProjects) {
		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
				for (IWorkbenchWindow window : windows) {
					IWorkbenchPage page = window.getActivePage();
					if (page == null) {
						continue;
					}
					for (IEditorReference ref : page.getEditorReferences()) {
						IEditorPart editor = ref.getEditor(false);
						if (!(editor instanceof ComponentEditor)) {
							continue;
						}
						// Get any file from this component editor to identify its project
						IEditorInput input = editor.getEditorInput();
						IFile file = ResourceUtil.getFile(input);
						if (file != null && affectedProjects.contains(file.getProject())) {
							WodBuilder.validateComponent(file, true, null);
						}
					}
				}
			} catch (Exception e) {
				// Ignore — don't let listener errors disrupt the workspace
			}
		});
	}
}

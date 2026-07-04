package org.objectstyle.wolips.editor.component;

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
import org.objectstyle.wolips.wodclipse.core.builder.WodBuilder;

/**
 * Listens for Java and API file saves and re-validates any open component editors
 * whose validation might be affected. This ensures that validation markers are updated
 * when a referenced Java class is modified (e.g., adding a method that resolves a
 * missing binding keypath) or when an API file changes (e.g., marking a binding as
 * required).
 *
 * <p>For Java file changes, only component editors in the same project are revalidated.
 * For API file changes, <b>all</b> open component editors are revalidated, because the
 * changed API file may belong to a dependency project that any consuming project could
 * reference — and determining the full project dependency graph would be expensive.
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

		// Collect projects that had Java files changed, and track whether any .api
		// file changed anywhere in the workspace
		Set<IProject> _javaAffectedProjects = new HashSet<>();
		boolean[] _apiChanged = new boolean[] { false };
		try {
			delta.accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(IResourceDelta d) throws CoreException {
					IResource resource = d.getResource();
					if (resource instanceof IFile) {
						IFile file = (IFile) resource;
						if (d.getKind() == IResourceDelta.CHANGED
								&& (d.getFlags() & IResourceDelta.CONTENT) != 0) {
							String ext = file.getFileExtension();
							if ("java".equals(ext)) {
								_javaAffectedProjects.add(file.getProject());
							}
							else if ("api".equals(ext) || "apiext".equals(ext)) {
								// An .apiext change alters an element's contract (required, choose/requires,
								// deprecation, unknownAttributes), so open component editors must re-validate —
								// same as .api. Revalidate all, since the changed element may be used anywhere.
								_apiChanged[0] = true;
							}
						}
						return false;
					}
					return true;
				}
			});
		} catch (CoreException e) {
			// Ignore
		}

		if (!_javaAffectedProjects.isEmpty() || _apiChanged[0]) {
			revalidateOpenComponents(_javaAffectedProjects, _apiChanged[0]);
		}
	}

	/**
	 * Revalidates open component editors affected by Java or API file changes.
	 *
	 * @param javaAffectedProjects projects where Java files changed — only editors
	 *     in these projects are revalidated for Java changes
	 * @param apiChanged if true, all open component editors are revalidated regardless
	 *     of project, because the API change may come from a dependency project
	 */
	private void revalidateOpenComponents(Set<IProject> javaAffectedProjects, boolean apiChanged) {
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
						IEditorInput input = editor.getEditorInput();
						IFile file = ResourceUtil.getFile(input);
						if (file == null) {
							continue;
						}
						// Revalidate if: an API file changed anywhere, or a Java file
						// changed in the same project as this component editor
						if (apiChanged || javaAffectedProjects.contains(file.getProject())) {
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

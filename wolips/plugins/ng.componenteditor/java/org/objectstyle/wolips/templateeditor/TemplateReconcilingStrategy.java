package org.objectstyle.wolips.templateeditor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * Reconciling strategy for TemplateSourceEditor that validates
 * inline WO bindings as the user types (with debouncing provided
 * by the MonoReconciler's delay).
 */
public class TemplateReconcilingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension {
	private TemplateSourceEditor _editor;
	private IProgressMonitor _progressMonitor;
	private IDocument _document;

	public TemplateReconcilingStrategy(TemplateSourceEditor editor) {
		_editor = editor;
	}

	public void setDocument(IDocument document) {
		_document = document;
	}

	public void reconcile() {
		IEditorInput input = _editor.getEditorInput();
		if (input instanceof IFileEditorInput) {
			IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					try {
						WodParserCache cache = _editor.getParserCache();
						cache.getHtmlEntry().setDocument(_document);
						cache.parse();
						cache.validate(true, false);
					}
					catch (Exception e) {
						Activator.getDefault().log(e);
					}
				}
			};

			try {
				IFile htmlFile = ((IFileEditorInput) input).getFile();
				ResourcesPlugin.getWorkspace().run(runnable, ResourcesPlugin.getWorkspace().getRuleFactory().markerRule(htmlFile), IWorkspace.AVOID_UPDATE, _progressMonitor);
			}
			catch (CoreException e) {
				Activator.getDefault().log(e);
			}
		}
	}

	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		reconcile();
	}

	public void reconcile(IRegion partition) {
		reconcile();
	}

	public void initialReconcile() {
		reconcile();
	}

	public void setProgressMonitor(IProgressMonitor monitor) {
		_progressMonitor = monitor;
	}
}

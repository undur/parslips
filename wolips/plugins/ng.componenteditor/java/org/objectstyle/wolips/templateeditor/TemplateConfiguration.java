package org.objectstyle.wolips.templateeditor;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.part.FileEditorInput;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.editor.wod.WodAnnotationHover;

import tk.eclipse.plugin.htmleditor.ColorProvider;
import tk.eclipse.plugin.htmleditor.HTMLHyperlinkDetector;
import tk.eclipse.plugin.htmleditor.assist.HTMLAssistProcessor;
import tk.eclipse.plugin.htmleditor.editors.HTMLConfiguration;

/**
 * Source viewer configuration for the template editor. Extends
 * {@link HTMLConfiguration} with template-specific features:
 *
 * <ul>
 *   <li>WO component binding autocomplete ({@link TemplateAssistProcessor})</li>
 *   <li>Inline WOD element hyperlinking</li>
 *   <li>Template validation reconciling</li>
 *   <li>Annotation hover (error messages on mouseover)</li>
 *   <li>Quick-assist (Cmd+1 quick-fixes for keypath errors)</li>
 * </ul>
 */
public class TemplateConfiguration extends HTMLConfiguration {
  private IContentAssistant _assistant;

  public TemplateConfiguration(ColorProvider colorProvider) {
    super(colorProvider);
  }

  @Override
  protected HTMLHyperlinkDetector createHyperlinkDetector() {
    HTMLHyperlinkDetector hyperlinkDetector = super.createHyperlinkDetector();
    hyperlinkDetector.addHyperlinkProvider(new InlineWodElementHyperlinkProvider());
    return hyperlinkDetector;
  }

  @Override
  public IReconciler getReconciler(ISourceViewer sourceViewer) {
    if (getEditorPart() instanceof TemplateSourceEditor) {
      TemplateReconcilingStrategy strategy = new TemplateReconcilingStrategy((TemplateSourceEditor) getEditorPart());
      MonoReconciler reconciler = new MonoReconciler(strategy, false);
      reconciler.setDelay(500);
      return reconciler;
    }
    return null;
  }

  /**
   * Returns a hover for the vertical ruler that shows error messages when
   * the mouse hovers over annotation markers.
   */
  @Override
  public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
    return new WodAnnotationHover(sourceViewer.getAnnotationModel(), getParserCacheOrNull());
  }

  /**
   * Returns a text hover that shows error messages on squiggly-underlined
   * text and component API documentation on {@code <wo:ComponentName>} tags.
   *
   * <p>When hovering over a component tag, the hover shows the component's
   * accepted bindings (from its {@code .api} file or the global
   * {@code WebObjectDefinitions.xml}), which bindings are required, and
   * their types/defaults. If the tag also has a validation error, the error
   * message is shown first, followed by the documentation.
   */
  @Override
  public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
    return new WodAnnotationHover(sourceViewer.getAnnotationModel(), getParserCacheOrNull());
  }

  /**
   * Attempts to obtain the parser cache for the current file. Returns null
   * if the cache can't be obtained (e.g. editor not fully initialized).
   */
  private WodParserCache getParserCacheOrNull() {
    try {
      if (getEditorPart() instanceof TemplateSourceEditor) {
        IFile file = ((FileEditorInput) getEditorPart().getEditorInput()).getFile();
        return WodParserCache.parser(file);
      }
    }
    catch (Exception e) {
      // Fall through — hover works without documentation
    }
    return null;
  }

  /**
   * Returns a quick-assist assistant that provides Cmd+1 quick-fixes
   * for keypath validation errors (e.g. "Replace 'nme' with 'name'").
   */
  @Override
  public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
    QuickAssistAssistant assistant = new QuickAssistAssistant();
    assistant.setQuickAssistProcessor(new TemplateQuickAssistProcessor());
    return assistant;
  }

  @Override
  protected HTMLAssistProcessor createAssistProcessor() {
    try {
      IFile file = ((FileEditorInput) getEditorPart().getEditorInput()).getFile();
      WodParserCache parserCache = WodParserCache.parser(file);

      // The project can be null if the editor opens before m2e has
      // finished importing the project (e.g. the wizard opens Main.html
      // immediately after creation). Guard against NPE here.
      ParsleyProject parsleyProject = null;
      if (parserCache.getProject() != null) {
        parsleyProject = (ParsleyProject) parserCache.getProject().getAdapter(ParsleyProject.class);
      }

      return new TemplateAssistProcessor(getEditorPart(), parserCache, parsleyProject);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to create assist processor.", e);
    }
  }
}

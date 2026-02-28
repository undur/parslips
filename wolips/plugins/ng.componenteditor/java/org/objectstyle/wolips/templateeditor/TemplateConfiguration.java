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
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.editor.WodAnnotationHover;

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
   * Returns a position-aware annotation hover that shows error messages
   * when the mouse hovers over squiggly-underlined text. Replaces the
   * default {@link tk.eclipse.plugin.htmleditor.editors.HTMLAnnotationHover}
   * which only works on the vertical ruler.
   */
  @Override
  public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
    return new WodAnnotationHover(sourceViewer.getAnnotationModel());
  }

  /**
   * Returns a text hover that shows error messages when the mouse hovers
   * over annotated text in the editor body. Uses the same annotation-model-based
   * hover as the vertical ruler.
   */
  @Override
  public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
    return new WodAnnotationHover(sourceViewer.getAnnotationModel());
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
      BuildProperties buildProperties = (BuildProperties)parserCache.getProject().getAdapter(BuildProperties.class);
      return new TemplateAssistProcessor(getEditorPart(), parserCache, buildProperties);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to create assist processor.", e);
    }
  }
}

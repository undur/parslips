package org.objectstyle.wolips.templateeditor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.objectstyle.wolips.wodclipse.core.quickfix.KeypathQuickFixGenerator;
import org.objectstyle.wolips.wodclipse.core.quickfix.ReplaceKeypathQuickFix;

/**
 * Quick-assist processor for the template editor, providing Cmd+1 support.
 *
 * <p>When invoked at a position that overlaps with a problem marker, this
 * processor reads the marker's "suggestions" attribute and offers completion
 * proposals that replace the invalid keypath segment with the suggested
 * correction. This gives the same quick-fix experience as the Problems view,
 * but accessible directly in the editor via Cmd+1.
 */
public class TemplateQuickAssistProcessor implements IQuickAssistProcessor {

  @Override
  public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
    return false;
  }

  @Override
  public boolean canFix(Annotation annotation) {
    if (annotation instanceof MarkerAnnotation) {
      try {
        IMarker marker = ((MarkerAnnotation) annotation).getMarker();
        String suggestions = (String) marker.getAttribute("suggestions");
        return suggestions != null && !suggestions.isEmpty();
      }
      catch (Exception e) {
        // Marker may have been deleted
      }
    }
    return false;
  }

  @Override
  public ICompletionProposal[] computeQuickAssistProposals(IQuickAssistInvocationContext invocationContext) {
    ISourceViewer viewer = invocationContext.getSourceViewer();
    int offset = invocationContext.getOffset();
    IAnnotationModel annotationModel = viewer.getAnnotationModel();
    IDocument document = viewer.getDocument();

    if (annotationModel == null || document == null) {
      return new ICompletionProposal[0];
    }

    // Resolve the line the cursor is on so we can offer quick-fixes
    // for any error on the same line, not just at the exact cursor position.
    int cursorLine;
    try {
      cursorLine = document.getLineOfOffset(offset);
    }
    catch (BadLocationException e) {
      return new ICompletionProposal[0];
    }

    List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();

    Iterator<?> annotations = annotationModel.getAnnotationIterator();
    while (annotations.hasNext()) {
      Annotation annotation = (Annotation) annotations.next();
      org.eclipse.jface.text.Position position = annotationModel.getPosition(annotation);

      if (position == null) {
        continue;
      }

      // Match any annotation on the same line as the cursor.
      int annotationLine;
      try {
        annotationLine = document.getLineOfOffset(position.getOffset());
      }
      catch (BadLocationException e) {
        continue;
      }
      if (annotationLine != cursorLine) {
        continue;
      }

      if (!(annotation instanceof MarkerAnnotation)) {
        continue;
      }

      IMarker marker = ((MarkerAnnotation) annotation).getMarker();
      try {
        String suggestionsStr = (String) marker.getAttribute("suggestions");
        if (suggestionsStr == null || suggestionsStr.isEmpty()) {
          continue;
        }

        String message = (String) marker.getAttribute(IMarker.MESSAGE);
        String invalidKey = KeypathQuickFixGenerator.extractInvalidKey(message);
        if (invalidKey == null) {
          continue;
        }

        // Read the document text at the marker range to find the
        // invalid key segment within the binding value.
        int charStart = marker.getAttribute(IMarker.CHAR_START, -1);
        int charEnd = marker.getAttribute(IMarker.CHAR_END, -1);
        if (charStart < 0 || charEnd < 0 || charEnd <= charStart) {
          continue;
        }

        String markedText = document.get(charStart, charEnd - charStart);
        int keyOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(markedText, invalidKey);
        if (keyOffset < 0) {
          continue;
        }

        int replaceStart = charStart + keyOffset;

        String[] suggestions = suggestionsStr.split(";");
        for (String suggestion : suggestions) {
          String trimmed = suggestion.trim();
          if (!trimmed.isEmpty()) {
            proposals.add(new CompletionProposal(
                trimmed,
                replaceStart,
                invalidKey.length(),
                trimmed.length(),
                null,
                "Replace '" + invalidKey + "' with '" + trimmed + "'",
                null,
                null));
          }
        }
      }
      catch (BadLocationException e) {
        // Marker position may be stale after edits
      }
      catch (Exception e) {
        // Marker may have been deleted
      }
    }

    return proposals.toArray(new ICompletionProposal[proposals.size()]);
  }

  @Override
  public String getErrorMessage() {
    return null;
  }
}

package org.objectstyle.wolips.templateeditor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
 * proposals that replace the invalid text with the suggested correction.
 * Supports both keypath errors ("There is no key 'nme'") and element type
 * errors ("The class for 'Str' is either missing").
 *
 * <p>Multiple errors on the same line are handled correctly — each marker
 * produces its own set of proposals, and the replacements target different
 * regions (e.g. the tag name vs. a binding value).
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

    // Track which proposals we've already added to avoid duplicates.
    // Duplicates can occur when both the builder and the reconciler create
    // markers for the same error, resulting in two MarkerAnnotation objects
    // with identical messages and positions.
    Set<String> seenProposals = new HashSet<String>();

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
      addProposalsForMarker(marker, document, proposals, seenProposals);
    }

    return proposals.toArray(new ICompletionProposal[proposals.size()]);
  }

  /**
   * Extracts the invalid name and suggestions from a marker and builds
   * completion proposals for each suggestion.  Handles keypath errors
   * ("There is no key 'nme'") and element type errors ("The class for 'Str'
   * is either missing"), including miscapitalized tag shortcuts which use
   * the same message format.
   */
  private void addProposalsForMarker(IMarker marker, IDocument document,
      List<ICompletionProposal> proposals, Set<String> seenProposals) {
    try {
      String suggestionsStr = (String) marker.getAttribute("suggestions");
      if (suggestionsStr == null || suggestionsStr.isEmpty()) {
        return;
      }

      // Try to extract the invalid name from the error message.
      // Could be a keypath segment or an element type name (including
      // miscapitalized tag shortcuts, which use the same message format).
      String message = (String) marker.getAttribute(IMarker.MESSAGE);
      String invalidName = KeypathQuickFixGenerator.extractInvalidKey(message);
      if (invalidName == null) {
        invalidName = KeypathQuickFixGenerator.extractInvalidElementType(message);
      }
      if (invalidName == null) {
        return;
      }

      // Read the document text at the marker range to find the
      // invalid name within the marked region.
      int charStart = marker.getAttribute(IMarker.CHAR_START, -1);
      int charEnd = marker.getAttribute(IMarker.CHAR_END, -1);
      if (charStart < 0 || charEnd < 0 || charEnd <= charStart) {
        return;
      }

      String markedText = document.get(charStart, charEnd - charStart);
      int keyOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(markedText, invalidName);
      if (keyOffset < 0) {
        return;
      }

      int replaceStart = charStart + keyOffset;

      String[] suggestions = suggestionsStr.split(";");
      for (String suggestion : suggestions) {
        String trimmed = suggestion.trim();
        if (!trimmed.isEmpty()) {
          // Deduplicate: build a key from the replacement offset, length,
          // and suggestion text. This prevents the same proposal from
          // appearing twice when both the builder and reconciler create
          // markers for the same error.
          String proposalKey = replaceStart + ":" + invalidName.length() + ":" + trimmed;
          if (seenProposals.add(proposalKey)) {
            proposals.add(new CompletionProposal(
                trimmed,
                replaceStart,
                invalidName.length(),
                trimmed.length(),
                null,
                "Replace '" + invalidName + "' with '" + trimmed + "'",
                null,
                null));
          }
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

  @Override
  public String getErrorMessage() {
    return null;
  }
}

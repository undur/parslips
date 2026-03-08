package org.objectstyle.wolips.templateeditor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
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

        // Has replacement suggestions (keypath or element type typos)
        String suggestions = (String) marker.getAttribute("suggestions");
        if (suggestions != null && !suggestions.isEmpty()) {
          return true;
        }

        // Keypath error without suggestions — can still offer "Create key"
        String message = (String) marker.getAttribute(IMarker.MESSAGE);
        if (KeypathQuickFixGenerator.extractInvalidKey(message) != null) {
          return true;
        }
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

    // Resolve the template file for "Create key" proposals
    IFile file = null;
    try {
      org.eclipse.ui.IEditorPart editor = PlatformUI.getWorkbench()
          .getActiveWorkbenchWindow().getActivePage().getActiveEditor();
      if (editor != null && editor.getEditorInput() instanceof FileEditorInput) {
        file = ((FileEditorInput) editor.getEditorInput()).getFile();
      }
    }
    catch (Exception e) {
      // No editor available — "Create key" proposals won't be offered
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
      addProposalsForMarker(marker, document, file, proposals, seenProposals);
    }

    return proposals.toArray(new ICompletionProposal[proposals.size()]);
  }

  /**
   * Extracts the invalid name and suggestions from a marker and builds
   * completion proposals for each suggestion.  Handles keypath errors
   * ("There is no key 'nme'") and element type errors ("The class for 'Str'
   * is either missing"), including miscapitalized tag shortcuts which use
   * the same message format.
   *
   * <p>For keypath errors, also offers a "Create key" proposal that opens
   * the Add Key dialog to generate the field/accessor on the Java class.
   */
  private void addProposalsForMarker(IMarker marker, IDocument document, IFile file,
      List<ICompletionProposal> proposals, Set<String> seenProposals) {
    try {
      String message = (String) marker.getAttribute(IMarker.MESSAGE);

      // Determine whether this is a keypath error or an element type error.
      // Only keypath errors get the "Create key" proposal.
      String invalidKeyName = KeypathQuickFixGenerator.extractInvalidKey(message);
      String invalidName = invalidKeyName;
      if (invalidName == null) {
        invalidName = KeypathQuickFixGenerator.extractInvalidElementType(message);
      }
      if (invalidName == null) {
        return;
      }

      // Add "Replace with" proposals if there are suggestions
      String suggestionsStr = (String) marker.getAttribute("suggestions");
      if (suggestionsStr != null && !suggestionsStr.isEmpty()) {
        // Read the document text at the marker range to find the
        // invalid name within the marked region.
        int charStart = marker.getAttribute(IMarker.CHAR_START, -1);
        int charEnd = marker.getAttribute(IMarker.CHAR_END, -1);
        if (charStart >= 0 && charEnd >= 0 && charEnd > charStart) {
          String markedText = document.get(charStart, charEnd - charStart);
          int keyOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(markedText, invalidName);
          if (keyOffset >= 0) {
            int replaceStart = charStart + keyOffset;

            // Check for closing tag position (stored by InlineWodProblem
            // for element type errors on tags with a close tag).
            int closeTagStart = marker.getAttribute("closeTagStart", -1);
            int closeTagEnd = marker.getAttribute("closeTagEnd", -1);

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
                  if (closeTagStart >= 0 && closeTagEnd > closeTagStart) {
                    // Element type error with a closing tag — replace both
                    // the opening and closing tag names.
                    String closeMarkedText = document.get(closeTagStart, closeTagEnd - closeTagStart);
                    int closeKeyOffset = ReplaceKeypathQuickFix.findKeySegmentOffset(closeMarkedText, invalidName);
                    if (closeKeyOffset >= 0) {
                      proposals.add(new ReplaceTagPairProposal(
                          trimmed, invalidName,
                          replaceStart, invalidName.length(),
                          closeTagStart + closeKeyOffset, invalidName.length()));
                    }
                    else {
                      // Closing tag name doesn't match — fall back to
                      // opening-tag-only replacement.
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
                  else {
                    // No closing tag (self-closing or keypath error) —
                    // single replacement.
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
          }
        }
      }

      // For keypath errors on the component class itself, also offer
      // "Create key" (or "Create action" for action bindings) to generate
      // code via the appropriate dialog. Nested keypaths (e.g. "session.nme")
      // use the message format "for the keypath '...'" — we don't offer
      // key creation for those since it would require modifying a different class.
      boolean isDirectKey = invalidKeyName != null
          && message != null
          && !message.contains("for the keypath");
      if (isDirectKey && file != null) {
        String bindingName = (String) marker.getAttribute("bindingName");
        String createKeyProposalKey = "createKey:" + invalidKeyName;
        if (seenProposals.add(createKeyProposalKey)) {
          proposals.add(new CreateKeyCompletionProposal(invalidKeyName, file, bindingName));
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

  /**
   * Completion proposal that replaces both the opening and closing tag names
   * in a single apply(). Used for element type quick-fixes on tags with a
   * closing tag (e.g. {@code <wo:container>...</wo:container>}).
   *
   * <p>The closing tag is replaced first to preserve the opening tag's
   * offset — replacing the opening tag first could shift the closing tag
   * position if the replacement has a different length.
   */
  private static class ReplaceTagPairProposal implements ICompletionProposal {
    private final String _replacement;
    private final String _invalidName;
    private final int _openOffset;
    private final int _openLength;
    private final int _closeOffset;
    private final int _closeLength;

    ReplaceTagPairProposal(String replacement, String invalidName,
        int openOffset, int openLength, int closeOffset, int closeLength) {
      _replacement = replacement;
      _invalidName = invalidName;
      _openOffset = openOffset;
      _openLength = openLength;
      _closeOffset = closeOffset;
      _closeLength = closeLength;
    }

    @Override
    public void apply(IDocument document) {
      try {
        // Replace closing tag first to preserve opening tag offset
        document.replace(_closeOffset, _closeLength, _replacement);
        document.replace(_openOffset, _openLength, _replacement);
      }
      catch (BadLocationException e) {
        // Offsets may be stale after concurrent edits
      }
    }

    @Override
    public Point getSelection(IDocument document) {
      // Place cursor at end of the replaced opening tag name
      return new Point(_openOffset + _replacement.length(), 0);
    }

    @Override
    public String getAdditionalProposalInfo() {
      return null;
    }

    @Override
    public String getDisplayString() {
      return "Replace '" + _invalidName + "' with '" + _replacement + "'";
    }

    @Override
    public Image getImage() {
      return null;
    }

    @Override
    public IContextInformation getContextInformation() {
      return null;
    }
  }
}

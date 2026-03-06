package org.objectstyle.wolips.wodclipse.core.quickfix;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.objectstyle.wolips.wodclipse.core.Activator;

/**
 * A quick-fix that replaces an invalid keypath segment with a suggested
 * correction. For example, replaces "nme" with "name" in the binding
 * value "$application.nme".
 *
 * <p>The fix reads the document text at the marker's character range,
 * finds the invalid key segment within it, and replaces just that
 * segment — preserving the surrounding keypath structure.
 */
public class ReplaceKeypathQuickFix implements IMarkerResolution {
  private String _invalidKey;
  private String _replacement;

  /**
   * @param invalidKey   the mistyped key to find (e.g. "nme")
   * @param replacement  the corrected key (e.g. "name")
   */
  public ReplaceKeypathQuickFix(String invalidKey, String replacement) {
    _invalidKey = invalidKey;
    _replacement = replacement;
  }

  @Override
  public String getLabel() {
    return "Replace '" + _invalidKey + "' with '" + _replacement + "'";
  }

  @Override
  public void run(IMarker marker) {
    try {
      IFile file = (IFile) marker.getResource();
      int charStart = marker.getAttribute(IMarker.CHAR_START, -1);
      int charEnd = marker.getAttribute(IMarker.CHAR_END, -1);
      if (charStart < 0 || charEnd < 0 || charEnd <= charStart) {
        return;
      }

      // Open the file in an editor so we can modify the document.
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      IEditorPart editor = IDE.openEditor(page, file);
      if (!(editor instanceof ITextEditor)) {
        // Try the adapted text editor (multi-page editors)
        ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
        if (textEditor == null) {
          return;
        }
        editor = textEditor;
      }

      IDocument document = ((ITextEditor) editor).getDocumentProvider()
          .getDocument(((ITextEditor) editor).getEditorInput());
      if (document == null) {
        return;
      }

      // Read the marked region (the entire binding value, e.g. "$application.nme"
      // or "application.nme" in WOD files).
      String markedText = document.get(charStart, charEnd - charStart);

      // Find the invalid key within the marked text. It should appear as a
      // keypath segment — either the whole text, or after a '.', or before a '.'.
      int keyOffset = findKeySegmentOffset(markedText, _invalidKey);
      if (keyOffset < 0) {
        return;
      }

      // Replace just the invalid segment.
      int replaceStart = charStart + keyOffset;
      document.replace(replaceStart, _invalidKey.length(), _replacement);

      // Delete the marker since the problem is now fixed.
      marker.delete();
    }
    catch (CoreException e) {
      Activator.getDefault().log(e);
    }
    catch (BadLocationException e) {
      Activator.getDefault().log(new CoreException(
          new org.eclipse.core.runtime.Status(
              org.eclipse.core.runtime.IStatus.ERROR,
              Activator.PLUGIN_ID,
              "Failed to apply keypath quick-fix", e)));
    }
  }

  /**
   * Finds the offset of a key segment within a binding value string.
   * Matches the key as a complete segment (delimited by '.' or start/end
   * of string), searching from the end since the invalid key is typically
   * the last segment in the keypath.
   *
   * @param bindingValue the full binding value text
   * @param key          the key segment to find
   * @return the offset within bindingValue, or -1 if not found
   */
  public static int findKeySegmentOffset(String bindingValue, String key) {
    if (bindingValue == null || key == null) {
      return -1;
    }

    // Search from the end — the invalid key is usually the last segment
    int searchFrom = bindingValue.length();
    while (searchFrom > 0) {
      int idx = bindingValue.lastIndexOf(key, searchFrom - 1);
      if (idx < 0) {
        return -1;
      }

      // Verify it's a complete segment: preceded by start-of-string, '.',
      // '$', or ':' and followed by end-of-string or '.'.
      // The ':' delimiter supports element type names in inline bindings
      // where the marker range includes the namespace prefix (e.g. "wo:WOString").
      boolean startOk = (idx == 0)
          || bindingValue.charAt(idx - 1) == '.'
          || bindingValue.charAt(idx - 1) == '$'
          || bindingValue.charAt(idx - 1) == ':';
      boolean endOk = (idx + key.length() == bindingValue.length())
          || bindingValue.charAt(idx + key.length()) == '.';

      if (startOk && endOk) {
        return idx;
      }

      searchFrom = idx;
    }

    return -1;
  }
}

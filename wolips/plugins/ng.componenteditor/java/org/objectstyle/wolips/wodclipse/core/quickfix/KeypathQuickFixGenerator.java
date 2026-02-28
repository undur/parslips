package org.objectstyle.wolips.wodclipse.core.quickfix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator;
import org.objectstyle.wolips.wodclipse.core.Activator;

/**
 * Provides quick-fix resolutions for keypath and element type validation errors.
 *
 * <p>Handles two error patterns:
 * <ul>
 *   <li>Keypath errors: "There is no key 'nme' in MyComponent"
 *   <li>Element type errors: "The class for 'Str' is either missing ..."
 *       (includes miscapitalized tag shortcuts, which use the same message format)
 * </ul>
 *
 * <p>Reads the "suggestions" marker attribute (populated during validation)
 * and offers "Replace with ..." quick-fix resolutions.
 *
 * <p>Registered via {@code plugin.xml} as a {@code markerResolutionGenerator}
 * for the {@code ng.componenteditor.problem} marker type.
 */
public class KeypathQuickFixGenerator implements IMarkerResolutionGenerator {

  /** Pattern to extract the invalid key from a keypath error message. */
  private static final Pattern INVALID_KEY_PATTERN = Pattern.compile("There is no key '([^']+)'");

  /** Pattern to extract the invalid element type from a missing-class error message. */
  private static final Pattern INVALID_ELEMENT_PATTERN = Pattern.compile("The class for '([^']+)' is either missing");

  @Override
  public IMarkerResolution[] getResolutions(IMarker marker) {
    try {
      String suggestionsStr = (String) marker.getAttribute("suggestions");
      if (suggestionsStr == null || suggestionsStr.isEmpty()) {
        return new IMarkerResolution[0];
      }

      // Extract the invalid name from the error message — could be a
      // keypath segment or an element type name (including miscapitalized
      // tag shortcuts, which use the same message format).
      String message = (String) marker.getAttribute(IMarker.MESSAGE);
      String invalidName = extractInvalidKey(message);
      if (invalidName == null) {
        invalidName = extractInvalidElementType(message);
      }
      if (invalidName == null) {
        return new IMarkerResolution[0];
      }

      String[] suggestions = suggestionsStr.split(";");
      List<IMarkerResolution> resolutions = new ArrayList<IMarkerResolution>();
      for (String suggestion : suggestions) {
        String trimmed = suggestion.trim();
        if (!trimmed.isEmpty()) {
          resolutions.add(new ReplaceKeypathQuickFix(invalidName, trimmed));
        }
      }
      return resolutions.toArray(new IMarkerResolution[resolutions.size()]);
    }
    catch (CoreException e) {
      Activator.getDefault().log(e);
      return new IMarkerResolution[0];
    }
  }

  /**
   * Extracts the invalid key name from a keypath error message of the form
   * "There is no key 'xyz' ...".
   *
   * @param message the marker error message
   * @return the invalid key, or null if the message doesn't match
   */
  public static String extractInvalidKey(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = INVALID_KEY_PATTERN.matcher(message);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  /**
   * Extracts the invalid element type name from a missing-class error message
   * of the form "The class for 'xyz' is either missing ...".
   *
   * @param message the marker error message
   * @return the invalid element type name, or null if the message doesn't match
   */
  public static String extractInvalidElementType(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = INVALID_ELEMENT_PATTERN.matcher(message);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

}

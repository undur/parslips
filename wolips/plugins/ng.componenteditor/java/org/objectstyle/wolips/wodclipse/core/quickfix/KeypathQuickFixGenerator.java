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
 * Provides quick-fix resolutions for keypath validation errors.
 *
 * <p>When validation finds "There is no key 'nme' in MyComponent", this
 * generator reads the "suggestions" marker attribute (populated by
 * {@link org.objectstyle.wolips.bindings.wod.AbstractWodBinding}) and
 * offers "Replace with 'name'" quick-fix resolutions.
 *
 * <p>Registered via {@code plugin.xml} as a {@code markerResolutionGenerator}
 * for the {@code ng.componenteditor.problem} marker type.
 */
public class KeypathQuickFixGenerator implements IMarkerResolutionGenerator {

  /** Pattern to extract the invalid key from the error message. */
  private static final Pattern INVALID_KEY_PATTERN = Pattern.compile("There is no key '([^']+)'");

  @Override
  public IMarkerResolution[] getResolutions(IMarker marker) {
    try {
      String suggestionsStr = (String) marker.getAttribute("suggestions");
      if (suggestionsStr == null || suggestionsStr.isEmpty()) {
        return new IMarkerResolution[0];
      }

      // Extract the invalid key name from the error message so we know
      // which segment of the keypath to replace.
      String message = (String) marker.getAttribute(IMarker.MESSAGE);
      String invalidKey = extractInvalidKey(message);
      if (invalidKey == null) {
        return new IMarkerResolution[0];
      }

      String[] suggestions = suggestionsStr.split(";");
      List<IMarkerResolution> resolutions = new ArrayList<IMarkerResolution>();
      for (String suggestion : suggestions) {
        String trimmed = suggestion.trim();
        if (!trimmed.isEmpty()) {
          resolutions.add(new ReplaceKeypathQuickFix(invalidKey, trimmed));
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
   * Extracts the invalid key name from an error message of the form
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
}

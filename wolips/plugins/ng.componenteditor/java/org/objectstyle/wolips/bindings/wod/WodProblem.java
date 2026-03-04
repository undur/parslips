package org.objectstyle.wolips.bindings.wod;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Position;

/**
 * A validation problem found during WOD or inline template validation.
 * Carries the error/warning message, position in the source file, and
 * optionally a list of quick-fix suggestions (e.g. "did you mean 'name'?"
 * for a mistyped key).
 */
public class WodProblem {
  private IWodModel _model;
  private String _message;
  private Position _position;
  private int _lineNumber;
  private boolean _warning;
  private IFile _forceFile;
  private List<String> _suggestions;

  public WodProblem(String message, Position position, int lineNumber, boolean warning) {
    _message = message;
    _position = position;
    _lineNumber = lineNumber;
    _warning = warning;
  }
  
  /** Currently unused — retained for a richer problem-reporting pipeline. */
  private void setModel(IWodModel model) {
    _model = model;
  }

  /** Currently unused — retained for overriding the file associated with a problem marker. */
  private void setForceFile(IFile forceFile) {
    _forceFile = forceFile;
  }
  
  public IFile getForceFile() {
    return _forceFile;
  }

  public String getMessage() {
    return _message;
  }

  /** Currently unused — companion to {@link #setModel}. */
  private IWodModel getModel() {
    return _model;
  }

  public Position getPosition() {
    return _position;
  }

  public int getLineNumber() {
    return _lineNumber;
  }

  public boolean isWarning() {
    return _warning;
  }

  /**
   * Sets quick-fix suggestions for this problem (e.g. close matches for a
   * mistyped key, sorted by edit distance).
   */
  public void setSuggestions(List<String> suggestions) {
    _suggestions = suggestions;
  }

  /**
   * Returns quick-fix suggestions for this problem, or an empty list if
   * none are available.  Suggestions are ordered by relevance (closest
   * match first).
   */
  public List<String> getSuggestions() {
    return _suggestions != null ? _suggestions : Collections.<String>emptyList();
  }

  @Override
  public String toString() {
    return "[" + getClass().getName() + ": message = " + _message + "]";
  }
}

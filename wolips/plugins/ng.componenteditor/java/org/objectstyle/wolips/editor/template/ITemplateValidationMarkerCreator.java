package org.objectstyle.wolips.editor.template;

public interface ITemplateValidationMarkerCreator {
  public void addMarker( int severity, int offset, int length, String message);
}

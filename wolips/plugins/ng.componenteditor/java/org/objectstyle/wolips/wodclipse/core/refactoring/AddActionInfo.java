package org.objectstyle.wolips.wodclipse.core.refactoring;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Holds the input values for the "Add Action" dialog: name and return type.
 *
 * <p>The default return type is determined by the project type: NG projects
 * default to {@code NGActionResults}, WO projects to {@code WOActionResults}.
 */
public class AddActionInfo {
  private IType _componentType;

  private String _name;

  private String _typeName;

  public AddActionInfo(IType componentType) {
    _componentType = componentType;
    _name = "newAction";
    _typeName = resolveDefaultActionResultsType(componentType);
  }

  /**
   * Returns "NGActionResults" for ng-objects projects, "WOActionResults" otherwise.
   * Falls back to "WOActionResults" if the project type cannot be determined.
   */
  private static String resolveDefaultActionResultsType(IType componentType) {
    try {
      ParsleyProject pp = (ParsleyProject) componentType.getJavaProject().getProject().getAdapter(ParsleyProject.class);
      if (pp != null && pp.isNGProject()) {
        return "NGActionResults";
      }
    }
    catch (Exception e) {
      // Fall through to WO default
    }
    return "WOActionResults";
  }

  public String getJavaTypeName() throws JavaModelException {
    String javaTypeName = _typeName;
    if (javaTypeName != null) {
      javaTypeName = BindingReflectionUtils.getFullClassName(_componentType.getJavaProject(), javaTypeName);
    }
    return javaTypeName;
  }

  public IType getComponentType() {
    return _componentType;
  }

  public String getName() {
    return _name;
  }

  public void setName(String name) {
    _name = name;
  }

  public String getTypeName() {
    return _typeName;
  }

  public void setTypeName(String typeName) {
    _typeName = typeName;
  }
}

package org.objectstyle.wolips.wodclipse.core.refactoring;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;

/**
 * Configuration for the "Add Key" refactoring dialog. Holds the key name,
 * type, and options for what code to generate (field, accessor, mutator).
 */
public class AddKeyInfo {
  private IType _componentType;

  private String _name;

  private String _typeName;

  private String _parameterTypeName;

  private boolean _createField;

  private boolean _createAccessorMethod;

  private boolean _prependGetToAccessorMethod;

  private boolean _createMutatorMethod;

  public AddKeyInfo(IType componentType) {
    _componentType = componentType;
    _name = "newKey";
    _typeName = "java.lang.String";
    _createField = true;
    _createAccessorMethod = true;
    _prependGetToAccessorMethod = false;
    _createMutatorMethod = true;
  }

  public String getJavaTypeName() throws JavaModelException {
    String javaTypeName = _typeName;
    if (javaTypeName != null) {
      javaTypeName = BindingReflectionUtils.getFullClassName(_componentType.getJavaProject(), javaTypeName);
    }
    return javaTypeName;
  }

  public String getJavaParameterTypeName() throws JavaModelException {
    String javaParameterTypeName = _parameterTypeName;
    if (javaParameterTypeName != null) {
      javaParameterTypeName = BindingReflectionUtils.getFullClassName(_componentType.getJavaProject(), javaParameterTypeName);
    }
    return javaParameterTypeName;
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

  public String getParameterTypeName() {
    return _parameterTypeName;
  }

  public void setParameterTypeName(String parameterTypeName) {
    _parameterTypeName = parameterTypeName;
  }

  public boolean isCreateField() {
    return _createField;
  }

  public void setCreateField(boolean createField) {
    _createField = createField;
  }

  public boolean isCreateAccessorMethod() {
    return _createAccessorMethod;
  }

  public void setCreateAccessorMethod(boolean createAccessorMethod) {
    _createAccessorMethod = createAccessorMethod;
  }

  public boolean isPrependGetToAccessorMethod() {
    return _prependGetToAccessorMethod;
  }

  public void setPrependGetToAccessorMethod(boolean prependGetToAccessorMethod) {
    _prependGetToAccessorMethod = prependGetToAccessorMethod;
  }

  public boolean isCreateMutatorMethod() {
    return _createMutatorMethod;
  }

  public void setCreateMutatorMethod(boolean createMutatorMethod) {
    _createMutatorMethod = createMutatorMethod;
  }

  public String getFieldName() {
    String[] suggestedFieldNames = NamingConventions.suggestVariableNames(NamingConventions.VK_INSTANCE_FIELD, 2, getName(), _componentType.getJavaProject(), 0, null, true);
    String fieldName = suggestedFieldNames[0];
    return fieldName;
  }
}

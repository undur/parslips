package org.objectstyle.wolips.bindings.wod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaProject;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.variables.ParsleyProject;

public class TagShortcut {
  private String _shortcut;
  private String _actual;
  private Map<String, String> _attributes;

  public TagShortcut(String shortcut, String actual) {
    this(shortcut, actual, new HashMap<String, String>());
  }

  public TagShortcut(String shortcut, String actual, String attributesStr) {
    this(shortcut, actual, new HashMap<String, String>());
    setAttributesAsString(attributesStr);
  }

  public TagShortcut(String shortcut, String actual, Map<String, String> attributes) {
    _shortcut = shortcut;
    _actual = actual;
    _attributes = attributes;
  }

  public String getShortcut() {
    return _shortcut;
  }

  public void setShortcut(String shortcut) {
    _shortcut = shortcut;
  }

  public String getActual() {
    return _actual;
  }

  /**
   * Returns the actual class name for the given project's framework.
   * For ng-objects projects, translates WO-prefixed class names to their
   * NG equivalents (e.g. "WOConditional" → "NGConditional"). Returns the
   * unmodified actual name for WO projects or when buildProperties is null.
   *
   * <p>This is a temporary bridge until tag shortcuts become per-project.
   */
  public String getActual(ParsleyProject parsleyProject) {
    if (parsleyProject != null && parsleyProject.isNGProject()) {
      return woToNGClassName(_actual);
    }
    return _actual;
  }

  /**
   * Translates a WO-prefixed class name to its NG equivalent ("WOConditional" → "NGConditional",
   * "WOCheckBox" → "NGCheckbox"). Non-WO-prefixed names pass through unchanged. The mapping —
   * including the spellings where ng-objects diverges from a plain prefix swap — lives in
   * {@link org.objectstyle.wolips.bindings.api.NGElementNames}, shared with the reverse lookup
   * that finds an NG element's binding definitions.
   *
   * <p>This whole bridge is a stopgap for ng projects that declare no
   * {@code parsley-tag-aliases.properties}: when a project does, the alias resolver
   * mirrors the runtime registry exactly and this code isn't consulted.
   */
  static String woToNGClassName(String className) {
    return org.objectstyle.wolips.bindings.api.NGElementNames.toNG(className);
  }

  /**
   * The legacy tag shortcuts that apply to a project — the list to complete, suggest and
   * catalogue from when the project declares no Parsley tag aliases.
   *
   * <p>For a WebObjects project that is the whole preference table. For an ng-objects project
   * the table is a guess (WO names bridged to NG names, see {@link #getActual(ParsleyProject)}),
   * so it is filtered to the shortcuts whose bridged class actually exists on the project's
   * classpath: {@code str} stays (NGString exists), {@code VBScript} goes (there is no
   * NGVBScript). Without this, an ng project on an ng-appserver that predates the shipped
   * {@code parsley-tag-aliases.properties} was offered the WebObjects vocabulary wholesale.
   */
  public static List<TagShortcut> applicableTo(IJavaProject javaProject, ParsleyProject parsleyProject, TypeCache typeCache) {
    final List<TagShortcut> all = ApiCache.getTagShortcuts();
    if (javaProject == null || parsleyProject == null || !parsleyProject.isNGProject()) {
      return all;
    }
    final List<TagShortcut> applicable = new ArrayList<TagShortcut>();
    for (TagShortcut shortcut : all) {
      try {
        if (BindingReflectionUtils.findElementType(javaProject, shortcut.getActual(parsleyProject), false, typeCache) != null) {
          applicable.add(shortcut);
        }
      }
      catch (Exception e) {
        // An unresolvable class is exactly a shortcut that doesn't apply.
      }
    }
    return applicable;
  }

  public void setActual(String actual) {
    _actual = actual;
  }

  public Map<String, String> getAttributes() {
    return _attributes;
  }

  /** Currently unused — retained for programmatic shortcut construction with pre-parsed attributes. */
  private void setAttributes(Map<String, String> attributes) {
    _attributes = attributes;
  }

  public void setAttributesAsString(String attributesStr) {
    _attributes.clear();
    String[] attributesSplit = attributesStr.split(",");
    for (int attributeNum = 0; attributeNum < attributesSplit.length; attributeNum++) {
      String[] kvPair = attributesSplit[attributeNum].split("=");
      if (kvPair.length == 2) {
        _attributes.put(kvPair[0].trim(), kvPair[1].trim());
      }
    }
  }

  public String getAttributesAsString() {
    StringBuffer attributesBuffer = new StringBuffer();
    for (Map.Entry<String, String> attribute : _attributes.entrySet()) {
      attributesBuffer.append(attribute.getKey());
      attributesBuffer.append("=");
      attributesBuffer.append(attribute.getValue());
      attributesBuffer.append(",");
    }
    if (attributesBuffer.length() > 0) {
      attributesBuffer.setLength(attributesBuffer.length() - 1);
    }
    return attributesBuffer.toString();
  }

  @Override
  public TagShortcut clone() {
    return new TagShortcut(getShortcut(), getActual(), new HashMap<String, String>(_attributes));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TagShortcut) {
      TagShortcut shortcut = (TagShortcut) obj;
      return getShortcut().equals(shortcut.getShortcut()) && getActual().equals(shortcut.getActual()) && getAttributes().equals(shortcut.getAttributes());
    }
    return false;
  }

  public static boolean hasChange(List<TagShortcut> tags1, List<TagShortcut> tags2) {
    if (tags1.size() != tags2.size()) {
      return true;
    }
    for (int i = 0; i < tags1.size(); i++) {
      TagShortcut tag1 = tags1.get(i);
      TagShortcut tag2 = tags2.get(i);
      if (!tag1.equals(tag2)) {
        return true;
      }
    }
    return false;
  }

  public static List<TagShortcut> fromPreferenceString(String value) {
    List<TagShortcut> list = new ArrayList<TagShortcut>();
    if (value != null) {
      String[] values = value.split("\n");
      for (int i = 0; i < values.length; i++) {
        String[] split = values[i].split("\t");
        if (split.length >= 2) {
          String shortcut = split[0];
          String actual = split[1];
          HashMap<String, String> attributes = new HashMap<String, String>();
          for (int attributeNum = 2; attributeNum < split.length; attributeNum += 2) {
            attributes.put(split[attributeNum], split[attributeNum + 1]);
          }
          list.add(new TagShortcut(shortcut, actual, attributes));
        }
      }
    }
    return list;
  }

  public static String toPreferenceString(List<TagShortcut> list) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < list.size(); i++) {
      TagShortcut tag = list.get(i);
      sb.append(tag.getShortcut());
      sb.append("\t");
      sb.append(tag.getActual());
      for (Map.Entry<String, String> entry : tag.getAttributes().entrySet()) {
        sb.append("\t");
        sb.append(entry.getKey());
        sb.append("\t");
        sb.append(entry.getValue());
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

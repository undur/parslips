package org.objectstyle.wolips.wodclipse.core.woo;

import static org.objectstyle.wolips.baseforplugins.util.CharSetUtils.ENCODING_UTF8;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.objectstyle.wolips.baseforplugins.util.CharSetUtils;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.bindings.wod.WodProblem;
import org.objectstyle.wolips.eomodeler.core.model.EOModelMap;
public class WooModel {
  public static final String IS_DIRTY = "IS_DIRTY";

  public static final String ENCODING = "encoding";

  public static final String DEFAULT_ENCODING = ENCODING_UTF8;

  public static final String DEFAULT_WO_RELEASE = "WebObjects 5.0";

  private IFile _file;

  private boolean _isDirty;

  private String _encoding;

  private String _woRelease = DEFAULT_WO_RELEASE;

  private EOModelMap _modelMap;

  private PropertyChangeSupport _changes = new PropertyChangeSupport(this);

  public WooModel(final IFile file) {
    _file = file;
    try {
      init();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public WooModel(final String contents) throws WooModelException {
    InputStream input = new ByteArrayInputStream(contents.getBytes());
    try {
      loadModelFromStream(input);
    }
    catch (Throwable e) {
      throw new WooModelException(e.getMessage(), e);
    }
  }

  public WooModel(final InputStream input) throws WooModelException {
    try {
      loadModelFromStream(input);
    }
    catch (Throwable e) {
      throw new WooModelException(e.getMessage(), e);
    }
  }

  public WooModel(IEditorInput editorInput) {
    if (editorInput instanceof IFileEditorInput) {
      _file = ((IFileEditorInput) editorInput).getFile();
    }
    try {
      init();
    }
    catch (Throwable e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void init() throws IOException {
    if (_file == null || !_file.exists()) {
      loadModelFromStream(new ByteArrayInputStream(blankContent().getBytes()));
    }
    else {
      loadModelFromFile(_file.getLocation().toFile());
    }
  }

  public String blankContent() {
    // XXX Should use components default encoding charset
    StringBuffer sb = new StringBuffer();
    sb.append("{\n");
    sb.append("    \"WebObjects Release\" = \"WebObjects 5.0\";\n");
    sb.append("     encoding = \"" + getEncoding() + "\";\n");
    sb.append("}\n");
    return sb.toString();
  }

  private void resetModel() {
    _encoding = null;
    _woRelease = DEFAULT_WO_RELEASE;
    _modelMap = null;
  }

  public String getLocation() {
    if (_file != null) {
      return _file.getFullPath().toString();
    }
    return null;
  }

  public String getEncoding() {
    if (_encoding == null) {
      if (_modelMap != null && _modelMap.containsKey("encoding")) {
        _encoding = _modelMap.getString("encoding", true);
      }
    }
    if (_encoding == null) {
      if (_file != null && _file.exists()) {
        try {
          _encoding = _file.getParent().getDefaultCharset();
          return _encoding;
        } catch (CoreException e) {
          WodclipsePlugin.getDefault().log(e);
        }
      }
      _encoding = DEFAULT_ENCODING;
    }
    return _encoding;
  }

  public void setEncoding(String encoding) {
    String oldEncoding = _encoding;
    _encoding = encoding;
    _changes.firePropertyChange(ENCODING, oldEncoding, _encoding);
  }

  private void loadModelFromFile(final File file) throws IOException {
    try (InputStream input = new java.io.FileInputStream(file)) {
      loadModelFromStream(input);
    }
  }

  /**
   * Parses a simple NeXT-style plist dictionary from the given stream.
   * The .woo format is a flat dictionary of string key-value pairs:
   * {@code { "key" = "value"; "key2" = "value2"; }}
   */
  public void loadModelFromStream(final InputStream input) throws IOException {
    String text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    _modelMap = new EOModelMap(parseSimplePlist(text));
  }

  public void parseModel() {
    if (_modelMap == null)
      return;

    if (_modelMap.containsKey("encoding")) {
      _encoding = _modelMap.getString("encoding", true);
    }
    if (_modelMap.containsKey("WebObjects Release")) {
      _woRelease = _modelMap.getString("WebObjects Release", true);
    }

    _isDirty = false;
  }

  public void doSave() throws IOException {
    if (_file == null) {
      throw new IOException("You can not save changes to a WooModel that is not backed by a file.");
    }

    File file = _file.getLocation().toFile();
    FileOutputStream writer = new FileOutputStream(file);
    try {
      doSave(writer);
      _isDirty = false;
      _file.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
    }
    catch (CoreException e) {
      WodclipsePlugin.getDefault().log(e);
    }
    finally {
      writer.close();
    }
  }

  public void doSave(final OutputStream writer) throws IOException {
    EOModelMap modelMap = toModelMap();
    writer.write(serializeSimplePlist(modelMap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public void doRevertToSaved() throws IOException {
    resetModel();
    loadModelFromFile(_file.getLocation().toFile());
    parseModel();
  }

  public EOModelMap toModelMap() {
    EOModelMap modelMap = _modelMap.cloneModelMap();
    modelMap.setString("WebObjects Release", _woRelease, true);
    modelMap.setString("encoding", _encoding, true);
    return modelMap;
  }

  public boolean isDirty() {
    return _isDirty;
  }

  public void markAsDirty() {
    boolean oldIsDirty = _isDirty;
    _isDirty = true;
    _changes.firePropertyChange(IS_DIRTY, oldIsDirty, _isDirty);
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    _changes.addPropertyChangeListener(listener);
  }

  public void addPropertyChangeListener(final String name, final PropertyChangeListener listener) {
    _changes.addPropertyChangeListener(name, listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    _changes.removePropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final String name, final PropertyChangeListener listener) {
    _changes.removePropertyChangeListener(name, listener);
  }

  @Override
  public String toString() {
    OutputStream modelStream = new ByteArrayOutputStream();
    try {
      this.doSave(modelStream);
    }
    catch (Exception e) {
      return null;
    }
    return modelStream.toString();
  }

  public List<WodProblem> getProblems(IJavaProject javaProject, IType type, TypeCache typeCache) {
    final List<WodProblem> problems = new ArrayList<WodProblem>();

    try {
      this.parseModel();
    }
    catch (Throwable e) {
      WodclipsePlugin.getDefault().log(e);
      problems.add(new WodProblem(e.getMessage(), null, 0, true));
      return problems;
    }
    if (_file == null) {
    	return problems;
    }
    boolean validateWooEncodings = Activator.getDefault().getPluginPreferences().getBoolean(PreferenceConstants.VALIDATE_WOO_ENCODINGS_KEY);
    if (validateWooEncodings) {
	    try {
	      String componentCharset = _file.getParent().getDefaultCharset();
	      String encoding = CharSetUtils.encodingNameFromObjectiveC(this.getEncoding());
	      if (!(encoding.equals(componentCharset))) {
	        problems.add(new WodProblem("WOO Encoding type " + encoding + " doesn't match component " + componentCharset, null, 0, true));
	      }

	      if (_file.getParent().exists()) {
	        for (IResource element : _file.getParent().members()) {
	          if (element.getType() == IResource.FILE) {
	            IFile file = (IFile) element;
	            String fileExtension = file.getFileExtension();
	            if (fileExtension != null && file.getFileExtension().matches("(xml|html|xhtml|wod)") && !file.getCharset().equals(encoding)) {
	              problems.add(new WodProblem("WOO Encoding type " + encoding + " doesn't match " + file.getName() + " of " + file.getCharset(), null, 0, true));
	            }
	          }
	        }
	      }

	    }
	    catch (CoreException e1) {
	      WodclipsePlugin.getDefault().log(e1);
	    }
    }

    return problems;
  }

  public IProject getProject() {
    if (_file != null) {
      return _file.getProject();
    }
    return null;
  }

  public String getName() {
    return _file.getName();
  }

  public static void updateEncoding(IFile file, String charset) {
    WooModel model = new WooModel(file);
    String encoding = CharSetUtils.encodingNameFromObjectiveC(model.getEncoding());
    if (!encoding.equals(charset)) {
      try {
        model._modelMap.setString("encoding", charset, true);
        File _file = file.getLocation().toFile();
        if (!_file.exists()) {
        	_file.createNewFile();
        }
        FileOutputStream writer = new FileOutputStream(_file);
        try {
          writer.write(serializeSimplePlist(model._modelMap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        finally {
          writer.close();
        }
      }
      catch (Throwable e) {
      	WodclipsePlugin.getDefault().log(e);
      }
    }
  }

  public IFile getFile() {
	return _file;
  }

  public void setFile(IFile file) {
	this._file = file;
  }

  // ---- Minimal NeXT-style plist parser/serializer for .woo files ----

  /**
   * Parses a simple NeXT-style plist dictionary with string key-value pairs.
   * Expected format: {@code { "key" = "value"; "key2" = "value2"; }}
   *
   * <p>This is a minimal parser sufficient for .woo files, which only contain
   * a flat dictionary of string values. It does not handle nested dictionaries,
   * arrays, data, or other plist types.
   */
  @SuppressWarnings("rawtypes")
  private static java.util.Map parseSimplePlist(String text) throws IOException {
    java.util.Map<String, String> map = new org.objectstyle.wolips.eomodeler.core.model.PropertyListMap<>();
    int i = 0;
    int len = text.length();

    // Skip to opening brace
    while (i < len && text.charAt(i) != '{') {
      i++;
    }
    if (i >= len) {
      throw new IOException("Invalid plist: no opening brace found");
    }
    i++; // skip '{'

    while (i < len) {
      // Skip whitespace
      while (i < len && Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      if (i >= len || text.charAt(i) == '}') {
        break;
      }

      // Parse key
      String key;
      if (text.charAt(i) == '"') {
        int[] endRef = { 0 };
        key = parseQuotedString(text, i, endRef);
        i = endRef[0];
      }
      else {
        int start = i;
        while (i < len && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '=') {
          i++;
        }
        key = text.substring(start, i);
      }

      // Skip whitespace and '='
      while (i < len && Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      if (i < len && text.charAt(i) == '=') {
        i++;
      }

      // Skip whitespace
      while (i < len && Character.isWhitespace(text.charAt(i))) {
        i++;
      }

      // Parse value
      String value;
      if (i < len && text.charAt(i) == '"') {
        int[] endRef = { 0 };
        value = parseQuotedString(text, i, endRef);
        i = endRef[0];
      }
      else {
        int start = i;
        while (i < len && text.charAt(i) != ';' && !Character.isWhitespace(text.charAt(i))) {
          i++;
        }
        value = text.substring(start, i);
      }

      // Skip whitespace and ';'
      while (i < len && Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      if (i < len && text.charAt(i) == ';') {
        i++;
      }

      map.put(key, value);
    }

    return map;
  }

  /**
   * Parses a double-quoted string starting at position {@code start} in the text,
   * handling backslash escapes. Returns the unescaped string content and sets
   * {@code endRef[0]} to the position after the closing quote.
   */
  private static String parseQuotedString(String text, int start, int[] endRef) {
    StringBuilder sb = new StringBuilder();
    int i = start + 1; // skip opening quote
    int len = text.length();

    while (i < len && text.charAt(i) != '"') {
      if (text.charAt(i) == '\\' && i + 1 < len) {
        i++;
        sb.append(text.charAt(i));
      }
      else {
        sb.append(text.charAt(i));
      }
      i++;
    }
    if (i < len) {
      i++; // skip closing quote
    }
    endRef[0] = i;
    return sb.toString();
  }

  /**
   * Serializes a map as a NeXT-style plist dictionary.
   * Produces output like: {@code { "key" = "value"; "key2" = "value2"; }}
   */
  @SuppressWarnings("rawtypes")
  private static String serializeSimplePlist(java.util.Map map) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    for (Object entryObj : map.entrySet()) {
      java.util.Map.Entry entry = (java.util.Map.Entry) entryObj;
      sb.append("    ");
      sb.append(quoteIfNeeded(String.valueOf(entry.getKey())));
      sb.append(" = ");
      sb.append(quoteIfNeeded(String.valueOf(entry.getValue())));
      sb.append(";\n");
    }
    sb.append("}\n");
    return sb.toString();
  }

  /**
   * Wraps a string in double quotes, escaping any internal quotes or backslashes.
   */
  private static String quoteIfNeeded(String s) {
    StringBuilder sb = new StringBuilder();
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\') {
        sb.append('\\');
      }
      sb.append(c);
    }
    sb.append('"');
    return sb.toString();
  }
}

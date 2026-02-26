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
import java.net.URL;
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
import org.objectstyle.woenvironment.plist.PropertyListParserException;
import org.objectstyle.woenvironment.plist.WOLPropertyListSerialization;
import org.objectstyle.wolips.baseforplugins.util.CharSetUtils;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.bindings.wod.WodProblem;
import org.objectstyle.wolips.eomodeler.core.model.EOModelMap;
import org.objectstyle.wolips.eomodeler.core.model.EOModelParserDataStructureFactory;
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

  public WooModel(final URL url) {
    // TODO: Fix me
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

  private void init() throws IOException, PropertyListParserException {
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
          e.printStackTrace();
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

  private void loadModelFromFile(final File file) throws IOException, PropertyListParserException {
    _modelMap = new EOModelMap((java.util.Map<?, ?>) WOLPropertyListSerialization.propertyListFromFile(file, new EOModelParserDataStructureFactory()));
  }

  public void loadModelFromStream(final InputStream input) throws IOException, PropertyListParserException {
    _modelMap = new EOModelMap((java.util.Map<?, ?>) WOLPropertyListSerialization.propertyListFromStream(input, new EOModelParserDataStructureFactory()));
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
      e.printStackTrace();
    }
    finally {
      writer.close();
    }
  }

  public void doSave(final OutputStream writer) throws IOException {
    EOModelMap modelMap = toModelMap();
    try {
      WOLPropertyListSerialization.propertyListToStream(writer, modelMap);
    }
    catch (PropertyListParserException e) {
      e.printStackTrace();
    }
  }

  public void doRevertToSaved() throws IOException, PropertyListParserException {
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
      e.printStackTrace();
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
	      e1.printStackTrace();
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
        WOLPropertyListSerialization.propertyListToStream(writer, model._modelMap);
      }
      catch (PropertyListParserException e) {
        e.printStackTrace();
      }
      catch (Throwable e) {
      	e.printStackTrace();
      }
    }
  }

  public IFile getFile() {
	return _file;
  }

  public void setFile(IFile file) {
	this._file = file;
  }
}

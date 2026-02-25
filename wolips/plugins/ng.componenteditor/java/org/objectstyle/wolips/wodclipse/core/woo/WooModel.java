package org.objectstyle.wolips.wodclipse.core.woo;

import static org.objectstyle.wolips.baseforplugins.util.CharSetUtils.ENCODING_UTF8;

import java.beans.PropertyChangeEvent;
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
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.swt.widgets.Shell;
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
// FIXME: eomodeler removed — display group and EO model support disabled
// import org.objectstyle.wolips.eomodeler.core.model.EOModelGroup;
// import org.objectstyle.wolips.eomodeler.core.model.EOModelVerificationFailure;
// import org.objectstyle.wolips.eomodeler.core.model.PropertyListMap;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.util.EOModelGroupCache;

public class WooModel {
  public static final String IS_DIRTY = "IS_DIRTY";

  public static final String DISPLAY_GROUP_NAME = "DISPLAY_GROUP_NAME";

  public static final String ENCODING = "encoding";

  public static final String DEFAULT_ENCODING = ENCODING_UTF8;

  public static final String DEFAULT_WO_RELEASE = "WebObjects 5.0";

  private IFile _file;

  private boolean _isDirty;

  // FIXME: eomodeler removed — was EOModelGroup _modelGroup for EO model lookups
  // private EOModelGroup _modelGroup;

  private String _encoding;

  private String _woRelease = DEFAULT_WO_RELEASE;

  private EOModelMap _modelMap;

  // FIXME: eomodeler removed — was PropertyListMap<Object, Object> for variable storage
  private Map<Object, Object> _variables;

  private List<DisplayGroup> _displayGroups;

  private List<DisplayGroup> _removedDisplayGroups;

  private PropertyChangeSupport _changes = new PropertyChangeSupport(this);

  private PropertyChangeListener _displayGroupListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      if (DisplayGroup.NAME.equals(evt.getPropertyName())) {
        PropertyChangeEvent newEvent = new PropertyChangeEvent(evt.getSource(), DISPLAY_GROUP_NAME, evt.getOldValue(), evt.getNewValue());
        _changes.firePropertyChange(newEvent);
      }
    }

  };

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
    _variables = null;
    _modelMap = null;
    _displayGroups = null;
    _removedDisplayGroups = null;
  }

  public String getLocation() {
    String location;
    if (_file != null) {
      location = _file.getFullPath().toString();
    }
    else {
      location = null;
    }
    return location;
  }

  public DisplayGroup[] getDisplayGroups() {
    if (_displayGroups != null) {
      return _displayGroups.toArray(new DisplayGroup[] {});
    }
    return new DisplayGroup[0];
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

  // FIXME: eomodeler removed — getModelGroup() returned EOModelGroup from WodParserCache
  // public EOModelGroup getModelGroup() {
  //   if (_modelGroup == null) {
  //     _modelGroup = WodParserCache.getModelGroupCache().getModelGroup(_file.getProject());
  //   }
  //   return _modelGroup;
  // }

  public void setEncoding(String encoding) {
    String oldEncoding = _encoding;
    _encoding = encoding;
    _changes.firePropertyChange(ENCODING, oldEncoding, _encoding);
  }

  private void loadModelFromFile(final File file) throws IOException, PropertyListParserException {
    _modelMap = new EOModelMap((Map<?, ?>) WOLPropertyListSerialization.propertyListFromFile(file, new EOModelParserDataStructureFactory()));
  }

  public void loadModelFromStream(final InputStream input) throws IOException, PropertyListParserException {
    _modelMap = new EOModelMap((Map<?, ?>) WOLPropertyListSerialization.propertyListFromStream(input, new EOModelParserDataStructureFactory()));
  }

  @SuppressWarnings("unchecked")
  public void parseModel() {
    if (_modelMap == null)
      return;

    if (_modelMap.containsKey("encoding")) {
      _encoding = _modelMap.getString("encoding", true);
    }
    if (_modelMap.containsKey("WebObjects Release")) {
      _woRelease = _modelMap.getString("WebObjects Release", true);
    }

    // FIXME: eomodeler removed — display group parsing disabled.
    // Previously parsed "variables" map to find WODisplayGroup/ERXDisplayGroup entries
    // and created DisplayGroup instances for each. Now we just store variables as-is.
    _displayGroups = new ArrayList<DisplayGroup>();

    Map<?, ?> variables = _modelMap.getMap("variables");
    if (variables != null) {
      // FIXME: eomodeler removed — display group detection and loading was here.
      // All variable entries were checked for WODisplayGroup/ERXDisplayGroup class names.
      // Now we just ignore the variables section for display groups.
    }

    _isDirty = false;
  }

  /**
   * FIXME: eomodeler removed — refactor() previously applied display group changes
   * (add/rename/remove fields) to the component's Java class. Disabled.
   */
  public void refactor(Shell shell, IRunnableContext context) {
    // no-op — eomodeler / display group refactoring removed
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
      _removedDisplayGroups = null;
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

    // FIXME: eomodeler removed — display group serialization was here.
    // Previously iterated over _displayGroups, called toMap() on each,
    // and merged them into the variables map. Now we just preserve existing variables.
    EOModelMap variableMap = new EOModelMap();
    if (_variables != null) {
      variableMap.putAll(_variables);
    }
    modelMap.setMap("variables", variableMap, true);
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

  // FIXME: eomodeler removed — createDisplayGroup() previously created DisplayGroup with eomodeler data sources
  public void createDisplayGroup(final String name) {
    // no-op — display group support removed
  }

  // FIXME: eomodeler removed — removeDisplayGroup() previously tracked removed display groups for refactoring
  public void removeDisplayGroup(final DisplayGroup selection) {
    // no-op — display group support removed
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

  public List<WodProblem> getProblems(IJavaProject javaProject, IType type, TypeCache typeCache, EOModelGroupCache modelCache) {
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

    // FIXME: eomodeler removed — display group validation was here.
    // Previously validated that WODisplayGroup variables were declared in the component class
    // and that editing contexts were valid. Disabled.

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
  	System.out.println("WooModel.updateEncoding: Setting encoding of " + file + " from " + encoding + " to " + charset);
    if (!encoding.equals(charset)) {
      try {
        model._modelMap.setString("encoding", charset, true);
        File _file = file.getLocation().toFile();
        if (!_file.exists()) {
        	System.out.println("WooModel.updateEncoding: creating file " + _file);
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

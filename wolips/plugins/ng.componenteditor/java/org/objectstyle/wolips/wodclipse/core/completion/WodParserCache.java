package org.objectstyle.wolips.wodclipse.core.completion;

import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValidationRule;
import org.objectstyle.wolips.bindings.wod.ITypeOwner;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.core.resources.types.LimitedLRUCache;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.builder.WodBuilder;
public class WodParserCache implements ITypeOwner {
  private static TypeCache _typeCache;
  private static LimitedLRUCache<String, WodParserCache> _parsers;

  private WodCacheEntry _wodEntry;
  private HtmlCacheEntry _htmlEntry;
  private WooCacheEntry _wooEntry;

  private TextViewerUndoManager _undoManager;
  private LocalizedComponentsLocateResult _componentsLocateResults;
  private IProject _project;
  private IJavaProject _javaProject;
  private IType _componentType;
  private IContainer _woFolder;
  private IFile _standaloneFile; // non-null for standalone HTML files (not inside .wo folder)
  private IFile _apiFile;

  private boolean _validated;
  private boolean _validating;

  private Object _validationLock = new Object();

  static {
    WodParserCache._typeCache = new TypeCache();
  }
  
  /**
   * Returns a WodParserCache entry for the given component name.
   * 
   * @param project the project to scope the search to
   * @param componentName the name of the component to lookup
   * @return the WodParserCache for the component
   * @throws CoreException if a core error occurs
   * @throws LocateException if a locate error occurs
   */
  public static WodParserCache parser(IProject project, String componentName) throws CoreException, LocateException {
  	LocalizedComponentsLocateResult locateResult = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
  	IFile resource = locateResult.getFirstWodFile();
  	if (resource == null) {
  		resource = locateResult.getFirstHtmlFile();
  	}
    WodParserCache parserCache = WodParserCache.parser(resource, true);
    if (parserCache._componentsLocateResults == null) {
    	parserCache._componentsLocateResults = locateResult;
    }
    return parserCache;
  }
  
  public static WodParserCache parser(IResource resource) throws CoreException, LocateException {
    return WodParserCache.parser(resource, true);
  }

  public static synchronized WodParserCache parser(IResource resource, boolean createIfMissing) throws CoreException, LocateException {
    if (_parsers == null) {
      WodParserCache._parsers = new LimitedLRUCache<String, WodParserCache>(10);
      ResourcesPlugin.getWorkspace().addResourceChangeListener(new WodParserCacheInvalidator());
    }
    String key = WodParserCache.getCacheKey(resource);
    WodParserCache cache = WodParserCache._parsers.get(key);
    if (cache == null && createIfMissing) {
      IFile standaloneFile = isStandaloneFile(resource) ? (IFile) resource : null;
      cache = new WodParserCache(getWoFolder(resource), standaloneFile);
      WodParserCache._parsers.put(key, cache);
    }
    return cache;
  }

  public static void invalidateResource(IResource resource) {
    try {
      Object cacheEntry = parser(resource, false);
      if (cacheEntry != null) {
        String key = getCacheKey(resource);
        _parsers.remove(key);
      }
    }
    catch (CoreException e) {
      WodclipsePlugin.getDefault().log(e);
    }
    catch (LocateException e) {
      WodclipsePlugin.getDefault().log(e);
    }
  }

  private static String getCacheKey(IResource resource) {
    // For standalone HTML files, use the file itself as the key (not the parent folder,
    // which may contain multiple unrelated standalone templates).
    if (isStandaloneFile(resource)) {
      return resource.getLocation().toPortableString();
    }
    String cacheKey;
    IContainer woFolder = getWoFolder(resource);
    if (woFolder == null) {
      cacheKey = resource.getLocation().toPortableString();
    }
    else {
      cacheKey = woFolder.getLocation().toPortableString();
    }
    return cacheKey;
  }

  /**
   * Returns true if the given resource is a standalone component file (HTML or WOD)
   * that is NOT inside a .wo folder.
   */
  private static boolean isStandaloneFile(IResource resource) {
    if (resource instanceof IFile) {
      String ext = resource.getFileExtension();
      if ("html".equals(ext) || "wod".equals(ext)) {
        IContainer parent = resource.getParent();
        return parent == null || !"wo".equals(parent.getFileExtension());
      }
    }
    return false;
  }

  private static IContainer getWoFolder(IResource resource) {
    IContainer woFolder;
    if (resource instanceof IFolder) {
      woFolder = (IContainer) resource;
    }
    else {
      woFolder = resource.getParent();
    }
    return woFolder;
  }

  protected WodParserCache(IContainer woFolder, IFile standaloneFile) throws CoreException, LocateException {
    _woFolder = woFolder;
    _standaloneFile = standaloneFile;
    init();
  }

  public WodParserCache() throws CoreException, LocateException {
    init();
  }

  private void init() throws CoreException, LocateException {
    _undoManager = new TextViewerUndoManager(25);
    _wodEntry = new WodCacheEntry(this);
    _htmlEntry = new HtmlCacheEntry(this);
    _wooEntry = new WooCacheEntry(this);
    clearCache();
  }

  public IContainer getWoFolder() {
    return _woFolder;
  }

  public IType getComponentType() throws CoreException, LocateException {
    checkLocateResults();
    if (_componentType == null) {
    	_componentType = _componentsLocateResults.getDotJavaType();
    }
    if (_componentType != null) {
    	return _componentType;
    }
    // No Java class for this component — return WOComponent/NGComponent as
    // a fallback so validation still runs, but do NOT cache it in
    // _componentType (the real type may resolve on a later call).
    if (_javaProject != null) {
    	String fallbackClass = ParsleyProject.getComponentClass(_javaProject.getProject());
    	return _javaProject.findType(fallbackClass);
    }
    return null;
  }

  public IProject getProject() {
    return _project;
  }

  public IJavaProject getJavaProject() {
    return _javaProject;
  }

  private void checkLocateResults() throws CoreException, LocateException {
    if (_componentsLocateResults != null) {
      if (!_componentsLocateResults.isValid()) {
        clearLocateResultsCache();
      }
    }
  }

  private void clearLocateResultsCache() throws CoreException, LocateException {
    if (_woFolder != null && _woFolder.exists() && LocatePlugin.getDefault() != null) {
      // For standalone HTML files, use the file itself for locate operations
      // (the file's name minus extension is the component name).
      // For .wo folders, the folder name minus extension is the component name.
      IResource locateResource = (_standaloneFile != null && _standaloneFile.exists()) ? _standaloneFile : _woFolder;
      _componentsLocateResults = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(locateResource);
      _project = _woFolder.getProject();
      _javaProject = JavaCore.create(_project);
      IFile locatedHtml = _componentsLocateResults.getFirstHtmlFile();
      _htmlEntry.setFile(locatedHtml != null ? locatedHtml : _standaloneFile);
      _wodEntry.setFile(_componentsLocateResults.getFirstWodFile());
      _apiFile = _componentsLocateResults.getDotApi(true);
      _componentType = null;
      _wooEntry.setFile(_componentsLocateResults.getFirstWooFile());
    }
    else {
      _woFolder = null;
    }
  }

  public void clearCache() throws CoreException, LocateException {
    clearLocateResultsCache();
    clearParserCache();
    clearValidationCache();
  }

  public void clearParserCache() throws CoreException, LocateException {
    _htmlEntry.clear();
    _wodEntry.clear();
    _wooEntry.clear();
  }

  private void clearValidationCache() {
    _setValidated(false);
  }

  public LocalizedComponentsLocateResult getComponentsLocateResults() {
    return _componentsLocateResults;
  }

  public ApiCache getApiCache() {
    return WodParserCache.getTypeCache().getApiCache(_javaProject);
  }

  public static TypeCache getTypeCache() {
    return WodParserCache._typeCache;
  }

  public IType getType() throws CoreException, LocateException {
    return getComponentType();
  }

  public TypeCache getCache() {
    return WodParserCache.getTypeCache();
  }

  /**
   * Returns the API snapshot for a component identified by element name.
   * Resolves the name to a type, then looks up the parsed {@code .api} file.
   *
   * @param elementName the element type name (e.g. "WOString", "MyComponent")
   * @return the API snapshot, or null if not found
   */
  public ApiSnapshot getApiSnapshot(String elementName) throws ApiModelException, JavaModelException {
    IType elementType = getElementType(elementName);
    return getApiSnapshot(elementType);
  }

  public IType getElementType(String elementName) throws JavaModelException {
    return BindingReflectionUtils.findElementType(_javaProject, elementName, false, WodParserCache.getTypeCache());
  }

  /**
   * Returns the API snapshot for a given type by looking up its {@code .api} file.
   *
   * @param type the JDT type to look up
   * @return the API snapshot, or null if not found
   */
  public ApiSnapshot getApiSnapshot(IType type) throws ApiModelException {
    return ApiUtils.findApiSnapshot(type, getApiCache());
  }

  public synchronized void parse() throws Exception {
	  if (_htmlEntry.shouldParse()) {
		  _htmlEntry.parse();
	  }

	  if (_wodEntry.shouldParse()) {
		  _wodEntry.parse();
	  }

	  if (_wooEntry.shouldParse()) {
		  _wooEntry.parse();
	  }
  }

  public void scheduleValidate(final boolean force, final boolean threaded) {
  	try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) {
					try {
						parse();
						validate(force, threaded);
					}
					catch (Exception ex) {
						Activator.getDefault().log(ex);
					}
				}
			}, null);
		}
  	catch (CoreException e) {
			Activator.getDefault().log(e);
		}
  }

  public void validate(boolean force, boolean threaded) throws CoreException {
    boolean validate = false;
    synchronized (_validationLock) {
      if (force || !_validating) {
        _validating = true;
        validate = true;
      }
    }

    if (validate) {
      if (force || !_validated) {
        boolean isWoFolder = _woFolder != null && "wo".equals(_woFolder.getFileExtension());
        if (threaded && isWoFolder) {
          WodBuilder.validateComponent(_woFolder, true, null);
        }
        else {
          // For standalone HTML templates _woFolder is the parent directory,
          // not a .wo bundle, so WodBuilder.validateComponent can't re-locate
          // the component.  Validate directly on this cache instance instead.
          try {
            WodParserCache.this._validate();
          }
          catch (Exception e) {
            WodclipsePlugin.getDefault().log(e);
          }
        }
      }
    }
  }

  public HtmlCacheEntry getHtmlEntry() {
    return _htmlEntry;
  }

  public WodCacheEntry getWodEntry() {
    return _wodEntry;
  }

  public void _setValidated(boolean validated) {
    // ignore validated = false if we're validating right now ...
    if (validated || !_validating) {
      _validated = validated;
    }
  }

  private void _validate() throws Exception {
    synchronized (_validationLock) {
      _validated = true;
      _validating = true;
    }

    try {
      _htmlEntry.deleteProblems();
      _wodEntry.deleteProblems();
      _wooEntry.deleteProblems();

      boolean validateEnabled = Activator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.VALIDATE_TEMPLATES_KEY);

      if (validateEnabled) {
        _htmlEntry.validate();
        _wodEntry.validate();
        _wooEntry.validate();
      }
    }
    finally {
      synchronized (_validationLock) {
        _validated = true;
        _validating = false;
      }
    }
  }

  public IFile getApiFile() throws CoreException, LocateException {
    checkLocateResults();
    return _apiFile;
  }

  public TagShortcut getTagShortcutNamed(String shortcut) {
    return ApiCache.getTagShortcutNamed(shortcut);
  }

  public List<TagShortcut> getTagShortcuts() {
    return ApiCache.getTagShortcuts();
  }

  public List<BindingValidationRule> getBindingValidationRules() {
    return ApiCache.getBindingValidationRules();
  }
}

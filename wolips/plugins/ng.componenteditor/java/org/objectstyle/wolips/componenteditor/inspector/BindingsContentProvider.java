package org.objectstyle.wolips.componenteditor.inspector;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.bindings.wod.VisibleBinding;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

public class BindingsContentProvider implements IStructuredContentProvider {
	private IJavaProject _javaProject;

	private TypeCache _cache;

	private ApiSnapshot _api;

	public void setContext(IJavaProject javaProject, TypeCache cache) {
		_javaProject = javaProject;
		_cache = cache;
	}

	public ApiSnapshot getApi() {
		return _api;
	}

	public Object[] getElements(Object inputElement) {
		Object[] visibleBindings = null;
		_api = null;
		if (inputElement instanceof IWodElement) {
			IWodElement wodElement = (IWodElement) inputElement;
			if (wodElement == null) {
				visibleBindings = new VisibleBinding[0];
			} else {
				if (_cache != null && _api == null) {
					try {
						_api = wodElement.getApi(_javaProject, _cache);
					} catch (Exception e) {
						_api = null;
						ComponenteditorPlugin.getDefault().log("Failed to load API for WO.", e);
					}
				}
				visibleBindings = wodElement.getVisibleBindings(_api);
			}
		} else {
			visibleBindings = new VisibleBinding[0];
		}
		return visibleBindings;
	}

	public void dispose() {
		// DO NOTHING
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		// DO NOTHING
	}
}

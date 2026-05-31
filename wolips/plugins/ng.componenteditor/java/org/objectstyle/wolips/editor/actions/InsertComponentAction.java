package org.objectstyle.wolips.editor.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.IApiBinding;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * <P>
 * This is the superclass of the actions that insert new components into the
 * component. Most of the guts of it are in the superclass here with the
 * configuration of the actions in the subclasses.
 * </P>
 * 
 * @author apl
 * 
 */

public abstract class InsertComponentAction extends InsertHtmlAndWodAction {
	private ApiSnapshot _api;

	/**
	 * Returns the API snapshot for the component being inserted.
	 * Cached after first lookup.
	 */
	public ApiSnapshot getApi() {
		String componentName = getComponentName();
		if (_api == null) {
			_api = getApi(componentName);
		}
		return _api;
	}

	/**
	 * Looks up the API snapshot for the named component from the parser cache.
	 *
	 * @param componentName the component name to look up
	 * @return the API snapshot, or null if not found
	 */
	protected ApiSnapshot getApi(String componentName) {
		ApiSnapshot api = null;
		if (componentName != null) {
			TemplateEditor te = getTemplateEditor();
			if (null != te) {
				IFileEditorInput input = (IFileEditorInput) te.getEditorInput();
				IFile file = input.getFile();

				try {
					WodParserCache cache = WodParserCache.parser(file);
					api = cache.getApiSnapshot(componentName);
				} catch (LocateException le) {
					ComponenteditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, ComponenteditorPlugin.PLUGIN_ID, IStatus.OK, "Unable to look up API for component.", le));
				} catch (CoreException ce) {
					ComponenteditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, ComponenteditorPlugin.PLUGIN_ID, IStatus.OK, "Unable to look up API for component.", ce));
				} catch (ApiModelException ame) {
					ComponenteditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, ComponenteditorPlugin.PLUGIN_ID, IStatus.OK, "Unable to look up API for component.", ame));
				}
			}
		}
		return api;
	}

	protected IJavaProject getJavaProject() {
		IJavaProject javaProject = null;
		TemplateEditor te = getTemplateEditor();
		if (te != null) {
			IFileEditorInput input = (IFileEditorInput) te.getEditorInput();
			IFile file = input.getFile();
			if (file != null) {
				javaProject = JavaCore.create(file.getProject());
			}
		}
		return javaProject;
	}

	protected List<IApiBinding> getRequiredBindings(String componentName) {
		List<IApiBinding> requiredBindings = null;
		ApiSnapshot api = getApi(componentName);
		if (api != null) {
			requiredBindings = api.getRequiredBindings();
		}
		return requiredBindings;
	}

	/**
	 * <P>
	 * This is a standard suffix for the component names. For example, you might
	 * like your string components to generally have "String" at the end.
	 * </P>
	 */

	public abstract String getComponentInstanceNameSuffix();

	/**
	 * <P>
	 * This is the name of the component that will be inserted. Some examples of
	 * standard component named might be <TT>WOString</TT>, <TT>WOForm</TT>
	 * etc...
	 * </P>
	 */
	public abstract String getComponentName();

	protected InsertComponentSpecification getComponentSpecification() {
		InsertComponentSpecification ics = _componentSpecification;

		int results;
		if (ics == null) {
			ics = new InsertComponentSpecification(getComponentName());
			ics.setComponentInstanceNameSuffix(getComponentInstanceNameSuffix());

			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			InsertComponentDialogue dialog = new InsertComponentDialogue(window.getShell(), getJavaProject(), ics);
			results = dialog.open();
		} else {
			results = Window.OK;
		}

		if (results == Window.OK) {
			ics.setRequiredBindings(getRequiredBindings(ics.getComponentName()));

			if (!ics.isInline()) {
				ics.setTagName("webobject");
				Map<String, String> attributes = new HashMap<String, String>();
				attributes.put("name", ics.getComponentInstanceName());
				ics.setAttributes(attributes);
			}

			ApiSnapshot api = getApi(ics.getComponentName());
			if (api != null) {
				ics.setComponentContent(api.isComponentContent());
			}
		} else {
			ics = null;
		}

		return ics;
	}
}

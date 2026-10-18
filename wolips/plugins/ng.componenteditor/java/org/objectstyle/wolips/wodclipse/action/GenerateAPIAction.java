/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2006 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. The end-user documentation included with the redistribution, if any, must
 * include the following acknowlegement: "This product includes software
 * developed by the ObjectStyle Group (http://objectstyle.org/)." Alternately,
 * this acknowlegement may appear in the software itself, if and wherever such
 * third-party acknowlegements normally appear.
 * 
 * 4. The names "ObjectStyle Group" and "Cayenne" must not be used to endorse or
 * promote products derived from this software without prior written permission.
 * For written permission, please contact andrus@objectstyle.org.
 * 
 * 5. Products derived from this software may not be called "ObjectStyle" nor
 * may "ObjectStyle" appear in their names without prior written permission of
 * the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals on
 * behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/>.
 *  
 */
package org.objectstyle.wolips.wodclipse.action;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.objectstyle.wolips.bindings.api.ApiCompanion;
import org.objectstyle.wolips.bindings.api.ApiextGenerator;
import org.objectstyle.wolips.bindings.api.ApiextGenerator.FoundBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * "Generate API": writes a component's {@code .apiext} from its class — a binding for every key
 * the parent can set into it, typed, and pushed back as well when the component synchronizes its
 * variables with its bindings. An existing {@code .apiext} keeps everything it has; only bindings
 * it lacks are added. Saving it writes the WebObjects {@code .api} too, for a WebObjects component
 * ({@link ApiCompanion}).
 *
 * <p>Offered on a bundle template's {@code .wo} folder and on a standalone template's {@code .html};
 * the {@code .apiext} goes beside it, named after the component, where the editor looks for it.
 */
public class GenerateAPIAction implements IObjectActionDelegate {
	private ISelection _selection;
	private IWorkbenchPart _part;

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		_part = targetPart;
	}

	public void selectionChanged(IAction action, ISelection selection) {
		_selection = selection;
	}

	public void run(IAction action) {
		if (!(_selection instanceof IStructuredSelection)) {
			return;
		}
		final List<String> report = new ArrayList<>();
		for (final Object selected : ((IStructuredSelection) _selection).toArray()) {
			if (selected instanceof IResource resource) {
				report.add(generate(resource));
			}
		}
		MessageDialog.openInformation(_part == null ? null : _part.getSite().getShell(), "Generate API", String.join("\n\n", report));
	}

	/** Generates one component's API; returns a line for the report. */
	private static String generate(IResource selected) {
		// A bundle template's .html stands for its .wo folder, beside which the .apiext goes.
		final IResource template = selected.getType() == IResource.FILE && "wo".equals(selected.getParent().getFileExtension()) ? selected.getParent() : selected;
		final String componentName = componentName(template);
		try {
			final WodParserCache cache = WodParserCache.parser(template);
			final IType componentType = cache.getComponentType();
			if (componentType == null) {
				return componentName + ": no Java class found, so there is nothing to generate the API from.";
			}
			final IFile apiextFile = template.getParent().getFile(new org.eclipse.core.runtime.Path(componentName + ".apiext"));
			final boolean existed = apiextFile.exists();
			final MutableApiextModel model = new MutableApiextModel(apiextFile);
			if (!existed) {
				model.className = componentType.getFullyQualifiedName();
			}
			final List<String> added = ApiextGenerator.addMissing(model, foundBindings(cache.getJavaProject(), componentType));
			if (!existed || !added.isEmpty()) {
				model.saveChanges();
			}
			if (existed && added.isEmpty()) {
				return componentName + ": " + apiextFile.getName() + " already has every binding.";
			}
			final String files = ApiCompanion.isWebObjects(apiextFile, model.className) ? apiextFile.getName() + " and " + ApiCompanion.apiFileFor(apiextFile).getName() : apiextFile.getName();
			final String bindings = added.isEmpty() ? "no bindings (the class has no keys a parent can set)" : String.join(", ", added);
			return componentName + ": " + (existed ? "added to " : "wrote ") + files + ": " + bindings + ".";
		}
		catch (Exception e) {
			WodclipsePlugin.getDefault().log(e);
			return componentName + ": failed (" + e.getMessage() + "); see the error log.";
		}
	}

	/** The component's name: the {@code .wo} folder's or the {@code .html} file's, without extension. */
	private static String componentName(IResource template) {
		final String name = template.getName();
		final int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/** The keys a parent can set into the component, with their types and whether they're pushed back. */
	private static List<FoundBinding> foundBindings(IJavaProject javaProject, IType componentType) throws JavaModelException {
		final TypeCache typeCache = WodParserCache.getTypeCache();
		final boolean pushesBack = BindingReflectionUtils.isWebObjectsComponent(componentType, typeCache) && Boolean.TRUE.equals(BindingReflectionUtils.synchronizesVariablesWithBindings(componentType));
		final List<FoundBinding> found = new ArrayList<>();
		for (final BindingValueKey key : BindingReflectionUtils.getBindingKeys(javaProject, componentType, "", false, BindingReflectionUtils.MUTATORS_ONLY, false, typeCache)) {
			if (BindingReflectionUtils.isSystemBindingValueKey(key, true)) {
				continue;
			}
			found.add(new FoundBinding(key.getBindingName(), typeName(key.getBindingMember()), pushesBack));
		}
		return found;
	}

	/** The type a setter takes or a field holds, resolved to a qualified name; null when unknown. */
	private static String typeName(IMember member) throws JavaModelException {
		final String signature;
		if (member instanceof IField field) {
			signature = field.getTypeSignature();
		}
		else if (member instanceof IMethod method && method.getParameterTypes().length == 1) {
			signature = method.getParameterTypes()[0];
		}
		else {
			return null;
		}
		final String erased = Signature.getTypeErasure(signature);
		final int arrayCount = Signature.getArrayCount(erased);
		final String element = Signature.toString(Signature.getElementType(erased));
		final String resolved = resolve(member.getDeclaringType(), element);
		return resolved + "[]".repeat(arrayCount);
	}

	/** A simple or partly qualified name from source, resolved against the declaring type's imports. */
	private static String resolve(IType declaringType, String name) throws JavaModelException {
		if (declaringType.isBinary() || name.indexOf('.') >= 0 && Character.isLowerCase(name.charAt(0)) || isPrimitive(name)) {
			return name;
		}
		final String[][] resolved = declaringType.resolveType(name);
		if (resolved == null || resolved.length != 1) {
			return name;
		}
		return resolved[0][0].isEmpty() ? resolved[0][1] : resolved[0][0] + "." + resolved[0][1];
	}

	private static boolean isPrimitive(String name) {
		return switch (name) {
			case "boolean", "byte", "char", "short", "int", "long", "float", "double" -> true;
			default -> false;
		};
	}
}

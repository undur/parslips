/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2005 - 2006 The ObjectStyle Group,
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */
package org.objectstyle.wolips.locate.result;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.variables.ParsleyProject;

/**
 * Holds the result of locating all files belonging to a single component.
 *
 * A component can be either:
 * <ul>
 *   <li>A traditional .wo folder containing html/wod/woo files, or</li>
 *   <li>A standalone .wo.html file (ng-objects style)</li>
 * </ul>
 *
 * In both cases, the component files (html, wod, woo, java, api) are tracked
 * directly as individual file references.
 */
public class LocalizedComponentsLocateResult extends AbstractLocateResult {

	// .wo folders (traditional WO component structure)
	private List<IFolder> components = new ArrayList<IFolder>();

	// Directly tracked component files
	private IFile htmlFile;
	private IFile wodFile;
	private IFile wooFile;
	private IFile dotJava;
	private IFile dotGroovy;
	private IFile dotApi;

	private String[] superclasses = new String[] { ParsleyProject.NG_ELEMENT_CLASS, ParsleyProject.WO_ELEMENT_CLASS };

	public LocalizedComponentsLocateResult() {
		super();
	}

	public String getName() {
		String name = null;
		if (dotJava != null) {
			name = LocatePlugin.getDefault().fileNameWithoutExtension(dotJava);
		}
		if (name == null && htmlFile != null) {
			name = LocatePlugin.getDefault().fileNameWithoutExtension(htmlFile);
		}
		if (name == null) {
			IFolder[] componentFolders = getComponents();
			if (componentFolders != null && componentFolders.length > 0) {
				name = LocatePlugin.getDefault().fileNameWithoutExtension(componentFolders[0]);
			}
		}
		if (name == null && dotApi != null) {
			name = LocatePlugin.getDefault().fileNameWithoutExtension(dotApi);
		}
		if (name == null) {
			name = "Unknown Component";
		}
		return name;
	}

	public void add(IResource resource) throws LocateException {
		super.add(resource);
		if (resource.getType() == IResource.FOLDER) {
			IFolder folder = (IFolder) resource;
			components.add(folder);
			// Eagerly resolve html/wod/woo from the .wo folder
			resolveFilesFromFolder(folder);
		} else if (resource.getType() == IResource.FILE) {
			IFile file = (IFile) resource;
			String extension = resource.getFileExtension();
			if ("java".equals(extension)) {
				addJavaFile(file);
			} else if ("groovy".equals(extension)) {
				if (dotGroovy != null) {
					String message = "Duplicate located: " + dotGroovy + " " + file;
					alert(message);
					throw new LocateException(message);
				}
				dotGroovy = file;
			} else if ("api".equals(extension)) {
				if (dotApi != null) {
					String message = "Duplicate located: " + dotApi + " " + file;
					alert(message);
				} else {
					dotApi = file;
				}
			} else if ("html".equals(extension)) {
				// Standalone .wo.html file or any HTML file
				htmlFile = file;
			} else {
				String message = "unknown extension on " + file;
				alert(message);
				throw new LocateException(message);
			}
		} else {
			String message = "unsupported type " + resource;
			alert(message);
			throw new LocateException(message);
		}
	}

	/**
	 * When a .wo folder is found, eagerly resolve and cache the html/wod/woo files
	 * from inside it (only if not already set).
	 */
	private void resolveFilesFromFolder(IFolder folder) {
		try {
			if (htmlFile == null) {
				htmlFile = getMemberWithExtension(folder, "html", true);
			}
			if (wodFile == null) {
				wodFile = getMemberWithExtension(folder, "wod", true);
			}
			if (wooFile == null) {
				wooFile = getMemberWithExtension(folder, "woo", false);
			}
		} catch (CoreException e) {
			LocatePlugin.getDefault().log(e);
		}
	}

	private void addJavaFile(IFile file) throws LocateException {
		if (dotJava != null) {
			IJavaElement javaElement = JavaCore.create(file);
			try {
				IJavaProject javaProject = javaElement.getJavaProject();
				if (javaProject != null && javaProject.isOnClasspath(javaElement)) {
					if (!isValidSubclass(javaElement)) {
						file = null;
					}
				} else {
					file = null;
				}
			} catch (JavaModelException e) {
				file = null;
				LocatePlugin.getDefault().log(e);
			}
		}
		if (file != null && dotJava != null) {
			IJavaElement javaElement = JavaCore.create(dotJava);
			try {
				IJavaProject javaProject = javaElement.getJavaProject();
				if (javaProject != null && javaProject.isOnClasspath(javaElement)) {
					if (!isValidSubclass(javaElement)) {
						dotJava = null;
					}
				} else {
					dotJava = null;
				}
			} catch (JavaModelException e) {
				dotJava = null;
				LocatePlugin.getDefault().log(e);
			}
		}
		if (file != null && dotJava != null) {
			String message = "Duplicate located: " + dotJava + " " + file;
			alert(message);
			throw new LocateException(message);
		}
		if (file != null) {
			dotJava = file;
		}
	}

	private void alert(final String message) {
		Display.getDefault().asyncExec(() -> MessageDialog.openError(null, "", message));
	}

	// ---- Accessors ----

	/**
	 * Returns the .wo component folders (traditional WO structure).
	 * May be empty for standalone .wo.html components.
	 */
	public IFolder[] getComponents() {
		return components.toArray(new IFolder[components.size()]);
	}

	public IFile getDotJava() {
		return dotJava;
	}

	public IFile getDotGroovy() {
		return dotGroovy;
	}

	public IFile getDotApi() {
		return dotApi;
	}

	public IFile getDotApi(boolean guessIfMissing) throws CoreException {
		IFile apiFile = dotApi;
		if (apiFile == null && guessIfMissing) {
			IFile html = getFirstHtmlFile();
			if (html != null) {
				IContainer apiFolder = html.getParent();
				// If inside a .wo folder, go up one more level
				if (apiFolder != null && apiFolder.getName().endsWith(".wo")) {
					apiFolder = apiFolder.getParent();
				}
				if (apiFolder != null) {
					apiFile = apiFolder.getFile(new Path(LocatePlugin.getDefault().fileNameWithoutExtension(html) + ".api"));
				}
			}
		}
		return apiFile;
	}

	/**
	 * Returns the standalone .wo.html file, or null if this is a traditional .wo folder component.
	 * @deprecated Use {@link #getFirstHtmlFile()} which handles both cases uniformly.
	 */
	@Deprecated
	public IFile getDotWoHtml() {
		// For backward compatibility — returns htmlFile only if there are no .wo folders
		if (components.isEmpty()) {
			return htmlFile;
		}
		return null;
	}

	public IType getDotJavaType() {
		IType dotJavaType = null;
		IFile javaFile = getDotJava();
		if (javaFile != null) {
			try {
				IJavaElement javaElement = JavaCore.create(javaFile);
				if (javaElement instanceof ICompilationUnit) {
					IType[] types = ((ICompilationUnit) javaElement).getTypes();
					if (types.length > 0) {
						dotJavaType = types[0];
					}
				}
			} catch (JavaModelException e) {
				LocatePlugin.getDefault().log(new RuntimeException(javaFile.getLocation() + " had a problem.", e));
			}
		}
		return dotJavaType;
	}

	// ---- Component file accessors (work for both .wo folders and standalone files) ----

	public IFile getFirstHtmlFile() throws CoreException {
		return htmlFile;
	}

	public IFile getFirstWodFile() throws CoreException {
		return wodFile;
	}

	public IFile getFirstWooFile() throws CoreException {
		return wooFile;
	}

	// ---- Validation ----

	public boolean isValid() {
		boolean valid = true;

		for (IFolder component : components) {
			if (!component.exists()) {
				valid = false;
			}
		}

		if (dotApi == null) {
			try {
				IFile guessDotApi = getDotApi(true);
				if (guessDotApi != null && guessDotApi.exists()) {
					valid = false;
				}
			} catch (CoreException e) {
				LocatePlugin.getDefault().log(e);
			}
		} else if (!dotApi.exists()) {
			valid = false;
		}

		if (dotJava == null) {
			// No Java class was found at locate time.  Invalidate so the
			// locate runs again — a Java class may have been created since.
			// The ComponentLocateCache handles caching, so re-locating is
			// cheap when nothing has changed.
			valid = false;
		} else if (!dotJava.exists()) {
			valid = false;
		}

		if (htmlFile != null && !htmlFile.exists()) {
			valid = false;
		}

		return valid;
	}

	/**
	 * Returns true if this result has any meaningful content (files or folders).
	 */
	public boolean hasContent() {
		return !components.isEmpty()
				|| dotJava != null
				|| htmlFile != null
				|| dotApi != null;
	}

	// ---- Static helpers for looking inside .wo folders (kept for ComponentEditorInput) ----

	public static IFile getHtml(IFolder component) throws CoreException {
		return getMemberWithExtension(component, "html", true);
	}

	public static IFile getWod(IFolder component) throws CoreException {
		return getMemberWithExtension(component, "wod", true);
	}

	public static IFile getWoo(IFolder component) throws CoreException {
		return getMemberWithExtension(component, "woo", false);
	}

	private static IFile getMemberWithExtension(IFolder folder, String extension, boolean mustExist) throws CoreException {
		if (folder == null || !folder.exists()) {
			return null;
		}
		IResource[] members = folder.members();
		for (IResource resource : members) {
			String fileExtension = resource.getFileExtension();
			if (resource.getType() == IResource.FILE && fileExtension != null && fileExtension.equalsIgnoreCase(extension)) {
				return (IFile) resource;
			}
		}
		if (!mustExist) {
			return folder.getFile(LocatePlugin.getDefault().fileNameWithoutExtension(folder.getName()) + "." + extension);
		}
		return null;
	}

	// ---- Private helpers ----

	private boolean isValidSubclass(IJavaElement javaElement) throws JavaModelException {
		if (superclasses == null || superclasses.length == 0) {
			return true;
		}
		ICompilationUnit compilationUnit = (ICompilationUnit) javaElement;
		IType typeToCheck = compilationUnit.findPrimaryType();
		ITypeHierarchy typeHierarchy = typeToCheck.newSupertypeHierarchy(new NullProgressMonitor());
		// getAllSupertypes() returns both superclasses and superinterfaces.
		// This matters because NGElement is an interface (unlike WOElement
		// which is a class), so getAllClasses() would miss it.
		IType[] types = typeHierarchy.getAllSupertypes(typeToCheck);
		for (IType type : types) {
			for (String superclass : superclasses) {
				if (type.getFullyQualifiedName().equals(superclass)) {
					return true;
				}
			}
		}
		return false;
	}
}

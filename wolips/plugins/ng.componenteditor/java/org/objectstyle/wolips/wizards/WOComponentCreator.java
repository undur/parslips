/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2002 - 2006 The ObjectStyle Group
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
package org.objectstyle.wolips.wizards;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.objectstyle.wolips.baseforplugins.util.CharSetUtils;

/**
 * Creates the files for a new WO component. Supports two formats:
 *
 * <ul>
 *   <li><b>Bundle (.wo folder)</b> — traditional WebObjects format with
 *       {@code .html}, {@code .wod}, {@code .woo}, {@code .java}, and
 *       optionally {@code .api} files inside a {@code ComponentName.wo}
 *       folder.</li>
 *   <li><b>Standalone (.html)</b> — single-file format for ng-objects
 *       projects with just {@code ComponentName.html} and
 *       {@code ComponentName.java}. No {@code .wo} folder, no {@code .wod}
 *       or {@code .woo}.</li>
 * </ul>
 *
 * <p>All files are generated directly (no Velocity templates) to avoid the
 * commons-lang OSGi classloader issue. Same approach as
 * {@code ExtractComponentAction}.
 *
 * @author mnolte
 * @author uli
 */
public class WOComponentCreator implements IRunnableWithProgress {
	private String componentName;

	private String packageName;

	private String superclassName;

	private boolean createBodyTag;

	private boolean createApiFile;

	/** Whether to create a standalone HTML file instead of a .wo bundle */
	private boolean standalone;

	private IResource parentResource;

	private WOComponentCreationPage page;

	private int htmlBodyType;

	private String wooEncoding;

	/**
	 * Constructor for WOComponentCreator.
	 *
	 * @param parentResource the folder or project where the component will be created
	 * @param componentName the name of the new component
	 * @param packageName the Java package for the component class
	 * @param superclassName the fully-qualified superclass name
	 * @param createBodyTag whether to include HTML body/doctype (bundle only)
	 * @param createApiFile whether to create an .api file (bundle only)
	 * @param standalone true for standalone HTML, false for .wo bundle
	 * @param page the wizard page (for reading doctype/encoding preferences)
	 */
	public WOComponentCreator(IResource parentResource, String componentName, String packageName, String superclassName, boolean createBodyTag, boolean createApiFile, boolean standalone, WOComponentCreationPage page) {
		this.parentResource = parentResource;
		this.componentName = componentName;
		this.packageName = packageName;
		this.superclassName = superclassName;
		this.createBodyTag = createBodyTag;
		this.createApiFile = createApiFile;
		this.standalone = standalone;
		this.page = page;
		this.htmlBodyType = page.getSelectedHTMLDocType().getTemplateIndex();
		this.wooEncoding = CharSetUtils.encodingNameFromObjectiveC(page.getSelectedEncoding());
	}

	public void run(IProgressMonitor monitor) throws InvocationTargetException {
		try {
			if (standalone) {
				createStandaloneComponent(monitor);
			}
			else {
				createBundleComponent(monitor);
			}
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		}
	}

	/**
	 * Creates a standalone component: just {@code ComponentName.html} in the
	 * target folder and {@code ComponentName.java} in the Java source folder.
	 *
	 * <p>Files are generated directly rather than through Velocity templates,
	 * avoiding the commons-lang OSGi classloader issue. Same approach as
	 * {@code ExtractComponentAction}.
	 */
	private void createStandaloneComponent(IProgressMonitor monitor) throws CoreException, InvocationTargetException {
		IProject project = this.parentResource.getProject();
		IJavaProject javaProject = JavaCore.create(project);

		// Find the Java source folder
		IPath componentJavaPath = findJavaSourcePath(javaProject);
		if (packageName != null && packageName.length() > 0) {
			componentJavaPath = componentJavaPath.append(new Path(packageName.replace('.', '/')));
		}

		// Ensure the Java source folder exists
		IPath projectRelativeJavaPath = componentJavaPath.removeFirstSegments(
				project.getLocation().segmentCount());
		IFolder javaSourceFolder = project.getFolder(projectRelativeJavaPath);
		prepareFolder(javaSourceFolder, monitor);

		// Determine the target folder for the .html file
		IContainer targetFolder;
		switch (this.parentResource.getType()) {
		case IResource.PROJECT:
			targetFolder = (IProject) this.parentResource;
			break;
		case IResource.FOLDER:
			targetFolder = (IFolder) this.parentResource;
			break;
		default:
			throw new InvocationTargetException(new Exception("Wrong parent resource - check validation"));
		}

		// 1. Create the standalone .html file (empty template)
		IFile htmlFile = targetFolder.getFile(new Path(this.componentName + ".html"));
		createFile(htmlFile, "\n", monitor);

		// 2. Create the .java file in the source folder
		String javaContent = generateJavaSource(this.componentName, this.packageName, this.superclassName);
		IFile javaFile = javaSourceFolder.getFile(new Path(this.componentName + ".java"));
		createFile(javaFile, javaContent, monitor);

		// Refresh and reveal the created files
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		page.setResourcesToReveal(new IResource[] { htmlFile, javaFile });
	}

	/**
	 * Creates a .wo bundle component with all required files generated directly.
	 *
	 * <p>Creates a {@code ComponentName.wo} folder containing {@code .html},
	 * {@code .wod}, and {@code .woo} files, plus {@code ComponentName.java}
	 * in the Java source folder. Optionally creates a {@code .api} file
	 * alongside the {@code .wo} folder.
	 *
	 * <p>Files are generated directly rather than through Velocity templates,
	 * avoiding the commons-lang OSGi classloader issue.
	 */
	private void createBundleComponent(IProgressMonitor monitor) throws CoreException, InvocationTargetException {
		IProject project = this.parentResource.getProject();
		IJavaProject javaProject = JavaCore.create(project);

		// Determine the .wo folder and its parent (for revealing and .api placement)
		IFolder componentFolder;
		IContainer parentFolder;

		switch (this.parentResource.getType()) {
		case IResource.PROJECT:
			componentFolder = ((IProject) this.parentResource).getFolder(this.componentName + ".wo");
			parentFolder = (IProject) this.parentResource;
			break;
		case IResource.FOLDER:
			componentFolder = ((IFolder) this.parentResource).getFolder(this.componentName + ".wo");
			parentFolder = (IFolder) this.parentResource;
			break;
		default:
			throw new InvocationTargetException(new Exception("Wrong parent resource - check validation"));
		}

		prepareFolder(componentFolder, monitor);

		// Find and prepare the Java source folder
		IPath componentJavaPath = findJavaSourcePath(javaProject);
		if (packageName != null && packageName.length() > 0) {
			componentJavaPath = componentJavaPath.append(new Path(packageName.replace('.', '/')));
		}
		IPath projectRelativeJavaPath = componentJavaPath.removeFirstSegments(
				project.getLocation().segmentCount());
		IFolder javaSourceFolder = project.getFolder(projectRelativeJavaPath);
		prepareFolder(javaSourceFolder, monitor);

		// 1. Create the .html file inside the .wo folder
		String htmlContent = this.createBodyTag ? generateHtmlWithDoctype(this.htmlBodyType) : "\n";
		IFile htmlFile = componentFolder.getFile(new Path(this.componentName + ".html"));
		createFile(htmlFile, htmlContent, monitor);

		// 2. Create an empty .wod file inside the .wo folder
		IFile wodFile = componentFolder.getFile(new Path(this.componentName + ".wod"));
		createFile(wodFile, "", monitor);

		// 3. Create the .woo file inside the .wo folder
		String wooContent = generateWooContent(this.wooEncoding);
		IFile wooFile = componentFolder.getFile(new Path(this.componentName + ".woo"));
		createFile(wooFile, wooContent, monitor);

		// 4. Create the .java file in the source folder
		String javaContent = generateJavaSource(this.componentName, this.packageName, this.superclassName);
		IFile javaFile = javaSourceFolder.getFile(new Path(this.componentName + ".java"));
		createFile(javaFile, javaContent, monitor);

		// 5. Optionally create the .api file alongside the .wo folder
		if (this.createApiFile) {
			String apiContent = generateApiContent(this.componentName);
			IFile apiFile = parentFolder.getFile(new Path(this.componentName + ".api"));
			createFile(apiFile, apiContent, monitor);
		}

		// Refresh and reveal the created files
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		page.setResourcesToReveal(new IResource[] { javaFile, wodFile });
	}

	/**
	 * Finds the first Java source folder in the project.
	 */
	private IPath findJavaSourcePath(IJavaProject javaProject) throws InvocationTargetException {
		try {
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
			for (IPackageFragmentRoot root : roots) {
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					return root.getCorrespondingResource().getLocation();
				}
			}
		}
		catch (Exception e) {
			throw new InvocationTargetException(e);
		}
		// Fallback: project root
		return javaProject.getProject().getLocation();
	}

	/**
	 * Generates the Java source for a new component class.
	 *
	 * <p>Derives the context class from the superclass: ng-objects components
	 * use {@code NGContext}, WebObjects components use {@code WOContext}.
	 * Same logic as {@code ExtractComponentAction.generateJavaSource()}.
	 */
	private String generateJavaSource(String componentName, String packageName,
			String superclassName) {

		StringBuilder sb = new StringBuilder();

		// Package declaration
		if (packageName != null && !packageName.isEmpty()) {
			sb.append("package ").append(packageName).append(";\n\n");
		}

		// Determine the context class from the superclass
		// ng-objects uses NGContext, WebObjects uses WOContext
		boolean isNg = superclassName.startsWith("ng.");
		String contextClass = isNg
				? "ng.appserver.NGContext"
				: "com.webobjects.appserver.WOContext";
		String contextShortName = isNg ? "NGContext" : "WOContext";

		// Import the context class
		sb.append("import ").append(contextClass).append(";\n");

		// Import the superclass if it's in a different package
		String superclassShortName = superclassName;
		String superclassPackage = null;
		int lastDot = superclassName.lastIndexOf('.');
		if (lastDot > 0) {
			superclassPackage = superclassName.substring(0, lastDot);
			superclassShortName = superclassName.substring(lastDot + 1);
			// Only import if in a different package
			if (!superclassPackage.equals(packageName)) {
				sb.append("import ").append(superclassName).append(";\n");
			}
		}

		sb.append("\n");
		sb.append("public class ").append(componentName);
		sb.append(" extends ").append(superclassShortName).append(" {\n");
		sb.append("\tpublic ").append(componentName).append("(").append(contextShortName).append(" context) {\n");
		sb.append("\t\tsuper(context);\n");
		sb.append("\t}\n");
		sb.append("}\n");

		return sb.toString();
	}

	/**
	 * Generates HTML content with the appropriate DOCTYPE declaration.
	 *
	 * <p>The {@code htmlBodyType} index corresponds to the template indices
	 * in the {@link WOComponentCreationPage.HTML} enum:
	 * 0=HTML 4.01 Strict, 1=HTML 4.01 Transitional, 2=XHTML 1.0 Strict,
	 * 3=XHTML 1.0 Transitional, 4=XHTML 1.0 Frameset, 5=XHTML 1.1,
	 * 6=Lazy Old HTML.
	 */
	private String generateHtmlWithDoctype(int bodyType) {
		StringBuilder sb = new StringBuilder();

		switch (bodyType) {
		case 0: // HTML 4.01 Strict
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\"\n");
			sb.append("   \"http://www.w3.org/TR/html4/strict.dtd\">\n\n");
			sb.append("<html lang=\"en\">\n");
			break;
		case 1: // HTML 4.01 Transitional
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n");
			sb.append("   \"http://www.w3.org/TR/html4/loose.dtd\">\n\n");
			sb.append("<html lang=\"en\">\n");
			break;
		case 2: // XHTML 1.0 Strict
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n");
			sb.append("\t\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n\n");
			sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n");
			break;
		case 3: // XHTML 1.0 Transitional
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n");
			sb.append("\t\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n\n");
			sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n");
			break;
		case 4: // XHTML 1.0 Frameset
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Frameset//EN\"\n");
			sb.append("\t\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd\">\n\n");
			sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n");
			break;
		case 5: // XHTML 1.1
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"\n");
			sb.append("\t\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n\n");
			sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n");
			break;
		case 6: // Lazy Old HTML
		default:
			sb.append("<html>\n");
			break;
		}

		sb.append("<head>\n");
		sb.append("\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n");
		sb.append("\t<title>untitled</title>\n");
		sb.append("</head>\n");
		sb.append("<body>\n\n");
		sb.append("</body>\n");
		sb.append("</html>\n");

		return sb.toString();
	}

	/**
	 * Generates the WOO file content with the specified encoding.
	 * The WOO format is a simple key-value format used by WebObjects.
	 */
	private String generateWooContent(String encoding) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("\t\"WebObjects Release\" = \"WebObjects 5.0\";\n");
		sb.append("\tencoding = \"").append(encoding).append("\";\n");
		sb.append("}\n");
		return sb.toString();
	}

	/**
	 * Generates a minimal .api file declaring the component.
	 */
	private String generateApiContent(String componentName) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		sb.append("<wodefinitions>\n");
		sb.append("\t<wo wocomponentcontent=\"false\" class=\"").append(componentName).append("\">\n");
		sb.append("\n");
		sb.append("\t</wo>\n");
		sb.append("</wodefinitions>\n");
		return sb.toString();
	}

	/**
	 * Creates a file with the given content. If the file already exists,
	 * it is overwritten.
	 */
	private void createFile(IFile file, String content, IProgressMonitor monitor)
			throws CoreException {
		ByteArrayInputStream stream = new ByteArrayInputStream(
				content.getBytes(StandardCharsets.UTF_8));
		if (file.exists()) {
			file.setContents(stream, true, false, monitor);
		}
		else {
			file.create(stream, true, monitor);
		}
	}

	/**
	 * Recursively creates folders if they don't exist.
	 */
	public void prepareFolder(IFolder _folder, IProgressMonitor _progressMonitor) throws CoreException {
		if (!_folder.exists()) {
			IContainer parent = _folder.getParent();
			if (parent instanceof IFolder) {
				prepareFolder((IFolder) parent, _progressMonitor);
			}
			_folder.create(false, true, _progressMonitor);
		}
	}
}

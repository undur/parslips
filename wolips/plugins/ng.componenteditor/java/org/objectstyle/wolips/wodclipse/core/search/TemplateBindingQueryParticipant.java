package org.objectstyle.wolips.wodclipse.core.search;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jdt.ui.search.IQueryParticipant;
import org.eclipse.jdt.ui.search.ISearchRequestor;
import org.eclipse.jdt.ui.search.QuerySpecification;
import org.eclipse.search.ui.text.Match;
import org.objectstyle.wolips.locate.LocatePlugin;
import org.objectstyle.wolips.locate.result.LocalizedComponentsLocateResult;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameBindingKeyParticipant;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameBindingKeyProcessor;
import org.objectstyle.wolips.wodclipse.core.refactoring.RenameComponentProcessor;

/**
 * Contributes template references to Eclipse's "Find References" search.
 *
 * <p>Handles two kinds of template references:
 *
 * <ol>
 *   <li><b>Binding key references</b> — when searching for references to a
 *       method or field in a component class (e.g. Ctrl+Shift+G on
 *       {@code name()}), finds matching binding key references in the
 *       component's own template files: inline HTML bindings
 *       ({@code value="$name"}) and WOD binding values
 *       ({@code value = name;}).</li>
 *   <li><b>Element type references</b> — when searching for references to a
 *       component or element class (e.g. Ctrl+Shift+G on {@code MyComponent}),
 *       finds template references across the project and all dependent
 *       projects: inline HTML tags ({@code <wo:MyComponent>}) and WOD
 *       element type declarations ({@code Foo : MyComponent { }}).</li>
 * </ol>
 *
 * <p>Binding key search is <b>Level 1</b>: only first-segment keys in the
 * component's own template. Deep keypath references (e.g.
 * {@code $selectedObject.name} in other templates) would require
 * type-resolving every keypath in every template — deferred as Level 2.
 *
 * <p>Element type search scans the element's own project plus all projects
 * that transitively depend on it, matching the scope used by the Usages tab
 * in the component editor.
 *
 * <p>Registered via plugin.xml as an {@code org.eclipse.jdt.ui.queryParticipants}
 * extension.
 *
 * @see RenameBindingKeyProcessor#findHtmlKeyOffsets(String, String, String)
 * @see RenameBindingKeyProcessor#findWodKeyOffsets(String, String)
 * @see RenameComponentProcessor#findHtmlElementOffsets(String, String)
 * @see RenameComponentProcessor#findWodElementOffsets(String, String)
 */
public class TemplateBindingQueryParticipant implements IQueryParticipant {

	@Override
	public void search(ISearchRequestor requestor, QuerySpecification querySpecification,
			IProgressMonitor monitor) throws CoreException {

		if (!(querySpecification instanceof ElementQuerySpecification)) {
			return;
		}

		ElementQuerySpecification elementSpec = (ElementQuerySpecification) querySpecification;

		// Only participate in REFERENCES searches
		if ((elementSpec.getLimitTo() & IJavaSearchConstants.REFERENCES) == 0
				&& (elementSpec.getLimitTo() & IJavaSearchConstants.ALL_OCCURRENCES) == 0) {
			return;
		}

		IJavaElement element = elementSpec.getElement();

		if (element instanceof IMethod || element instanceof IField) {
			searchBindingKeyReferences(requestor, (IMember) element, monitor);
		}
		else if (element instanceof IType) {
			searchElementTypeReferences(requestor, (IType) element, monitor);
		}
	}

	/**
	 * Searches for binding key references to the given method or field in
	 * the component's own template files.
	 *
	 * <p>Only participates if the declaring type is a WOComponent/NGComponent
	 * subclass — plain elements don't have template binding keys.
	 */
	private void searchBindingKeyReferences(ISearchRequestor requestor, IMember member,
			IProgressMonitor monitor) throws CoreException {

		IType declaringType = member.getDeclaringType();
		if (declaringType == null) {
			return;
		}

		IProject project = declaringType.getJavaProject().getProject();

		// Only activate for Parsley-managed projects
		if (!ParsleyProject.shouldHandleProject(project)) {
			return;
		}

		// Only participate if the declaring type is a WOComponent/NGComponent
		if (!RenameBindingKeyParticipant.isComponent(declaringType)) {
			return;
		}

		// Derive the binding key from the member name
		String bindingKey = RenameBindingKeyProcessor.deriveBindingKey(member);
		if (bindingKey == null || bindingKey.isEmpty()) {
			return;
		}

		// Locate the component's template files
		String componentName = declaringType.getElementName();
		LocalizedComponentsLocateResult locateResult;
		try {
			locateResult = LocatePlugin.getDefault().getLocalizedComponentsLocateResult(project, componentName);
		}
		catch (Exception e) {
			return;
		}

		if (locateResult == null || !locateResult.hasContent()) {
			return;
		}

		// Determine the inline binding prefix for this project
		BuildProperties buildProperties = (BuildProperties) project.getAdapter(BuildProperties.class);
		String inlineBindingPrefix = buildProperties != null ? buildProperties.getInlineBindingPrefix() : "$";

		// Scan the HTML file for inline binding key references
		try {
			IFile htmlFile = locateResult.getFirstHtmlFile();
			if (htmlFile != null && htmlFile.exists()) {
				String content = RenameComponentProcessor.readFileContent(htmlFile);
				if (content != null) {
					List<int[]> offsets = RenameBindingKeyProcessor.findHtmlKeyOffsets(
							content, bindingKey, inlineBindingPrefix);
					for (int[] offset : offsets) {
						requestor.reportMatch(new Match(htmlFile, offset[0], offset[1]));
					}
				}
			}
		}
		catch (IOException e) {
			// Skip unreadable files
		}

		// Scan the WOD file for binding key references
		try {
			IFile wodFile = locateResult.getFirstWodFile();
			if (wodFile != null && wodFile.exists()) {
				String content = RenameComponentProcessor.readFileContent(wodFile);
				if (content != null) {
					List<int[]> offsets = RenameBindingKeyProcessor.findWodKeyOffsets(content, bindingKey);
					for (int[] offset : offsets) {
						requestor.reportMatch(new Match(wodFile, offset[0], offset[1]));
					}
				}
			}
		}
		catch (IOException e) {
			// Skip unreadable files
		}
	}

	/**
	 * Searches for element type references to the given type in all templates
	 * across the project and its dependent projects.
	 *
	 * <p>Matches inline HTML tags ({@code <wo:TypeName>}) and WOD element
	 * type declarations ({@code Foo : TypeName { }}). The scan scope matches
	 * the Usages tab: the type's own project plus all projects that
	 * transitively depend on it.
	 *
	 * <p>Only participates if the type is a WOElement/NGElement subclass
	 * (checked via {@link ParsleyProject} class name constants) — searching
	 * for references to a plain Java class wouldn't produce template results.
	 */
	private void searchElementTypeReferences(ISearchRequestor requestor, IType type,
			IProgressMonitor monitor) throws CoreException {

		IProject project = type.getJavaProject().getProject();

		// Only activate for Parsley-managed projects
		if (!ParsleyProject.shouldHandleProject(project)) {
			return;
		}

		String elementName = type.getElementName();

		// Collect the type's project and all projects that depend on it
		Set<IProject> projectsToScan = new HashSet<>();
		collectDependentProjects(project, projectsToScan);

		for (IProject scanProject : projectsToScan) {
			if (monitor.isCanceled()) {
				break;
			}
			if (!scanProject.isOpen() || !scanProject.isAccessible()) {
				continue;
			}

			scanProjectForElementReferences(requestor, scanProject, elementName, monitor);
		}
	}

	/**
	 * Scans a single project's templates for references to the named element
	 * type, reporting matches to the search requestor.
	 */
	private void scanProjectForElementReferences(ISearchRequestor requestor,
			IProject project, String elementName, IProgressMonitor monitor) throws CoreException {

		project.accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				if (monitor.isCanceled()) {
					return false;
				}
				if (resource.isDerived()) {
					return false;
				}
				if (resource.getType() != IResource.FILE) {
					return true;
				}

				IFile file = (IFile) resource;
				String extension = file.getFileExtension();
				if (extension == null) {
					return false;
				}

				boolean isHtml = "html".equalsIgnoreCase(extension);
				boolean isWod = "wod".equalsIgnoreCase(extension);
				if (!isHtml && !isWod) {
					return false;
				}

				try {
					String content = RenameComponentProcessor.readFileContent(file);
					if (content == null) {
						return false;
					}

					List<int[]> offsets;
					if (isHtml) {
						offsets = RenameComponentProcessor.findHtmlElementOffsets(content, elementName);
					}
					else {
						offsets = RenameComponentProcessor.findWodElementOffsets(content, elementName);
					}

					for (int[] offset : offsets) {
						requestor.reportMatch(new Match(file, offset[0], offset[1]));
					}
				}
				catch (IOException e) {
					// Skip unreadable files
				}
				return false;
			}
		});
	}

	/**
	 * Collects the given project and all projects that transitively depend on
	 * it (via {@link IProject#getReferencingProjects()}).
	 *
	 * <p>Same algorithm used by the Usages tab — a component in a library
	 * can be referenced from any application project that depends on the
	 * library.
	 */
	private static void collectDependentProjects(IProject project, Set<IProject> result) {
		if (!result.add(project)) {
			return; // already visited — prevents cycles
		}
		for (IProject dependent : project.getReferencingProjects()) {
			collectDependentProjects(dependent, result);
		}
	}

	@Override
	public int estimateTicks(QuerySpecification specification) {
		if (specification instanceof ElementQuerySpecification) {
			IJavaElement element = ((ElementQuerySpecification) specification).getElement();
			if (element instanceof IType) {
				// Element type search scans multiple projects — give it
				// more weight than binding key search (which scans 2 files)
				return 200;
			}
		}
		// Binding key search: at most 2 files
		return 50;
	}

	@Override
	public IMatchPresentation getUIParticipant() {
		// Matches are reported against IFile objects, which the standard
		// Search view already knows how to render and navigate.
		return null;
	}
}

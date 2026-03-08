package org.objectstyle.wolips.locate.result;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.objectstyle.wolips.locate.LocateException;
import org.objectstyle.wolips.locate.LocatePlugin;

/**
 * Immutable description of an element and all its associated files.
 *
 * <p>This is the "shared currency" for answering "what files belong to
 * this element?" — a question that switch handlers, extract refactorings,
 * rename participants, the usages tab, and the editor input all need to
 * answer. Previously each consumer called the locate system independently
 * and extracted the fields it needed; {@code ElementDescriptor} captures
 * the answer once in a clean, typed API.
 *
 * <p>An {@code ElementDescriptor} wraps the result of
 * {@link LocalizedComponentsLocateResult} without replacing it — the
 * locate machinery stays as-is. Think of this as the immutable projection
 * of a mutable locate result.
 *
 * <p>All {@code IFile} fields are nullable. A null file means the element
 * doesn't have that artifact (e.g. a dynamic element has no HTML file).
 * A non-null file may or may not exist on disk — callers should check
 * {@link IFile#exists()} when that matters.
 *
 * @see TemplateFormat
 * @see LocalizedComponentsLocateResult
 */
public class ElementDescriptor {

	private final String _name;
	private final IProject _project;
	private final IFile _htmlFile;
	private final IFile _wodFile;
	private final IFile _wooFile;
	private final IFile _apiFile;
	private final IFile _javaFile;
	private final TemplateFormat _templateFormat;

	/**
	 * Primary constructor — private, use factory methods.
	 */
	private ElementDescriptor(String name, IProject project, IFile htmlFile,
			IFile wodFile, IFile wooFile, IFile apiFile, IFile javaFile,
			TemplateFormat templateFormat) {
		if (name == null) {
			throw new IllegalArgumentException("name must not be null");
		}
		if (project == null) {
			throw new IllegalArgumentException("project must not be null");
		}
		if (templateFormat == null) {
			throw new IllegalArgumentException("templateFormat must not be null");
		}
		_name = name;
		_project = project;
		_htmlFile = htmlFile;
		_wodFile = wodFile;
		_wooFile = wooFile;
		_apiFile = apiFile;
		_javaFile = javaFile;
		_templateFormat = templateFormat;
	}

	/**
	 * Package-private constructor for unit testing — accepts raw values
	 * without requiring a {@link LocalizedComponentsLocateResult}.
	 */
	ElementDescriptor(String name, IProject project, TemplateFormat templateFormat,
			IFile htmlFile, IFile wodFile, IFile wooFile, IFile apiFile, IFile javaFile) {
		this(name, project, htmlFile, wodFile, wooFile, apiFile, javaFile, templateFormat);
	}

	// ---- Factory methods ----

	/**
	 * Creates a descriptor from a locate result. This is the bridge between
	 * the mutable locate system and the immutable descriptor.
	 *
	 * <p>The template format is detected from the locate result:
	 * <ul>
	 *   <li>{@code .wo} folders present → {@link TemplateFormat#BUNDLE}</li>
	 *   <li>HTML file present (no {@code .wo} folders) → {@link TemplateFormat#STANDALONE}</li>
	 *   <li>Neither → {@link TemplateFormat#NONE}</li>
	 * </ul>
	 *
	 * @param result  the locate result to wrap (must not be null)
	 * @param project the owning project (must not be null)
	 * @return an immutable descriptor, never null
	 */
	public static ElementDescriptor fromLocateResult(
			LocalizedComponentsLocateResult result, IProject project) throws CoreException {

		// Determine template format
		boolean hasWoFolders = result.getComponents().length > 0;
		IFile htmlFile = result.getFirstHtmlFile();
		IFile wodFile = result.getFirstWodFile();
		IFile wooFile = result.getFirstWooFile();

		TemplateFormat format;
		if (hasWoFolders) {
			format = TemplateFormat.BUNDLE;
		}
		else if (htmlFile != null) {
			format = TemplateFormat.STANDALONE;
		}
		else {
			format = TemplateFormat.NONE;
		}

		// For standalone templates, there's no WOD or WOO
		if (format == TemplateFormat.STANDALONE) {
			wodFile = null;
			wooFile = null;
		}

		// Use getDotApi(true) to include the guessed path if no explicit
		// .api file was found — matches how ComponentEditorInput and the
		// switch handlers work (they open .api files that may not yet exist).
		IFile apiFile = result.getDotApi(true);

		return new ElementDescriptor(
				result.getName(),
				project,
				htmlFile,
				wodFile,
				wooFile,
				apiFile,
				result.getDotJava(),
				format);
	}

	/**
	 * Resolves an element descriptor for the given file by calling the
	 * locate system. This is the primary entry point for most consumers.
	 *
	 * @param file any file belonging to the element (HTML, WOD, Java, API, etc.)
	 * @return a descriptor, or {@code null} if the locate system can't
	 *         resolve the file to a known element
	 */
	public static ElementDescriptor forFile(IFile file) {
		if (file == null) {
			return null;
		}
		try {
			LocalizedComponentsLocateResult result =
					LocatePlugin.getDefault().getLocalizedComponentsLocateResult(file);
			if (result == null) {
				return null;
			}
			return fromLocateResult(result, file.getProject());
		}
		catch (CoreException | LocateException e) {
			LocatePlugin.getDefault().log(e);
			return null;
		}
	}

	// ---- Accessors ----

	/** Element name, never null. */
	public String getName() {
		return _name;
	}

	/** Owning project, never null. */
	public IProject getProject() {
		return _project;
	}

	/** Template HTML file, or null if this element has no template. */
	public IFile getHtmlFile() {
		return _htmlFile;
	}

	/** WOD file, or null (only present for bundle templates). */
	public IFile getWodFile() {
		return _wodFile;
	}

	/** WOO file, or null (only present for bundle templates). */
	public IFile getWooFile() {
		return _wooFile;
	}

	/**
	 * API file, or null. May be a guessed path that doesn't exist on disk
	 * yet — callers should check {@link IFile#exists()} when that matters.
	 */
	public IFile getApiFile() {
		return _apiFile;
	}

	/** Java source file, or null. */
	public IFile getJavaFile() {
		return _javaFile;
	}

	/** Template format — bundle, standalone, or none. */
	public TemplateFormat getTemplateFormat() {
		return _templateFormat;
	}

	// ---- Convenience methods ----

	/** Returns true if this element has a template (bundle or standalone). */
	public boolean hasTemplate() {
		return _templateFormat != TemplateFormat.NONE;
	}

	/** Returns true if this element uses a bundle template (.wo folder). */
	public boolean isBundle() {
		return _templateFormat == TemplateFormat.BUNDLE;
	}

	/** Returns true if this element uses a standalone template (single .html). */
	public boolean isStandalone() {
		return _templateFormat == TemplateFormat.STANDALONE;
	}

	/**
	 * Returns the primary Java type for this element's Java file, or null
	 * if no Java file exists or the type cannot be resolved.
	 *
	 * <p>This replaces the {@code getDotJavaType()} pattern on
	 * {@link LocalizedComponentsLocateResult}.
	 */
	public IType getJavaType() {
		if (_javaFile == null || !_javaFile.exists()) {
			return null;
		}
		try {
			IJavaElement javaElement = JavaCore.create(_javaFile);
			if (javaElement instanceof ICompilationUnit) {
				IType[] types = ((ICompilationUnit) javaElement).getTypes();
				if (types.length > 0) {
					return types[0];
				}
			}
		}
		catch (Exception e) {
			// Return null on failure — caller decides how to handle
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ElementDescriptor[");
		sb.append(_name);
		sb.append(", ").append(_templateFormat);
		if (_htmlFile != null) {
			sb.append(", html=").append(_htmlFile.getProjectRelativePath());
		}
		if (_wodFile != null) {
			sb.append(", wod=").append(_wodFile.getProjectRelativePath());
		}
		if (_apiFile != null) {
			sb.append(", api=").append(_apiFile.getProjectRelativePath());
		}
		if (_javaFile != null) {
			sb.append(", java=").append(_javaFile.getProjectRelativePath());
		}
		sb.append("]");
		return sb.toString();
	}
}

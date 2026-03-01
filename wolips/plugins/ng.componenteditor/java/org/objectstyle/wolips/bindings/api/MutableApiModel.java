package org.objectstyle.wolips.bindings.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.objectstyle.wolips.locate.LocatePlugin;

/**
 * File-backed mutable model for editing {@code .api} files.
 *
 * <p>Replaces the former DOM-backed {@code ApiModel} class.
 * It uses {@link ApiParser} to parse XML into an {@link ApiSnapshot}, provides
 * direct mutation of the snapshot, and uses {@link ApiSerializer} to write
 * changes back to disk.
 *
 * <p>Unlike the old DOM-backed model, there is no shared mutable DOM tree to
 * synchronize on. The editor holds the only reference to its snapshot, mutations
 * are simple field assignments on POJOs, and serialization is a one-shot write.
 *
 * <p>Used by the {@code .api} editor ({@link org.objectstyle.wolips.apieditor.editor.ApiEditor})
 * and by {@link org.objectstyle.wolips.wodclipse.action.GenerateAPIAction}.
 */
public class MutableApiModel {

	private File _file;
	private IFile _eclipseFile;
	private ApiSnapshot _snapshot;
	private boolean _isDirty;

	/**
	 * Creates a model backed by a plain {@link File}. If the file does not
	 * exist or is empty, a blank {@code .api} file is created with the
	 * component name derived from the filename.
	 *
	 * @param file the {@code .api} file on disk
	 * @throws ApiModelException if parsing fails
	 */
	public MutableApiModel(File file) throws ApiModelException {
		_file = file;
		if (!file.exists() || file.length() == 0) {
			createBlankFile(file);
		}
		_snapshot = ApiParser.parseFile(file);
	}

	/**
	 * Creates a model backed by an Eclipse {@link IFile}. If the file does not
	 * exist or is empty, a blank {@code .api} file is created.
	 *
	 * @param file the Eclipse workspace file
	 * @throws ApiModelException if parsing fails
	 */
	public MutableApiModel(IFile file) throws ApiModelException {
		_eclipseFile = file;
		_file = file.getLocation().toFile();
		if (!file.exists() || _file.length() == 0) {
			createBlankFile(_file);
		}
		_snapshot = ApiParser.parseFile(_file);
	}

	/**
	 * Returns the current snapshot. The editor mutates this snapshot directly
	 * (adding/removing bindings, toggling flags, etc.) and then calls
	 * {@link #saveChanges()} to serialize it back to disk.
	 */
	public ApiSnapshot getSnapshot() {
		return _snapshot;
	}

	/** Returns true if the model has unsaved changes. */
	public boolean isDirty() {
		return _isDirty;
	}

	/**
	 * Marks the model as having unsaved changes. Call this after any mutation
	 * to the snapshot so the editor knows to enable the Save action.
	 */
	public void markAsDirty() {
		_isDirty = true;
	}

	/**
	 * Serializes the current snapshot to XML and writes it to the backing file.
	 *
	 * <p>After writing, refreshes the Eclipse resource (if backed by an
	 * {@link IFile}) so that the workspace picks up the change and
	 * {@link org.objectstyle.wolips.componenteditor.part.JavaChangeRevalidator}
	 * can trigger revalidation of open component editors.
	 *
	 * @throws ApiModelException if serialization or I/O fails
	 */
	public void saveChanges() throws ApiModelException {
		if (_file == null) {
			throw new ApiModelException("Cannot save: no backing file.");
		}

		try {
			try (Writer writer = new FileWriter(_file)) {
				ApiSerializer.serialize(_snapshot, writer);
			}

			if (_eclipseFile != null) {
				try {
					_eclipseFile.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				} catch (CoreException e) {
					// Refresh failure is non-fatal — the file was already written
				}
			}

			_isDirty = false;
		} catch (IOException e) {
			throw new ApiModelException("Failed to save .api file.", e);
		}
	}

	/**
	 * Creates a blank {@code .api} file with the component name derived from
	 * the filename (without extension).
	 */
	private void createBlankFile(File file) throws ApiModelException {
		String componentName = LocatePlugin.getDefault().fileNameWithoutExtension(file);
		try (FileWriter writer = new FileWriter(file)) {
			writer.write(blankContent(componentName));
		} catch (IOException e) {
			throw new ApiModelException("Failed to create blank .api file.", e);
		}
	}

	/**
	 * Returns the XML content for a new blank {@code .api} file.
	 *
	 * @param name the component class name (without package)
	 * @return well-formed XML for an empty {@code .api} file
	 */
	public static String blankContent(String name) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		sb.append("<wodefinitions>\n");
		sb.append("    <wo wocomponentcontent=\"false\" class=\"").append(name).append("\">");
		sb.append("    </wo>\n");
		sb.append("</wodefinitions>\n");
		return sb.toString();
	}
}

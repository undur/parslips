package org.objectstyle.wolips.bindings.api;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Runs {@link ApiextFileValidator} for a changed {@code .apiext} file on a {@link WorkspaceJob},
 * off the resource-change notification thread — resources (markers) must not be modified from within
 * that notification, so the {@link WodParserCacheInvalidator} schedules this instead of validating
 * inline. The job takes the file itself as its scheduling rule (marker changes only need the file),
 * so concurrent validations of different {@code .apiext} files don't serialize.
 */
public final class ApiextFileValidationJob extends WorkspaceJob {

	private final IFile _file;

	private ApiextFileValidationJob(IFile file) {
		super("Validating " + file.getName());
		_file = file;
		setRule(file);
		setSystem(true); // housekeeping — keep it out of the Progress view
	}

	/** Schedules validation of the given {@code .apiext} file. No-op for null. */
	public static void schedule(IFile file) {
		if (file != null) {
			new ApiextFileValidationJob(file).schedule();
		}
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor) {
		try {
			ApiextFileValidator.validate(_file);
		}
		catch (CoreException e) {
			// Marker manipulation failed — non-fatal; the file just keeps its prior markers.
			return new Status(IStatus.WARNING, "ng.componenteditor", "Failed to validate " + _file.getName(), e);
		}
		return Status.OK_STATUS;
	}
}

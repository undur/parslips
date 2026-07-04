package org.objectstyle.wolips.bindings.api;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.objectstyle.wolips.bindings.api.ApiextConstraintValidator.Problem;
import org.objectstyle.wolips.bindings.api.ApiextConstraintValidator.Severity;

/**
 * Validates an {@code .apiext} <em>file</em> and reflects the results as problem markers on the file
 * itself (Milestone 1 of the {@code .apiext} validation work). This surfaces the consumer-enforced
 * integrity checks — the ones the DTD cannot express — to the author of the {@code .apiext}, so a
 * typo'd binding reference or a malformed {@code <choose>} shows up where they can see and fix it,
 * rather than silently weakening a constraint.
 * <p>
 * The checks come from {@link ApiextConstraintValidator} (reference integrity, {@code <choose>}
 * arity/bounds, the deprecated+required lint, legacy-construct-in-{@code .apiext}). Markers are
 * currently file-level (no char range) — the {@link Problem}s are structural and don't carry source
 * positions yet; precise ranges can follow. A parse failure (malformed XML / no {@code <wo>}) is
 * itself reported as one error marker.
 */
public final class ApiextFileValidator {

	/** The marker type for {@code .apiext}-file problems. Shared with the template problem marker. */
	public static final String MARKER_TYPE = "ng.componenteditor.problem";

	private ApiextFileValidator() {
		// static only
	}

	/**
	 * Re-validates the given {@code .apiext} file: clears prior markers of {@link #MARKER_TYPE} and
	 * creates fresh ones for any problems found. No-op for a null/inaccessible file. Swallows and logs
	 * nothing here — callers (a resource listener) decide error handling; a {@link CoreException} from
	 * marker manipulation propagates.
	 */
	public static void validate(IFile file) throws CoreException {
		if (file == null || !file.exists()) {
			return;
		}

		// Clear our previous markers first, so a now-clean file loses its old problems.
		file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);

		final byte[] bytes;
		try {
			bytes = file.getContents().readAllBytes();
		}
		catch (Exception e) {
			// Couldn't read the file — nothing to validate.
			return;
		}

		final ApiextModel model = ApiextModel.parse(bytes);
		if (model == null) {
			createMarker(file, IMarker.SEVERITY_ERROR,
					"This .apiext file could not be parsed (malformed XML, or no <wo> element).");
			return;
		}

		final List<Problem> problems = ApiextConstraintValidator.validate(model);
		for (final Problem p : problems) {
			final int severity = p.getSeverity() == Severity.WARNING ? IMarker.SEVERITY_WARNING : IMarker.SEVERITY_ERROR;
			createMarker(file, severity, p.getMessage());
		}
	}

	private static void createMarker(IFile file, int severity, String message) throws CoreException {
		final IMarker marker = file.createMarker(MARKER_TYPE);
		marker.setAttribute(IMarker.MESSAGE, message);
		marker.setAttribute(IMarker.SEVERITY, Integer.valueOf(severity));
		marker.setAttribute(IMarker.TRANSIENT, false);
	}
}

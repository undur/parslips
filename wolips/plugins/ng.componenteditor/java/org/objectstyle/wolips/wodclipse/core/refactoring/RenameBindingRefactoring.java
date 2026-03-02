package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * LTK {@link Refactoring} wrapper for renaming bindings across template files.
 *
 * <p>Wraps one or more binding renames (old→new name pairs) for a single
 * component, scans the project for references via {@link RenameBindingProcessor},
 * and presents the changes through Eclipse's standard refactoring preview dialog.
 *
 * <p>Used by {@link org.objectstyle.wolips.apieditor.editor.ApiEditor} when
 * saving an {@code .api} file with renamed bindings and the "Refactor on rename"
 * option enabled.
 */
public class RenameBindingRefactoring extends Refactoring {

	private final IProject _project;
	private final String _componentName;
	private final Map<String, String> _renames;
	private CompositeChange _change;

	/**
	 * @param project the project to scan for template references
	 * @param componentName the element type name (e.g. "MyComponent")
	 * @param renames map of old binding name → new binding name
	 */
	public RenameBindingRefactoring(IProject project, String componentName, Map<String, String> renames) {
		_project = project;
		_componentName = componentName;
		_renames = renames;
	}

	@Override
	public String getName() {
		if (_renames.size() == 1) {
			Map.Entry<String, String> entry = _renames.entrySet().iterator().next();
			return "Rename binding '" + entry.getKey() + "' to '" + entry.getValue()
					+ "' in " + _componentName + " templates";
		}
		return "Rename " + _renames.size() + " bindings in " + _componentName + " templates";
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		// All renames are processed in a single pass per file, so we get
		// one CompositeChange with one TextFileChange per affected file.
		// This avoids offset corruption when multiple bindings in the same
		// file are renamed simultaneously.
		_change = RenameBindingProcessor.computeBindingReferenceChanges(
				_project, _componentName, _renames);

		if (_change == null || _change.getChildren().length == 0) {
			_change = null;
			return RefactoringStatus.createInfoStatus(
					"No template references found for the renamed binding(s).");
		}

		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		return _change;
	}
}

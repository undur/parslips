package org.objectstyle.wolips.editor.template;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionDialog;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddActionInfo;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyDialog;
import org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyInfo;

/**
 * Quick-fix proposal that opens the "Add Key" dialog to create a missing
 * binding key on the component's Java class. Shown alongside "Replace with"
 * proposals in the Cmd+1 quick-assist menu for keypath errors.
 */
public class CreateKeyCompletionProposal implements ICompletionProposal {

	private final String _keyName;
	private final IFile _file;
	/** The binding name (e.g. "action"), or null if unknown. */
	private final String _bindingName;

	/**
	 * @param keyName     the missing key name to pre-fill in the dialog
	 * @param file        the template file, used to resolve the component type
	 * @param bindingName the binding name (e.g. "action"), or null if unknown
	 */
	public CreateKeyCompletionProposal(String keyName, IFile file, String bindingName) {
		_keyName = keyName;
		_file = file;
		_bindingName = bindingName;
	}

	/**
	 * Opens the appropriate dialog to create the missing key. For action
	 * bindings (e.g. {@code action="$performSubmit"}), opens the "Add Action"
	 * dialog which generates a {@code WOActionResults} method stub. For all
	 * other bindings, opens the "Add Key" dialog for field/accessor creation.
	 *
	 * <p>The actual Java source edit is performed by the dialog's operation
	 * ({@link org.objectstyle.wolips.wodclipse.core.refactoring.AddActionOperation}
	 * or {@link org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyOperation}),
	 * so we don't modify the template document here.
	 */
	@Override
	public void apply(IDocument document) {
		try {
			WodParserCache parserCache = WodParserCache.parser(_file);
			IType componentType = parserCache.getComponentType();
			if (componentType == null) {
				return;
			}

			boolean isAction = _bindingName != null && ApiUtils.isActionBindingName(_bindingName);

			Display.getDefault().asyncExec(() -> {
				try {
					if (isAction) {
						AddActionInfo actionInfo = new AddActionInfo(componentType);
						actionInfo.setName(_keyName);
						AddActionDialog.open(actionInfo, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
					}
					else {
						AddKeyInfo info = new AddKeyInfo(componentType);
						info.setName(_keyName);
						AddKeyDialog.open(info, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
					}
				}
				catch (Exception ex) {
					// Dialog may fail if the Java model is unavailable
				}
			});
		}
		catch (Exception e) {
			// If we can't resolve the component type, silently do nothing
		}
	}

	@Override
	public String getDisplayString() {
		boolean isAction = _bindingName != null && ApiUtils.isActionBindingName(_bindingName);
		if (isAction) {
			return "Create action '" + _keyName + "'";
		}
		return "Create key '" + _keyName + "'";
	}

	@Override
	public Point getSelection(IDocument document) {
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		boolean isAction = _bindingName != null && ApiUtils.isActionBindingName(_bindingName);
		if (isAction) {
			return "Opens a dialog to create the action method '" + _keyName + "' on the component's Java class.";
		}
		return "Opens a dialog to create the key '" + _keyName + "' on the component's Java class.";
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IContextInformation getContextInformation() {
		return null;
	}
}

package org.objectstyle.wolips.templateeditor;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
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

	/**
	 * @param keyName the missing key name to pre-fill in the dialog
	 * @param file    the template file, used to resolve the component type
	 */
	public CreateKeyCompletionProposal(String keyName, IFile file) {
		_keyName = keyName;
		_file = file;
	}

	/**
	 * Opens the "Add Key" dialog. The actual document edit is performed by
	 * {@link org.objectstyle.wolips.wodclipse.core.refactoring.AddKeyOperation},
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

			AddKeyInfo info = new AddKeyInfo(componentType);
			info.setName(_keyName);

			Display.getDefault().asyncExec(() -> {
				try {
					AddKeyDialog.open(info, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
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
		return "Create key '" + _keyName + "'";
	}

	@Override
	public Point getSelection(IDocument document) {
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
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

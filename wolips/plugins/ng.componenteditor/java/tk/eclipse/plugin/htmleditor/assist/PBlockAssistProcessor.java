package tk.eclipse.plugin.htmleditor.assist;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Content assist for the inside of a {@code <p:comment>} or {@code <p:raw>} block.
 *
 * <p>These two Parsley directives get their own document partition
 * ({@code HTMLPartitionScanner.HTML_P_BLOCK}) because their content is opaque — a
 * {@code p:comment} is ignored entirely and a {@code p:raw} is literal text, so neither
 * contains markup, bindings, or keypaths. The only meaningful completion inside such a
 * block is therefore its <em>matching close tag</em>.
 *
 * <p>This matters in practice when you've just typed an opening {@code <p:comment>} whose
 * close doesn't exist yet: the partitioner greedily pairs that open with the next
 * {@code </p:comment>} further down the file, so the caret lands inside a p-block. The
 * general HTML assist processor, run there, tries to read the block as markup and offers
 * nothing — which looked like "no close-tag completion for {@code p:comment}." Routing the
 * p-block partition to this processor instead makes the obvious completion available:
 * close the block you're in.
 */
public class PBlockAssistProcessor implements IContentAssistProcessor {

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		final IDocument document = viewer.getDocument();
		final String openName = enclosingPBlockName(document, offset);
		if (openName == null) {
			return new ICompletionProposal[0];
		}

		final String closeTag = "</" + openName + ">";

		// If the close tag is already present immediately at the caret (e.g. the user moved
		// back into a balanced block), don't propose a duplicate.
		try {
			final String ahead = document.get(offset, Math.min(closeTag.length(), document.getLength() - offset));
			if (closeTag.equals(ahead)) {
				return new ICompletionProposal[0];
			}
		}
		catch (BadLocationException e) {
			// fall through and propose anyway
		}

		final CompletionProposal proposal = new CompletionProposal(
				closeTag, offset, 0, closeTag.length(),
				HTMLPlugin.getDefault().getImageRegistry().get(HTMLPlugin.ICON_TAG),
				closeTag, null, null);
		return new ICompletionProposal[] { proposal };
	}

	/**
	 * @return the directive name ({@code p:comment} or {@code p:raw}) of the block the caret
	 *         is inside, by scanning back from the caret for the nearest unclosed opener; or
	 *         null if the caret isn't within such a block.
	 */
	private static String enclosingPBlockName(IDocument document, int offset) {
		try {
			final String before = document.get(0, offset);
			final int comment = before.lastIndexOf("<p:comment");
			final int raw = before.lastIndexOf("<p:raw");
			final int openIdx = Math.max(comment, raw);
			if (openIdx == -1) {
				return null;
			}
			final String name = (openIdx == comment) ? "p:comment" : "p:raw";

			// Only inside the block if the opener's own '>' is behind the caret, and the
			// opener hasn't already been closed before the caret.
			final int openEnd = before.indexOf('>', openIdx);
			if (openEnd == -1) {
				return null; // still typing the opening tag itself — not yet inside the block
			}
			if (before.indexOf("</" + name + ">", openEnd) != -1) {
				return null; // this opener is already closed before the caret
			}
			return name;
		}
		catch (BadLocationException e) {
			return null;
		}
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return null;
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}
}

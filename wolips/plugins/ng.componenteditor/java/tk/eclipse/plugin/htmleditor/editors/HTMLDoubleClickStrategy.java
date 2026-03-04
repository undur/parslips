package tk.eclipse.plugin.htmleditor.editors;

import org.eclipse.jface.text.*;

public class HTMLDoubleClickStrategy implements ITextDoubleClickStrategy {
	protected ITextViewer fText;

	public void doubleClicked(ITextViewer part) {
		int pos = part.getSelectedRange().x;

		if (pos < 0)
			return;

		fText = part;

		if (!selectComment(pos)) {
			selectWord(pos);
		}
	}
	/**
	 * Attempts to select a quoted attribute value when the caret is inside
	 * one. Scans backward and forward for the nearest {@code "} characters,
	 * then selects the content between them.
	 *
	 * <p>To avoid false matches when the caret is on an attribute <em>name</em>
	 * (between the closing quote of one value and the opening quote of the
	 * next), the selected text is checked for {@code =} signs — if present,
	 * the match spans across attribute boundaries and is rejected, allowing
	 * {@link #selectWord(int)} to handle the selection instead.
	 */
	protected boolean selectComment(int caretPos) {
		IDocument doc = fText.getDocument();
		int startPos, endPos;

		try {
			int pos = caretPos;
			char c = ' ';

			while (pos >= 0) {
				c = doc.getChar(pos);
				if (c == '\\') {
					pos -= 2;
					continue;
				}
				if (c == Character.LINE_SEPARATOR || c == '\"' || c=='<' || c=='>')
					break;
				--pos;
			}

			if (c != '\"')
				return false;

			startPos = pos;

			pos = caretPos;
			int length = doc.getLength();
			c = ' ';

			while (pos < length) {
				c = doc.getChar(pos);
				if (c == Character.LINE_SEPARATOR || c == '\"' || c=='<' || c=='>')
					break;
				++pos;
			}
			if (c != '\"')
				return false;

			endPos = pos;

			// Verify we're inside a single quoted value, not spanning across
			// attribute boundaries (e.g. between ..."$docs"  item="...).
			// An '=' sign in the selected text means we've crossed from one
			// attribute's value into the next — reject and let selectWord()
			// handle it.
			int offset = startPos + 1;
			int len = endPos - offset;
			String selected = doc.get(offset, len);
			if (selected.indexOf('=') >= 0) {
				return false;
			}

			fText.setSelectedRange(offset, len);
			return true;
		} catch (BadLocationException x) {
		}

		return false;
	}
	protected boolean selectWord(int caretPos) {

		IDocument doc = fText.getDocument();
		int startPos, endPos;

		try {

			int pos = caretPos;
			char c;

			while (pos >= 0) {
				c = doc.getChar(pos);
				if (Character.isWhitespace(c) || c=='<' || c=='>' || c=='=' || c=='/')
					break;
				--pos;
			}

			startPos = pos;

			pos = caretPos;
			int length = doc.getLength();

			while (pos < length) {
				c = doc.getChar(pos);
				if (Character.isWhitespace(c) || c=='<' || c=='>' || c=='=' || c=='/')
					break;
				++pos;
			}

			endPos = pos;
			selectRange(startPos, endPos);
			return true;

		} catch (BadLocationException x) {
		}

		return false;
	}

	private void selectRange(int startPos, int stopPos) {
		int offset = startPos + 1;
		int length = stopPos - offset;
		fText.setSelectedRange(offset, length);
	}
}
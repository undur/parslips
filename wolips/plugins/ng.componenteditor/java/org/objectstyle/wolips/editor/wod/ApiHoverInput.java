package org.objectstyle.wolips.editor.wod;

import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;

/**
 * A concrete {@link BrowserInformationControlInput} carrying the HTML for the template
 * editor's element hover.
 *
 * <p>Why this exists: {@code BrowserInformationControl.setInput} requires its input to be
 * a {@code BrowserInformationControlInput} (it asserts the type). Only with such an input
 * does the control run its content-measuring path — it loads the HTML into the embedded
 * browser, waits for layout, and sizes the popup to the rendered content. Passing a plain
 * {@code String} instead bypasses that and the popup opens at a tiny default size (the bug
 * we hit). So the hover returns one of these via {@code ITextHoverExtension2.getHoverInfo2}.
 *
 * <p>{@code BrowserInformationControlInput} is abstract; this minimal subclass just holds
 * the HTML. {@code getInputElement()}/{@code getInputName()} are used by the control's
 * back/forward navigation, which the element hover doesn't use, so they return the html
 * and a fixed label respectively.
 */
public class ApiHoverInput extends BrowserInformationControlInput {

	private final String _html;

	public ApiHoverInput(String html) {
		super(null); // no previous input — the hover has no navigation history
		_html = html;
	}

	@Override
	public String getHtml() {
		return _html;
	}

	@Override
	public Object getInputElement() {
		return _html;
	}

	@Override
	public String getInputName() {
		return "";
	}
}

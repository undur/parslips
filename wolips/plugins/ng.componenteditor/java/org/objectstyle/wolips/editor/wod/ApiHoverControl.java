package org.objectstyle.wolips.editor.wod;

import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

/**
 * The element-hover popup for the template editor: an HTML view that renders the
 * rich {@code .apiext} card (and the plain {@code .api} preview / validation errors).
 *
 * <p>This hosts our own SWT {@link Browser} inside an {@link AbstractInformationControl}
 * rather than reusing JFace's {@code BrowserInformationControl}. That control is built
 * for Javadoc tooltips: it disables JavaScript and is hardwired to <em>size itself to
 * its content</em>, measuring the content by laying the HTML out as plain text. For our
 * cards that's doubly wrong — the plain-text measure collapses tables to a few lines, and
 * there is no way to make it clip-and-scroll. Hosting a plain {@code Browser} in a
 * fixed-size shell gives us what we actually want: a consistent popup size with the
 * browser scrolling natively when a card is taller than the popup.
 *
 * <p>{@link AbstractInformationControl} supplies the rest — the shell, focus handling,
 * resize, dispose, the status-line affordance, and the hover→sticky→focus lifecycle — so
 * this class only wires up the browser, the input, and a fixed size hint.
 */
public class ApiHoverControl extends AbstractInformationControl implements IInformationControlExtension2 {

	/** The popup's fixed size. Tall cards scroll within it; the resizable variant can grow. */
	static final int WIDTH = 670;
	static final int HEIGHT = 560;

	private Browser _browser;
	private String _html;

	/**
	 * Whether this control enforces the fixed open size. True for the transient hover (so it
	 * opens roomy instead of at the content-measured height the framework would impose); false
	 * for the resizable/sticky variant, which must honour the user's own resizes.
	 */
	private final boolean _enforceOpenSize;

	/**
	 * Enriched/sticky variant: resizable popup the user can grow, scroll and select from.
	 * Used by {@link #getInformationPresenterControlCreator()} when the hover is made sticky.
	 *
	 * @param parent the parent shell
	 */
	public ApiHoverControl(Shell parent) {
		super(parent, true); // (Shell, boolean) → resizable
		_enforceOpenSize = false;
		create();
	}

	/**
	 * Transient variant: shown on mouse-over with the focus affordance ("Press F2…") in the
	 * status line, which is also what makes the control focusable so it can be made sticky.
	 *
	 * @param parent     the parent shell
	 * @param affordance the status-line affordance text
	 */
	public ApiHoverControl(Shell parent, String affordance) {
		super(parent, affordance); // (Shell, String) → transient with status line
		_enforceOpenSize = true;
		create();
	}

	@Override
	protected void createContent(Composite parent) {
		parent.setLayout(new FillLayout());
		_browser = new Browser(parent, SWT.NONE);
		_browser.setJavascriptEnabled(false); // we don't need JS; matches the trust posture of a tooltip
		if (_html != null) {
			_browser.setText(_html);
		}
	}

	@Override
	public void setInformation(String content) {
		// Superseded by setInput (IInformationControlExtension2); kept for the interface.
		_html = content;
		if (_browser != null && !_browser.isDisposed()) {
			_browser.setText(content == null ? "" : content);
		}
	}

	@Override
	public void setInput(Object input) {
		setInformation(input == null ? null : input.toString());
	}

	@Override
	public boolean hasContents() {
		return _html != null && !_html.isEmpty();
	}

	@Override
	public Point computeSizeHint() {
		// Fixed size on purpose — content height can't be measured reliably up front, and a
		// consistent popup that scrolls beats one that resizes per element.
		return new Point(WIDTH, HEIGHT);
	}

	@Override
	public void setSize(int width, int height) {
		// For the transient hover the framework calls this with a content-measured height,
		// not the height we return from computeSizeHint (which it treats as advisory). That
		// measure lays the HTML out as plain text, so our <table> collapses to a few lines
		// and the popup opens far too short. Enforce our intended open size so the card opens
		// roomy; the browser scrolls anything taller. The resizable (sticky) variant does NOT
		// enforce, so the user can freely grow/shrink it once it's made sticky.
		if (_enforceOpenSize) {
			super.setSize(Math.max(width, WIDTH), Math.max(height, HEIGHT));
		}
		else {
			super.setSize(width, height);
		}
	}

	@Override
	public boolean isFocusControl() {
		return _browser != null && !_browser.isDisposed() && _browser.isFocusControl();
	}

	@Override
	public void setFocus() {
		if (_browser != null && !_browser.isDisposed()) {
			_browser.setFocus();
		}
	}

	/**
	 * The control used when the hover is "made sticky" (moused into / focused). Returns a
	 * resizable variant of this control, so the user can grow it and scroll/select freely.
	 */
	@Override
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(Shell parent) {
				return new ApiHoverControl(parent);
			}
		};
	}
}

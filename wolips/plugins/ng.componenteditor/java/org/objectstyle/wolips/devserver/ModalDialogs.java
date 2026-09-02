package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

/**
 * Sees — and answers — Eclipse's modal dialogs on behalf of an external tool.
 *
 * <p>A modal dialog ("Errors in Workspace — Proceed with launch?", "Hot code replace
 * failed", "Confirm perspective switch"…) stops the world for a human: nothing else in
 * the workbench responds until it is answered. For an agent driving Eclipse over the dev
 * server it is worse — the agent can't see it at all. Its request hangs or its wait times
 * out, and it starts "fixing" a problem that is actually a dialog waiting for a click.
 * This class makes those dialogs observable ({@link #list}) and answerable
 * ({@link #press}, {@link #close}), for {@code /dialogs}, {@code /status}, and the
 * {@code /launch} wait loop.
 *
 * <p>Everything here runs on the SWT thread. A modal dialog runs its own event loop, so
 * {@code asyncExec} runnables still execute while one is open — but a UI thread that is
 * genuinely wedged (a long operation, a deadlock) would hang a {@code syncExec} forever,
 * so all UI work here is bounded by {@link #UI_TIMEOUT_MILLIS} and reports the UI thread
 * as unresponsive instead of taking the request down with it.
 */
final class ModalDialogs {

	/** How long to wait for the UI thread before reporting it unresponsive. */
	static final long UI_TIMEOUT_MILLIS = 3000;

	/** One open modal dialog, reduced to what a caller needs to understand and answer it. */
	static final class Dialog {
		final String title;
		final String message; // the dialog's label/link texts, newline-joined
		final List<String> buttons; // push-button labels, mnemonics stripped

		Dialog(String title, String message, List<String> buttons) {
			this.title = title;
			this.message = message;
			this.buttons = buttons;
		}

		String toJson() {
			return "{\"title\":\"" + DevServerJson.escape(title)
					+ "\",\"message\":\"" + DevServerJson.escape(message)
					+ "\",\"buttons\":" + DevServerJson.stringArray(buttons) + "}";
		}
	}

	/** The outcome of a UI-thread query: the dialogs, or the fact that the UI didn't answer. */
	static final class Snapshot {
		final boolean uiResponsive;
		final List<Dialog> dialogs;

		Snapshot(boolean uiResponsive, List<Dialog> dialogs) {
			this.uiResponsive = uiResponsive;
			this.dialogs = dialogs;
		}

		String dialogsJson() {
			final StringBuilder b = new StringBuilder("[");
			for (int i = 0; i < dialogs.size(); i++) {
				if (i > 0) {
					b.append(',');
				}
				b.append(dialogs.get(i).toJson());
			}
			return b.append(']').toString();
		}
	}

	private ModalDialogs() {
	}

	/** The currently open modal dialogs (empty when none — or when the UI thread didn't answer in time). */
	static Snapshot list() {
		final List<Dialog> dialogs = new ArrayList<>();
		final boolean responsive = onUiThread(() -> {
			for (final Shell shell : modalShells()) {
				dialogs.add(describe(shell));
			}
		});
		return new Snapshot(responsive, dialogs);
	}

	/**
	 * Presses the named button on the topmost modal dialog (or the one whose title contains
	 * {@code titleFilter}, when given). Matching ignores case, mnemonic ampersands and a
	 * trailing ellipsis, so {@code press=proceed} hits "&Proceed". Returns a JSON outcome.
	 */
	static String press(String buttonText, String titleFilter) {
		final String[] outcome = new String[1];
		final boolean responsive = onUiThread(() -> {
			final Shell shell = target(titleFilter);
			if (shell == null) {
				outcome[0] = "{\"pressed\":false,\"reason\":\"no open modal dialog" + (titleFilter == null ? "" : " matching the title") + "\"}";
				return;
			}
			final Dialog dialog = describe(shell);
			final Button button = findButton(shell, buttonText);
			if (button == null) {
				outcome[0] = "{\"pressed\":false,\"reason\":\"no such button\",\"dialog\":" + dialog.toJson() + "}";
				return;
			}
			// notifyListeners drives the button's SelectionListeners exactly as a click
			// would — JFace dialogs (MessageDialog & co.) close and return through those.
			button.setFocus();
			button.notifyListeners(SWT.Selection, new Event());
			outcome[0] = "{\"pressed\":true,\"button\":\"" + DevServerJson.escape(strip(button.getText()))
					+ "\",\"dialog\":\"" + DevServerJson.escape(dialog.title) + "\"}";
		});
		return responsive ? outcome[0] : unresponsiveJson();
	}

	/** Closes the topmost modal dialog (or the title-matched one) — the Escape/window-close path. */
	static String close(String titleFilter) {
		final String[] outcome = new String[1];
		final boolean responsive = onUiThread(() -> {
			final Shell shell = target(titleFilter);
			if (shell == null) {
				outcome[0] = "{\"closed\":false,\"reason\":\"no open modal dialog" + (titleFilter == null ? "" : " matching the title") + "\"}";
				return;
			}
			final String title = shell.getText();
			shell.close();
			outcome[0] = "{\"closed\":true,\"dialog\":\"" + DevServerJson.escape(title) + "\"}";
		});
		return responsive ? outcome[0] : unresponsiveJson();
	}

	static String unresponsiveJson() {
		return "{\"uiResponsive\":false,\"reason\":\"the Eclipse UI thread did not answer within " + UI_TIMEOUT_MILLIS + "ms\"}";
	}

	// ---- UI-thread internals ----

	/**
	 * Runs the work on the SWT thread, bounded by {@link #UI_TIMEOUT_MILLIS}. Returns false
	 * (without running, or without waiting for the run to finish) when the UI thread
	 * didn't get to it in time. Callers must only read their results when this is true.
	 */
	private static boolean onUiThread(Runnable work) {
		final Display display = Display.getDefault();
		if (display.isDisposed()) {
			return false;
		}
		final CountDownLatch done = new CountDownLatch(1);
		display.asyncExec(() -> {
			try {
				work.run();
			}
			finally {
				done.countDown();
			}
		});
		try {
			return done.await(UI_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** Visible modal shells, most recently opened last (SWT keeps shells in creation order). */
	private static List<Shell> modalShells() {
		final List<Shell> result = new ArrayList<>();
		for (final Shell shell : Display.getDefault().getShells()) {
			if (shell.isDisposed() || !shell.isVisible()) {
				continue;
			}
			if ((shell.getStyle() & (SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL)) != 0) {
				result.add(shell);
			}
		}
		return result;
	}

	/** The newest modal shell, or the newest whose title contains the filter (case-insensitive). */
	private static Shell target(String titleFilter) {
		final List<Shell> shells = modalShells();
		for (int i = shells.size() - 1; i >= 0; i--) {
			final Shell shell = shells.get(i);
			if (titleFilter == null || titleFilter.isEmpty()
					|| shell.getText().toLowerCase().contains(titleFilter.toLowerCase())) {
				return shell;
			}
		}
		return null;
	}

	private static Dialog describe(Shell shell) {
		final List<String> texts = new ArrayList<>();
		final List<String> buttons = new ArrayList<>();
		collect(shell, texts, buttons);
		return new Dialog(shell.getText(), String.join("\n", texts), buttons);
	}

	/**
	 * Walks the widget tree collecting the human-readable parts: label/link texts (the
	 * message) and push buttons (the answers). Check boxes such as "Always launch without
	 * asking" are reported as buttons too, prefixed so they're not mistaken for answers.
	 */
	private static void collect(Composite parent, List<String> texts, List<String> buttons) {
		for (final Control control : parent.getChildren()) {
			if (control instanceof Label) {
				addText(((Label) control).getText(), texts);
			}
			else if (control instanceof CLabel) {
				addText(((CLabel) control).getText(), texts);
			}
			else if (control instanceof Link) {
				addText(((Link) control).getText(), texts);
			}
			else if (control instanceof Button) {
				final Button button = (Button) control;
				final String label = strip(button.getText());
				if (label.isEmpty()) {
					continue;
				}
				if ((button.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
					buttons.add("[" + (button.getSelection() ? "x" : " ") + "] " + label);
				}
				else {
					buttons.add(label);
				}
			}
			if (control instanceof Composite) {
				collect((Composite) control, texts, buttons);
			}
		}
	}

	private static void addText(String text, List<String> texts) {
		final String cleaned = text == null ? "" : text.strip();
		if (!cleaned.isEmpty()) {
			texts.add(cleaned);
		}
	}

	private static Button findButton(Composite parent, String wanted) {
		for (final Control control : parent.getChildren()) {
			if (control instanceof Button && (control.getStyle() & (SWT.CHECK | SWT.RADIO)) == 0
					&& matches(((Button) control).getText(), wanted)) {
				return (Button) control;
			}
			if (control instanceof Composite) {
				final Button found = findButton((Composite) control, wanted);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	// ---- pure helpers (unit-tested) ----

	/** Strips SWT mnemonic ampersands ("&Proceed" → "Proceed") and surrounding whitespace. */
	static String strip(String buttonText) {
		if (buttonText == null) {
			return "";
		}
		return buttonText.replace("&&", "\u0001").replace("&", "").replace("\u0001", "&").strip();
	}

	/** Whether a button label answers to the wanted name: case-insensitive, mnemonic- and ellipsis-insensitive. */
	static boolean matches(String actualLabel, String wanted) {
		if (wanted == null) {
			return false;
		}
		return normalize(actualLabel).equals(normalize(wanted));
	}

	private static String normalize(String label) {
		String s = strip(label).toLowerCase();
		while (s.endsWith("...") || s.endsWith("…")) {
			s = s.endsWith("...") ? s.substring(0, s.length() - 3) : s.substring(0, s.length() - 1);
			s = s.strip();
		}
		return s;
	}
}

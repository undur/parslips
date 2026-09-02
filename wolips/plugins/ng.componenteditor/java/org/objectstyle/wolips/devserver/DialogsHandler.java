package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * {@code /dialogs} — see and answer Eclipse's modal dialogs, for callers who can't.
 *
 * <ul>
 * <li>No parameters — list the open modal dialogs: title, message text, and buttons.</li>
 * <li>{@code press=BUTTON} — press that button (case-insensitive; "&" mnemonics and a
 * trailing "..." ignored) on the topmost modal dialog.</li>
 * <li>{@code close=true} — close the topmost modal dialog (the window-close/Escape path).</li>
 * <li>{@code title=TEXT} — with {@code press} or {@code close}: target the dialog whose
 * title contains this text instead of the topmost one.</li>
 * </ul>
 *
 * <p>Dev-server launches never raise Eclipse's own launch prompts (see {@link LaunchHandler}),
 * so this exists for everything else that can stop the world — hot-code-replace failures,
 * perspective-switch prompts, error dialogs from other tooling — and for the cases where
 * the developer left a dialog open. {@code /status} reports the same list, and a launch
 * wait reports a dialog that appears mid-wait, so a caller learns about a blocking dialog
 * from the request that was blocked by it.
 */
class DialogsHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String title = params.get("title");
		final String press = params.get("press");
		if (press != null && !press.isEmpty()) {
			return ModalDialogs.press(press, title);
		}
		if ("true".equalsIgnoreCase(params.get("close"))) {
			return ModalDialogs.close(title);
		}
		final ModalDialogs.Snapshot snapshot = ModalDialogs.list();
		return "{\"uiResponsive\":" + snapshot.uiResponsive + ",\"dialogs\":" + snapshot.dialogsJson() + "}";
	}
}

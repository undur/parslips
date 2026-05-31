package org.objectstyle.wolips.editor.menu;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.handlers.IHandlerService;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Delegates command execution to WOLips' equivalent command handler.
 *
 * <p>When Parsley's keybinding shadows are active, Parsley's command handlers
 * receive all keyboard shortcut invocations — even for non-Parsley projects.
 * For those projects, we delegate to the corresponding WOLips command so
 * WOLips behavior is preserved.
 *
 * <p>This works because the keybinding shadows only affect the key-to-command
 * mapping, not the command handlers themselves. WOLips' commands and handlers
 * are still registered and can be invoked programmatically.
 *
 * <p>WORKAROUND: WOLips coexistence.
 */
public class WOLipsCommandDelegate {

	/**
	 * Executes a WOLips command by ID, using the handler service from the
	 * current workbench window.
	 *
	 * @param wolipsCommandId  the WOLips command ID to execute
	 * @param event            the original execution event (used to obtain
	 *                         the workbench window)
	 * @return the result of the WOLips command execution, or {@code null}
	 *         if the command could not be executed
	 */
	public static Object execute(String wolipsCommandId, ExecutionEvent event) {
		try {
			IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
			IHandlerService handlerService = window.getService(IHandlerService.class);
			if (handlerService != null) {
				return handlerService.executeCommand(wolipsCommandId, null);
			}
		}
		catch (Exception e) {
			// WOLips command may not be available (e.g. WOLips not installed,
			// or command not defined). Fail silently — the user just sees no
			// action, which is the expected fallback.
			HTMLPlugin.logException(e);
		}
		return null;
	}
}

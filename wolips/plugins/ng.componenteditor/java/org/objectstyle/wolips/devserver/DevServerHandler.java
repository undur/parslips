package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * Handles one kind of dev-server request. Implementations receive the parsed
 * query parameters and perform their Eclipse-side action (open an editor,
 * refresh a resource). A plain "ok" 200 response is sent by the framework on
 * normal return; an exception becomes a 500.
 *
 * <p>Implementations typically dispatch the actual UI work onto the SWT thread
 * via {@code Display.getDefault().asyncExec(...)}, since they run on a server
 * request thread.
 */
interface DevServerHandler {

	/**
	 * @param params decoded query parameters (a {@code pw} entry from legacy
	 *               clients, if present, is ignored — there is no password)
	 * @throws Exception any failure; the server logs it and responds with 500
	 */
	void handle(Map<String, String> params) throws Exception;
}

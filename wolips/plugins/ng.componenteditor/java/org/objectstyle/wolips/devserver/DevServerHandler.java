package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * Handles one kind of dev-server request. Implementations receive the parsed
 * query parameters and perform their Eclipse-side action (open an editor,
 * refresh a resource, validate a template). An exception becomes a 500.
 *
 * <p>Implementations typically dispatch the actual UI work onto the SWT thread
 * via {@code Display.getDefault().asyncExec(...)}, since they run on a server
 * request thread.
 */
interface DevServerHandler {

	/**
	 * @param params decoded query parameters (a {@code pw} entry from legacy
	 *               clients, if present, is ignored — there is no password)
	 * @return the response body to send (e.g. a JSON document for handlers that
	 *         report results), or {@code null} for fire-and-forget handlers whose
	 *         success is conveyed by a plain {@code "ok"} 200
	 * @throws Exception any failure; the server logs it and responds with 500
	 */
	String handle(Map<String, String> params) throws Exception;
}

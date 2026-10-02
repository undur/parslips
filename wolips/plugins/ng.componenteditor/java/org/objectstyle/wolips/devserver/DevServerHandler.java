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
 *
 * <h2>Conventions every endpoint follows</h2>
 * The surface is read by agents that land cold, so it must be predictable: one way to say
 * each thing, everywhere. (Issue #6 has the background; a new endpoint conforms to these.)
 * <ul>
 *   <li><b>Parameters.</b> {@code app} (or {@code config}) names a launch: a launch config,
 *       or a project with one. {@code project} names a workspace project. {@code name} names
 *       something being created or registered. {@code component}, {@code element},
 *       {@code path} and {@code className} are what they say. Booleans are the literals
 *       {@code true}/{@code false}; anything else for a flag that acts is an {@code error},
 *       never silently one of the two.</li>
 *   <li><b>{@code error} — the call is wrong.</b> A missing or invalid parameter:
 *       {@code {"error":"missing required parameter 'component'"}}. The caller fixes the
 *       call; retrying it as is can't help. Always names the parameter.</li>
 *   <li><b>{@code reason} — the call was fine, it didn't happen.</b> The workspace said no
 *       (unknown or closed project, port held, compile errors, nothing to open). The
 *       response keeps its usual shape with the outcome at its empty value
 *       ({@code "launched":false}, {@code "opened":[]}) and adds {@code reason}, a sentence
 *       for the caller to act on. <em>A response with {@code reason} means it did not
 *       happen</em> — that is the one test a client needs.</li>
 *   <li><b>{@code hint} — the next call.</b> When a refusal has an obvious remedy, the
 *       call that applies it, ready to run ({@code "/openProject?project=X"}).</li>
 *   <li><b>Never a blind "ok".</b> The plain {@code ok} (a {@code null} return) means the
 *       action happened. A handler that couldn't act says so with {@code error} or
 *       {@code reason} — an {@code ok} for nothing done reads as success and sends the
 *       caller looking for why the change "didn't take".</li>
 *   <li><b>Status codes.</b> Both {@code error} and {@code reason} answer 200: clients read
 *       the body, and {@code curl -f} would throw the reason away with a 4xx. 500 is
 *       reserved for a handler that threw — a dev-server bug — answered as
 *       {@code {"error":"internal error: …","internal":true}}.</li>
 *   <li><b>Bodies.</b> JSON (an object) for anything structured; the content type is inferred
 *       from the body ({@code application/json} for {@code {}}/{@code []}). Plain text is for
 *       {@code ok} and for {@code /console}'s log text; HTML only for {@code /watch}.</li>
 *   <li><b>Discovery.</b> Every endpoint, with its parameters, is in {@link IndexHandler}'s
 *       index (and the skill's endpoint reference). An endpoint missing there doesn't exist
 *       as far as an agent is concerned.</li>
 * </ul>
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

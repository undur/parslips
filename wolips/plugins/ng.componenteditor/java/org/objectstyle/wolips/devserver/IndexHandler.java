package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * The dev server's self-description: {@code /} (and {@code /help}) returns a JSON index of
 * every endpoint with its parameters and semantics. This makes the API discoverable by an
 * agent (or a developer with curl) that lands in a session cold, instead of requiring
 * out-of-band documentation that drifts.
 *
 * <p>Because the JDK HTTP server routes any otherwise-unmatched path to the {@code /}
 * context, this handler doubles as the 404: unknown paths get the index too, so a typo'd
 * endpoint name answers with the list of real ones.
 */
class IndexHandler implements DevServerHandler {

	// Hand-maintained alongside DevServer.start()'s context registrations. A mismatch is
	// caught the first time anyone reads this index, which is exactly its job.
	private static final String INDEX = """
			{
			  "server": "Parslips dev server",
			  "endpoints": [
			    {"path": "/status", "params": "app?", "description": "What is running: per launch config — running/mode/uptime, project open state, compile errors, registered port/pid + reachability. Omit app for all."},
			    {"path": "/launch", "params": "config|app, mode=debug|run, open=true?, ignoreErrors=true?, allowMultiple=true?, waitForPort?, timeout?", "description": "Launch a config (preferring local/dev configs on project-name queries). Refuses with a reason when the config is unknown, the project is closed (open=true overrides), the project OR ANY PROJECT IT DEPENDS ON has compile errors after an incremental build (ignoreErrors=true overrides; the refusal names the broken projects and the clean-rebuild call that usually fixes them) or it is already running (allowMultiple=true overrides). Never raises Eclipse's launch prompts (errors-in-workspace, save, switch-to-debug) - every decision is made here and reported as data. With waitForPort, blocks until the app answers, dies, a modal dialog appears (reported, see /dialogs), or timeout (default 60s). Omit config to list all configs."},
			    {"path": "/dialogs", "params": "press=BUTTON?, close=true?, title?", "description": "See and answer Eclipse's modal dialogs - the stop-the-world prompts an external caller cannot see. No params: list them (title, message, buttons). press=BUTTON presses that button on the topmost dialog (case-insensitive, mnemonics ignored); close=true closes it; title=TEXT targets the dialog whose title contains TEXT. /status reports the same list."},
			    {"path": "/stop", "params": "app|config, force=true?", "description": "Terminate the matching Eclipse launch (clean), falling back to SIGTERM of the registered pid; force=true goes straight to kill -9."},
			    {"path": "/restart", "params": "app|config, refresh=proj1,proj2?, + all /launch params", "description": "stop -> wait for termination -> refresh+rebuild the named projects -> launch. One call for the whole edit-see-it cycle; reports each stage."},
			    {"path": "/refreshProject", "params": "project?, build=false?, clean=true?", "description": "Refresh a project (or all) from disk and incrementally rebuild so hot-code-replace picks the change up. Returns ok when the build settled clean; returns a JSON build report when it produced compile errors."},
			    {"path": "/problems", "params": "project?, severity=error|warning, limit?", "description": "Problem markers (the Problems view) as JSON. Default: errors only, all open projects with errors."},
			    {"path": "/console", "params": "app|config, tail?", "description": "The app's console output (tail, default 100 lines) — including after the process died, which is when you need it: startup failures are only visible here."},
			    {"path": "/breakpoints", "params": "skipAll=true|false?", "description": "List workspace breakpoints and the Skip All Breakpoints state; skipAll toggles the master switch (non-destructive)."},
			    {"path": "/openProject", "params": "project|all, related=false?", "description": "Open a closed workspace project together with its workspace dependencies (transitive, pom-resolved) - dependency resolution only sees open projects, so opening just one is rarely enough."},
			    {"path": "/validate", "params": "component", "description": "Validate a component template and report template problems."},
			    {"path": "/revalidate", "params": "project?", "description": "Revalidate EVERY component template in a project (or, with no project, the whole workspace), replacing all template problem markers with freshly computed ones. The bulk cure for stale/phantom markers (validation is per-file and event-driven, so a Java clean/rebuild never refreshes them). Synchronous and slow for big workspaces - use a generous client timeout."},
			    {"path": "/purgeMarkers", "params": "project?", "description": "Bring out your dead: delete orphaned exact-stock-type PROBLEM markers on js/css/html/xml files - the permanently-stale leavings of the removed legacy validators. Typed markers (template validation, JDT, WTP) are never touched. Omit project for the whole workspace."},
			    {"path": "/elementApi", "params": "element (name or comma-separated list), project?, raw=true?", "description": "The resolved binding API of one or more elements, in a project's context, as JSON: bindings with pull/push directions+types, required/default/deprecation, cross-binding constraints with their generated messages, and content/unknownAttributes policies. Names resolve through the project's tag aliases (str -> WOString -> ERXWOString). raw=true returns the canonical .apiext XML instead. This is the editor hover, as data."},
			    {"path": "/activity", "params": "since?, clear=true?", "description": "The dev server's request feed as JSON: every handled request with path, query, status, duration and (capped) response body, newest last. since=SEQ returns only entries after that sequence number - the poll cursor. clear=true empties the buffer. Requests to /activity and /watch themselves are never in the feed."},
			    {"path": "/watch", "params": "", "description": "A live spectator page (HTML): polls /activity and renders the feed as narrated human-readable activity with a running tally. Open it in a browser to watch an agent work the workspace."},
			    {"path": "/apps", "params": "", "description": "Apps that self-registered (name, port, pid), liveness-checked."},
			    {"path": "/registerApp", "params": "name, port, pid?, runtime?", "description": "Called by running apps to announce themselves. runtime is ng or wo, echoed by /apps and /status so a tool picks the right endpoint URL form."},
			    {"path": "/refresh", "params": "path", "description": "Refresh a single workspace path from disk."},
			    {"path": "/openJavaFile", "params": "className, lineNumber?", "description": "Open a Java source file in Eclipse (exception-page links)."},
			    {"path": "/openComponent", "params": "component", "description": "Open a component in the editor."}
			  ]
			}""";

	@Override
	public String handle(Map<String, String> params) {
		return INDEX;
	}
}
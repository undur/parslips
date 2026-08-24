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
			    {"path": "/launch", "params": "config|app, mode=debug|run, open=true?, ignoreErrors=true?, allowMultiple=true?, waitForPort?, timeout?", "description": "Launch a config (preferring local/dev configs on project-name queries). Refuses with a reason when the config is unknown, the project is closed (open=true overrides), the project has compile errors (ignoreErrors=true overrides) or it is already running (allowMultiple=true overrides). With waitForPort, blocks until the app answers, dies, or timeout (default 60s). Omit config to list all configs."},
			    {"path": "/stop", "params": "app|config, force=true?", "description": "Terminate the matching Eclipse launch (clean), falling back to SIGTERM of the registered pid; force=true goes straight to kill -9."},
			    {"path": "/restart", "params": "app|config, refresh=proj1,proj2?, + all /launch params", "description": "stop -> wait for termination -> refresh+rebuild the named projects -> launch. One call for the whole edit-see-it cycle; reports each stage."},
			    {"path": "/refreshProject", "params": "project?, build=false?, clean=true?", "description": "Refresh a project (or all) from disk and incrementally rebuild so hot-code-replace picks the change up. Returns ok when the build settled clean; returns a JSON build report when it produced compile errors."},
			    {"path": "/problems", "params": "project?, severity=error|warning, limit?", "description": "Problem markers (the Problems view) as JSON. Default: errors only, all open projects with errors."},
			    {"path": "/console", "params": "app|config, tail?", "description": "The app's console output (tail, default 100 lines) — including after the process died, which is when you need it: startup failures are only visible here."},
			    {"path": "/breakpoints", "params": "skipAll=true|false?", "description": "List workspace breakpoints and the Skip All Breakpoints state; skipAll toggles the master switch (non-destructive)."},
			    {"path": "/openProject", "params": "project|all, related=false?", "description": "Open a closed workspace project together with its workspace dependencies (transitive, pom-resolved) - dependency resolution only sees open projects, so opening just one is rarely enough."},
			    {"path": "/validate", "params": "component", "description": "Validate a component template and report template problems."},
			    {"path": "/elementApi", "params": "element (name or comma-separated list), project?, raw=true?", "description": "The resolved binding API of one or more elements, in a project's context, as JSON: bindings with pull/push directions+types, required/default/deprecation, cross-binding constraints with their generated messages, and content/unknownAttributes policies. Names resolve through the project's tag aliases (str -> WOString -> ERXWOString). raw=true returns the canonical .apiext XML instead. This is the editor hover, as data."},
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
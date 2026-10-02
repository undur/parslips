package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;

/**
 * {@code /threads} — the app threads the Eclipse debugger has SUSPENDED, and resuming them.
 *
 * <p>Apps launched from Eclipse run in debug mode, and the debugger suspends a thread — with
 * nothing on the outside to say so — when it hits a breakpoint, or (by default, "Suspend
 * execution on uncaught exceptions" and "… on compilation errors") an uncaught exception or code
 * that didn't compile: a refresh
 * that compiled with errors still produces a class, hot-swaps it into the running app, and the
 * first request reaching the broken method stops dead. To an agent that is a request that never
 * answers. The developer sees Eclipse jump to its thread view.
 *
 * <ul>
 *   <li>No parameters: every suspended thread, per app, with why (breakpoint, exception, a
 *       compile error) and where (class, method, line).</li>
 *   <li>{@code resume=all}, or {@code resume=APP} — resume them. A thread stopped on a compile
 *       error stops again the next time it reaches the broken code: fix and refresh first.</li>
 * </ul>
 *
 * {@code /status} carries the same list per app, so a hang is visible from the call an agent
 * makes first.
 */
class ThreadsHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String resume = params.get("resume");
		if (resume != null && resume.isEmpty()) {
			return JsonObject.error("resume takes 'all' or an app name");
		}
		final List<JsonObject> suspended = new ArrayList<>();
		final List<JsonObject> resumed = new ArrayList<>();
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			final String app = launch.getLaunchConfiguration() == null ? null : launch.getLaunchConfiguration().getName();
			final boolean resumeThis = resume != null && ("all".equals(resume) || resume.equalsIgnoreCase(app));
			for (final IDebugTarget target : launch.getDebugTargets()) {
				if (target.isTerminated() || target.isDisconnected()) {
					continue;
				}
				for (final IThread thread : target.getThreads()) {
					if (!thread.isSuspended()) {
						continue;
					}
					final JsonObject json = describe(app, thread);
					if (resumeThis && thread.canResume()) {
						thread.resume();
						resumed.add(json);
					}
					else {
						suspended.add(json);
					}
				}
			}
		}
		final JsonObject response = new JsonObject();
		if (resume != null) {
			response.put("resumed", resumed);
			if (resumed.isEmpty()) {
				response.put("reason", "all".equals(resume) ? "no suspended threads" : "no suspended threads in an app named '" + resume + "'");
			}
		}
		response.put("suspended", suspended);
		final List<JsonObject> autoResumed = AutoResume.recent();
		if (!autoResumed.isEmpty()) {
			// Compile-error suspensions in dev-server launches, resumed at once so the request
			// failed fast (see AutoResume) — the why behind a recent 500.
			response.put("autoResumed", autoResumed);
		}
		if (!suspended.isEmpty() && resume == null) {
			response.put("hint", "/threads?resume=all - but a thread stopped on a compile error stops again: fix the error and refresh first");
		}
		return response.toString();
	}

	/** The suspended threads of one launch config (for /status), as JSON objects. */
	static List<JsonObject> suspendedIn(String configName) {
		final List<JsonObject> suspended = new ArrayList<>();
		for (final ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
			if (launch.getLaunchConfiguration() == null || !launch.getLaunchConfiguration().getName().equals(configName)) {
				continue;
			}
			for (final IDebugTarget target : launch.getDebugTargets()) {
				try {
					if (target.isTerminated()) {
						continue;
					}
					for (final IThread thread : target.getThreads()) {
						if (thread.isSuspended()) {
							suspended.add(describe(configName, thread));
						}
					}
				}
				catch (final DebugException e) {
					// The target went away while we looked.
				}
			}
		}
		return suspended;
	}

	/**
	 * A suspended thread: its name, why it stopped, and where. "Why" comes from the breakpoint
	 * that suspended it: a line breakpoint, an exception breakpoint (by exception type), or
	 * JDT's own compile-error suspension (an exception breakpoint on java.lang.Error with no
	 * marker of the developer's), reported as {@code compile error}.
	 */
	static JsonObject describe(String app, IThread thread) throws DebugException {
		final JsonObject json = new JsonObject().put("app", app).put("thread", thread.getName()).put("reason", reasonFor(thread));
		final IStackFrame frame = thread.getTopStackFrame();
		if (frame instanceof IJavaStackFrame javaFrame) {
			json.put("at", new JsonObject()
					.put("class", javaFrame.getDeclaringTypeName())
					.put("method", javaFrame.getMethodName())
					.put("line", javaFrame.getLineNumber()));
		}
		return json;
	}

	/** Whether the thread was suspended by one of JDT's hidden preference breakpoints, not one the developer set. */
	static boolean suspendedByHiddenBreakpoint(IThread thread) {
		final String reason = reasonFor(thread);
		return "compile error".equals(reason) || "uncaught exception".equals(reason);
	}

	private static boolean isHidden(IBreakpoint breakpoint) {
		return breakpoint.getMarker() == null || DebugPlugin.getDefault().getBreakpointManager().getBreakpoint(breakpoint.getMarker()) == null;
	}

	static String reasonFor(IThread thread) {
		for (final IBreakpoint breakpoint : thread.getBreakpoints()) {
			try {
				if (breakpoint instanceof IJavaExceptionBreakpoint exception) {
					final String type = exception.getTypeName();
					// JDT implements two debug preferences as HIDDEN exception breakpoints,
					// never registered with the breakpoint manager: "Suspend execution on
					// compilation errors" (on java.lang.Error) and "Suspend execution on
					// uncaught exceptions" (on Throwable, uncaught). A breakpoint the developer
					// set is registered; these are not.
					if (isHidden(breakpoint)) {
						return "java.lang.Error".equals(type) ? "compile error" : "uncaught exception";
					}
					return "exception " + type;
				}
				if (breakpoint instanceof IJavaLineBreakpoint line) {
					return "breakpoint at line " + line.getLineNumber();
				}
			}
			catch (final Exception e) {
				// Fall through to the generic answer.
			}
		}
		return thread.getBreakpoints().length > 0 ? "breakpoint" : "suspended";
	}
}

package org.objectstyle.wolips.devserver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IThread;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Keeps a compile error from hanging an app the dev server launched.
 *
 * <p>A refresh that compiles with errors still produces classes, and a running debug app gets
 * them hot-swapped in. By default ("Suspend execution on compilation errors") the debugger then
 * suspends the first thread that reaches an error — the request never answers, the caller
 * waits until its own timeout, and fixing the code later pops Eclipse's "Obsolete Methods on
 * the Stack" dialog over the still-suspended frame. That wait was the largest cost in
 * agent-driven work.
 *
 * <p>The same goes for "Suspend execution on uncaught exceptions": an exception that escapes
 * the app's own handling stops its thread, invisibly. Both preferences are implemented by JDT
 * as hidden exception breakpoints (see {@link ThreadsHandler#suspendedByHiddenBreakpoint}).
 *
 * <p>For launches the dev server started, such a suspension is resumed at once: the
 * {@code Error("Unresolved compilation problem: …")} or the exception carries on, the request
 * fails fast with it, and the message lands in the app's log or console. Only the hidden
 * breakpoints' suspensions are touched — breakpoints and exception breakpoints the developer
 * set still stop — and apps the developer launched from Eclipse keep Eclipse's behaviour. The last few auto-resumes are kept for
 * {@code /threads}, so a caller can see why its request failed.
 */
final class AutoResume implements IDebugEventSetListener {

	private static final AutoResume INSTANCE = new AutoResume();

	/** Launches the dev server started. Weak: a terminated launch goes when Eclipse drops it. */
	private static final Set<ILaunch> LAUNCHES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	/** The most recent auto-resumes, newest last. */
	private static final Deque<JsonObject> RECENT = new ArrayDeque<>();
	private static final int RECENT_LIMIT = 10;

	private static boolean _installed;

	private AutoResume() {
	}

	static synchronized void install() {
		if (!_installed) {
			DebugPlugin.getDefault().addDebugEventListener(INSTANCE);
			_installed = true;
		}
	}

	/** Called for every launch the dev server starts (/launch, /restart, /createProject). */
	static void register(ILaunch launch) {
		if (launch != null) {
			LAUNCHES.add(launch);
		}
	}

	static List<JsonObject> recent() {
		synchronized (RECENT) {
			return new ArrayList<>(RECENT);
		}
	}

	@Override
	public void handleDebugEvents(DebugEvent[] events) {
		for (final DebugEvent event : events) {
			if (event.getKind() != DebugEvent.SUSPEND || event.getDetail() != DebugEvent.BREAKPOINT || !(event.getSource() instanceof IThread thread)) {
				continue;
			}
			if (!LAUNCHES.contains(thread.getLaunch()) || !ThreadsHandler.suspendedByHiddenBreakpoint(thread)) {
				continue;
			}
			try {
				final JsonObject what = ThreadsHandler.describe(thread.getLaunch().getLaunchConfiguration() == null ? null : thread.getLaunch().getLaunchConfiguration().getName(), thread);
				what.put("time", System.currentTimeMillis());
				// Resumed off the event dispatch, which mustn't block on the target VM.
				new Thread(() -> {
					try {
						thread.resume();
					}
					catch (final DebugException e) {
						ComponenteditorPlugin.getDefault().log(e);
					}
				}, "Parslips auto-resume").start();
				synchronized (RECENT) {
					RECENT.addLast(what);
					while (RECENT.size() > RECENT_LIMIT) {
						RECENT.removeFirst();
					}
				}
			}
			catch (final DebugException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}
	}
}

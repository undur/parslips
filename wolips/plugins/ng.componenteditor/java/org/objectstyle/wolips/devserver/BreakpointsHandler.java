package org.objectstyle.wolips.devserver;

import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Lists the workspace's breakpoints and toggles the "Skip All Breakpoints" master switch.
 * Exists because of a real failure mode: a forgotten breakpoint on a hot class makes an
 * app inexplicably slow (or frozen) <em>only</em> when Eclipse-launched, and from outside
 * the IDE there is no way to even suspect it. One call answers "are there breakpoints,
 * and where?" — and one more disarms them all, non-destructively.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>(none) — list all breakpoints plus the master-switch state.</li>
 *   <li>{@code skipAll} — {@code true} activates "Skip All Breakpoints" (breakpoints stay
 *       defined but none suspend anything); {@code false} re-arms them.</li>
 * </ul>
 */
class BreakpointsHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final IBreakpointManager manager = DebugPlugin.getDefault().getBreakpointManager();

		final String skipAll = params.get("skipAll");
		if (skipAll != null && !skipAll.isEmpty()) {
			// The manager's enablement is the inverse of "skip all".
			manager.setEnabled(!"true".equalsIgnoreCase(skipAll));
		}

		final StringBuilder b = new StringBuilder();
		b.append("{\"skipAll\":").append(!manager.isEnabled()).append(",\"breakpoints\":[");
		boolean first = true;
		for (final IBreakpoint breakpoint : manager.getBreakpoints()) {
			if (!first) {
				b.append(',');
			}
			first = false;
			b.append(breakpointJson(breakpoint));
		}
		b.append("]}");
		return b.toString();
	}

	private static String breakpointJson(IBreakpoint breakpoint) {
		String resource = "";
		int line = -1;
		boolean enabled = false;
		try {
			enabled = breakpoint.isEnabled();
			final IMarker marker = breakpoint.getMarker();
			if (marker != null) {
				if (marker.getResource() != null) {
					resource = marker.getResource().getFullPath().toString();
				}
				line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
			}
		}
		catch (Exception e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		return "{\"resource\":\"" + DevServerJson.escape(resource) + "\",\"line\":" + line + ",\"enabled\":" + enabled + "}";
	}
}
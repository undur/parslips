package org.objectstyle.wolips.devserver;

import java.util.Map;

/**
 * Serves the console output of a launched application — including one that already died,
 * which is the case this endpoint exists for: a launch that fails during startup leaves
 * its only evidence in the Eclipse console, invisible to external tools until now.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code app} (or {@code config}) — the app/config/project name (required).</li>
 *   <li>{@code tail} — number of trailing lines to return (default 100).</li>
 * </ul>
 *
 * <p>The response is plain text: a single {@code #}-prefixed status line (config name,
 * running/terminated, exit value when known) followed by the console tail. Plain text on
 * purpose — the consumer is a human or an agent that wants to grep a stack trace, not
 * parse JSON-escaped newlines.
 */
class ConsoleHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) {
		final String name = params.get("app") != null ? params.get("app") : params.get("config");
		if (name == null || name.isEmpty()) {
			return "{\"error\":\"missing required parameter 'app'\"}";
		}

		final ConsoleBuffer.Buffer buffer = ConsoleBuffer.find(name);
		if (buffer == null) {
			return "{\"error\":\"no console captured for \\\"" + DevServerJson.escape(name)
					+ "\\\" — nothing launched under that name since Eclipse started\"}";
		}

		int tail = 100;
		try {
			if (params.get("tail") != null) {
				tail = Math.max(1, Integer.parseInt(params.get("tail")));
			}
		}
		catch (NumberFormatException e) {
			// keep the default
		}

		final boolean terminated = buffer.isTerminated();
		final Integer exit = buffer.exitValue();
		final StringBuilder out = new StringBuilder();
		out.append("# config: ").append(buffer.configName)
				.append("  state: ").append(terminated ? "terminated" : "running");
		if (exit != null) {
			out.append("  exit: ").append(exit);
		}
		out.append('\n');
		out.append(lastLines(buffer.text(), tail));
		return out.toString();
	}

	private static String lastLines(String text, int lineCount) {
		if (text.isEmpty()) {
			return "(no output)";
		}
		int pos = text.length();
		int lines = 0;
		while (pos > 0 && lines < lineCount) {
			final int newline = text.lastIndexOf('\n', pos - 1);
			lines++;
			if (newline == -1) {
				return text;
			}
			pos = newline;
			if (lines == lineCount) {
				return text.substring(newline + 1);
			}
		}
		return text;
	}
}
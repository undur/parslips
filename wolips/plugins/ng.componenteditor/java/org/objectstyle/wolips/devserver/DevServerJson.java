package org.objectstyle.wolips.devserver;

/**
 * Minimal JSON helpers shared by dev-server handlers that emit JSON response bodies.
 * The dev server hand-builds small JSON documents rather than pulling in a JSON
 * library — the payloads are tiny and fixed-shape — so this just centralises correct
 * string escaping so each handler doesn't reinvent (and subtly mis-implement) it.
 */
final class DevServerJson {

	private DevServerJson() {
	}

	/** Escapes a string for safe inclusion inside a JSON string literal. */
	static String escape(String s) {
		if (s == null) {
			return "";
		}
		final StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			switch (c) {
			case '"':
				b.append("\\\"");
				break;
			case '\\':
				b.append("\\\\");
				break;
			case '\n':
				b.append("\\n");
				break;
			case '\r':
				b.append("\\r");
				break;
			case '\t':
				b.append("\\t");
				break;
			default:
				if (c < 0x20) {
					b.append(String.format("\\u%04x", (int) c));
				}
				else {
					b.append(c);
				}
			}
		}
		return b.toString();
	}
}

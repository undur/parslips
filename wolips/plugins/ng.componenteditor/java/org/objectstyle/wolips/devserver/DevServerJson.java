package org.objectstyle.wolips.devserver;

/**
 * Minimal JSON helpers shared by dev-server handlers that emit JSON response bodies.
 * The dev server hand-builds small JSON documents rather than pulling in a JSON
 * library — the payloads are tiny and fixed-shape — so this just centralises correct
 * string escaping so each handler doesn't reinvent (and subtly mis-implement) it.
 */
public final class DevServerJson {

	private DevServerJson() {
	}

	/**
	 * Renders a value as a JSON string literal ( quoted and escaped ), or the literal {@code null}
	 * for a null value — the distinction matters for nullable fields where absence is meaningful
	 * (an unstated policy is {@code null}, not an empty string).
	 */
	public static String str(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + escape(value) + "\"";
	}

	/** Renders a list of strings as a JSON array literal. */
	public static String stringArray(java.util.List<String> values) {
		final StringBuilder b = new StringBuilder(values.size() * 24 + 2);
		b.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append('"').append(escape(values.get(i))).append('"');
		}
		b.append(']');
		return b.toString();
	}

	/** Escapes a string for safe inclusion inside a JSON string literal. */
	public static String escape(String s) {
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

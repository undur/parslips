package org.objectstyle.wolips.devserver;

import java.util.Collection;

/**
 * A small ordered JSON object builder for dev-server responses. The older handlers concatenate
 * JSON by hand, which is where escaping slips in (a raw quote in a component name, a missing
 * comma); the endpoints added for #6 build through this instead, so every value is escaped and
 * every separator placed by one piece of code.
 *
 * <p>Keys keep insertion order (agents and humans read these responses top to bottom, so the
 * identifying fields go first). Values: strings (escaped, null → JSON {@code null}), numbers,
 * booleans, nested {@link JsonObject}s, collections of those, or pre-rendered JSON via
 * {@link #raw}. Absent optional fields are simply not put.
 */
final class JsonObject {

	private final StringBuilder _b = new StringBuilder(128).append('{');
	private boolean _empty = true;

	JsonObject put(String key, String value) {
		return raw(key, DevServerJson.str(value));
	}

	JsonObject put(String key, long value) {
		return raw(key, Long.toString(value));
	}

	JsonObject put(String key, boolean value) {
		return raw(key, Boolean.toString(value));
	}

	JsonObject put(String key, JsonObject value) {
		return raw(key, value == null ? "null" : value.toString());
	}

	/** An array of strings, JsonObjects or numbers (anything else is rendered as a string). */
	JsonObject put(String key, Collection<?> values) {
		return raw(key, array(values));
	}

	/** Puts the string only when it is non-null — for optional fields whose absence is the signal. */
	JsonObject putIfPresent(String key, String value) {
		return value == null ? this : put(key, value);
	}

	/** A value that is already JSON (another handler's response, an existing renderer's output). */
	JsonObject raw(String key, String json) {
		if (!_empty) {
			_b.append(',');
		}
		_empty = false;
		_b.append('"').append(DevServerJson.escape(key)).append("\":").append(json);
		return this;
	}

	static String array(Collection<?> values) {
		final StringBuilder b = new StringBuilder(values.size() * 24 + 2).append('[');
		boolean first = true;
		for (final Object value : values) {
			if (!first) {
				b.append(',');
			}
			first = false;
			if (value instanceof JsonObject || value instanceof Number || value instanceof Boolean) {
				b.append(value);
			}
			else {
				b.append(DevServerJson.str(value == null ? null : value.toString()));
			}
		}
		return b.append(']').toString();
	}

	/** {@code {"error": …}} — the call itself is wrong (see {@link DevServerHandler}'s conventions). */
	static String error(String message) {
		return new JsonObject().put("error", message).toString();
	}

	/** {@code {"error":"missing required parameter 'NAME'"}}. */
	static String missing(String parameter) {
		return error("missing required parameter '" + parameter + "'");
	}

	@Override
	public String toString() {
		return _b.toString() + '}';
	}
}

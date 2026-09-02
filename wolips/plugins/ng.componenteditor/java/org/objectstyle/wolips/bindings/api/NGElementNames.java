package org.objectstyle.wolips.bindings.api;

import java.util.HashMap;
import java.util.Map;

/**
 * The name bridge between WebObjects elements and their ng-objects counterparts, in both
 * directions — the single place that knows ng-objects mostly mirrors WO names with an
 * {@code NG} prefix ({@code WOString} ↔ {@code NGString}) but not always
 * ({@code WOCheckBox} ↔ {@code NGCheckbox}).
 *
 * <p>Two things need this: expanding a legacy tag shortcut for an ng project
 * ({@code checkbox} → {@code WOCheckBox} → which ng class?), and finding binding
 * definitions for an ng element ({@code NGCheckbox} → which bundled {@code WO*.apiext}?).
 * Both used to do a bare prefix swap independently, and both broke on the same element.
 * A survey of every legacy shortcut against ng-objects' element classes found exactly one
 * spelling divergence; it's declared once here, for both directions.
 *
 * <p>This is a stopgap by design: once ng-objects ships its own {@code .apiext} definitions
 * (and declares its tag names in {@code parsley-tag-aliases.properties}, which it now does),
 * nothing needs to guess an ng element's name from a WO one.
 */
public final class NGElementNames {

	/** WO class name → ng-objects class name, for the elements whose ng spelling differs from the prefix swap. */
	private static final Map<String, String> WO_TO_NG_EXCEPTIONS = Map.of(
			"WOCheckBox", "NGCheckbox");

	private static final Map<String, String> NG_TO_WO_EXCEPTIONS = new HashMap<>();
	static {
		for (final Map.Entry<String, String> e : WO_TO_NG_EXCEPTIONS.entrySet()) {
			NG_TO_WO_EXCEPTIONS.put(e.getValue(), e.getKey());
		}
	}

	private NGElementNames() {
	}

	/** {@code WOString} → {@code NGString}, {@code WOCheckBox} → {@code NGCheckbox}; non-WO names pass through unchanged. */
	public static String toNG(String woClassName) {
		if (woClassName == null || !woClassName.startsWith("WO")) {
			return woClassName;
		}
		final String exception = WO_TO_NG_EXCEPTIONS.get(woClassName);
		return exception != null ? exception : "NG" + woClassName.substring(2);
	}

	/** {@code NGString} → {@code WOString}, {@code NGCheckbox} → {@code WOCheckBox}; non-NG names pass through unchanged. */
	public static String toWO(String ngClassName) {
		if (ngClassName == null || !ngClassName.startsWith("NG")) {
			return ngClassName;
		}
		final String exception = NG_TO_WO_EXCEPTIONS.get(ngClassName);
		return exception != null ? exception : "WO" + ngClassName.substring(2);
	}
}

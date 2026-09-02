package org.objectstyle.wolips.bindings.api;

import java.util.Map;

/**
 * The name bridge from WebObjects elements to their ng-objects counterparts, for expanding a
 * legacy tag shortcut in an ng project ({@code checkbox} → {@code WOCheckBox} → which ng class?).
 * ng-objects mostly mirrors WO names with an {@code NG} prefix ({@code WOString} ↔
 * {@code NGString}) but not always ({@code WOCheckBox} ↔ {@code NGCheckbox}); a survey of every
 * legacy shortcut against ng-objects' element classes found exactly that one divergence, declared
 * here.
 *
 * <p>This is a stopgap for ng projects whose classpath declares no
 * {@code parsley-tag-aliases.properties}: when one does (ng-appserver ships one), the alias
 * resolver mirrors the runtime registry exactly and this code isn't consulted. There is
 * deliberately no reverse (NG → WO) mapping any more: an ng element's bindings come from the
 * {@code .apiext} ng-objects ships for it, never from the WebObjects element of the same name,
 * whose API is not necessarily the same.
 */
public final class NGElementNames {

	/** WO class name → ng-objects class name, for the elements whose ng spelling differs from the prefix swap. */
	private static final Map<String, String> WO_TO_NG_EXCEPTIONS = Map.of(
			"WOCheckBox", "NGCheckbox");

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
}

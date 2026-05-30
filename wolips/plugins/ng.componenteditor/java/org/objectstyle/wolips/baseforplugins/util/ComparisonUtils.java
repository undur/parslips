package org.objectstyle.wolips.baseforplugins.util;

import java.util.Objects;

/**
 * Null-safe string equality with an optional "blank is null" mode.
 *
 * <p>For plain null-safe object equality, use {@link java.util.Objects#equals};
 * this class exists only for the blank-is-null variant, which the JDK doesn't
 * provide.
 */
public class ComparisonUtils {
	/**
	 * Null-safe string equality where, when {@code _blankIsNull} is true, a null
	 * string and an empty string are treated as equal (both "blank").
	 */
	public static boolean equals(String _o1, String _o2, boolean _blankIsNull) {
		boolean equals = Objects.equals(_o1, _o2);
		if (!equals && _blankIsNull) {
			equals = ((_o1 == null || _o1.length() == 0) && (_o2 == null || _o2.length() == 0));
		}
		return equals;
	}
}

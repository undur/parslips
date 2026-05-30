package org.objectstyle.wolips.baseforplugins.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class StringUtils {

	/**
	 * Checks if the specified String contains only digits.
	 */
	public static boolean isDigitsOnly(String aString) {
		for (int i = aString.length(); i-- > 0;) {
			char c = aString.charAt(i);
			if (!Character.isDigit(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the specified String contains only digits, a minus, or a decimal.
	 */
	public static boolean isNumericOnly(String aString) {
		boolean foundDecimal = false;
		for (int i = aString.length(); i-- > 0;) {
			char c = aString.charAt(i);
			if (c == '-') {
				if (i != 0) {
					return false;
				}
			} else if (c == '.') {
				if (!foundDecimal) {
					foundDecimal = true;
				} else {
					return false;
				}
			} else if (!Character.isDigit(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Null-safe string equality where, when {@code blankIsNull} is true, a null
	 * string and an empty string are treated as equal (both "blank"). For plain
	 * null-safe equality, use {@link java.util.Objects#equals}.
	 */
	public static boolean equals(String s1, String s2, boolean blankIsNull) {
		boolean equals = Objects.equals(s1, s2);
		if (!equals && blankIsNull) {
			equals = ((s1 == null || s1.length() == 0) && (s2 == null || s2.length() == 0));
		}
		return equals;
	}

	public static String getErrorMessage(Throwable _t) {
		return StringUtils.getErrorMessage(null, _t);
	}

	public static String getErrorMessage(String initialMessage, Throwable _t) {
		StringBuffer messageBuffer = new StringBuffer();
		if (initialMessage != null) {
			messageBuffer.append(initialMessage);
			if (_t != null) {
				messageBuffer.append(" ");
			}
		}

		Throwable t = _t;
		while (t != null) {
			String message = t.getMessage();
			if (message == null && !(t instanceof InvocationTargetException)) {
				String name = t.getClass().getName();
				int lastDotIndex = name.lastIndexOf('.');
				name = name.substring(lastDotIndex + 1);
				message = name;
			}

			if (message != null) {
				message = message.trim();
				messageBuffer.append(message);
				if (!message.endsWith(".")) { //$NON-NLS-1$
					messageBuffer.append(".  "); //$NON-NLS-1$
				}
				else if (!message.endsWith("  ")) {
					messageBuffer.append("  ");
				}
			}

			Throwable cause = t.getCause();
			if (t == cause) {
				t = null;
			} else {
				t = cause;
			}
		}
		return messageBuffer.toString();
	}

	public static String findUnusedName(String newName, Object obj, String getMethodName) {
		try {
			String safeNewName = newName;
			if (safeNewName == null) {
				safeNewName = "MISSING";
			}
			Method getMethod = obj.getClass().getMethod(getMethodName, String.class);
			boolean unusedNameFound = (getMethod.invoke(obj, safeNewName) == null);
			String unusedName = safeNewName;
			if (!unusedNameFound) {
				int cutoffLength;
				for (cutoffLength = safeNewName.length(); cutoffLength > 0; cutoffLength --) {
					if (!Character.isDigit(safeNewName.charAt(cutoffLength - 1))) {
						break;
					}
				}
				String newWithoutTrailingNumber = safeNewName.substring(0, cutoffLength);
				unusedNameFound = (getMethod.invoke(obj, newWithoutTrailingNumber) == null);
				unusedName = newWithoutTrailingNumber;
				for (int dupeNameNum = 1; !unusedNameFound; dupeNameNum++) {
					unusedName = newWithoutTrailingNumber + dupeNameNum;
					Object existingObject = getMethod.invoke(obj, unusedName);
					unusedNameFound = (existingObject == null);
				}
			}
			return unusedName;
		} catch (Throwable t) {
			throw new RuntimeException("Failed to find unused name for '" + newName + "' with method '" + getMethodName + "'.", t);
		}
	}

}

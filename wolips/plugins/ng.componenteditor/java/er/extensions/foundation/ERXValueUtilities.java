package er.extensions.foundation;

/**
 * ERXValueUtilities has useful conversion methods for
 * reading and transforming <code>boolean</code> values.
 */
public class ERXValueUtilities {

	public static boolean isNull(Object obj) {
		return obj == null;
	}

	public static boolean booleanValue(Object obj) {
		return booleanValueWithDefault(obj, false);
	}

	public static boolean booleanValueWithDefault(Object obj, boolean def) {
		if (isNull(obj)) {
			return def;
		}
		if (obj instanceof Boolean) {
			return (Boolean) obj;
		}
		if (obj instanceof Number) {
			return ((Number) obj).intValue() != 0;
		}
		if (obj instanceof String) {
			String strValue = ((String) obj).trim();
			if (strValue.length() > 0) {
				if (strValue.equalsIgnoreCase("no") || strValue.equalsIgnoreCase("false") || strValue.equalsIgnoreCase("n")) {
					return false;
				}
				if (strValue.equalsIgnoreCase("yes") || strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("y")) {
					return true;
				}
				try {
					return Integer.parseInt(strValue) != 0;
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Failed to parse a boolean from the value '" + strValue + "'.");
				}
			}
		}
		return def;
	}
}

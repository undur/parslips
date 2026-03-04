package org.objectstyle.wolips.wizards;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Accessor for externalized UI strings in {@code Messages.properties}.
 */
public class Messages {
	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(Messages.class.getName());

	private Messages() {
	}

	public static String getString(String key) {
		try {
			return BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}

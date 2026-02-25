package org.objectstyle.wolips.variables;

import org.objectstyle.woenvironment.env.WOVariables;

public class ProjectVariables {
	private WOVariables _variables;

	public ProjectVariables(WOVariables variables) {
		_variables = variables;
	}

	public String getString(String name) {
		return _variables.getProperty(name);
	}

	public String getString(String name, String defaultValue) {
		String value = getString(name);
		if (value == null) {
			value = defaultValue;
		}
		return value;
	}

	public boolean getBoolean(String name, boolean defaultValue) {
		String value = getString(name);
		if (value == null) {
			return defaultValue;
		}
		value = value.trim();
		if (value.isEmpty()) {
			return defaultValue;
		}
		if (value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("y")) {
			return true;
		}
		if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("n")) {
			return false;
		}
		return defaultValue;
	}
}

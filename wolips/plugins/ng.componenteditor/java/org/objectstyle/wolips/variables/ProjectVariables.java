package org.objectstyle.wolips.variables;

import org.objectstyle.woenvironment.env.WOVariables;

import er.extensions.foundation.ERXValueUtilities;

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
		return ERXValueUtilities.booleanValueWithDefault(getString(name), defaultValue);
	}
}

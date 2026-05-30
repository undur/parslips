package org.objectstyle.wolips.bindings.api;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import java.util.Objects;
import org.objectstyle.wolips.bindings.wod.TypeCache;

/**
 * Simple in-memory implementation of {@link IApiBinding} that is not backed
 * by a DOM element. Used as the standard binding representation on the read
 * path (via {@link ApiSnapshot}) and also as a fallback when no .api file
 * is available.
 *
 * <p>Instances are mutable — used both for construction by {@link ApiParser}
 * and for direct editing by the {@code .api} editor (which mutates bindings
 * in-place and serializes on save via {@link ApiSerializer}).
 */
public class SimpleApiBinding implements IApiBinding {
	private String _name;
	private String _defaults;
	private boolean _required;
	private boolean _explicitlyRequired;
	private boolean _willSet;
	private boolean _explicitlySettable;

	public SimpleApiBinding(String name) {
		_name = name;
	}

	public int compareTo(IApiBinding o) {
		return (o == null) ? -1 : getName() == null ? -1 : getName().compareTo(o.getName());
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SimpleApiBinding && Objects.equals(((SimpleApiBinding) o).getName(), getName());
	}

	@Override
	public int hashCode() {
		String name = getName();
		return name == null ? 0 : name.hashCode();
	}

	public boolean isAction() {
		return ApiUtils.isActionBinding(this);
	}

	public void setDefaults(String defaults) {
		_defaults = defaults;
	}

	public String getDefaults() {
		return _defaults;
	}

	public int getSelectedDefaults() {
		return ApiUtils.getSelectedDefaults(this);
	}

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}

	public boolean isRequired() {
		return _required;
	}

	public void setRequired(boolean required) {
		_required = required;
	}

	@Override
	public boolean isExplicitlyRequired() {
		return _explicitlyRequired;
	}

	public void setExplicitlyRequired(boolean explicitlyRequired) {
		_explicitlyRequired = explicitlyRequired;
	}

	public boolean isWillSet() {
		return _willSet;
	}

	public void setWillSet(boolean willSet) {
		_willSet = willSet;
	}

	@Override
	public boolean isExplicitlySettable() {
		return _explicitlySettable;
	}

	public void setExplicitlySettable(boolean explicitlySettable) {
		_explicitlySettable = explicitlySettable;
	}

	/**
	 * Sets the defaults value by index into {@link IApiBinding#ALL_DEFAULTS}.
	 * Index 0 clears the defaults (sets to null).
	 */
	public void setDefaults(int index) {
		if (index == 0) {
			_defaults = null;
		}
		else if (index > 0 && index < ALL_DEFAULTS.length) {
			_defaults = ALL_DEFAULTS[index];
		}
	}

	public String[] getValidValues(String partialValue, IJavaProject javaProject, IType componentType, TypeCache typeCache) throws JavaModelException {
		return ApiUtils.getValidValues(this, partialValue, javaProject, componentType, typeCache);
	}
}

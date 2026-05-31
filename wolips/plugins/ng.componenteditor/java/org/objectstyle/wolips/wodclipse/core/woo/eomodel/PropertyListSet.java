package org.objectstyle.wolips.wodclipse.core.woo.eomodel;

import java.util.Collection;
import java.util.TreeSet;

public class PropertyListSet<T> extends TreeSet<T> {
	public PropertyListSet() {
		super(PropertyListComparator.AscendingInsensitivePropertyListComparator);
	}

	public PropertyListSet(Object[] guideArray) {
		super(PropertyListComparator.propertyListComparatorWithGuideArray(guideArray));
	}

	public PropertyListSet(Collection<T> _set) {
		this();
		addAll(_set);
	}
}

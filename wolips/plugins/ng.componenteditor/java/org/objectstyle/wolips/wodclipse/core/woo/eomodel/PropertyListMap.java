package org.objectstyle.wolips.wodclipse.core.woo.eomodel;

import java.util.Map;
import java.util.TreeMap;

public class PropertyListMap<U, V> extends TreeMap<U, V> {
	public PropertyListMap() {
		super(PropertyListComparator.AscendingSensitivePropertyListComparator);
	}

	public PropertyListMap(Map<U, V> _map) {
		this();
		putAll(_map);
	}
}

package org.objectstyle.wolips.bindings.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IType;
import org.objectstyle.wolips.bindings.Activator;
import org.objectstyle.wolips.bindings.preferences.PreferenceConstants;
import org.objectstyle.wolips.bindings.wod.BindingValidationRule;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.core.resources.types.LimitedLRUCache;

/**
 * Multi-level cache for API lookups. Caches resolved element type names,
 * parsed {@link ApiSnapshot} instances, and negative lookups (missing .api
 * files) to avoid repeated file system and classpath searches.
 *
 * <p>Also provides static access to tag shortcuts and binding validation
 * rules from preferences.
 */
public class ApiCache {
	private static String _tagShortcutsStr;
	private static String _bindingValidationRulesStr;
	private static List<TagShortcut> _tagShortcuts;
	private static List<BindingValidationRule> _bindingValidationRules;

	/** Maps element name (short class name) → fully-qualified type name. */
	private Map<String, String> _elementNameToTypeCache;

	/** Maps fully-qualified type name → parsed API snapshot. */
	private Map<String, ApiSnapshot> _elementTypeToApiSnapshotCache;

	/** Maps fully-qualified type name → true if no .api file exists. */
	private Map<String, Boolean> _elementTypeToApiMissingCache;

	/**
	 * Tracks when each "missing" entry was recorded (wall-clock time from
	 * {@code System.currentTimeMillis()}). After a TTL expires, the negative
	 * entry is considered stale and the file system is re-checked. This handles
	 * the case where a {@code .api} file is created after the initial lookup.
	 */
	private Map<String, Long> _elementTypeToApiMissingTimestampCache;

	/**
	 * Tracks the file modification timestamp at the time each snapshot was
	 * cached. Used to detect when .api files change and need reparsing.
	 * Maps fully-qualified type name → last-modified timestamp (or
	 * IFile modification stamp).
	 */
	private Map<String, Long> _elementTypeToApiTimestampCache;

	public ApiCache() {
		clearCache();
	}

	public synchronized void clearCache() {
		_elementNameToTypeCache = Collections.synchronizedMap(new LimitedLRUCache<String, String>(100));
		_elementTypeToApiSnapshotCache = Collections.synchronizedMap(new LimitedLRUCache<String, ApiSnapshot>(100));
		_elementTypeToApiMissingCache = Collections.synchronizedMap(new LimitedLRUCache<String, Boolean>(100));
		_elementTypeToApiMissingTimestampCache = Collections.synchronizedMap(new LimitedLRUCache<String, Long>(100));
		_elementTypeToApiTimestampCache = Collections.synchronizedMap(new LimitedLRUCache<String, Long>(100));
	}

	public synchronized void clearCacheForElementNamed(String elementName) {
		String elementTypeName = _elementNameToTypeCache.remove(elementName);
		if (elementTypeName != null) {
			clearApiForElementTypeName(elementTypeName);
		}
	}

	public synchronized void clearCache(IType elementType) {
		clearApiForElementType(elementType);
		String elementName = elementType.getElementName();
		if (elementName != null) {
			_elementNameToTypeCache.remove(elementName);
		}
	}

	public void clearApiForElementType(IType type) {
		String fqn = type.getFullyQualifiedName();
		_elementTypeToApiMissingCache.remove(fqn);
		_elementTypeToApiMissingTimestampCache.remove(fqn);
		_elementTypeToApiSnapshotCache.remove(fqn);
		_elementTypeToApiTimestampCache.remove(fqn);
	}

	private void clearApiForElementTypeName(String elementTypeName) {
		_elementTypeToApiMissingCache.remove(elementTypeName);
		_elementTypeToApiMissingTimestampCache.remove(elementTypeName);
		_elementTypeToApiSnapshotCache.remove(elementTypeName);
		_elementTypeToApiTimestampCache.remove(elementTypeName);
	}

	public Boolean apiMissingForElementType(IType type) {
		return _elementTypeToApiMissingCache.get(type.getFullyQualifiedName());
	}

	public void setApiMissingForElementType(boolean missing, IType type) {
		String fqn = type.getFullyQualifiedName();
		_elementTypeToApiMissingCache.put(fqn, Boolean.valueOf(missing));
		if (missing) {
			_elementTypeToApiMissingTimestampCache.put(fqn, Long.valueOf(System.currentTimeMillis()));
		} else {
			_elementTypeToApiMissingTimestampCache.remove(fqn);
		}
	}

	/**
	 * Returns the wall-clock time when the "missing" flag was recorded,
	 * or null if no negative entry exists.
	 */
	public Long getApiMissingTimestampForType(IType type) {
		return _elementTypeToApiMissingTimestampCache.get(type.getFullyQualifiedName());
	}

	/** Returns the cached API snapshot for the given type, or null if not cached. */
	public ApiSnapshot getApiSnapshotForType(IType type) {
		return _elementTypeToApiSnapshotCache.get(type.getFullyQualifiedName());
	}

	/** Caches an API snapshot for the given type. Also clears the "missing" flag. */
	public void setApiSnapshotForType(ApiSnapshot snapshot, IType type) {
		_elementTypeToApiSnapshotCache.put(type.getFullyQualifiedName(), snapshot);
		setApiMissingForElementType(false, type);
	}

	/** Returns the cached file timestamp for the given type's .api file, or null. */
	public Long getApiTimestampForType(IType type) {
		return _elementTypeToApiTimestampCache.get(type.getFullyQualifiedName());
	}

	/** Caches the file timestamp for the given type's .api file. */
	public void setApiTimestampForType(long timestamp, IType type) {
		_elementTypeToApiTimestampCache.put(type.getFullyQualifiedName(), Long.valueOf(timestamp));
	}

	public String getElementTypeNamed(String elementName) {
		return _elementNameToTypeCache.get(elementName);
	}

	public void setElementTypeForName(IType elementType, String elementName) {
		_elementNameToTypeCache.put(elementName, elementType.getFullyQualifiedName());
	}

	public static TagShortcut getTagShortcutNamed(String shortcut) {
		TagShortcut matchingTagShortcut = null;
		for (TagShortcut tagShortcut : getTagShortcuts()) {
			if (matchingTagShortcut == null && tagShortcut.getShortcut().equalsIgnoreCase(shortcut)) {
				matchingTagShortcut = tagShortcut;
			}
		}
		return matchingTagShortcut;
	}

	public static synchronized List<TagShortcut> getTagShortcuts() {
		String tagShortcutsStr = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.TAG_SHORTCUTS_KEY);
		if (tagShortcutsStr == null || tagShortcutsStr.isEmpty()) {
			// Preference store not initialized (activator not started by OSGi); trigger default initialization
			new org.objectstyle.wolips.bindings.preferences.PreferenceInitializer().initializeDefaultPreferences();
			tagShortcutsStr = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.TAG_SHORTCUTS_KEY);
		}
		if ((tagShortcutsStr == null && _tagShortcutsStr != null) || (tagShortcutsStr != null && !tagShortcutsStr.equals(_tagShortcutsStr))) {
			_tagShortcutsStr = tagShortcutsStr;
			_tagShortcuts = TagShortcut.fromPreferenceString(tagShortcutsStr);
		}
		return _tagShortcuts;
	}

	public static synchronized List<BindingValidationRule> getBindingValidationRules() {
		String bindingValidationRulesStr = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.BINDING_VALIDATION_RULES_KEY);
		if ((bindingValidationRulesStr == null && _bindingValidationRulesStr != null) || (bindingValidationRulesStr != null && !bindingValidationRulesStr.equals(_bindingValidationRulesStr))) {
			_bindingValidationRulesStr = bindingValidationRulesStr;
			_bindingValidationRules = BindingValidationRule.fromPreferenceString(bindingValidationRulesStr);
		}
		return _bindingValidationRules;
	}
}

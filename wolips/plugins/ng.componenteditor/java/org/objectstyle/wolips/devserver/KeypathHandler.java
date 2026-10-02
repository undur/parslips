package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IType;
import org.objectstyle.wolips.bindings.wod.AbstractWodBinding;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * {@code /keypath} — resolve a keypath against a component, the way the validator does, and
 * report every hop: {@code game.homeTeam.rating} → the key, the type it yields, and where it is
 * declared, segment by segment. Answers "what is {@code game} here, and does
 * {@code homeTeam.rating} exist on it?" without the agent reading three classes — and, for a
 * keypath that breaks, which segment broke, on which type, and the validator's own
 * "did you mean" suggestions.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>{@code component} — required; the component whose class the keypath starts from.</li>
 *   <li>{@code keypath} — required; as written in a template. An inline {@code $} prefix is
 *       accepted and ignored; {@code @operator} and {@code |helper} suffixes are recognised.</li>
 *   <li>{@code project} — optional hint.</li>
 * </ul>
 *
 * <p>{@code valid:true} means every segment resolved. A key on a {@code java.lang.Object} (or a
 * collection) can't be checked statically: such a keypath answers {@code valid:true} with
 * {@code unchecked} naming the first key that wasn't verified — the validator stays silent on
 * those too.
 */
class KeypathHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws Exception {
		final String componentName = params.get("component");
		if (componentName == null || componentName.isEmpty()) {
			return JsonObject.missing("component");
		}
		String keypath = params.get("keypath");
		if (keypath == null || keypath.isEmpty()) {
			return JsonObject.missing("keypath");
		}
		// Templates write inline bindings as $keypath; the keypath is what follows.
		if (keypath.startsWith("$")) {
			keypath = keypath.substring(1);
		}
		if (keypath.startsWith("^") || keypath.startsWith("~") || keypath.startsWith("\"")) {
			return JsonObject.error("'" + keypath + "' is not a keypath ('^' is a parent binding, '~' OGNL, quotes a constant); only keypaths resolve against a class");
		}

		final String projectHint = DevServerComponents.projectParam(params);
		final DevServerComponents.Found found = DevServerComponents.find(componentName, projectHint);
		if (found == null) {
			return new JsonObject().put("component", componentName).put("keypath", keypath).put("valid", false)
					.put("reason", DevServerComponents.notFoundReason(componentName, projectHint)).toString();
		}
		final IType type = found.descriptor().getJavaType();
		if (type == null) {
			return new JsonObject().put("component", componentName).put("keypath", keypath).put("valid", false)
					.put("reason", "component '" + componentName + "' has no Java class, so a keypath has nothing to resolve against").toString();
		}
		return resolve(type, keypath).toString();
	}

	static JsonObject resolve(IType type, String keypath) throws Exception {
		final BindingValueKeyPath path = new BindingValueKeyPath(keypath, type, type.getJavaProject(), WodParserCache.getTypeCache());

		final List<JsonObject> segments = new ArrayList<>();
		for (final BindingValueKey key : path.getBindingKeys()) {
			segments.add(new JsonObject()
					.put("key", key.getBindingName())
					.put("type", DevServerJava.typeName(key))
					.put("declaredIn", key.getDeclaringType() == null ? null : key.getDeclaringType().getFullyQualifiedName('.'))
					.put("via", DevServerJava.memberKind(key.getBindingMember()))
					.put("at", DevServerJava.location(key.getBindingMember())));
		}

		// The validator's rule: in a WebObjects component, a KVC operator on a java.util
		// collection is valid syntax that fails at render (WO's operators need an NSArray).
		final String operatorProblem = operatorProblem(type, path);

		final JsonObject json = new JsonObject()
				.put("component", type.getElementName())
				.put("class", type.getFullyQualifiedName('.'))
				.put("keypath", keypath)
				.put("valid", path.isValid() && operatorProblem == null);
		json.putIfPresent("problem", operatorProblem);
		json.put("segments", segments);
		json.putIfPresent("operator", path.getOperator());
		json.putIfPresent("helper", path.getHelperFunction());

		if (!path.isValid()) {
			final IType on = path.getInvalidKeyType();
			json.put("invalidKey", path.getInvalidKey())
					.put("on", on == null ? null : on.getFullyQualifiedName('.'))
					.put("suggestions", AbstractWodBinding.suggestKeysForInvalidKey(path, WodParserCache.getTypeCache()));
		}
		else if (path.isAmbiguous()) {
			// Resolution stopped at a type that can't be checked (Object, a collection, a
			// component): the rest is taken on trust, as the validator does.
			json.put("unchecked", path.getInvalidKey())
					.put("note", path.isNSCollection() ? "passes through a collection; later keys can't be checked statically"
							: path.isWOComponent() ? "passes through a component; later keys can't be checked statically"
							: "reaches a java.lang.Object; later keys can't be checked statically");
		}
		else if (path.exists() && operatorProblem == null) {
			// Whether a value binding can push back (a form field's value=) — a common question.
			json.put("settable", path.isSettable());
		}
		return json;
	}

	private static String operatorProblem(IType type, BindingValueKeyPath path) throws Exception {
		final org.objectstyle.wolips.bindings.wod.TypeCache cache = WodParserCache.getTypeCache();
		if (path.getOperator() == null || !path.isValid() || path.isAmbiguous() || !org.objectstyle.wolips.bindings.utils.BindingReflectionUtils.isWebObjectsComponent(type, cache)) {
			return null;
		}
		final IType collection = path.getLastType();
		if (collection == null || org.objectstyle.wolips.bindings.utils.BindingReflectionUtils.isNSArray(collection, cache)
				|| !org.objectstyle.wolips.bindings.utils.BindingReflectionUtils.isType(collection, new String[] { "java.util.Collection" }, cache)) {
			return null;
		}
		return "'@" + path.getOperator() + "' only works on an NSArray in WebObjects, and this is a " + collection.getFullyQualifiedName('.') + "; it fails at render";
	}
}

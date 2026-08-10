package org.objectstyle.wolips.bindings.api;

import java.util.List;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;
import org.objectstyle.wolips.bindings.api.ApiextModel.TypeRef;
import org.objectstyle.wolips.devserver.DevServerJson;

/**
 * Renders an {@link ApiextModel} as a machine-readable JSON document — the JSON sibling of
 * {@link ApiextHtmlRenderer} (which renders the same model as an HTML card for the hover / Element
 * Reference). Used by the dev server's element-API lookup endpoint so a tool can read a tag's real
 * binding API (types, directions, constraints with their generated messages, policies) over HTTP
 * instead of reading the element's Java source to work it out.
 *
 * <p>The shape is the "interpreted" view — the consumer's understanding of the element, uniform
 * across {@code .apiext} and legacy {@code .api} (which is adapted into an {@link ApiextModel} on
 * the way in). It is <em>not</em> the raw {@code .apiext} XML; the endpoint offers that separately.
 *
 * <p>Hand-built JSON, matching the dev server's house style (see {@link DevServerJson}); the
 * payloads are small and fixed-shape.
 */
public class ApiextJsonRenderer {

	private ApiextJsonRenderer() {
	}

	/**
	 * @return the model as a JSON object: {@code {name, source, doc, content, unknownAttributes,
	 *         deprecated, deprecationNote, bindings:[…], constraints:[…]}}. Null-valued policies are
	 *         emitted as JSON {@code null} (absent = "no policy stated"), never synthesized.
	 */
	public static String render(String displayName, ApiextModel model) {
		final StringBuilder b = new StringBuilder(1024);

		b.append('{');
		b.append("\"name\":").append(DevServerJson.str(displayName));
		b.append(",\"source\":").append(DevServerJson.str(model.getSource().getLabel()));
		b.append(",\"doc\":").append(DevServerJson.str(model.getDoc()));
		b.append(",\"content\":").append(DevServerJson.str(model.getContent() == null ? null : lower(model.getContent().name())));
		b.append(",\"unknownAttributes\":").append(DevServerJson.str(model.getUnknownAttributes() == null ? null : lower(model.getUnknownAttributes().name())));
		b.append(",\"deprecated\":").append(model.isDeprecated());
		if (model.isDeprecated()) {
			b.append(",\"deprecationNote\":").append(DevServerJson.str(model.getDeprecationNote()));
		}

		b.append(",\"bindings\":[");
		final List<Binding> bindings = model.getBindings();
		for (int i = 0; i < bindings.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			renderBinding(b, bindings.get(i));
		}
		b.append(']');

		b.append(",\"constraints\":[");
		final List<Constraint> constraints = model.getConstraints();
		for (int i = 0; i < constraints.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			renderConstraint(b, constraints.get(i));
		}
		b.append(']');

		b.append('}');
		return b.toString();
	}

	private static void renderBinding(StringBuilder b, Binding binding) {
		b.append('{');
		b.append("\"name\":").append(DevServerJson.str(binding.getName()));
		b.append(",\"required\":").append(binding.isRequired());
		b.append(",\"pull\":");
		renderTypes(b, binding.getPullTypes());
		b.append(",\"push\":");
		renderTypes(b, binding.getPushTypes());
		// A convenience the caller would otherwise derive: pull-only / push-only / two-way / none.
		b.append(",\"direction\":").append(DevServerJson.str(direction(binding)));
		if (binding.getDefaultValue() != null) {
			b.append(",\"default\":").append(DevServerJson.str(binding.getDefaultValue()));
		}
		if (binding.getDefaults() != null) {
			b.append(",\"defaults\":").append(DevServerJson.str(binding.getDefaults()));
		}
		if (binding.getDoc() != null) {
			b.append(",\"doc\":").append(DevServerJson.str(binding.getDoc()));
		}
		b.append(",\"deprecated\":").append(binding.isDeprecated());
		if (binding.isDeprecated()) {
			b.append(",\"deprecationNote\":").append(DevServerJson.str(binding.getDeprecationNote()));
		}
		b.append('}');
	}

	/** Emits an array of {@code {type, interpretation?}} objects for one direction's types. */
	private static void renderTypes(StringBuilder b, List<TypeRef> types) {
		b.append('[');
		for (int i = 0; i < types.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			final TypeRef type = types.get(i);
			b.append("{\"type\":").append(DevServerJson.str(type.getName()));
			if (type.getInterpretation() != null) {
				b.append(",\"interpretation\":").append(DevServerJson.str(type.getInterpretation()));
			}
			b.append('}');
		}
		b.append(']');
	}

	private static String direction(Binding binding) {
		final boolean pull = !binding.getPullTypes().isEmpty();
		final boolean push = !binding.getPushTypes().isEmpty();
		if (pull && push) {
			return "both";
		}
		if (pull) {
			return "pull";
		}
		if (push) {
			return "push";
		}
		return "none";
	}

	private static void renderConstraint(StringBuilder b, Constraint constraint) {
		b.append('{');
		// The generated (or author-overridden) human message — the same one the hover shows.
		b.append("\"message\":").append(DevServerJson.str(ApiextConstraintMessages.describe(constraint)));

		if (constraint instanceof Choose choose) {
			b.append(",\"kind\":\"choose\"");
			if (choose.getMin() != null) {
				b.append(",\"min\":").append(choose.getMin());
			}
			if (choose.getMax() != null) {
				b.append(",\"max\":").append(choose.getMax());
			}
			b.append(",\"alternatives\":[");
			final List<Alternative> alternatives = choose.getAlternatives();
			for (int i = 0; i < alternatives.size(); i++) {
				if (i > 0) {
					b.append(',');
				}
				renderAlternative(b, alternatives.get(i));
			}
			b.append(']');
		}
		else if (constraint instanceof Requires requires) {
			b.append(",\"kind\":\"requires\"");
			b.append(",\"binding\":").append(DevServerJson.str(requires.getBinding()));
			b.append(",\"must\":").append(DevServerJson.str(lower(requires.getMust().name())));
			if (requires.getAntecedent() != null) {
				b.append(",\"when\":");
				renderAlternative(b, requires.getAntecedent());
			}
		}

		b.append('}');
	}

	/**
	 * A constraint alternative / antecedent: either a single binding or an any-of set. Emitted as
	 * {@code {anyOf:bool, bindings:[…]}} so both forms have one uniform shape.
	 */
	private static void renderAlternative(StringBuilder b, Alternative alternative) {
		b.append("{\"anyOf\":").append(alternative.isAnyOf());
		b.append(",\"bindings\":").append(DevServerJson.stringArray(alternative.getBindingNames()));
		b.append('}');
	}

	private static String lower(String value) {
		return value == null ? null : value.toLowerCase();
	}
}

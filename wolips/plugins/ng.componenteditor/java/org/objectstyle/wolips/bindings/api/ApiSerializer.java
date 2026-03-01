package org.objectstyle.wolips.bindings.api;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Serializes an {@link ApiSnapshot} back to {@code .api} XML format.
 *
 * <p>This is the write-path counterpart to {@link ApiParser}. It produces
 * well-formed XML matching the existing {@code .api} file format:
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
 * <wodefinitions>
 *     <wo wocomponentcontent="false" class="MyComponent">
 *         <binding name="item" defaults="Actions"/>
 *         <binding name="value" required="YES"/>
 *         <validation message="'item' is a required binding.">
 *             <unbound name="item"/>
 *         </validation>
 *     </wo>
 * </wodefinitions>
 * }</pre>
 *
 * <p>The serializer produces validation elements for bindings where the required
 * or will-set state is implicit (i.e. not expressed via an explicit attribute).
 * Bindings that are explicitly required ({@code required="YES"}) or explicitly
 * settable ({@code settable="YES"}) use attributes instead.
 *
 * <p>Not instantiable — all methods are static.
 */
public final class ApiSerializer {

	private ApiSerializer() {
	}

	/**
	 * Serializes an {@link ApiSnapshot} to XML and writes it to the given writer.
	 *
	 * @param snapshot the API definition to serialize
	 * @param writer the target writer (caller is responsible for closing)
	 * @throws IOException if writing fails
	 */
	public static void serialize(ApiSnapshot snapshot, Writer writer) throws IOException {
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
		writer.write("<wodefinitions>\n");
		serializeWo(snapshot, writer);
		writer.write("</wodefinitions>\n");
	}

	/**
	 * Serializes the {@code <wo>} element and its children (bindings, validations,
	 * preview) from the snapshot.
	 */
	private static void serializeWo(ApiSnapshot snapshot, Writer writer) throws IOException {
		writer.write("    <wo wocomponentcontent=\"");
		writer.write(snapshot.isComponentContent() ? "true" : "false");
		writer.write("\" class=\"");
		writer.write(escapeXml(snapshot.getClassName()));
		writer.write("\">\n");

		// Serialize binding elements
		for (IApiBinding binding : snapshot.getBindings()) {
			serializeBinding(binding, writer);
		}

		// Serialize existing validation rules from the parsed tree.
		// These include any complex validations that aren't simple
		// required/willSet patterns.
		for (ApiValidation validation : snapshot.getValidations()) {
			serializeValidation(validation, writer, "        ");
		}

		// Serialize preview content if present
		if (snapshot.getPreview() != null && !snapshot.getPreview().isEmpty()) {
			writer.write("        <preview>");
			writer.write(snapshot.getPreview());
			writer.write("</preview>\n");
		}

		writer.write("    </wo>\n");
	}

	/**
	 * Serializes a single {@code <binding>} element with its attributes.
	 *
	 * <p>Attributes are written only when they have meaningful values:
	 * <ul>
	 *   <li>{@code name} — always written
	 *   <li>{@code defaults} — written when non-null and non-empty
	 *   <li>{@code required} — written as "YES" only when explicitly required
	 *   <li>{@code settable} — written as "YES" only when explicitly settable
	 * </ul>
	 */
	private static void serializeBinding(IApiBinding binding, Writer writer) throws IOException {
		writer.write("        <binding name=\"");
		writer.write(escapeXml(binding.getName()));
		writer.write("\"");

		String defaults = binding.getDefaults();
		if (defaults != null && !defaults.isEmpty()) {
			writer.write(" defaults=\"");
			writer.write(escapeXml(defaults));
			writer.write("\"");
		}

		if (binding.isExplicitlyRequired()) {
			writer.write(" required=\"YES\"");
		}

		if (binding.isExplicitlySettable()) {
			writer.write(" settable=\"YES\"");
		}

		writer.write("/>\n");
	}

	/**
	 * Serializes a validation tree node and its children recursively.
	 *
	 * <p>The output format matches the original {@code .api} XML structure:
	 * top-level {@code <validation>} elements have a {@code message} attribute,
	 * leaf predicates have a {@code name} attribute, and composite operators
	 * recursively contain their children.
	 */
	private static void serializeValidation(ApiValidation validation, Writer writer, String indent) throws IOException {
		String tagName = tagNameForKind(validation.getKind());
		if (tagName == null) {
			return;
		}

		writer.write(indent);
		writer.write("<");
		writer.write(tagName);

		// Write attributes based on kind
		if (validation.getKind() == ApiValidation.Kind.VALIDATION && validation.getMessage() != null) {
			writer.write(" message=\"");
			writer.write(escapeXml(validation.getMessage()));
			writer.write("\"");
		}

		if (validation.getBindingName() != null) {
			writer.write(" name=\"");
			writer.write(escapeXml(validation.getBindingName()));
			writer.write("\"");
		}

		if (validation.getKind() == ApiValidation.Kind.COUNT && validation.getCountTest() != null) {
			writer.write(" test=\"");
			writer.write(escapeXml(validation.getCountTest()));
			writer.write("\"");
		}

		List<ApiValidation> children = validation.getChildren();
		if (children.isEmpty()) {
			writer.write("/>\n");
		} else {
			writer.write(">\n");
			for (ApiValidation child : children) {
				serializeValidation(child, writer, indent + "    ");
			}
			writer.write(indent);
			writer.write("</");
			writer.write(tagName);
			writer.write(">\n");
		}
	}

	/**
	 * Returns the XML tag name for a validation kind.
	 */
	private static String tagNameForKind(ApiValidation.Kind kind) {
		switch (kind) {
			case VALIDATION:  return "validation";
			case AND:         return "and";
			case OR:          return "or";
			case NOT:         return "not";
			case COUNT:       return "count";
			case BOUND:       return "bound";
			case UNBOUND:     return "unbound";
			case SETTABLE:    return "settable";
			case UNSETTABLE:  return "unsettable";
			case GETTABLE:    return "gettable";
			case UNGETTABLE:  return "ungettable";
			default:          return null;
		}
	}

	/**
	 * Escapes special XML characters in attribute values and text content.
	 */
	private static String escapeXml(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
				case '&':  sb.append("&amp;"); break;
				case '<':  sb.append("&lt;"); break;
				case '>':  sb.append("&gt;"); break;
				case '"':  sb.append("&quot;"); break;
				case '\'': sb.append("&apos;"); break;
				default:   sb.append(ch); break;
			}
		}
		return sb.toString();
	}
}

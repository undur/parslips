package org.objectstyle.wolips.bindings.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An immutable, parsed view of a {@code .apiext} file — the extended element-API
 * format AjaxSlim is prototyping alongside the classic {@code .api} format (see
 * {@code docs/apiext.dtd}).
 *
 * <p>The format is deliberately in flux, so the single most important property of this
 * class is its <b>parsability gate</b>: {@link #parse} returns {@code null} for anything
 * it can't fully and cleanly turn into a model — malformed XML, an unexpected shape, a
 * missing {@code <wo>}. Callers treat {@code null} as "no usable .apiext" and fall back
 * to the {@code .api} path. Nothing half-parsed is ever surfaced; an experimental file
 * with a typo simply doesn't change behaviour rather than producing a broken hover.
 *
 * <p>What it captures (the .apiext additions over .api): an element-level Markdown
 * {@code <doc>}, the {@code passthrough} flag, and per-binding
 * accepted {@code <type>}s plus a Markdown {@code <doc>}. Rendering (Markdown, badges,
 * table) is a separate concern — see {@code ApiextHtmlRenderer}.
 */
public final class ApiextModel {

	/**
	 * Which on-disk format this model was loaded from. Both render through the same
	 * template (an {@code .api} is just an {@code .apiext} with the extension fields
	 * empty); the source drives only the little badge in the hover header.
	 */
	public enum SourceKind {
		API(".api"), APIEXT(".apiext");

		private final String _label;

		SourceKind(String label) {
			_label = label;
		}

		/** The badge label, e.g. {@code ".apiext"}. */
		public String getLabel() {
			return _label;
		}
	}

	/**
	 * An accepted type for a binding: a fully-qualified Java class (or value-set name),
	 * optionally carrying an {@code interpretation} — a reading rule applied to the value
	 * <em>without</em> changing the type (the type stays the real, validatable constraint).
	 * The only interpretation today is {@code "truthy"}. The interpretation is documentation,
	 * shown as a qualifier — e.g. {@code "Object (truthy)"}.
	 */
	public static final class TypeRef {
		private final String _name;
		private final String _interpretation; // e.g. "truthy", or null

		TypeRef(String name, String interpretation) {
			_name = name;
			_interpretation = interpretation;
		}

		/** The type name as declared (fully-qualified Java class or value-set name). */
		public String getName() {
			return _name;
		}

		/** The interpretation qualifier (e.g. {@code "truthy"}), or null. */
		public String getInterpretation() {
			return _interpretation;
		}

		/** Two TypeRefs are equal when both name and interpretation match (used to merge pull==push). */
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof TypeRef)) {
				return false;
			}
			final TypeRef other = (TypeRef) o;
			return java.util.Objects.equals(_name, other._name)
					&& java.util.Objects.equals(_interpretation, other._interpretation);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(_name, _interpretation);
		}
	}

	/**
	 * A single binding's extended definition.
	 *
	 * <p>Types are declared with <b>directionality</b>: a {@code <pull>} type (the value the
	 * element reads/displays) and/or a {@code <push>} type (the value it writes back). Types
	 * may only appear inside {@code <pull>}/{@code <push>} — not directly on {@code <binding>}.
	 * Many bindings pull and push the same type; some genuinely differ (a checkbox pulls a
	 * truthy {@code Object} but pushes a {@code Boolean}). Bindings from a plain {@code .api}
	 * file carry no type info at all (both lists empty). Rendering decides the direction
	 * arrows from which lists are populated.
	 */
	public static final class Binding {
		private final String _name;
		private final List<TypeRef> _pullTypes; // <pull><type>… read by the element
		private final List<TypeRef> _pushTypes; // <push><type>… written back by the element
		private final String _doc; // raw Markdown, or null
		private final boolean _required;

		Binding(String name, List<TypeRef> pullTypes, List<TypeRef> pushTypes, String doc, boolean required) {
			_name = name;
			_pullTypes = Collections.unmodifiableList(pullTypes);
			_pushTypes = Collections.unmodifiableList(pushTypes);
			_doc = doc;
			_required = required;
		}

		public String getName() {
			return _name;
		}

		/** Types the element pulls (reads), in declared order; never null, may be empty. */
		public List<TypeRef> getPullTypes() {
			return _pullTypes;
		}

		/** Types the element pushes (writes back), in declared order; never null, may be empty. */
		public List<TypeRef> getPushTypes() {
			return _pushTypes;
		}

		/** The binding's Markdown documentation, or null if none. */
		public String getDoc() {
			return _doc;
		}

		public boolean isRequired() {
			return _required;
		}
	}

	/** A cross-binding validation rule (message + the binding names it concerns). */
	public static final class Validation {
		private final String _message;

		Validation(String message) {
			_message = message;
		}

		public String getMessage() {
			return _message;
		}
	}

	private final SourceKind _source;
	private final String _className;
	private final boolean _componentContent;
	private final boolean _passthrough;
	private final String _doc; // raw Markdown, or null
	private final List<Binding> _bindings;
	private final List<Validation> _validations;

	/**
	 * Where the element comes from — its originating framework/bundle (e.g. "JavaWebObjects",
	 * "AjaxSlim"), or null if unknown. This is not part of the {@code .apiext} file; it's
	 * derived from the resolved {@code IType} and attached by the caller, because knowing the
	 * source framework is useful both in the element-help list and in the rendered card.
	 */
	private String _origin;

	private ApiextModel(SourceKind source, String className, boolean componentContent, boolean passthrough,
			String doc, List<Binding> bindings, List<Validation> validations) {
		_source = source;
		_className = className;
		_componentContent = componentContent;
		_passthrough = passthrough;
		_doc = doc;
		_bindings = Collections.unmodifiableList(bindings);
		_validations = Collections.unmodifiableList(validations);
	}

	/** Which on-disk format this model was loaded from ({@code .api} or {@code .apiext}). */
	public SourceKind getSource() {
		return _source;
	}

	/** The originating framework/bundle (e.g. "AjaxSlim"), or null if unknown/unset. */
	public String getOrigin() {
		return _origin;
	}

	/** Attaches the originating framework/bundle name (derived from the resolved {@code IType}). */
	public void setOrigin(String origin) {
		_origin = origin;
	}

	/**
	 * Adapts a classic {@link ApiSnapshot} (parsed from a {@code .api} file or the global
	 * {@code WebObjectDefinitions.xml}) into this model so both formats render through the
	 * same template. The {@code .apiext}-only fields — element/binding Markdown docs,
	 * accepted types, the passthrough flag — are simply left empty, which
	 * the renderer omits. Marked {@link SourceKind#API}.
	 *
	 * @param className the display/class name for the header
	 * @param api       the snapshot to adapt
	 */
	public static ApiextModel fromApiSnapshot(String className, ApiSnapshot api) {
		final List<Binding> bindings = new ArrayList<>();
		for (final IApiBinding b : api.getBindings()) {
			// .api has no type/direction/doc info — empty pull/push lists and null doc, which
			// the renderer renders as blank cells (no type, no direction arrow).
			bindings.add(new Binding(b.getName(), new ArrayList<>(), new ArrayList<>(), null, b.isRequired()));
		}
		final List<Validation> validations = new ArrayList<>();
		for (final ApiValidation v : api.getValidations()) {
			final String message = v.getMessage();
			if (message != null && !message.isEmpty()) {
				validations.add(new Validation(message));
			}
		}
		return new ApiextModel(SourceKind.API, className, api.isComponentContent(), false,
				null, bindings, validations);
	}

	public String getClassName() {
		return _className;
	}

	public boolean isComponentContent() {
		return _componentContent;
	}

	public boolean isPassthrough() {
		return _passthrough;
	}

	/** The element's Markdown documentation (role/description), or null. */
	public String getDoc() {
		return _doc;
	}

	public List<Binding> getBindings() {
		return _bindings;
	}

	public List<Validation> getValidations() {
		return _validations;
	}

	/**
	 * Parses {@code .apiext} XML into a model, or returns {@code null} if it can't be
	 * cleanly parsed (malformed XML, no {@code <wo>} element, or any unexpected error).
	 * This is the parsability gate that keeps the in-flux format from breaking tooling.
	 */
	public static ApiextModel parse(InputStream xml) {
		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// The files declare no DOCTYPE in practice, and we don't want a SYSTEM "apiext.dtd"
			// reference (if one appears) to cause a network/file fetch or fail the parse.
			factory.setValidating(false);
			factory.setNamespaceAware(false);
			trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
			trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);

			final DocumentBuilder builder = factory.newDocumentBuilder();
			// A no-op entity resolver as a belt-and-suspenders guard against DTD fetches.
			builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new ByteArrayInputStream(new byte[0])));

			final Document document = builder.parse(xml);
			return fromDocument(document);
		}
		catch (Exception e) {
			// In-flux format: any failure means "no usable .apiext", fall back to .api.
			return null;
		}
	}

	/** Convenience for the common case of parsing a byte array. */
	public static ApiextModel parse(byte[] xml) {
		return parse(new ByteArrayInputStream(xml));
	}

	private static ApiextModel fromDocument(Document document) {
		final Element root = document.getDocumentElement();
		if (root == null || !"wodefinitions".equals(root.getNodeName())) {
			return null;
		}
		final Element wo = firstChildElement(root, "wo");
		if (wo == null) {
			return null;
		}

		final String className = attr(wo, "class");
		final boolean componentContent = boolAttr(wo, "wocomponentcontent");
		final boolean passthrough = boolAttr(wo, "passthrough");

		String elementDoc = null;
		final List<Binding> bindings = new ArrayList<>();
		final List<Validation> validations = new ArrayList<>();

		// First pass: collect bindings named in validations, so we can mark required.
		// (A binding the rules say must be bound is shown as required in the preview.)
		for (Node n = wo.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) n;
			switch (el.getNodeName()) {
			case "doc":
				if (elementDoc == null) {
					elementDoc = text(el);
				}
				break;
			case "binding":
				bindings.add(parseBinding(el));
				break;
			case "validation":
				validations.add(new Validation(attr(el, "message")));
				break;
			default:
				// documentation (WO external pointer), legacy <tags>, and anything else:
				// ignored for the preview.
				break;
			}
		}

		return new ApiextModel(SourceKind.APIEXT, className, componentContent, passthrough, emptyToNull(elementDoc), bindings, validations);
	}

	private static Binding parseBinding(Element bindingEl) {
		final String name = attr(bindingEl, "name");
		final boolean required = "true".equalsIgnoreCase(attr(bindingEl, "required")) || "yes".equalsIgnoreCase(attr(bindingEl, "required"));
		final List<TypeRef> pullTypes = new ArrayList<>(); // <pull><type>
		final List<TypeRef> pushTypes = new ArrayList<>(); // <push><type>
		String doc = null;
		for (Node n = bindingEl.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) n;
			switch (el.getNodeName()) {
			case "pull":
				collectTypes(el, pullTypes);
				break;
			case "push":
				collectTypes(el, pushTypes);
				break;
			case "doc":
				if (doc == null) {
					doc = text(el);
				}
				break;
			default:
				// <type> directly under <binding> is no longer valid; ignored if present.
				break;
			}
		}
		return new Binding(name, pullTypes, pushTypes, emptyToNull(doc), required);
	}

	/** Collects the {@code <type>} children of a {@code <pull>}/{@code <push>} block into {@code out}. */
	private static void collectTypes(Element directionEl, List<TypeRef> out) {
		for (Node n = directionEl.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() == Node.ELEMENT_NODE && "type".equals(n.getNodeName())) {
				addType(out, (Element) n);
			}
		}
	}

	/** Parses one {@code <type [interpretation=...]>} element into {@code out} (skips empty types). */
	private static void addType(List<TypeRef> out, Element typeEl) {
		final String type = text(typeEl);
		if (type != null && !type.isEmpty()) {
			out.add(new TypeRef(type, emptyToNull(attr(typeEl, "interpretation"))));
		}
	}

	// ---- small DOM helpers ----

	private static Element firstChildElement(Element parent, String name) {
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
				return (Element) n;
			}
		}
		return null;
	}

	private static String attr(Element el, String name) {
		return el.hasAttribute(name) ? el.getAttribute(name) : null;
	}

	private static boolean boolAttr(Element el, String name) {
		final String v = attr(el, name);
		return "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
	}

	/** The element's text content (CDATA included), trimmed; never null but may be empty. */
	private static String text(Element el) {
		final StringBuilder sb = new StringBuilder();
		final NodeList children = el.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			final short type = child.getNodeType();
			if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
				sb.append(child.getNodeValue());
			}
		}
		return sb.toString().trim();
	}

	private static String emptyToNull(String s) {
		return (s == null || s.isEmpty()) ? null : s;
	}

	private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
		try {
			factory.setFeature(feature, value);
		}
		catch (Exception e) {
			// Feature not supported by this parser — fine; the entity resolver still guards us.
		}
	}
}

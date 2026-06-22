package org.objectstyle.wolips.bindings.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * {@code <doc>}, framework {@code <tags>}, the {@code passthrough} flag, and per-binding
 * accepted {@code <type>}s plus a Markdown {@code <doc>}. Rendering (Markdown, badges,
 * table) is a separate concern — see {@code ApiextHtmlRenderer}.
 */
public final class ApiextModel {

	/** A single binding's extended definition. */
	public static final class Binding {
		private final String _name;
		private final List<String> _types;
		private final String _doc; // raw Markdown, or null
		private final boolean _required;

		Binding(String name, List<String> types, String doc, boolean required) {
			_name = name;
			_types = Collections.unmodifiableList(types);
			_doc = doc;
			_required = required;
		}

		public String getName() {
			return _name;
		}

		/** Accepted types (fully-qualified or value-set names), in declared order; never null. */
		public List<String> getTypes() {
			return _types;
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

	private final String _className;
	private final boolean _componentContent;
	private final boolean _passthrough;
	private final String _doc; // raw Markdown, or null
	private final List<String> _tags;
	private final List<Binding> _bindings;
	private final List<Validation> _validations;

	private ApiextModel(String className, boolean componentContent, boolean passthrough,
			String doc, List<String> tags, List<Binding> bindings, List<Validation> validations) {
		_className = className;
		_componentContent = componentContent;
		_passthrough = passthrough;
		_doc = doc;
		_tags = Collections.unmodifiableList(tags);
		_bindings = Collections.unmodifiableList(bindings);
		_validations = Collections.unmodifiableList(validations);
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

	/** Framework categorization tags (e.g. "update", "widget"); never null. */
	public List<String> getTags() {
		return _tags;
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
		final List<String> tags = new ArrayList<>();
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
			case "tags":
				for (Node t = el.getFirstChild(); t != null; t = t.getNextSibling()) {
					if (t.getNodeType() == Node.ELEMENT_NODE && "tag".equals(t.getNodeName())) {
						final String tag = text((Element) t);
						if (tag != null && !tag.isEmpty()) {
							tags.add(tag);
						}
					}
				}
				break;
			case "binding":
				bindings.add(parseBinding(el));
				break;
			case "validation":
				validations.add(new Validation(attr(el, "message")));
				break;
			default:
				// documentation (WO external pointer) and anything else: ignored for the preview.
				break;
			}
		}

		return new ApiextModel(className, componentContent, passthrough, emptyToNull(elementDoc), tags, bindings, validations);
	}

	private static Binding parseBinding(Element bindingEl) {
		final String name = attr(bindingEl, "name");
		final boolean required = "true".equalsIgnoreCase(attr(bindingEl, "required")) || "yes".equalsIgnoreCase(attr(bindingEl, "required"));
		final List<String> types = new ArrayList<>();
		String doc = null;
		for (Node n = bindingEl.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) n;
			if ("type".equals(el.getNodeName())) {
				final String type = text(el);
				if (type != null && !type.isEmpty()) {
					types.add(type);
				}
			}
			else if ("doc".equals(el.getNodeName()) && doc == null) {
				doc = text(el);
			}
		}
		return new Binding(name, types, emptyToNull(doc), required);
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

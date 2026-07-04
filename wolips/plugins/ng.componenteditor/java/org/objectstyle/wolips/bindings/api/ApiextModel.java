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

	/**
	 * A cross-binding constraint. Two concrete kinds — {@link Choose} (cardinality over a set of
	 * alternatives) and {@link Requires} (implication) — replace the legacy {@code <validation>}
	 * predicate language (see the apiext-format spec, § Constraints). Each carries an optional
	 * author {@code message} override; when absent, a consumer generates one from the typed rule
	 * (see {@link ApiextHtmlRenderer}). {@code getMessage()} returns the override or {@code null}.
	 */
	public static abstract class Constraint {
		private final String _message; // author override, or null → consumer generates

		Constraint(String message) {
			_message = emptyToNull(message);
		}

		/** The author's message override, or null when the consumer should generate one. */
		public String getMessage() {
			return _message;
		}
	}

	/**
	 * One alternative of a {@link Choose}, or the antecedent of a {@link Requires}: either a single
	 * binding, or an {@link #isAnyOf() any-of} group (satisfied iff ≥1 member bound, counting as
	 * exactly one satisfied alternative). Never nests — a group's members are always single bindings.
	 */
	public static final class Alternative {
		private final List<String> _bindingNames; // 1 = single binding; ≥2 = an <any-of> group

		Alternative(List<String> bindingNames) {
			_bindingNames = Collections.unmodifiableList(bindingNames);
		}

		static Alternative binding(String name) {
			final List<String> l = new ArrayList<>(1);
			l.add(name);
			return new Alternative(l);
		}

		/** True if this is an {@code <any-of>} group (≥2 bindings, OR-combined). */
		public boolean isAnyOf() {
			return _bindingNames.size() > 1;
		}

		/** The binding name(s): one for a single binding, several for an {@code <any-of>} group. */
		public List<String> getBindingNames() {
			return _bindingNames;
		}
	}

	/**
	 * {@code <choose min max>} — between {@code min} (default 0) and {@code max} (default unbounded)
	 * of the alternatives must be satisfied. {@code getMin()}/{@code getMax()} return null for an
	 * absent bound. Covers at-least-one ({@code min}), at-most-one ({@code max=1}) and exactly-one.
	 */
	public static final class Choose extends Constraint {
		private final Integer _min;
		private final Integer _max;
		private final List<Alternative> _alternatives;

		Choose(Integer min, Integer max, List<Alternative> alternatives, String message) {
			super(message);
			_min = min;
			_max = max;
			_alternatives = Collections.unmodifiableList(alternatives);
		}

		/** Lower bound, or null if the {@code min} attribute was absent (defaults to 0). */
		public Integer getMin() {
			return _min;
		}

		/** Upper bound, or null if the {@code max} attribute was absent (unbounded). */
		public Integer getMax() {
			return _max;
		}

		public List<Alternative> getAlternatives() {
			return _alternatives;
		}
	}

	/** The obligation a {@link Requires} places on its consequent binding. */
	public enum Obligation {
		/** Must be bound. Only valid with an antecedent (unconditional "bound" is {@code required=}). */
		BOUND,
		/** If bound, the value must be assignable (a keypath, not a constant). Does not imply BOUND. */
		SETTABLE,
		/** If bound, the value must be a resolvable keypath (not a constant). Does not imply BOUND. */
		GETTABLE;

		static Obligation parse(String s) {
			if ("settable".equalsIgnoreCase(s)) {
				return SETTABLE;
			}
			if ("gettable".equalsIgnoreCase(s)) {
				return GETTABLE;
			}
			return BOUND; // DTD default
		}
	}

	/**
	 * {@code <requires binding must when>} — when the antecedent holds, {@code binding} must meet the
	 * {@link Obligation}. The antecedent is a single binding ({@code when="x"}), an {@link Alternative
	 * any-of} child, or absent (unconditional — only legal for SETTABLE/GETTABLE). {@code getAntecedent()}
	 * returns null for the unconditional form.
	 */
	public static final class Requires extends Constraint {
		private final String _binding;
		private final Obligation _must;
		private final Alternative _antecedent; // null = unconditional

		Requires(String binding, Obligation must, Alternative antecedent, String message) {
			super(message);
			_binding = binding;
			_must = must;
			_antecedent = antecedent;
		}

		/** The consequent binding (must meet the obligation when the antecedent holds). */
		public String getBinding() {
			return _binding;
		}

		public Obligation getMust() {
			return _must;
		}

		/** The antecedent, or null for the unconditional form. */
		public Alternative getAntecedent() {
			return _antecedent;
		}
	}

	private final SourceKind _source;
	private final String _className;
	private final boolean _componentContent;
	private final boolean _passthrough;
	private final String _doc; // raw Markdown, or null
	private final List<Binding> _bindings;
	private final List<Constraint> _constraints;
	/** Verbatim messages from legacy {@code .api} {@code <validation>} rules — see {@link #fromApiSnapshot}. */
	private final List<String> _legacyMessages;
	/**
	 * Names of removed-grammar constructs ({@code validation}, {@code documentation}) found in an
	 * {@code .apiext} file — a spec violation the {@link ApiextConstraintValidator} flags. Empty for
	 * a well-formed {@code .apiext} and for the {@code .api} bridge.
	 */
	private final List<String> _legacyConstructs;

	/**
	 * Where the element comes from — its originating framework/bundle (e.g. "JavaWebObjects",
	 * "AjaxSlim"), or null if unknown. This is not part of the {@code .apiext} file; it's
	 * derived from the resolved {@code IType} and attached by the caller, because knowing the
	 * source framework is useful both in the element-help list and in the rendered card.
	 */
	private String _origin;

	private ApiextModel(SourceKind source, String className, boolean componentContent, boolean passthrough,
			String doc, List<Binding> bindings, List<Constraint> constraints, List<String> legacyMessages,
			List<String> legacyConstructs) {
		_source = source;
		_className = className;
		_componentContent = componentContent;
		_passthrough = passthrough;
		_doc = doc;
		_bindings = Collections.unmodifiableList(bindings);
		_constraints = Collections.unmodifiableList(constraints);
		_legacyMessages = Collections.unmodifiableList(legacyMessages);
		_legacyConstructs = Collections.unmodifiableList(legacyConstructs);
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
		// A legacy .api carries <validation> predicate trees, which have no typed representation in
		// the new constraint model (they are exactly what the .apiext format replaced). We don't
		// invent Choose/Requires for them — we surface their author messages verbatim as
		// legacyMessages, which the renderer shows as plain lines (no generated text, no grouping).
		final List<String> legacyMessages = new ArrayList<>();
		for (final ApiValidation v : api.getValidations()) {
			final String message = v.getMessage();
			if (message != null && !message.isEmpty()) {
				legacyMessages.add(message);
			}
		}
		return new ApiextModel(SourceKind.API, className, api.isComponentContent(), false,
				null, bindings, new ArrayList<>(), legacyMessages, new ArrayList<>());
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

	/** The typed cross-binding constraints ({@link Choose} / {@link Requires}). Empty for legacy {@code .api}. */
	public List<Constraint> getConstraints() {
		return _constraints;
	}

	/**
	 * Verbatim messages from legacy {@code .api} {@code <validation>} rules, which have no typed
	 * representation. Empty for {@code .apiext}. Shown by the renderer as plain lines.
	 */
	public List<String> getLegacyMessages() {
		return _legacyMessages;
	}

	/**
	 * Names of removed-grammar constructs ({@code validation}, {@code documentation}) found in this
	 * {@code .apiext} file — a spec violation. Empty for well-formed {@code .apiext} and for {@code .api}.
	 */
	public List<String> getLegacyConstructs() {
		return _legacyConstructs;
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
		// #17: .apiext uses wrapsContent (legacy .api's wocomponentcontent lives only on the .api path).
		final boolean componentContent = boolAttr(wo, "wrapsContent");
		final boolean passthrough = boolAttr(wo, "passthrough");

		String elementDoc = null;
		final List<Binding> bindings = new ArrayList<>();
		final List<Constraint> constraints = new ArrayList<>();
		final List<String> legacyConstructs = new ArrayList<>();

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
			case "choose":
				constraints.add(parseChoose(el));
				break;
			case "requires":
				constraints.add(parseRequires(el));
				break;
			case "validation":
			case "documentation":
				// Removed from the .apiext grammar (#9 replaced <validation>; #12 removed
				// <documentation>). A well-formed .apiext has none; we record the sighting so
				// ApiextConstraintValidator can flag it (a legacy construct in .apiext is an error,
				// not a fallback), but parse leniently so the rest of the file still renders.
				legacyConstructs.add(el.getNodeName());
				break;
			default:
				// Anything else unrecognized: ignored for the preview.
				break;
			}
		}

		return new ApiextModel(SourceKind.APIEXT, className, componentContent, passthrough,
				emptyToNull(elementDoc), bindings, constraints, new ArrayList<>(), legacyConstructs);
	}

	/**
	 * Parses a {@code <choose min max>} element. Structural gates the DTD can't express
	 * (≥2 alternatives, min/max presence/arithmetic) are enforced separately by
	 * {@link ApiextConstraintValidator}; here we parse leniently and let that layer report problems,
	 * so a slightly-malformed file still renders what it can rather than failing the whole model.
	 */
	private static Choose parseChoose(Element el) {
		final Integer min = intAttr(el, "min");
		final Integer max = intAttr(el, "max");
		final List<Alternative> alts = new ArrayList<>();
		for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element child = (Element) n;
			if ("binding".equals(child.getNodeName())) {
				alts.add(Alternative.binding(attr(child, "name")));
			}
			else if ("any-of".equals(child.getNodeName())) {
				alts.add(parseAnyOf(child));
			}
		}
		return new Choose(min, max, alts, attr(el, "message"));
	}

	/**
	 * Parses a {@code <requires binding must when>} element. The antecedent is {@code when="x"}, an
	 * {@code <any-of>} child, or absent (unconditional). If both {@code when} and an {@code <any-of>}
	 * are present the DTD form is ambiguous; we prefer the {@code <any-of>} child and let the
	 * validator flag the conflict.
	 */
	private static Requires parseRequires(Element el) {
		final String binding = attr(el, "binding");
		final Obligation must = Obligation.parse(attr(el, "must"));
		Alternative antecedent = null;
		final Element anyOf = firstChildElement(el, "any-of");
		if (anyOf != null) {
			antecedent = parseAnyOf(anyOf);
		}
		else {
			final String when = emptyToNull(attr(el, "when"));
			if (when != null) {
				antecedent = Alternative.binding(when);
			}
		}
		return new Requires(binding, must, antecedent, attr(el, "message"));
	}

	/** Parses an {@code <any-of>} group into an {@link Alternative} (its member binding names). */
	private static Alternative parseAnyOf(Element el) {
		final List<String> names = new ArrayList<>();
		for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() == Node.ELEMENT_NODE && "binding".equals(n.getNodeName())) {
				names.add(attr((Element) n, "name"));
			}
		}
		return new Alternative(names);
	}

	/** Parses a non-negative integer attribute, or null if absent/blank/unparseable. */
	private static Integer intAttr(Element el, String name) {
		final String s = emptyToNull(attr(el, name));
		if (s == null) {
			return null;
		}
		try {
			return Integer.valueOf(s.trim());
		}
		catch (NumberFormatException e) {
			return null; // the validator reports non-integer min/max
		}
	}

	private static Binding parseBinding(Element bindingEl) {
		final String name = attr(bindingEl, "name");
		final boolean required = boolAttr(bindingEl, "required");
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

	/**
	 * Reads a boolean attribute on the {@code .apiext} path. The grammar enumerates booleans as
	 * {@code (true|false)} (#13), so legacy spellings ({@code YES}/{@code yes}/{@code NO}) are not
	 * accepted here — only a literal {@code "true"} is true. (The tolerant legacy path reads {@code .api}
	 * booleans separately, via {@link ApiParser}; this method is {@code .apiext}-only.)
	 */
	private static boolean boolAttr(Element el, String name) {
		return "true".equals(attr(el, name));
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

package org.objectstyle.wolips.bindings.api;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;
import org.objectstyle.wolips.bindings.api.ApiextModel.TypeRef;
import org.objectstyle.wolips.bindings.api.ApiextModel.UnknownAttributes;

/**
 * Writes an {@link ApiextModel} back to {@code .apiext} XML — the write-path counterpart to
 * {@link ApiextModel#parse}, and the serializer the form editor saves through. Emits the current
 * grammar ({@code wrapsContent}, {@code unknownAttributes}, {@code <binding>} with
 * {@code <pull>}/{@code <push>}/{@code <default>}/{@code <deprecated>}, and the typed
 * {@code <choose>}/{@code <any-of>}/{@code <requires>} constraints).
 * <p>
 * A plain, ordered string writer (like {@link ApiSerializer}) — not a DOM/Transformer — with tab
 * indentation matching the hand-authored corpus. Because it regenerates from the model, the output
 * is <em>canonical</em>: author comments and incidental whitespace are not preserved (the form is
 * the source of truth; the file is its projection). Round-trip fidelity is at the model level —
 * {@code parse(serialize(m))} reproduces {@code m} — which is what the tests assert.
 */
public final class ApiextSerializer {

	private static final String TAB = "\t";

	private ApiextSerializer() {
		// static only
	}

	/** Serializes the model to a string. */
	public static String serialize(ApiextModel model) {
		final StringWriter sw = new StringWriter();
		try {
			serialize(model, sw);
		}
		catch (IOException e) {
			// StringWriter never throws.
			throw new IllegalStateException(e);
		}
		return sw.toString();
	}

	/** Serializes the model to the given writer (caller closes it). */
	public static void serialize(ApiextModel model, Writer w) throws IOException {
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		w.write("<wodefinitions>\n");
		serializeWo(model, w);
		w.write("</wodefinitions>\n");
	}

	private static void serializeWo(ApiextModel model, Writer w) throws IOException {
		w.write(TAB + "<wo class=\"");
		w.write(escapeAttr(model.getClassName()));
		w.write("\"");
		// content and unknownAttributes are nullable policies — emit only when declared.
		if (model.getContent() != null) {
			w.write(" content=\"" + model.getContent().name().toLowerCase() + "\"");
		}
		if (model.getUnknownAttributes() != null) {
			w.write(" unknownAttributes=\"" + policy(model.getUnknownAttributes()) + "\"");
		}
		w.write(">\n\n");

		if (model.getDoc() != null) {
			w.write(TAB + TAB);
			writeDoc(model.getDoc(), w);
			w.write("\n\n");
		}
		if (model.isDeprecated()) {
			w.write(TAB + TAB + "<deprecated>");
			w.write(escapeText(model.getDeprecationNote() == null ? "" : model.getDeprecationNote()));
			w.write("</deprecated>\n\n");
		}

		for (final Binding b : model.getBindings()) {
			serializeBinding(b, w);
			w.write("\n");
		}

		for (final Constraint c : model.getConstraints()) {
			serializeConstraint(c, w);
		}
		if (!model.getConstraints().isEmpty()) {
			w.write("\n");
		}

		w.write(TAB + "</wo>\n");
	}

	private static void serializeBinding(Binding b, Writer w) throws IOException {
		w.write(TAB + TAB + "<binding name=\"");
		w.write(escapeAttr(b.getName()));
		w.write("\"");
		if (b.getDefaults() != null) {
			w.write(" defaults=\"" + escapeAttr(b.getDefaults()) + "\"");
		}
		if (b.isRequired()) {
			w.write(" required=\"true\"");
		}
		w.write(">\n");

		writeDirection("pull", b.getPullTypes(), w);
		writeDirection("push", b.getPushTypes(), w);

		if (b.getDefaultValue() != null) {
			w.write(TAB + TAB + TAB + "<default>" + escapeText(b.getDefaultValue()) + "</default>\n");
		}
		if (b.getDoc() != null) {
			w.write(TAB + TAB + TAB);
			writeDoc(b.getDoc(), w);
			w.write("\n");
		}
		if (b.isDeprecated()) {
			w.write(TAB + TAB + TAB + "<deprecated>");
			w.write(escapeText(b.getDeprecationNote() == null ? "" : b.getDeprecationNote()));
			w.write("</deprecated>\n");
		}

		w.write(TAB + TAB + "</binding>\n");
	}

	/** Writes {@code <pull><type ...>…</type>…</pull>} on one line, matching the corpus. */
	private static void writeDirection(String dir, List<TypeRef> types, Writer w) throws IOException {
		if (types.isEmpty()) {
			return;
		}
		w.write(TAB + TAB + TAB + "<" + dir + ">");
		for (final TypeRef t : types) {
			w.write("<type");
			if (t.getInterpretation() != null) {
				w.write(" interpretation=\"" + escapeAttr(t.getInterpretation()) + "\"");
			}
			w.write(">" + escapeText(t.getName()) + "</type>");
		}
		w.write("</" + dir + ">\n");
	}

	private static void serializeConstraint(Constraint c, Writer w) throws IOException {
		if (c instanceof Choose) {
			serializeChoose((Choose) c, w);
		}
		else if (c instanceof Requires) {
			serializeRequires((Requires) c, w);
		}
	}

	private static void serializeChoose(Choose choose, Writer w) throws IOException {
		w.write(TAB + TAB + "<choose");
		if (choose.getMin() != null) {
			w.write(" min=\"" + choose.getMin() + "\"");
		}
		if (choose.getMax() != null) {
			w.write(" max=\"" + choose.getMax() + "\"");
		}
		if (choose.getMessage() != null) {
			w.write(" message=\"" + escapeAttr(choose.getMessage()) + "\"");
		}
		w.write(">\n");
		for (final Alternative alt : choose.getAlternatives()) {
			serializeAlternative(alt, w, TAB + TAB + TAB);
		}
		w.write(TAB + TAB + "</choose>\n");
	}

	private static void serializeRequires(Requires requires, Writer w) throws IOException {
		w.write(TAB + TAB + "<requires binding=\"" + escapeAttr(requires.getBinding()) + "\"");
		if (requires.getMust() != ApiextModel.Obligation.BOUND) {
			w.write(" must=\"" + requires.getMust().name().toLowerCase() + "\"");
		}
		final Alternative antecedent = requires.getAntecedent();
		// A single-binding antecedent serializes as the when= attribute; an any-of as a child.
		final boolean anyOfAntecedent = antecedent != null && antecedent.isAnyOf();
		if (antecedent != null && !anyOfAntecedent) {
			w.write(" when=\"" + escapeAttr(antecedent.getBindingNames().get(0)) + "\"");
		}
		if (requires.getMessage() != null) {
			w.write(" message=\"" + escapeAttr(requires.getMessage()) + "\"");
		}
		if (anyOfAntecedent) {
			w.write(">\n");
			serializeAlternative(antecedent, w, TAB + TAB + TAB);
			w.write(TAB + TAB + "</requires>\n");
		}
		else {
			w.write("/>\n");
		}
	}

	/** Serializes a {@code <choose>}/{@code <requires>} alternative: a bare binding or an any-of group. */
	private static void serializeAlternative(Alternative alt, Writer w, String indent) throws IOException {
		if (alt.isAnyOf()) {
			w.write(indent + "<any-of>");
			for (final String name : alt.getBindingNames()) {
				w.write("<binding name=\"" + escapeAttr(name) + "\"/>");
			}
			w.write("</any-of>\n");
		}
		else {
			w.write(indent + "<binding name=\"" + escapeAttr(alt.getBindingNames().get(0)) + "\"/>\n");
		}
	}

	/**
	 * Writes a {@code <doc>}: wrapped in CDATA when the text contains Markdown/XML specials (so the
	 * corpus's rich docs round-trip without escaping), else as escaped text.
	 */
	private static void writeDoc(String doc, Writer w) throws IOException {
		if (needsCdata(doc)) {
			w.write("<doc><![CDATA[" + doc + "]]></doc>");
		}
		else {
			w.write("<doc>" + escapeText(doc) + "</doc>");
		}
	}

	private static boolean needsCdata(String s) {
		// Use CDATA when the text carries characters that would otherwise need escaping or that
		// read badly escaped (the corpus wraps any doc with backticks, <, &, etc.).
		return s.indexOf('<') >= 0 || s.indexOf('&') >= 0 || s.indexOf('`') >= 0 || s.indexOf('>') >= 0;
	}

	private static String policy(UnknownAttributes p) {
		return p.name().toLowerCase();
	}

	private static String escapeAttr(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String escapeText(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

package org.objectstyle.wolips.bindings.api;

import java.util.List;

/**
 * Renders an {@link ApiextModel} to the HTML shown in the template editor's element
 * hover. The visual language deliberately mirrors AjaxSlim's {@code /element-reference}
 * page — status badges, a bindings table, a role/description block, a validation box —
 * so the {@code .apiext} look is consistent between the rendered reference and the hover.
 *
 * <p>Markdown in {@code <doc>} is rendered with an inline subset (inline {@code `code`},
 * {@code **bold**}, {@code *italic*}, {@code [links]()}, paragraph breaks, and fenced
 * code blocks) — which is what the AjaxSlim docs actually use.
 */
public final class ApiextHtmlRenderer {

	private ApiextHtmlRenderer() {
	}

	/**
	 * @param displayName the hover header (may include a "shortcut → Class" arrow)
	 * @param model       the parsed .apiext model
	 * @return a full HTML document suitable for a {@code BrowserInformationControl}
	 */
	public static String render(String displayName, ApiextModel model) {
		return "<html><head><style>" + css() + "</style></head><body>" + renderBody(displayName, model) + "</body></html>";
	}

	/** @return the CSS for the {@code .apiext} card (without the surrounding {@code <style>} tag). */
	public static String css() {
		return CSS;
	}

	/**
	 * Renders just the card's body content — no {@code <html>}/{@code <head>}/{@code <body>}
	 * wrapper. Used by callers that compose their own document (the hover wraps this in a
	 * single, scrollable {@code <body>} together with any error block), avoiding the nested
	 * {@code <html>} that results from embedding a full document.
	 */
	public static String renderBody(String displayName, ApiextModel model) {
		final StringBuilder b = new StringBuilder(2048);

		// Header. The source marker (.api / .apiext) is pinned to the top-right corner,
		// away from the element's own categorization badges. The title row carries the
		// element name; a "status" row below the name carries the structural badges
		// (container element, binding passthrough).
		b.append("<div class=\"hdr\">");

		// Source marker, top-right corner.
		b.append("<span class=\"src src-").append(model.getSource() == ApiextModel.SourceKind.APIEXT ? "apiext" : "api")
				.append("\">").append(esc(model.getSource().getLabel())).append("</span>");

		// Title row: element name.
		b.append("<h3><code>").append(esc(displayName)).append("</code></h3>");

		// Origin: which framework/bundle the element comes from (when known).
		if (model.getOrigin() != null && !model.getOrigin().isEmpty()) {
			b.append("<div class=\"origin\">from ").append(esc(model.getOrigin())).append("</div>");
		}

		// Status row: structural badges below the name (only if any apply).
		if (model.isComponentContent() || model.isPassthrough()) {
			b.append("<div class=\"status\">");
			if (model.isComponentContent()) {
				b.append("<span class=\"badge b-container\">Container element</span>");
			}
			if (model.isPassthrough()) {
				b.append("<span class=\"badge b-passthrough\">Binding passthrough</span>");
			}
			b.append("</div>");
		}

		b.append("</div>");

		// Role / description (element-level doc).
		if (model.getDoc() != null) {
			b.append("<div class=\"role\">").append(markdown(model.getDoc())).append("</div>");
		}

		// Bindings table, listed alphabetically by name (a copy — we don't reorder the model).
		// (Required-first ordering is a possible future tweak; alphabetical is the default.)
		final List<ApiextModel.Binding> bindings = new java.util.ArrayList<>(model.getBindings());
		bindings.sort(java.util.Comparator.comparing(bd -> bd.getName() == null ? "" : bd.getName().toLowerCase()));
		if (!bindings.isEmpty()) {
			b.append("<table class=\"bindings\"><thead><tr><th>Binding</th><th>Type</th><th>Description</th></tr></thead><tbody>");
			for (final ApiextModel.Binding binding : bindings) {
				b.append("<tr><td class=\"b-name\">");
				if (binding.isRequired()) {
					b.append("<span class=\"req\">&bull;</span> ");
				}
				b.append(esc(binding.getName())).append("</td>");
				b.append("<td class=\"b-type\">").append(typeCell(binding)).append("</td>");
				b.append("<td>").append(binding.getDoc() != null ? markdown(binding.getDoc()) : "").append("</td>");
				b.append("</tr>");
			}
			b.append("</tbody></table>");
		}

		// Validation rules.
		if (!model.getValidations().isEmpty()) {
			b.append("<div class=\"valids\">");
			for (final ApiextModel.Validation v : model.getValidations()) {
				if (v.getMessage() != null) {
					b.append("<p class=\"vmsg\">").append(esc(v.getMessage())).append("</p>");
				}
			}
			b.append("</div>");
		}

		return b.toString();
	}

	/**
	 * Renders a tidy "no API" card for an element that has neither an {@code .api} nor an
	 * {@code .apiext} definition: the element name, its origin (when known), and a muted
	 * empty-state message — instead of a bare name floating in an empty popup. Reuses the
	 * same {@code <body>} CSS via {@link #css()} so it looks consistent with real cards.
	 *
	 * @param displayName the element name (may include a "shortcut → Class" arrow)
	 * @param origin      the originating framework/bundle, or null if unknown
	 * @return the body-fragment HTML
	 */
	public static String renderNoApiBody(String displayName, String origin) {
		final StringBuilder b = new StringBuilder(256);
		b.append("<div class=\"hdr\">");
		b.append("<h3><code>").append(esc(displayName)).append("</code></h3>");
		if (origin != null && !origin.isEmpty()) {
			b.append("<div class=\"origin\">from ").append(esc(origin)).append("</div>");
		}
		b.append("</div>");
		b.append("<div class=\"no-api\">No API documentation available for this element.</div>");
		return b.toString();
	}

	/**
	 * Renders the Type cell for a binding, with directionality arrows:
	 * <ul>
	 *   <li>pull only &rarr; a grey {@code ↓} (read; the norm, so it recedes)</li>
	 *   <li>push only &rarr; an orange {@code ↑} (writes back &mdash; a behaviour change)</li>
	 *   <li>pull and push with the <em>same</em> type(s) &rarr; one row, red {@code ↕} (two-way)</li>
	 *   <li>pull and push with <em>different</em> types &rarr; two rows ({@code ↓} pull, {@code ↑} push)</li>
	 *   <li>no direction info (legacy {@code <type>} / {@code .api}) &rarr; the type(s), no arrow</li>
	 * </ul>
	 */
	private static String typeCell(ApiextModel.Binding binding) {
		final List<ApiextModel.TypeRef> pull = binding.getPullTypes();
		final List<ApiextModel.TypeRef> push = binding.getPushTypes();

		// No directionality (e.g. a binding from a plain .api file, which carries no type
		// info): empty type cell, no arrow.
		if (pull.isEmpty() && push.isEmpty()) {
			return "";
		}

		// Both directions with identical type sets: a single two-way row.
		if (!pull.isEmpty() && pull.equals(push)) {
			return dirRow("dir-both", "↕", "Two-way: pulled (read) and pushed (written back)", pull);
		}

		// Otherwise each present direction gets its own row.
		final StringBuilder sb = new StringBuilder();
		if (!pull.isEmpty()) {
			sb.append(dirRow("dir-pull", "↓", "Pulled (read by the element)", pull));
		}
		if (!push.isEmpty()) {
			sb.append(dirRow("dir-push", "↑", "Pushed (written back by the element)", push));
		}
		return sb.toString();
	}

	/** One directionality row: an arrow (coloured by {@code dirClass}) followed by the joined types. */
	private static String dirRow(String dirClass, String arrow, String title, List<ApiextModel.TypeRef> types) {
		return "<div class=\"dir-row\"><span class=\"dir " + dirClass + "\" title=\"" + esc(title) + "\">"
				+ arrow + "</span> " + esc(joinTypes(types)) + "</div>";
	}

	/** Joins type refs into a short, readable string (simple names + interpretation, " | "-separated). */
	private static String joinTypes(List<ApiextModel.TypeRef> types) {
		if (types == null || types.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < types.size(); i++) {
			if (i > 0) {
				sb.append(" | ");
			}
			final ApiextModel.TypeRef t = types.get(i);
			sb.append(simpleName(t.getName()));
			if (t.getInterpretation() != null) {
				sb.append(" (").append(t.getInterpretation()).append(")");
			}
		}
		return sb.toString();
	}

	/** "com.webobjects.appserver.WOActionResults" → "WOActionResults"; value-set names pass through. */
	private static String simpleName(String type) {
		final int dot = type.lastIndexOf('.');
		return dot >= 0 ? type.substring(dot + 1) : type;
	}

	// ---- minimal inline-Markdown → HTML ------------------------------------

	/**
	 * Renders the Markdown subset the .apiext docs use: paragraphs (blank-line
	 * separated), fenced ``` code blocks, and inline {@code `code`}, {@code **bold**},
	 * {@code *italic*}, {@code [text](url)}. Deliberately small — not a full Markdown
	 * implementation; the format only uses these.
	 */
	static String markdown(String md) {
		final StringBuilder out = new StringBuilder();
		final String[] lines = md.replace("\r\n", "\n").split("\n", -1);

		boolean inCode = false;
		String codeLang = null;
		final StringBuilder code = new StringBuilder();
		final StringBuilder para = new StringBuilder();

		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.startsWith("```")) {
				if (inCode) {
					// close code block
					out.append("<pre class=\"md-code\"><code");
					if (codeLang != null && !codeLang.isEmpty()) {
						out.append(" class=\"lang-").append(esc(codeLang)).append('"');
					}
					out.append('>').append(esc(code.toString())).append("</code></pre>");
					code.setLength(0);
					inCode = false;
					codeLang = null;
				}
				else {
					flushParagraph(out, para);
					inCode = true;
					codeLang = trimmed.length() > 3 ? trimmed.substring(3).trim() : null;
				}
				continue;
			}
			if (inCode) {
				code.append(line).append('\n');
				continue;
			}
			if (trimmed.isEmpty()) {
				flushParagraph(out, para);
			}
			else {
				if (para.length() > 0) {
					para.append(' ');
				}
				para.append(line);
			}
		}
		// Trailing unterminated code fence: emit what we have rather than lose it.
		if (inCode && code.length() > 0) {
			out.append("<pre class=\"md-code\"><code>").append(esc(code.toString())).append("</code></pre>");
		}
		flushParagraph(out, para);
		return out.toString();
	}

	private static void flushParagraph(StringBuilder out, StringBuilder para) {
		if (para.length() == 0) {
			return;
		}
		out.append("<p>").append(inline(para.toString())).append("</p>");
		para.setLength(0);
	}

	/**
	 * Applies inline Markdown to already-paragraph-joined text. Backtick code spans are
	 * extracted first (and their content escaped, not interpreted) so emphasis markers
	 * inside code aren't mangled; the surrounding text is escaped then re-marked.
	 */
	private static String inline(String text) {
		final StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			final char c = text.charAt(i);
			if (c == '`') {
				final int end = text.indexOf('`', i + 1);
				if (end > i) {
					out.append("<code>").append(esc(text.substring(i + 1, end))).append("</code>");
					i = end + 1;
					continue;
				}
			}
			// Accumulate a run of non-code text, then apply emphasis/links to it.
			final int nextCode = text.indexOf('`', i);
			final int runEnd = nextCode == -1 ? text.length() : nextCode;
			out.append(emphasisAndLinks(text.substring(i, runEnd)));
			i = runEnd;
		}
		return out.toString();
	}

	/** Escapes, then applies **bold**, *italic*, and [text](url) on non-code text. */
	private static String emphasisAndLinks(String raw) {
		String s = esc(raw);
		// links: [text](url)
		s = s.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", "<a href=\"$2\">$1</a>");
		// bold then italic (bold first so ** isn't eaten by single-* rule)
		s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
		s = s.replaceAll("(?<!\\*)\\*(?!\\*)([^*]+)\\*(?!\\*)", "<em>$1</em>");
		return s;
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		final StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			switch (c) {
			case '&':
				b.append("&amp;");
				break;
			case '<':
				b.append("&lt;");
				break;
			case '>':
				b.append("&gt;");
				break;
			case '"':
				b.append("&quot;");
				break;
			default:
				b.append(c);
			}
		}
		return b.toString();
	}

	/** Hover-sized adaptation of the /element-reference page styling. */
	private static final String CSS = "body{font-family:-apple-system,system-ui,sans-serif;font-size:10pt;color:#1a1a1a;line-height:1.5;margin:0;padding:4px 6px;}"
				+ ".hdr{position:relative;padding-right:64px;margin:.1rem 0 .4rem;}"
				+ "h3{margin:0;font-size:1.1em;display:flex;align-items:baseline;gap:.4rem;flex-wrap:wrap;}"
				+ "h3 code{background:none;padding:0;color:#1a4f8a;font-weight:700;}"
				+ ".src{position:absolute;top:0;right:0;font-size:.66em;font-weight:600;font-family:ui-monospace,Menlo,monospace;padding:.05rem .35rem;border-radius:4px;white-space:nowrap;}"
				+ ".src-apiext{background:#eef6ff;color:#1c4f8a;border:1px solid #c5dcf6;}"
				+ ".src-api{background:#f1f3f5;color:#6c757d;border:1px solid #d3d8de;}"
				+ ".origin{color:#999;font-size:.82em;margin:.05rem 0 .1rem;}"
				+ ".no-api{color:#9aa0a8;font-style:italic;font-size:.9em;margin:.6rem 0 .2rem;}"
				+ ".status{display:flex;gap:.35rem;flex-wrap:wrap;margin-top:.3rem;}"
				+ ".badge{font-size:.68em;font-weight:600;text-transform:uppercase;letter-spacing:.03em;padding:.05rem .4rem;border-radius:4px;white-space:nowrap;}"
				+ ".b-container{background:#fff3e0;color:#9a5b17;border:1px solid #f0d2a8;}"
				+ ".b-passthrough{background:#efe9ff;color:#5b3aa3;border:1px solid #d4c8f5;}"
				+ "code{background:#f4f4f4;padding:.02rem .25rem;border-radius:3px;font-size:.9em;}"
				+ "a{color:#2b6cb0;text-decoration:none;}"
				+ ".role p{margin:.2rem 0 .4rem;}"
				+ "table.bindings{width:100%;border-collapse:collapse;margin:.4rem 0 .2rem;font-size:.92em;}"
				+ "table.bindings th{text-align:left;color:#777;font-weight:600;font-size:.8em;text-transform:uppercase;border-bottom:2px solid #eee;padding:.2rem .4rem;}"
				+ "table.bindings td{padding:.25rem .4rem;border-bottom:1px solid #f2f2f2;vertical-align:top;}"
				+ "table.bindings td.b-name{white-space:nowrap;font-family:ui-monospace,Menlo,monospace;color:#1a4f8a;font-size:.9em;}"
				+ "table.bindings td.b-type{white-space:nowrap;color:#888;font-size:.85em;font-family:ui-monospace,monospace;}"
				+ ".dir-row{white-space:nowrap;line-height:1.5;}"
				+ ".dir{font-weight:400;}"
				+ ".dir-pull{color:#c2c8d0;}"
				+ ".dir-push{color:#e8730c;}"
				+ ".dir-both{color:#e0142b;}"
				+ "table.bindings td p{margin:0;} table.bindings td p+p{margin-top:.4rem;}"
				+ ".req{color:#c0392b;font-weight:700;}"
				+ "pre.md-code{background:#f7f8fa;border:1px solid #e6e8ec;border-left:3px solid #2b6cb0;border-radius:0 5px 5px 0;padding:.4rem .6rem;margin:.4rem 0;overflow-x:auto;font-size:.85em;}"
				+ "pre.md-code code{background:none;padding:0;}"
				+ ".valids{margin:.4rem 0 0;padding:.4rem .6rem;background:#fffbea;border-left:3px solid #f0c000;border-radius:4px;font-size:.85em;}"
				+ ".valids .vmsg{margin:.1rem 0;color:#7a5b00;}";
}

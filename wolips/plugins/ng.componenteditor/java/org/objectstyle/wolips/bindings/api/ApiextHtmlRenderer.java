package org.objectstyle.wolips.bindings.api;

import java.util.List;

/**
 * Renders an {@link ApiextModel} to the HTML shown in the template editor's element
 * hover. The visual language deliberately mirrors AjaxSlim's {@code /element-reference}
 * page — tag badges, a bindings table, a role/description block, a validation box — so
 * the {@code .apiext} look is consistent between the rendered reference and the hover.
 *
 * <p>Markdown in {@code <doc>} is rendered with an inline subset (inline {@code `code`},
 * {@code **bold**}, {@code *italic*}, {@code [links]()}, paragraph breaks, and fenced
 * code blocks) — which is what the AjaxSlim docs actually use.
 */
public final class ApiextHtmlRenderer {

	private ApiextHtmlRenderer() {
	}

	/** Tag value → CSS class, matching the reference page's badge styling. */
	private static String tagClass(String tag) {
		switch (tag.toLowerCase()) {
		case "update":
			return "t-update";
		case "widget":
			return "t-widget";
		case "trigger":
		case "activity":
			return "t-trigger";
		case "server":
			return "t-server";
		default:
			return "t-update";
		}
	}

	/**
	 * @param displayName the hover header (may include a "shortcut → Class" arrow)
	 * @param model       the parsed .apiext model
	 * @return a full HTML document suitable for a {@code BrowserInformationControl}
	 */
	public static String render(String displayName, ApiextModel model) {
		final StringBuilder b = new StringBuilder(2048);
		b.append("<html><head><style>").append(css()).append("</style></head><body>");

		// Header: element name + tag badges + passthrough badge.
		b.append("<h3><code>").append(esc(displayName)).append("</code>");
		for (final String tag : model.getTags()) {
			b.append(" <span class=\"tag ").append(tagClass(tag)).append("\">").append(esc(cap(tag))).append("</span>");
		}
		if (model.isPassthrough()) {
			b.append(" <span class=\"tag t-passthrough\">Passthrough</span>");
		}
		if (model.isComponentContent()) {
			b.append(" <span class=\"muted\">has content</span>");
		}
		b.append("</h3>");

		// Role / description (element-level doc).
		if (model.getDoc() != null) {
			b.append("<div class=\"role\">").append(markdown(model.getDoc())).append("</div>");
		}

		// Bindings table.
		final List<ApiextModel.Binding> bindings = model.getBindings();
		if (!bindings.isEmpty()) {
			b.append("<table class=\"bindings\"><thead><tr><th>Binding</th><th>Type</th><th>Description</th></tr></thead><tbody>");
			for (final ApiextModel.Binding binding : bindings) {
				b.append("<tr><td class=\"b-name\">");
				if (binding.isRequired()) {
					b.append("<span class=\"req\">&bull;</span> ");
				}
				b.append(esc(binding.getName())).append("</td>");
				b.append("<td class=\"b-type\">").append(esc(joinTypes(binding.getTypes()))).append("</td>");
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

		b.append("</body></html>");
		return b.toString();
	}

	/** Joins a binding's accepted types into a short, readable string (simple names, " | "-separated). */
	private static String joinTypes(List<String> types) {
		if (types == null || types.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < types.size(); i++) {
			if (i > 0) {
				sb.append(" | ");
			}
			sb.append(simpleName(types.get(i)));
		}
		return sb.toString();
	}

	/** "com.webobjects.appserver.WOActionResults" → "WOActionResults"; value-set names pass through. */
	private static String simpleName(String type) {
		final int dot = type.lastIndexOf('.');
		return dot >= 0 ? type.substring(dot + 1) : type;
	}

	private static String cap(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
	private static String css() {
		return "body{font-family:-apple-system,system-ui,sans-serif;font-size:9pt;color:#1a1a1a;line-height:1.5;margin:0;padding:4px 6px;}"
				+ "h3{margin:.1rem 0 .3rem;font-size:1.1em;display:flex;align-items:baseline;gap:.4rem;flex-wrap:wrap;}"
				+ "h3 code{background:none;padding:0;color:#1a4f8a;font-weight:700;}"
				+ ".muted{color:#999;font-size:.8em;font-weight:normal;}"
				+ ".tag{font-size:.68em;font-weight:600;text-transform:uppercase;letter-spacing:.03em;padding:.05rem .35rem;border-radius:4px;white-space:nowrap;}"
				+ ".t-widget{background:#e6fbe6;color:#167a16;border:1px solid #b5e6b5;}"
				+ ".t-update{background:#e3f0ff;color:#1c4f8a;border:1px solid #bcd8f5;}"
				+ ".t-trigger{background:#fff3e0;color:#9a5b17;border:1px solid #f0d2a8;}"
				+ ".t-server{background:#efe9ff;color:#5b3aa3;border:1px solid #d4c8f5;}"
				+ ".t-passthrough{background:#f1f3f5;color:#495057;border:1px solid #d3d8de;}"
				+ "code{background:#f4f4f4;padding:.02rem .25rem;border-radius:3px;font-size:.9em;}"
				+ "a{color:#2b6cb0;text-decoration:none;}"
				+ ".role p{margin:.2rem 0 .4rem;}"
				+ "table.bindings{width:100%;border-collapse:collapse;margin:.4rem 0 .2rem;font-size:.92em;}"
				+ "table.bindings th{text-align:left;color:#777;font-weight:600;font-size:.8em;text-transform:uppercase;border-bottom:2px solid #eee;padding:.2rem .4rem;}"
				+ "table.bindings td{padding:.25rem .4rem;border-bottom:1px solid #f2f2f2;vertical-align:top;}"
				+ "table.bindings td.b-name{white-space:nowrap;font-family:ui-monospace,Menlo,monospace;color:#1a4f8a;font-size:.9em;}"
				+ "table.bindings td.b-type{white-space:nowrap;color:#888;font-size:.85em;font-family:ui-monospace,monospace;}"
				+ "table.bindings td p{margin:0;} table.bindings td p+p{margin-top:.4rem;}"
				+ ".req{color:#c0392b;font-weight:700;}"
				+ "pre.md-code{background:#f7f8fa;border:1px solid #e6e8ec;border-left:3px solid #2b6cb0;border-radius:0 5px 5px 0;padding:.4rem .6rem;margin:.4rem 0;overflow-x:auto;font-size:.85em;}"
				+ "pre.md-code code{background:none;padding:0;}"
				+ ".valids{margin:.4rem 0 0;padding:.4rem .6rem;background:#fffbea;border-left:3px solid #f0c000;border-radius:4px;font-size:.85em;}"
				+ ".valids .vmsg{margin:.1rem 0;color:#7a5b00;}";
	}
}

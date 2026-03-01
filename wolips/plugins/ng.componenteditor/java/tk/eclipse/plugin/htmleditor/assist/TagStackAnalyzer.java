package tk.eclipse.plugin.htmleditor.assist;

import java.util.Stack;

/**
 * Analyzes HTML/template source text to determine the current tag context at
 * a given cursor position. Builds a stack of unclosed tags by scanning the
 * source up to the cursor, then reports:
 *
 * <ul>
 *   <li><b>word</b> — the partial token at the cursor (may start with "&lt;"
 *       if the user is mid-tag)</li>
 *   <li><b>prevTag</b> — the most recently opened tag name (for attribute
 *       completion)</li>
 *   <li><b>lastTag</b> — the innermost unclosed tag name (for close-tag
 *       completion)</li>
 *   <li><b>attr</b> — the most recently seen attribute name</li>
 * </ul>
 *
 * <p>This class is a pure-logic extraction from {@link HTMLAssistProcessor}
 * with no Eclipse/SWT dependencies, making it directly unit-testable.
 */
public class TagStackAnalyzer {

	/**
	 * Analyzes the given HTML text (typically the document content from the
	 * start up to the cursor position) and returns the tag context.
	 *
	 * @param text the source text to analyze
	 * @return a four-element array: {@code [word, prevTag, lastTag, attr]}
	 */
	public static String[] getLastWord(String text) {
		StringBuffer sb = new StringBuffer();
		Stack<String> stack = new Stack<String>();
		String word = "";
		String prevTag = "";
		String lastTag = "";
		String attr = "";
		String temp1 = "";
		String temp2 = "";
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			// skip scriptlet
			if (c == '<' && text.length() > i + 1 && text.charAt(i + 1) == '%') {
				i = text.indexOf("%>", i + 2);
				if (i == -1) {
					i = text.length();
				}
				continue;
			}
			// skip XML declaration
			if (c == '<' && text.length() > i + 1 && text.charAt(i + 1) == '?') {
				i = text.indexOf("?>", i + 2);
				if (i == -1) {
					i = text.length();
				}
				continue;
			}
			if (isDelimiter(c)) {
				temp1 = sb.toString();
				// Skip whitespace inside a quoted attribute value (e.g. value="hello world").
				// Only when inside a tag — an apostrophe in body text (e.g. "what's")
				// must not start quote tracking, or it swallows subsequent content.
				if (!prevTag.equals("") && temp1.length() > 1 && ((temp1.startsWith("\"") && !temp1.endsWith("\"") && c != '"') || (temp1.startsWith("'") && !temp1.endsWith("'") && c != '\''))) {
					sb.append(c);
					continue;
				}

				if (!temp1.equals("")) {
					temp2 = temp1;
					if (temp2.endsWith("=") && !prevTag.equals("") && !temp2.equals("=")) {
						attr = temp2.substring(0, temp2.length() - 1);
					}
				}
				if (temp1.startsWith("<") && !temp1.startsWith("</") && !temp1.startsWith("<!")) {
					prevTag = temp1.substring(1);
					if (!temp1.endsWith("/")) {
						stack.push(prevTag);
					}
				}
				else if (temp1.startsWith("</") && stack.size() != 0) {
					stack.pop();
				}
				else if (temp1.endsWith("/") && stack.size() != 0
						&& !temp1.startsWith("\"") && !temp1.startsWith("'")) {
					// Self-closing tag marker (e.g. <img />) — pop the tag we just pushed.
					// Guard against quoted attribute values like href="/" which also end
					// with "/" but must not affect the tag stack.
					stack.pop();
				}
				sb.setLength(0);

				if (c == '<') {
					sb.append(c);
				}
				else if ((c == '"' || c == '\'') && !prevTag.equals("")) {
					// Start tracking a quoted attribute value — only when inside
					// a tag. Apostrophes in body text (e.g. "what's") must not
					// start quote tracking, or subsequent content gets swallowed.
					if (temp1.startsWith("\"") || temp1.startsWith("'")) {
						sb.append(temp1);
					}
					sb.append(c);
				}
				else if (c == '>') {
					prevTag = "";
					attr = "";
				}
			}
			else {
				if (c == '=' && !prevTag.equals("")) {
					attr = temp2.trim();
				}
				temp1 = sb.toString();
				if (temp1.length() > 1 && ((temp1.startsWith("\"") && temp1.endsWith("\"")) || (temp1.startsWith("'") && temp1.endsWith("'")))) {
					sb.setLength(0);
				}
				sb.append(c);
			}
		}

		if (stack.size() != 0) {
			lastTag = stack.pop();
		}
		if (attr.endsWith("=")) {
			attr = attr.substring(0, attr.length() - 1);
		}
		word = sb.toString();

		return new String[] { word, prevTag, lastTag, attr };
	}

	/**
	 * Tests whether a character is a token delimiter in the tag-stack scanner.
	 */
	public static boolean isDelimiter(char c) {
		if (c == ' ' || c == '(' || c == ')' || c == ','
				|| c == ';' || c == '\n' || c == '\r' || c == '\t' || c == '+' || c == '>' || c == '<' || c == '*' || c == '^'
				|| c == '"' || c == '\'') {
			return true;
		}
		return false;
	}
}

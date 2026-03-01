package jp.aonir.fuzzyxml.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FuzzyXMLUtil {

  /** XML */
  private static Pattern encoding = Pattern.compile("<\\?xml\\s+[^\\?>]*?encoding\\s*=\\s*\"(.*?)\"[^\\?>]*?\\?>");

  private static Pattern script = Pattern.compile("(<script.*?>)(.*?)(</script>)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  private static Pattern woTag = Pattern.compile("<(/*)(wo|webobject)(s*[^>]*)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static Pattern whiteSpace = Pattern.compile("([\\ \\t\\r\\n])");

  /**
   * &lt;script ... &gt; &lt;/script&gt;
   * 
   * @param source XML
   * @return 
   */
  public static String escapeScript(String source) {
    StringBuffer sb = new StringBuffer();
    int lastIndex = 0;
    Matcher matcher = script.matcher(source);
    while (matcher.find()) {
      if (matcher.start() != 0) {
        sb.append(source.substring(lastIndex, matcher.start()));
      }
      sb.append(matcher.group(1));
      String group2 = matcher.group(2);
      //sb.append("<!--");
      group2 = woTag.matcher(group2).replaceAll("[WOOPEN]$1$2$3[WOCLOSE]");
      group2 = group2.replaceAll("<", " ");
      group2 = group2.replaceAll(">", " ");
      group2 = group2.replaceAll("\\[WOOPEN\\]", "<");
      group2 = group2.replaceAll("\\[WOCLOSE\\]", ">");
      sb.append(group2);
      /*
      for (int i = 0; i < group2.length(); i++) {
        sb.append(" ");
      }
      */
      //sb.append("-->");
      sb.append(matcher.group(3));
      lastIndex = matcher.end();
    }
    if (lastIndex < source.length()) {
      sb.append(source.substring(lastIndex));
    }
    return sb.toString();
  }

  /**
   * Patterns for matching {@code <p:raw>} and {@code <p:comment>} open tags.
   * Used by {@link #pBlock2space} to find block boundaries.
   */
  private static Pattern pBlockOpenTag = Pattern.compile(
      "<(p:raw|p:comment)(\\s[^>]*)?>",
      Pattern.CASE_INSENSITIVE);

  /**
   * Replaces the content inside {@code <p:raw>...</p:raw>} and
   * {@code <p:comment>...</p:comment>} blocks with spaces, preserving the
   * open and close tags. This must run before other preprocessing steps
   * ({@link #escapeString}, {@link #comment2space}, etc.) so that broken
   * or unclosed quotes inside these blocks don't corrupt the preprocessed
   * source.
   */
  public static String pBlock2space(String source) {
    StringBuffer sb = new StringBuffer();
    int last = 0;
    Matcher openMatcher = pBlockOpenTag.matcher(source);
    while (openMatcher.find()) {
      String tagName = openMatcher.group(1); // "p:raw" or "p:comment"
      int openTagEnd = openMatcher.end();
      // Find the corresponding close tag (case-insensitive)
      String closeTag = "</" + tagName + ">";
      int closeIndex = source.toLowerCase().indexOf(closeTag.toLowerCase(), openTagEnd);
      if (closeIndex == -1) {
        // No close tag — leave the rest of the source as-is
        break;
      }
      // Copy everything up to and including the open tag
      sb.append(source.substring(last, openTagEnd));
      // Replace content between open and close tags with spaces
      for (int i = openTagEnd; i < closeIndex; i++) {
        sb.append(' ');
      }
      // Copy the close tag itself
      int closeTagEnd = closeIndex + closeTag.length();
      sb.append(source.substring(closeIndex, closeTagEnd));
      last = closeTagEnd;
    }
    if (last < source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  /**
   * @param source
   * @return
   */
  public static String escapeString(String source) {
    StringBuffer sb = new StringBuffer();
    int flag = 0;
    boolean tag = false;
    boolean escape = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (tag) {
        // MS: I took out escaping .. This is potentially a really sketchy thing to do, but it
        // was breaking attributes like   numberformat = "\$#,##0.00"
        // Q: Added back in but handle escaping differently now
        if ((flag == 1 || flag == 2) && c == '\\') {
          escape = true;
          continue;
        }
        else if (flag == 0 && c == '"') {
          flag = 1;
        }
        else if (flag == 1 && c == '"') {
          if (!escape) {
            flag = 0;
          } else {
            sb.append('\\');
          }
          escape = false;
        }
        else if (flag == 0 && c == '\'') {
          flag = 2;
        }
        else if (flag == 2 && c == '\'') {
          if (!escape) {
            flag = 0;
          } else {
            sb.append('\\');
          }
          escape = false;
        }
        else if ((flag == 1 || flag == 2)) {
          sb.append(' ');
          if (escape) {
            sb.append(' ');
            escape = false;
          }
          continue;
        }
        else if (flag == 0 && c == '>') {
          tag = false;
        }
      }
      else if (c == '<') {
        tag = true;
      }
      sb.append(c);
    }

    return sb.toString();
  }

  /**
   * HTML/JSP/XML

   * <ul>
   *   <li>&lt;!-- --&gt;</li>
   * </ul>
   * 
   * @param source XML
   * @param contentsOnly true
   *                     false
   * @return
   */
  public static String comment2space(String source, boolean contentsOnly) {
    int index = 0;
    int last = 0;
    StringBuffer sb = new StringBuffer();
    while ((index = source.indexOf("<!--", last)) != -1) {
      int end = source.indexOf("-->", index);
      if (end != -1) {
        sb.append(source.substring(last, index));
        int length = end - index + 3;
        if (contentsOnly) {
          sb.append("<!--");
          length = length - 7;
        }

        int i = 0;
        Matcher woMatcher = woTag.matcher(source.substring(index + 4, end));
        while (woMatcher.find()) {
          int woTagStart = woMatcher.start();
          int woTagEnd = woMatcher.end();
          for (; woTagStart > i; woTagStart --) {
            sb.append(" ");
          }
          sb.append(woMatcher.group());
          i = woTagEnd;
        }
        
        for (; i < length; i++) {
          sb.append(" ");
        }
        
        if (contentsOnly) {
          sb.append("-->");
        }
      }
      else {
        break;
      }
      last = end + 3;
    }
    if (last != source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  public static String cdata2space(String source, boolean contentsOnly) {
    int index = 0;
    int last = 0;
    StringBuffer sb = new StringBuffer();
    while ((index = source.indexOf("<![CDATA[", last)) != -1) {
      int end = source.indexOf("]]>", index);
      if (end != -1) {
        sb.append(source.substring(last, index));
        int length = end - index + 3;
        if (contentsOnly) {
          sb.append("<![CDATA[");
          length = length - 12;
        }
        for (int i = 0; i < length; i++) {
          sb.append(" ");
        }
        if (contentsOnly) {
          sb.append("]]>");
        }
      }
      else {
        break;
      }
      last = end + 3;
    }
    if (last != source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  public static String doctype2space(String source, boolean contentsOnly) {
    int index = 0;
    int last = 0;
    StringBuffer sb = new StringBuffer();
    while ((index = source.indexOf("<!DOCTYPE", last)) != -1) {
      sb.append(source.substring(last, index));
      if (contentsOnly) {
        sb.append("<!DOCTYPE");
      }
      else {
        sb.append("         ");
      }
      boolean flag = false;
      for (index = index + 9; index < source.length(); index++) {
        char c = source.charAt(index);
        if (c == '[') {
          flag = true;
        }
        if (flag == true && c == ']') {
          flag = false;
        }
        if (flag == false && c == '>') {
          if (contentsOnly) {
            sb.append('>');
          }
          else {
            sb.append(' ');
          }
          break;
        }
        sb.append(" ");
      }
      last = index + 1;
    }
    if (last != source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  public static String processing2space(String source, boolean contentsOnly) {
    int index = 0;
    int last = 0;
    StringBuffer sb = new StringBuffer();
    while ((index = source.indexOf("<?", last)) != -1) {
      int end = source.indexOf("?>", index);
      if (end != -1) {
        sb.append(source.substring(last, index));
        int length = end - index + 2;
        if (contentsOnly) {
          sb.append("<?");
          length = length - 4;
        }
        for (int i = 0; i < length; i++) {
          sb.append(" ");
        }
        if (contentsOnly) {
          sb.append("?>");
        }
      }
      else {
        break;
      }
      last = end + 2;
    }
    if (last != source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  public static String scriptlet2space(String source, boolean contentsOnly) {
    int index = 0;
    int last = 0;
    StringBuffer sb = new StringBuffer();
    while ((index = source.indexOf("<%", last)) != -1) {
      int end = source.indexOf("%>", index);
      if (end != -1) {
        sb.append(source.substring(last, index));
        int length = end - index + 2;
        if (contentsOnly) {
          sb.append("<%");
          length = length - 4;
        }
        for (int i = 0; i < length; i++) {
          sb.append(" ");
        }
        if (contentsOnly) {
          sb.append("%>");
        }
      }
      else {
        break;
      }
      last = end + 2;
    }
    if (last != source.length()) {
      sb.append(source.substring(last));
    }
    return sb.toString();
  }

  public static byte[] readStream(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      int len = 0;
      byte[] buf = new byte[1024 * 8];
      while ((len = in.read(buf)) != -1) {
        out.write(buf, 0, len);
      }
      out.close();
    }
    finally {
      in.close();
    }
    byte[] result = out.toByteArray();
    return result;
  }

  /**
   * Escapes characters that need entity representation in HTML output.
   * <p>
   * Three categories of characters are escaped:
   * <ol>
   *   <li><b>XML-structural characters</b> ({@code &}, {@code <}, {@code >},
   *       {@code "}, {@code '}) — always escaped, required for well-formed
   *       markup.</li>
   *   <li><b>Non-printable HTML entities</b> ({@code &nbsp;}, {@code &shy;},
   *       etc.) — characters that have semantic meaning as entities and would
   *       lose that meaning if left as literal Unicode characters. These are
   *       re-encoded so a roundtrip through the parser preserves them.</li>
   * </ol>
   * <p>
   * Printable non-ASCII characters (accented letters, Icelandic characters,
   * etc.) are left as-is — they are valid UTF-8 and should not be converted
   * to HTML entities like {@code &oacute;} or {@code &eth;}.
   *
   * @param value  the text to escape
   * @param isHTML when true, non-printable HTML entities like {@code &nbsp;}
   *               are also escaped; when false, only XML-structural
   *               characters are escaped
   */
  public static String escape(String value, boolean isHTML) {
    Entities xmlEntities = Entities.XML;
    // HTML40 is used only to look up entity names for non-printable
    // characters — we never bulk-encode the full table.
    Entities htmlEntities = isHTML ? Entities.HTML40 : null;

    StringBuffer buf = new StringBuffer(value.length() * 2);
    int i;
    for (i = 0; i < value.length(); ++i) {
      char ch = value.charAt(i);

      // Always escape XML-structural characters.
      String entityName = xmlEntities.entityName(ch);
      if (entityName != null) {
        buf.append('&');
        buf.append(entityName);
        buf.append(';');
        continue;
      }

      // In HTML mode, re-encode non-printable characters that have HTML
      // entity names (e.g. &nbsp; = char 160). These have semantic
      // meaning that would be lost as literal characters. Printable
      // characters (letters, digits, symbols) are left as-is.
      if (htmlEntities != null && ch > 127 && !isPrintableCharacter(ch)) {
        entityName = htmlEntities.entityName(ch);
        if (entityName != null) {
          buf.append('&');
          buf.append(entityName);
          buf.append(';');
          continue;
        }
      }

      buf.append(ch);
    }
    return buf.toString();
  }

  /**
   * Returns true if a character is a printable letter, digit, or symbol
   * that should be preserved as a literal UTF-8 character rather than
   * converted to an HTML entity. Non-printable characters like the
   * non-breaking space (char 160) return false.
   */
  private static boolean isPrintableCharacter(char ch) {
    int type = Character.getType(ch);
    return type == Character.UPPERCASE_LETTER
        || type == Character.LOWERCASE_LETTER
        || type == Character.TITLECASE_LETTER
        || type == Character.MODIFIER_LETTER
        || type == Character.OTHER_LETTER
        || type == Character.DECIMAL_DIGIT_NUMBER
        || type == Character.LETTER_NUMBER
        || type == Character.OTHER_NUMBER
        || type == Character.DASH_PUNCTUATION
        || type == Character.START_PUNCTUATION
        || type == Character.END_PUNCTUATION
        || type == Character.CONNECTOR_PUNCTUATION
        || type == Character.OTHER_PUNCTUATION
        || type == Character.MATH_SYMBOL
        || type == Character.CURRENCY_SYMBOL
        || type == Character.MODIFIER_SYMBOL
        || type == Character.OTHER_SYMBOL
        || type == Character.INITIAL_QUOTE_PUNCTUATION
        || type == Character.FINAL_QUOTE_PUNCTUATION;
  }

  public static String decode(String value, boolean isHTML) {
    Entities entities = null;
    if (isHTML) {
      entities = Entities.HTML40;
    }
    else {
      entities = Entities.XML;
    }

    StringBuffer buf = new StringBuffer(value.length());
    int i;
    for (i = 0; i < value.length(); ++i) {
      char ch = value.charAt(i);
      if (ch == '&') {
        int semi = value.indexOf(';', i + 1);
        if (semi == -1) {
          buf.append(ch);
          continue;
        }
        String entityName = value.substring(i + 1, semi);
        int entityValue;
        if (entityName.length() == 0) {
          entityValue = -1;
        }
        else if (entityName.charAt(0) == '#') {
          if (entityName.length() == 1) {
            entityValue = -1;
          }
          else {
            char charAt1 = entityName.charAt(1);
            try {
              if (charAt1 == 'x' || charAt1 == 'X') {
                entityValue = Integer.valueOf(entityName.substring(2), 16).intValue();
              }
              else {
                entityValue = Integer.parseInt(entityName.substring(1));
              }
            }
            catch (NumberFormatException ex) {
              entityValue = -1;
            }
          }
        }
        else {
          entityValue = entities.entityValue(entityName);
        }
        if (entityValue == -1) {
          buf.append('&');
          buf.append(entityName);
          buf.append(';');
        }
        else {
          buf.append((char) (entityValue));
        }
        i = semi;
      }
      else {
        buf.append(ch);
      }
    }
    return buf.toString();
  }

  public static String escapeCDATA(String value) {
    value = value.replaceAll(">", "&gt;");
    return value;
  }

  public static String getEncoding(byte[] bytes) {
    String str = new String(bytes);
    Matcher matcher = encoding.matcher(str);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;

  }

  public static int getSpaceIndex(String value) {
    Matcher matcher = whiteSpace.matcher(value);
    if (matcher.find()) {
      return matcher.start(1);
    }
    return -1;
  }

  public static boolean isWhitespace(char c) {
    if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
      return true;
    }
    return false;
  }

  public static boolean isAllUppercase(String str) {
    for (int i = str.length() - 1; i >= 0; i --) {
      if (!Character.isUpperCase(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }
  
  public static boolean isAllWhitescape(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isWhitespace(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }
  
  public static String blockIndent(RenderContext renderContext, String text) {
    StringBuffer indent = new StringBuffer();
    String value = text;
    
    if (value.trim().length() == 0)
      return value;
    renderContext.appendIndent(indent);
    Pattern pattern = Pattern.compile("^[^\\s]", Pattern.MULTILINE);
    while (value.length() > 0 && !pattern.matcher(value).find()) {
      value = value.replaceAll("^[ \t]", "").replaceAll("\n[ \t]", "\n");
    }

    value = value.replaceAll("(\n)(.+)", "$1" + indent + "$2");
    return value;
  }
}

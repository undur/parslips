package tk.eclipse.plugin.htmleditor.editors;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.swt.graphics.Color;

import tk.eclipse.plugin.htmleditor.ColorProvider;
import tk.eclipse.plugin.htmleditor.HTMLPlugin;

public class HTMLTagScanner extends RuleBasedScanner {

  private boolean _inWoTag;
  private final Color _woBackground;
  private final Map<IToken, IToken> _woTokenCache = new HashMap<>();

  public HTMLTagScanner(ColorProvider colorProvider) {
    IToken literal = colorProvider.getToken(HTMLPlugin.PREF_COLOR_STRING);
    IToken tagName = colorProvider.getToken(HTMLPlugin.PREF_COLOR_TAG);
    IToken woTagName = colorProvider.getToken(HTMLPlugin.PREF_COLOR_WO_TAG, IHTMLColorConstants.WO_TAG_BG);
    IToken attributeName = colorProvider.getToken(HTMLPlugin.PREF_COLOR_ATTRIBUTE);
    IToken ognlBinding = colorProvider.getToken(HTMLPlugin.PREF_COLOR_OGNL);
    IToken dynamicBinding = colorProvider.getToken(HTMLPlugin.PREF_COLOR_DYNAMIC);

    _woBackground = colorProvider.getColor(IHTMLColorConstants.WO_TAG_BG);

    IRule[] rules = new IRule[] {
      new MultiLineRule("\"~" , "\"" , ognlBinding, '\\'),
      new MultiLineRule("\'~" , "\'" , ognlBinding, '\\'),
      new MultiLineRule("\"$" , "\"" , dynamicBinding, '\\'),
      new MultiLineRule("\'$" , "\'" , dynamicBinding, '\\'),
      new MultiLineRule("\"[" , "]\"" , dynamicBinding, '\\'),
      new MultiLineRule("\'[" , "]\'" , dynamicBinding, '\\'),
      new MultiLineRule("\"" , "\"" , literal, '\\'),
      new MultiLineRule("'"  , "'"  , literal, '\\'),
      new WhitespaceRule(new HTMLWhitespaceDetector()),
      new HTMLTagNameRule(tagName, woTagName),
      new HTMLAttributeNameRule(attributeName)
    };

    setDefaultReturnToken(new Token(new TextAttribute(null)));
    setRules(rules);
  }

  @Override
  public void setRange(IDocument document, int offset, int length) {
    super.setRange(document, offset, length);

    try {
      // Read enough characters to detect the longest prefix (</webobject = 12 chars)
      String start = document.get(offset, Math.min(length, 13));
      String lowerStart = start.toLowerCase();
      _inWoTag = lowerStart.startsWith("<wo:") || lowerStart.startsWith("</wo:") ||
                 lowerStart.startsWith("<wo ") || lowerStart.startsWith("</wo>") ||
                 lowerStart.startsWith("<webobject") || lowerStart.startsWith("</webobject") ||
                 lowerStart.startsWith("<p:raw") || lowerStart.startsWith("</p:raw") ||
                 lowerStart.startsWith("<p:comment") || lowerStart.startsWith("</p:comment");
    }
    catch (Exception e) {
      _inWoTag = false;
    }
  }

  @Override
  public IToken nextToken() {
    IToken token = super.nextToken();

    if (!_inWoTag || token.isEOF() || token.isUndefined()) {
      return token;
    }

    // For WO tags, wrap every token with the WO background color
    return woToken(token);
  }

  /**
   * Returns a version of the given token with the WO background tint applied.
   */
  private IToken woToken(IToken token) {
    IToken cached = _woTokenCache.get(token);
    if (cached != null) {
      return cached;
    }

    Object data = token.getData();
    if (data instanceof TextAttribute ta) {
      Token tinted = new Token(new TextAttribute(ta.getForeground(), _woBackground, ta.getStyle()));
      _woTokenCache.put(token, tinted);
      return tinted;
    }

    // Whitespace or other tokens without TextAttribute — give them just the background
    Token tinted = new Token(new TextAttribute(null, _woBackground, 0));
    _woTokenCache.put(token, tinted);
    return tinted;
  }
}

package jp.aonir.fuzzyxml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

import jp.aonir.fuzzyxml.event.FuzzyXMLErrorEvent;
import jp.aonir.fuzzyxml.event.FuzzyXMLErrorListener;
import jp.aonir.fuzzyxml.internal.FuzzyXMLAttributeImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLCDATAImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLCommentImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLDocTypeImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLDocumentImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLElementImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLPreImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLProcessingInstructionImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLScriptImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLStyleImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLTextImpl;
import jp.aonir.fuzzyxml.internal.FuzzyXMLUtil;
import jp.aonir.fuzzyxml.resources.Messages;

public class FuzzyXMLParser {

	private Stack<FuzzyXMLNode> _stack = new Stack<FuzzyXMLNode>();
	private String _originalSource;
	private List<FuzzyXMLNode> _roots;
	private FuzzyXMLDocType _docType;

	private List<FuzzyXMLErrorListener> _listeners = new ArrayList<FuzzyXMLErrorListener>();
	private List<FuzzyXMLElement> _nonCloseElements = new ArrayList<FuzzyXMLElement>();
	private List<String> _looseNamespaces = new ArrayList<String>();
	private List<String> _autocloseTags = new ArrayList<String>();
	private List<String> _looseTags = new ArrayList<String>();

	private boolean _wellFormedRequired = false;
	private boolean _isHTML = false;

	// Regular expressions used for parsing
	private Pattern _tag = Pattern.compile("<((|/)([^<>]*))([^<]?|>)");
	private Pattern _docTypeName = Pattern.compile("^<!DOCTYPE[ \r\n\t]+([\\w\\-_]*)", Pattern.CASE_INSENSITIVE);
	private Pattern _docTypePublic = Pattern.compile("PUBLIC[ \r\n\t]+\"([^\"]*)\"[ \r\n\t]*\"*([^\">]*)\"*", Pattern.CASE_INSENSITIVE);
	private Pattern _docTypeSystem = Pattern.compile("SYSTEM[ \r\n\t]+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
	private Pattern _docTypeSubset = Pattern.compile("\\[([^\\]]*)\\]>");
	private Pattern _invalidStringPattern = Pattern.compile("([<>&])");
	private Pattern _preCloseTagPattern = Pattern.compile("<\\s*/\\s*PRE\\s*>", Pattern.CASE_INSENSITIVE);
	private Pattern _pRawCloseTagPattern = Pattern.compile("<\\s*/\\s*p:raw\\s*>", Pattern.CASE_INSENSITIVE);
	private Pattern _pCommentCloseTagPattern = Pattern.compile("<\\s*/\\s*p:comment\\s*>", Pattern.CASE_INSENSITIVE);

	public FuzzyXMLParser(boolean wellFormedRequired) {
		this(wellFormedRequired, false);
	}

	public FuzzyXMLParser(boolean wellFormedRequired, boolean isHTML) {
		super();
		_wellFormedRequired = wellFormedRequired;
		_roots = new LinkedList<FuzzyXMLNode>();
		_isHTML = isHTML;
		// MS: Hardcoded that "wo" is a loose namespace
		addLooseNamespace("wo");
		addLooseNamespace("webobject");
		addLooseNamespace("webobjects");
		if (!_wellFormedRequired) {
			addAutocloseTag("img");
			addAutocloseTag("br");
			addAutocloseTag("hr");
			addAutocloseTag("meta");
			addAutocloseTag("link");
			addAutocloseTag("input");
			addAutocloseTag("spacer");
			addAutocloseTag("frame");
			addAutocloseTag("basefont");
			addAutocloseTag("base");
			addAutocloseTag("area");
			addAutocloseTag("col");
			addAutocloseTag("isindex");
			addAutocloseTag("param");
			addLooseTag("p");
			addLooseTag("li");
		}
	}

	/**
	 * An autoclose tag is like br or link where it commonly does not have a
	 * closing tag
	 * but it also never has contents.
	 * 
	 * @param autocloseTag
	 *          the name of the tag to make loose
	 */
	public void addAutocloseTag(String autocloseTag) {
		_autocloseTags.add(autocloseTag);
		addLooseTag(autocloseTag);
	}

	/**
	 * A "loose" tag is like li or p where people lazily often do not close them
	 * properly,
	 * but they may have content.
	 * 
	 * @param looseTag
	 *          the name of the tag to make loose
	 */
	public void addLooseTag(String looseTag) {
		_looseTags.add(looseTag);
	}

	/**
	 * A "loose" namespace is like the wo: namespace. We don't actually require
	 * that
	 * wo:if have a corresponding wo:if close tag -- it actually just needs a
	 * wo close tag.
	 * 
	 * @param namespace
	 *          the name of the namespace to make loose
	 */
	public void addLooseNamespace(String namespace) {
		_looseNamespaces.add(namespace);
	}

	/**
	 * Adds a listener for parse error events.
	 *
	 * @param listener
	 *          the error listener
	 */
	public void addErrorListener(FuzzyXMLErrorListener listener) {
		_listeners.add(listener);
	}

	private void fireErrorEvent(int offset, int length, String message, FuzzyXMLNode node) {
		FuzzyXMLErrorEvent evt = new FuzzyXMLErrorEvent(offset, length, message, node);
		for (FuzzyXMLErrorListener listener : _listeners) {
			listener.error(evt);
		}
	}

	/**
	 * Fires any deferred parse errors that were collected during attribute
	 * scanning. Error offsets in {@link TagInfo} are relative to the tag
	 * content text (after the opening {@code <}); {@code tagContentOffset}
	 * is the document-level offset of that text so errors get correct
	 * global positions.
	 *
	 * @param info the parsed tag info (may contain deferred errors)
	 * @param tagContentOffset the document offset of the tag content text
	 *        (i.e. one past the {@code <})
	 */
	private void fireDeferredTagErrors(TagInfo info, int tagContentOffset) {
		for (TagError error : info.getErrors()) {
			fireErrorEvent(tagContentOffset + error.offset, error.length, error.message, null);
		}
	}

	/**
	 * Parses an XML document from an input stream.
	 * Character encoding is detected from the XML declaration.
	 *
	 * @param in
	 *          the input stream
	 * @return the parsed document
	 * @throws IOException
	 */
	public FuzzyXMLDocument parse(InputStream in) throws IOException {
		byte[] bytes = FuzzyXMLUtil.readStream(in);
		String encode = FuzzyXMLUtil.getEncoding(bytes);
		if (encode == null) {
			return parse(new String(bytes));
		}
		return parse(new String(bytes, encode));
	}

	/**
	 * Parses an XML document from a file.
	 * Character encoding is detected from the XML declaration.
	 *
	 * @param file
	 *          the file to parse
	 * @return the parsed document
	 * @throws IOException
	 */
	public FuzzyXMLDocument parse(File file) throws IOException {
		byte[] bytes = FuzzyXMLUtil.readStream(new FileInputStream(file));
		String encode = FuzzyXMLUtil.getEncoding(bytes);
		if (encode == null) {
			return parse(new String(bytes));
		}
		return parse(new String(bytes, encode));
	}

	protected int _parse(String source, int initialOffset, boolean woOnly, boolean parseAsSynthetic) {
		// Begin parsing
		Matcher matcher = _tag.matcher(source);
		int lastIndex = initialOffset - 1;
		while (matcher.find()) {
			int start = matcher.start() + initialOffset;
			int end = matcher.end() + initialOffset;
			if (lastIndex == -1 && start > 0) {
				handleText(0, start, true);
			}
			else if (lastIndex != (initialOffset - 1) && lastIndex < start) {
				handleText(lastIndex, start, true);
			}
			String originalText = matcher.group(1);
			String text = originalText.trim();

			// Detect missing closing '>' — the regex allows tags to match
			// without a proper '>', using group(4) to capture the final
			// character. If group(4) is not '>', the tag is unclosed.
			// Skip special constructs (comments, CDATA, declarations) which
			// have different closing syntax.
			if (!">".equals(matcher.group(4))
					&& !text.startsWith("!")
					&& !text.startsWith("?")
					&& !text.startsWith("%")
					&& text.length() > 0) {
				// Extract the tag name for the error message
				String tagName = text;
				int spaceIdx = FuzzyXMLUtil.getSpaceIndex(tagName);
				if (spaceIdx != -1) {
					tagName = tagName.substring(0, spaceIdx);
				}
				if (tagName.startsWith("/")) {
					tagName = tagName.substring(1);
				}
				fireErrorEvent(start, end - start,
						"Missing closing '>' on <" + tagName + "> tag.", null);
			}
			// Scriptlet tag
			if (!woOnly && text.startsWith("%")) {
				// ignore
				handleText(start, end, false);
			}
			else if (!woOnly && text.startsWith("?")) {
				handleDeclaration(start, end);
			}
			else if (!woOnly && (text.startsWith("!DOCTYPE") || text.startsWith("!doctype"))) {
				handleDoctype(start, end, text);
			}
			else if (!woOnly && text.startsWith("![CDATA[")) {
				handleCDATA(start, end, _originalSource.substring(start, end));
			}
			else if (!woOnly && (text.equalsIgnoreCase("pre") || text.toLowerCase().startsWith("pre "))) {
				end = handlePreTag(start, end);
				matcher.region(end, source.length());
			}
			// p:raw — preserve content as literal text, skip all dynamic tag processing
			else if (!woOnly && (text.equalsIgnoreCase("p:raw") || text.toLowerCase().startsWith("p:raw ") || text.toLowerCase().startsWith("p:raw/"))) {
				end = handlePRawTag(start, end);
				matcher.region(end, source.length());
			}
			// p:comment — skip content entirely, treat as a template-level comment
			else if (!woOnly && (text.equalsIgnoreCase("p:comment") || text.toLowerCase().startsWith("p:comment ") || text.toLowerCase().startsWith("p:comment/"))) {
				end = handlePCommentTag(start, end);
				matcher.region(end, source.length());
			}
			else if (text.startsWith("/") && (!woOnly || WodHtmlUtils.isWOTag(text.substring(1)))) {
				handleCloseTag(start, end, text);
			}
			else if (text.endsWith("/") && (!woOnly || WodHtmlUtils.isWOTag(text))) {
				if (originalText.endsWith(" ")) {
					fireErrorEvent(start, end - start, "You can not have a space between the / and the > in your webobject tags.", null);
				}
				handleEmptyTag(start, end, parseAsSynthetic);
			}
			else if (!woOnly && text.startsWith("!--")) {
				end = _originalSource.indexOf("-->", start);
				if (end > 0) {
					end += 3;
				}
				handleComment(start, end, _originalSource.substring(start, end));
				matcher.region(end, source.length());
			}
			else if (!woOnly || WodHtmlUtils.isWOTag(text)) {
				handleStartTag(start, end, parseAsSynthetic);
			}
			lastIndex = end;
		}
		return lastIndex;
	}

	/**
	 * Parses the given XML/HTML source string and returns the resulting document.
	 *
	 * @param source
	 *          the XML/HTML source
	 * @return the parsed FuzzyXMLDocument
	 */
	public FuzzyXMLDocument parse(String source) {
		// Preserve original source for attribute value extraction
		_originalSource = source;
		// Blank out p:raw and p:comment block content before any other preprocessing,
		// so broken/unclosed quotes inside these blocks don't corrupt escapeString
		source = FuzzyXMLUtil.pBlock2space(source);
		// Blank out comments, CDATA, and DOCTYPE for safe tag matching
		source = FuzzyXMLUtil.comment2space(source, true);
		source = FuzzyXMLUtil.escapeScript(source);
		source = FuzzyXMLUtil.scriptlet2space(source, true);
		source = FuzzyXMLUtil.cdata2space(source, true);
		source = FuzzyXMLUtil.doctype2space(source, true);
		source = FuzzyXMLUtil.processing2space(source, true);
		source = FuzzyXMLUtil.escapeString(source);

		int lastIndex = _parse(source, 0, false, false);

		if (_stack.size() > 0 && _nonCloseElements.size() > 0) {
			FuzzyXMLElementImpl lastElement = (FuzzyXMLElementImpl) _nonCloseElements.get(_nonCloseElements.size() - 1);
			String lowercaseLastElementName = lastElement.getName().toLowerCase();
			if (!_looseTags.contains(lowercaseLastElementName)) {
				fireErrorEvent(lastElement.getOffset(), lastElement.getLength(), Messages.getMessage("error.noCloseTag", lastElement.getName()), null);
			}

			for (FuzzyXMLNode openNode : _stack) {
				if (openNode instanceof FuzzyXMLElementImpl) {
					FuzzyXMLElementImpl openElement = (FuzzyXMLElementImpl) openNode;
					openElement.setLength(lastIndex - openElement.getOffset());
					if (openElement.getParentNode() == null) {
						_roots.add(openElement);
					}
					else {
						((FuzzyXMLElementImpl) openElement.getParentNode()).appendChildWithNoCheck(openElement);
					}
				}
			}
		}

		// MS: Capture trailing text that isn't inside of a tag at all
		if (lastIndex != source.length()) {
			handleText(Math.max(0, lastIndex), source.length(), true);
		}

		FuzzyXMLElement docElement = null;
		if (_roots.size() == 0) {
			docElement = new FuzzyXMLElementImpl(null, "document", 0, _originalSource.length(), 0);
			// docElement.appendChild(root);
		}
		else {
			FuzzyXMLNode firstRoot = _roots.get(0);
			FuzzyXMLNode lastRoot = _roots.get(_roots.size() - 1);
			docElement = new FuzzyXMLElementImpl(null, "document", firstRoot.getOffset(), lastRoot.getOffset() + lastRoot.getLength() - firstRoot.getOffset(), 0);
			for (FuzzyXMLNode root : _roots) {
				((FuzzyXMLElementImpl) docElement).appendChildWithNoCheck(root);
			}
		}
		FuzzyXMLDocumentImpl doc = new FuzzyXMLDocumentImpl(docElement, _docType);
		doc.setHTML(_isHTML);
		return doc;
	}

	/** Processes a CDATA node. */
	private void handleCDATA(int offset, int end, String text) {
		closeAutocloseTags();
		text = text.replaceFirst("<!\\[CDATA\\[", "");
		text = text.replaceFirst("\\]\\]>", "");
		FuzzyXMLCDATAImpl cdata = new FuzzyXMLCDATAImpl(getParent(), text, offset, end - offset);
		if (getParent() != null) {
			((FuzzyXMLElement) getParent()).appendChild(cdata);
		}
		else {
			_roots.add(cdata);
		}

		_stack.push(cdata);
		_parse(text, offset + "<![CDATA[".length(), true, true);
		FuzzyXMLNode poppedNode = _stack.pop();
		if (poppedNode != cdata) {
			_stack.push(poppedNode);
		}
	}

	private int handlePreTag(int offset, int end) {
		closeAutocloseTags();
		String[] content = _preCloseTagPattern.split(_originalSource.substring(end, _originalSource.length()), 2);
		String text = content[0];
		TagInfo info = parseTagContents(_originalSource.substring(offset + 1, end - 1));
		fireDeferredTagErrors(info, offset + 1);
		FuzzyXMLPreImpl preNode = new FuzzyXMLPreImpl(getParent(), text, offset, text.length());
		handleStartTag(preNode, info, offset, end);
		String preBlock = _originalSource.substring(offset, end + text.length() + 1);
		return _parse(preBlock, offset, true, false) - 1;
	}

	/**
	 * Handles a {@code <p:raw>} block. Content between the open and close tags
	 * is preserved as a single text node — no dynamic tag processing occurs.
	 * The {@code <p:raw>} element itself appears in the DOM so the validator
	 * can recognize it and skip child validation.
	 */
	private int handlePRawTag(int offset, int end) {
		closeAutocloseTags();
		String remaining = _originalSource.substring(end);
		Matcher closeMatcher = _pRawCloseTagPattern.matcher(remaining);
		int closeTagEnd;
		String rawText;
		boolean hasCloseTag = closeMatcher.find();
		if (hasCloseTag) {
			rawText = remaining.substring(0, closeMatcher.start());
			closeTagEnd = end + closeMatcher.end();
		} else {
			// No close tag found — treat rest of document as raw content
			rawText = remaining;
			closeTagEnd = _originalSource.length();
		}

		TagInfo info = parseTagContents(_originalSource.substring(offset + 1, end - 1));
		fireDeferredTagErrors(info, offset + 1);
		FuzzyXMLElementImpl rawElement = new FuzzyXMLElementImpl(getParent(), info.name, offset, closeTagEnd - offset, info.nameOffset);

		// Add attributes (if any) and push onto stack
		AttrInfo[] attrs = info.getAttrs();
		for (int i = 0; i < attrs.length; i++) {
			rawElement.appendChild(createFuzzyXMLAttribute(rawElement, offset, attrs[i]));
		}
		_stack.push(rawElement);
		_nonCloseElements.add(rawElement);

		// Add the raw content as a single text node (no further parsing)
		if (!rawText.isEmpty()) {
			FuzzyXMLTextImpl textNode = new FuzzyXMLTextImpl(rawElement, rawText, end, rawText.length());
			rawElement.appendChild(textNode);
		}

		// Close the element and record close tag positions so that
		// linked rename (Cmd+2, R) can pair the open and close tags
		_stack.pop();
		rawElement.setLength(closeTagEnd - offset);
		if (hasCloseTag) {
			int closeTagAbsoluteOffset = end + closeMatcher.start();
			String closeTagText = _originalSource.substring(closeTagAbsoluteOffset, closeTagEnd);
			rawElement.setCloseTagOffset(closeTagAbsoluteOffset);
			rawElement.setCloseTagLength(closeTagText.length() - 2); // exclude < and >
			// closeNameOffset is relative to after the '<' (matching handleCloseTag's
			// convention, since renameHtmlTag computes: closeTagOffset + closeNameOffset + 1)
			String afterBracket = closeTagText.substring(1);
			int nameStart = afterBracket.indexOf(info.name);
			if (nameStart != -1) {
				rawElement.setCloseNameOffset(nameStart);
			}
		}
		_nonCloseElements.remove(rawElement);
		if (rawElement.getParentNode() == null) {
			_roots.add(rawElement);
		} else {
			((FuzzyXMLElementImpl) rawElement.getParentNode()).appendChildWithNoCheck(rawElement);
		}

		return closeTagEnd;
	}

	/**
	 * Handles a {@code <p:comment>} block. Content inside is not parsed at all
	 * and will be invisible to validation. Uses a {@link FuzzyXMLElementImpl}
	 * (same as {@link #handlePRawTag}) so that linked rename (Cmd+2, R) can
	 * pair the open and close tags, and the validator can recognise it via
	 * {@link WodHtmlUtils#isParserDirective(String)}.
	 */
	private int handlePCommentTag(int offset, int end) {
		closeAutocloseTags();
		String remaining = _originalSource.substring(end);
		Matcher closeMatcher = _pCommentCloseTagPattern.matcher(remaining);
		int closeTagEnd;
		boolean hasCloseTag = closeMatcher.find();
		if (hasCloseTag) {
			closeTagEnd = end + closeMatcher.end();
		} else {
			// No close tag found — treat rest of document as comment
			closeTagEnd = _originalSource.length();
		}

		TagInfo info = parseTagContents(_originalSource.substring(offset + 1, end - 1));
		fireDeferredTagErrors(info, offset + 1);
		FuzzyXMLElementImpl commentElement = new FuzzyXMLElementImpl(getParent(), info.name, offset, closeTagEnd - offset, info.nameOffset);

		// Add attributes (if any) and push onto stack
		AttrInfo[] attrs = info.getAttrs();
		for (int i = 0; i < attrs.length; i++) {
			commentElement.appendChild(createFuzzyXMLAttribute(commentElement, offset, attrs[i]));
		}
		_stack.push(commentElement);
		_nonCloseElements.add(commentElement);

		// No child content — comment content is intentionally discarded

		// Close the element and record close tag positions so that
		// linked rename (Cmd+2, R) can pair the open and close tags
		_stack.pop();
		commentElement.setLength(closeTagEnd - offset);
		if (hasCloseTag) {
			int closeTagAbsoluteOffset = end + closeMatcher.start();
			String closeTagText = _originalSource.substring(closeTagAbsoluteOffset, closeTagEnd);
			commentElement.setCloseTagOffset(closeTagAbsoluteOffset);
			commentElement.setCloseTagLength(closeTagText.length() - 2); // exclude < and >
			// closeNameOffset is relative to after the '<' (matching handleCloseTag's
			// convention, since renameHtmlTag computes: closeTagOffset + closeNameOffset + 1)
			String afterBracket = closeTagText.substring(1);
			int nameStart = afterBracket.indexOf(info.name);
			if (nameStart != -1) {
				commentElement.setCloseNameOffset(nameStart);
			}
		}
		_nonCloseElements.remove(commentElement);
		if (commentElement.getParentNode() == null) {
			_roots.add(commentElement);
		} else {
			((FuzzyXMLElementImpl) commentElement.getParentNode()).appendChildWithNoCheck(commentElement);
		}

		return closeTagEnd;
	}

	/** Creates a text node from the source range [offset, end). */
	private void handleText(int offset, int end, boolean escape) {
		String text = _originalSource.substring(offset, end);
		closeAutocloseTags();
		FuzzyXMLTextImpl textNode = new FuzzyXMLTextImpl(getParent(), FuzzyXMLUtil.decode(text, _isHTML), offset, end - offset);
		textNode.setRawValue(text);
		textNode.setEscape(escape);
		if (getParent() != null) {
			((FuzzyXMLElement) getParent()).appendChild(textNode);
		}
		else {
			_roots.add(textNode);
		}
	}

	/** Processes an XML declaration (processing instruction). */
	private void handleDeclaration(int offset, int end) {
		closeAutocloseTags();
		String text = _originalSource.substring(offset, end);
		text = text.replaceFirst("^<\\?", "");
		text = text.replaceFirst("\\?>$", "");
		text = text.trim();

		String[] dim = text.split("[ \r\n\t]+");
		String name = dim[0];
		String data = text.substring(name.length()).trim();

		FuzzyXMLProcessingInstructionImpl pi = new FuzzyXMLProcessingInstructionImpl(null, name, data, offset, end - offset);
		if (getParent() != null) {
			// Append to parent element
			((FuzzyXMLElement) getParent()).appendChild(pi);
		}
		else {
			_roots.add(pi);
		}

		// XML should not have autoclosing tags
		if (name.startsWith("xml")) {
			_autocloseTags.clear();
		}
	}

	/** Processes a DOCTYPE declaration. */
	private void handleDoctype(int offset, int end, String text) {
		closeAutocloseTags();
		if (_docType == null) {
			String name = "";
			String publicId = "";
			String systemId = "";
			String internalSubset = "";

			text = _originalSource.substring(offset, end);
			Matcher matcher = _docTypeName.matcher(text);
			if (matcher.find()) {
				name = matcher.group(1);
			}
			matcher = _docTypePublic.matcher(text);
			if (matcher.find()) {
				publicId = matcher.group(1);
				systemId = matcher.group(2);
			}
			else {
				matcher = _docTypeSystem.matcher(text);
				if (matcher.find()) {
					systemId = matcher.group(1);
				}
			}
			matcher = _docTypeSubset.matcher(text);
			if (matcher.find()) {
				internalSubset = matcher.group(1);
			}
			FuzzyXMLDocTypeImpl impl = new FuzzyXMLDocTypeImpl(null, name, publicId, systemId, internalSubset, offset, end - offset);
			impl.setRawValue(text);
			_docType = impl;
		}
	}

	private void closeAutocloseTags() {
		if (_stack.size() > 0) {
			FuzzyXMLElementImpl lastOpenElement = (FuzzyXMLElementImpl) _stack.peek();
			String name = lastOpenElement.getName().toLowerCase();
			if (_autocloseTags.contains(name) || lastOpenElement.isForbiddenFromHavingChildren()) {
				int openTagEndOffset = lastOpenElement.getOffset() + lastOpenElement.getOpenTagLength();
				handleCloseTag(openTagEndOffset, openTagEndOffset, "/" + name, false);
			}
		}
	}

	/** Processes a close tag. */
	private void handleCloseTag(int offset, int end, String text) {
		handleCloseTag(offset, end, text, true);
	}

	private void handleCloseTag(int offset, int end, String text, boolean showMismatchError) {
		String tagName = text.substring(1).trim();

		// MS: Chuck does close tags like </webobject closing something else>
		int chuckIndex = tagName.indexOf(' ');
		if (chuckIndex != -1) {
			String chuckWord = tagName.substring(0, chuckIndex);
			if (WodHtmlUtils.isWOTag(chuckWord)) {
				tagName = chuckWord;
			}
		}

		// Empty stack means we've seen a close tag with nothing on the
		// open-tag stack — an extraneous close tag, e.g. </div> appearing
		// after all open tags have already been properly closed, or at
		// the very start of the document. Report it as a noStartTag error
		// (unless we're in a suppress-errors recursive call) and return.
		// We can't actually pop anything, but the user should see the mistake.
		if (_stack.size() == 0) {
			if (showMismatchError) {
				fireErrorEvent(offset, end - offset, Messages.getMessage("error.noStartTag", tagName), null);
			}
			return;
		}

		FuzzyXMLElementImpl lastOpenElement = (FuzzyXMLElementImpl) _stack.pop();
		String lowercaseLastOpenElementName = lastOpenElement.getName().toLowerCase();
		String lowercaseCloseTagName = tagName.toLowerCase();

		boolean closeTagMatches = lowercaseLastOpenElementName.equals(lowercaseCloseTagName);
		if (!closeTagMatches) {
			closeAutocloseTags();

			// Allow </wo> to close </wo:if>
			boolean looseNamespace = false;
			int colonIndex = lowercaseLastOpenElementName.indexOf(':');
			if (colonIndex != -1) {
				String elementNamespace = lowercaseLastOpenElementName.substring(0, colonIndex);
				if (lowercaseCloseTagName.equals(elementNamespace) && _looseNamespaces.contains(elementNamespace)) {
					tagName = lastOpenElement.getName();
					lowercaseCloseTagName = lowercaseLastOpenElementName;
					looseNamespace = true;
				}
			}

			if (!looseNamespace) {
				boolean looseTag = false;
				if (_looseTags.contains(lowercaseLastOpenElementName)) {
					looseTag = true;
				}

				if (looseTag) {
					while (lowercaseLastOpenElementName != null && !lowercaseLastOpenElementName.equals(lowercaseCloseTagName) && _looseTags.contains(lowercaseLastOpenElementName)) {
						int lastOpenElementEndOffset = end;
						_stack.push(lastOpenElement);
						handleCloseTag(lastOpenElementEndOffset, lastOpenElementEndOffset, "/" + lastOpenElement.getName(), false);

						if (_stack.size() == 0) {
							lastOpenElement = null;
							lowercaseLastOpenElementName = null;
						}
						else {
							lastOpenElement = (FuzzyXMLElementImpl) _stack.pop();
							lowercaseLastOpenElementName = lastOpenElement.getName().toLowerCase();
						}
					}
				}
				else {
					FuzzyXMLElement matchingOpenElement = null;
					for (FuzzyXMLElement nonCloseElement : _nonCloseElements) {
						if (nonCloseElement.getName().equalsIgnoreCase(lowercaseCloseTagName)) {
							matchingOpenElement = nonCloseElement;
						}
					}
					if (matchingOpenElement == null) {
						if (showMismatchError) {
							fireErrorEvent(offset, end - offset, Messages.getMessage("error.noStartTag", tagName), null);
						}
						_stack.push(lastOpenElement);
						return;
					}

					if (showMismatchError) {
						fireErrorEvent(lastOpenElement.getOffset(), lastOpenElement.getLength(), "Missing </" + lastOpenElement.getName() + "> tag", null);
					}
					_stack.push(lastOpenElement);
					handleCloseTag(offset, offset, "/" + lastOpenElement.getName(), false);
					lastOpenElement = (FuzzyXMLElementImpl) _stack.pop();
					lowercaseLastOpenElementName = lastOpenElement.getName().toLowerCase();
				}
			}
		}

		if (lastOpenElement != null) {
			lastOpenElement.setLength(end - lastOpenElement.getOffset());
			if (closeTagMatches) {
				lastOpenElement.setCloseTagOffset(offset);
				lastOpenElement.setCloseTagLength(end - offset - 2);
				lastOpenElement.setCloseNameOffset(text.indexOf(tagName));
			}
			_nonCloseElements.remove(lastOpenElement);
			if (lastOpenElement.getParentNode() == null) {
				_roots.add(lastOpenElement);
				for (FuzzyXMLElement error : _nonCloseElements) {
					if (showMismatchError) {
						fireErrorEvent(error.getOffset(), error.getLength(), Messages.getMessage("error.noCloseTag", error.getName()), error);
					}
				}
			}
			else {
				((FuzzyXMLElementImpl) lastOpenElement.getParentNode()).appendChildWithNoCheck(lastOpenElement);
			}
		}
	}

	private void checkAttributeValue(FuzzyXMLAttribute attr) {
		String str = attr.getRawValue();
		if (str != null) {
			// MS: Don't consider nested tags for escaping ...
			if (attr.hasNestedTag()) {
				str = str.replaceAll("<[^>]*>", "");
			}

			str = str.replaceAll("&[^&; \"]+;", " ");
			Matcher invalidStringMatcher = _invalidStringPattern.matcher(str);
			while (invalidStringMatcher.find()) {
				String invalidPart = invalidStringMatcher.group();
				fireErrorEvent(attr.getParentNode().getOffset() + attr.getValueDataOffset() + 1, attr.getValueDataLength(), "The character '" + invalidPart + "' must be escaped.", attr);
			}

		}
	}

	/** Processes a self-closing (empty) tag. */
	private void handleEmptyTag(int offset, int end, boolean synthetic) {
		closeAutocloseTags();
		TagInfo info = parseTagContents(_originalSource.substring(offset + 1, end - 1));
		fireDeferredTagErrors(info, offset + 1);
		FuzzyXMLNode parent = getParent();
		FuzzyXMLElementImpl element = new FuzzyXMLElementImpl(parent, info.name, offset, end - offset, info.nameOffset);
		if (parent == null) {
			_roots.add(element);
		}
		else {
			((FuzzyXMLElement) parent).appendChild(element);
		}
		// Add attributes
		AttrInfo[] attrs = info.getAttrs();
		for (int i = 0; i < attrs.length; i++) {
			FuzzyXMLAttributeImpl attr = createFuzzyXMLAttribute(element, offset, attrs[i]);
			element.appendChild(attr);
		}

		element.setSynthetic(synthetic);

		checkElement(element);
	}

	protected void checkElement(FuzzyXMLElement element) {
		for (FuzzyXMLAttribute attr : element.getAttributes()) {
			if (!_wellFormedRequired) {
				if (!WodHtmlUtils.isWOTag((FuzzyXMLElement) attr.getParentNode())) {
					_stack.push(attr.getParentNode());
					_parse(attr.getValue(), element.getOffset() + attr.getValueDataOffset() + 1, true, true);
					FuzzyXMLNode poppedNode = _stack.pop();
					if (poppedNode != attr.getParentNode()) {
						_stack.push(poppedNode);
					}
				}
			}
			else {
				checkAttributeValue(attr);
			}
		}
	}

	/** Processes an HTML comment. */
	private void handleComment(int offset, int end, String text) {
		closeAutocloseTags();
		FuzzyXMLNode parent = getParent();
		FuzzyXMLCommentImpl comment = new FuzzyXMLCommentImpl(parent, text, offset, end - offset);

		if (parent == null) {
			_roots.add(comment);
		}
		else {
			((FuzzyXMLElement) parent).appendChild(comment);
		}

		_stack.push(comment);
		_parse(text.replaceFirst("<[^>]+-->$", ""), offset, true, true);
		FuzzyXMLNode poppedNode = _stack.pop();
		if (poppedNode != comment) {
			_stack.push(poppedNode);
		}
	}

	/** Processes an open tag (parses tag text and creates element). */
	private void handleStartTag(int offset, int end, boolean synthetic) {
		closeAutocloseTags();
		String tagContents = _originalSource.substring(offset, end);
		// MS: If you're in the middle of typing, offset + 1 to end - 1 can put
		// you in an invalid state (for instance, if you just type "<" that will
		// overlap.
		if (tagContents.startsWith("<")) {
			tagContents = tagContents.substring(1);
		}
		if (tagContents.endsWith(">")) {
			tagContents = tagContents.substring(0, tagContents.length() - 1);
		}
		TagInfo info = parseTagContents(tagContents);
		fireDeferredTagErrors(info, offset + 1);
		FuzzyXMLElement element;
		if (info.name.equalsIgnoreCase("script")) {
			element = new FuzzyXMLScriptImpl(getParent(), info.name, offset, end - offset, info.nameOffset);
		}
		else if (info.name.equalsIgnoreCase("style")) {
			element = new FuzzyXMLStyleImpl(getParent(), info.name, offset, end - offset, info.nameOffset);
		}
		else {
			element = new FuzzyXMLElementImpl(getParent(), info.name, offset, end - offset, info.nameOffset);
		}
		handleStartTag(element, info, offset, end);
		element.setSynthetic(synthetic);
	}

	protected FuzzyXMLAttributeImpl createFuzzyXMLAttribute(FuzzyXMLElement element, int offset, AttrInfo attrInfo) {
		String namespace = null;
		String name = attrInfo.name;
		if (name != null) {
			int colonIndex = name.indexOf(':');
			if (colonIndex != -1) {
				namespace = name.substring(0, colonIndex);
				name = name.substring(colonIndex + 1);
			}
		}
		if (_wellFormedRequired) {
			FuzzyXMLAttributeImpl attr = new FuzzyXMLAttributeImpl(element, namespace, name, FuzzyXMLUtil.decode(attrInfo.value, false), attrInfo.rawValue, attrInfo.offset + offset, attrInfo.end - attrInfo.offset + 1, attrInfo.valueOffset);
			attr.setHasNestedTag(attrInfo.hasNestedTag);
			attr.setQuoteCharacter(attrInfo.quote);
			return attr;
		}
		FuzzyXMLAttributeImpl attr = new FuzzyXMLAttributeImpl(element, namespace, name, attrInfo.value, attrInfo.rawValue, attrInfo.offset + offset, attrInfo.end - attrInfo.offset + 1, attrInfo.valueOffset);
		attr.setHasNestedTag(attrInfo.hasNestedTag);
		attr.setQuoteCharacter(attrInfo.quote);
		if (attrInfo.value.indexOf('"') >= 0 || attrInfo.value.indexOf('\'') >= 0 || attrInfo.value.indexOf('<') >= 0 || attrInfo.value.indexOf('>') >= 0 || attrInfo.value.indexOf('&') >= 0) {
			attr.setEscape(false);
		}
		return attr;
	}

	/** Adds parsed attributes to the element and pushes it onto the open-element stack. */
	private void handleStartTag(FuzzyXMLElement element, TagInfo info, int offset, int end) {
		AttrInfo[] attrs = info.getAttrs();
		for (int i = 0; i < attrs.length; i++) {
			element.appendChild(createFuzzyXMLAttribute(element, offset, attrs[i]));
		}
		_stack.push(element);
		_nonCloseElements.add(element);

		checkElement(element);
	}

	/** Returns the top of the open-element stack without removing it. */
	private FuzzyXMLNode getParent() {
		if (_stack.size() == 0) {
			return null;
		}
		return _stack.get(_stack.size() - 1);
	}

	/** Parses the text content of a tag into a name and attributes. */
	private TagInfo parseTagContents(String text) {
		// Trim whitespace
		Range trimmedRange = Range.trimmedRange(text);
		text = trimmedRange.trim(text);
		// Remove trailing slash for self-closing tags
		if (text.endsWith("/")) {
			text = text.substring(0, text.length() - 1);
		}
		// Tag name is everything up to the first space
		TagInfo info = new TagInfo();
		if (FuzzyXMLUtil.getSpaceIndex(text) != -1) {
			info.name = text.substring(0, FuzzyXMLUtil.getSpaceIndex(text)).trim();
			info.nameOffset = trimmedRange.getOffset();
			parseAttributeContents(info, text);
		}
		else {
			info.name = text;
		}
		return info;
	}

	private static enum AttributeParseState {
		Start, BeforeAttributeName, InAttributeName, AfterAttributeName, InAttributeValue, InNestedTag,
	}

	/** Parses the attribute portion of a tag into name-value pairs. */
	private void parseAttributeContents(TagInfo info, String text) {

		AttributeParseState state = AttributeParseState.Start;
		StringBuffer tokenBuffer = new StringBuffer();
		String name = null;
		char quoteCharacter = 0;
		int start = -1;
		int valueOffset = -1;
		boolean escape = false;
		boolean hasNestedTag = false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (state == AttributeParseState.Start && FuzzyXMLUtil.isWhitespace(c)) {
				state = AttributeParseState.BeforeAttributeName;
			}
			else if (state == AttributeParseState.BeforeAttributeName && !FuzzyXMLUtil.isWhitespace(c)) {
				if (start == -1) {
					start = i;
				}
				state = AttributeParseState.InAttributeName;
				tokenBuffer.append(c);
			}
			else if (state == AttributeParseState.InAttributeName) {
				if (c == '=') {
					state = AttributeParseState.AfterAttributeName;
					name = tokenBuffer.toString().trim();
					tokenBuffer.setLength(0);
					valueOffset = -1;
				}
				else if (c == '"' || c == '\'') {
					// A quote character inside an attribute name means the '='
					// was omitted — e.g. negate"true" instead of negate="true".
					// Record the error and recover by treating the quote as if
					// '=' had been present, so the attribute parses correctly
					// and downstream tools (validation, autocomplete) still work.
					String attrName = tokenBuffer.toString().trim();
					info.addError(start, i - start,
							"Missing '=' after attribute '" + attrName
							+ "' — did you mean " + attrName + "=" + c + "...?");
					name = attrName;
					tokenBuffer.setLength(0);
					quoteCharacter = c;
					valueOffset = i + 1;
					state = AttributeParseState.InAttributeValue;
				}
				else {
					tokenBuffer.append(c);
				}
			}
			else if (state == AttributeParseState.AfterAttributeName && !FuzzyXMLUtil.isWhitespace(c)) {
				if (valueOffset == -1) {
					valueOffset = i;
				}
				if (c == '\'' || c == '\"') {
					quoteCharacter = c;
				}
				else {
					quoteCharacter = 0;
					tokenBuffer.append(c);
				}
				state = AttributeParseState.InAttributeValue;
			}
			else if (state == AttributeParseState.InAttributeValue) {
				if (c == quoteCharacter && escape == true) {
					tokenBuffer.append(c);
					escape = false;
				}
				else if (c == quoteCharacter || (quoteCharacter == 0 && FuzzyXMLUtil.isWhitespace(c))) {
					// add an attribute
					AttrInfo attr = new AttrInfo();
					attr.name = FuzzyXMLUtil.decode(name, _isHTML);
					attr.rawValue = tokenBuffer.toString();
					attr.value = FuzzyXMLUtil.decode(attr.rawValue, _isHTML);
					attr.valueOffset = valueOffset;
					attr.offset = start;
					attr.end = i + 1;
					attr.quote = quoteCharacter;
					attr.hasNestedTag = hasNestedTag;
					info.addAttr(attr);

					// reset
					tokenBuffer.setLength(0);
					state = AttributeParseState.BeforeAttributeName;
					start = -1;
					hasNestedTag = false;
				}
				else if (c == '\\') {
					if (escape == true) {
						tokenBuffer.append(c);
						escape = false;
					}
					else {
						// MS: I took out escaping .. This is potentially a really sketchy
						// thing to do, but it
						// was breaking attributes like numberformat = "\$#,##0.00"
						// Q: moved append to following 'else' block
						escape = true;
					}
				}
				else if (c == '<') {
					hasNestedTag = true;
					state = AttributeParseState.InNestedTag;
					tokenBuffer.append(c);
				}
				else {
					if (escape) {
						tokenBuffer.append('\\');
						escape = false;
					}
					tokenBuffer.append(c);
				}
			}
			else if (state == AttributeParseState.InNestedTag) {
				tokenBuffer.append(c);
				if (c == '>') {
					state = AttributeParseState.InAttributeValue;
				}
			}
		}
		if ((state == AttributeParseState.InAttributeValue || state == AttributeParseState.InNestedTag) && quoteCharacter == 0) {
			AttrInfo attr = new AttrInfo();
			attr.name = FuzzyXMLUtil.decode(name, _isHTML);
			attr.rawValue = tokenBuffer.toString();
			attr.value = FuzzyXMLUtil.decode(attr.rawValue, _isHTML);
			attr.valueOffset = valueOffset;
			attr.offset = start;
			attr.end = text.length();
			attr.quote = quoteCharacter;
			attr.hasNestedTag = hasNestedTag;
			info.addAttr(attr);
		}
		// Matcher matcher = attr.matcher(text);
		// while(matcher.find()){
		// AttrInfo attr = new AttrInfo();
		// attr.name = matcher.group(1);
		// attr.value = FuzzyXMLUtil.decode(matcher.group(3));
		// attr.offset = matcher.start();
		// attr.end = matcher.end();
		// info.addAttr(attr);
		// }
	}

	private class TagInfo {
		private String name;
		private int nameOffset;
		private ArrayList<AttrInfo> attrs = new ArrayList<AttrInfo>();

		/** Parse errors detected during attribute scanning (e.g. missing '='). */
		private ArrayList<TagError> errors = new ArrayList<TagError>();

		/**
		 * Adds an attribute to this tag's attribute list. If an attribute
		 * with the same name already exists (case-insensitive), the duplicate
		 * is dropped and an error is recorded — duplicate attributes are a
		 * parse error per the HTML spec (the first occurrence wins).
		 */
		public void addAttr(AttrInfo attr) {
			AttrInfo[] info = getAttrs();
			for (int i = 0; i < info.length; i++) {
				if (info[i].name.equalsIgnoreCase(attr.name)) {
					addError(attr.offset, attr.name.length(),
							"Duplicate attribute '" + attr.name + "'"
							+ (attr.name.equals(info[i].name) ? "" : " (same as '" + info[i].name + "')")
							+ " — the first value will be used.");
					return;
				}
			}
			attrs.add(attr);
		}

		public AttrInfo[] getAttrs() {
			return attrs.toArray(new AttrInfo[attrs.size()]);
		}

		/**
		 * Records a parse error with offsets relative to the tag content text
		 * (i.e. the string passed to parseAttributeContents). These are fired
		 * as proper error events later, once the tag's global offset is known.
		 */
		public void addError(int offset, int length, String message) {
			errors.add(new TagError(offset, length, message));
		}

		public TagError[] getErrors() {
			return errors.toArray(new TagError[errors.size()]);
		}
	}

	/**
	 * A parse error detected during attribute scanning, with offsets relative
	 * to the tag content text. Converted to a global-offset error event by the
	 * tag handler methods (handleStartTag, handleEmptyTag, etc.) once the
	 * tag's position in the document is known.
	 */
	private static class TagError {
		final int offset;
		final int length;
		final String message;

		TagError(int offset, int length, String message) {
			this.offset = offset;
			this.length = length;
			this.message = message;
		}
	}

	private class AttrInfo {
		private String name;
		private String value;
		private String rawValue;
		private int offset;
		private int valueOffset;
		private int end;
		private char quote;
		private boolean hasNestedTag;
	}

	public static class Range {
		private int _offset;
		private int _length;

		public Range() {
		}

		public int getOffset() {
			return _offset;
		}

		public int getLength() {
			return _length;
		}

		public String trim(String str) {
			return str.substring(_offset, _offset + _length);
		}

		public static Range trimmedRange(String str) {
			int i = 0;
			int length = str.length();
			Range r = new Range();
			for (i = 0; i < length && str.charAt(i) <= ' '; i++) {
				// DO NOTHING
			}
			r._offset = i;

			for (i = length - 1; i > r._offset && str.charAt(i) <= ' '; i--) {
				// DO NOTHING
			}
			r._length = (i - r._offset + 1);
			return r;
		}
	}

}

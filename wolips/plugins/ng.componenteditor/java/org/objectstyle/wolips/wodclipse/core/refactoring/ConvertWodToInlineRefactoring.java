package org.objectstyle.wolips.wodclipse.core.refactoring;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.objectstyle.wolips.bindings.wod.IWodBinding;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.bindings.wod.IWodModel;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.preferences.PreferenceConstants;
import org.objectstyle.wolips.wodclipse.core.util.WodDocumentUtils;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

import jp.aonir.fuzzyxml.FuzzyXMLDocument;
import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.FuzzyXMLNode;

/**
 * Converts a single WOD-reference tag ({@code <webobject name="X">}) to inline
 * binding syntax ({@code <wo:Type binding="value">}).
 *
 * <p>This is the reverse of {@link ConvertInlineToWodRefactoring}. The
 * refactoring:
 * <ol>
 *   <li>Finds the element at the cursor offset in the HTML document
 *   <li>Verifies it's a WOD-reference tag (not already inline)
 *   <li>Looks up the element name in the WOD model to get the type and bindings
 *   <li>Replaces the open tag with an inline {@code <wo:Type ...>} tag
 *   <li>Replaces the close tag with {@code </wo:Type>}
 *   <li>Removes the WOD entry from the WOD file
 * </ol>
 *
 * <p>Respects the "Spaces around equals" formatting preference and the
 * project's configured inline binding prefix/suffix.
 *
 * @see ConvertInlineToWodRefactoring
 */
public class ConvertWodToInlineRefactoring implements IRunnableWithProgress {

	/**
	 * Convenience entry point: wraps the refactoring in the standard
	 * HTML+WOD document handling provided by {@link TemplateRefactoring}.
	 */
	public static void run(WodParserCache cache, int offset, ParsleyProject parsleyProject, IProgressMonitor progressMonitor) throws InvocationTargetException, InterruptedException, CoreException {
		TemplateRefactoring.processHtmlAndWod(new ConvertWodToInlineRefactoring(cache, offset, parsleyProject), cache, progressMonitor);
	}

	private final WodParserCache _cache;
	private final int _offset;
	private final ParsleyProject _parsleyProject;

	public ConvertWodToInlineRefactoring(WodParserCache cache, int offset, ParsleyProject parsleyProject) {
		_cache = cache;
		_offset = offset;
		_parsleyProject = parsleyProject;
	}

	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException {
		try {
			FuzzyXMLDocument htmlModel = _cache.getHtmlEntry().getModel();
			FuzzyXMLElement element = htmlModel.getElementByOffset(_offset);
			if (element == null) {
				return;
			}

			// getElementByOffset returns the innermost (smallest) element at the
			// cursor position. If the cursor is inside a child element nested within
			// a <webobject> tag (e.g. a <p>, <br>, or even an inline <wo:Type>),
			// we get the child instead of the <webobject>. Walk up the parent chain
			// to find the nearest WOD-reference ancestor.
			element = findWodReferenceElement(element);
			if (element == null) {
				return;
			}

			// Get the element name from the tag's "name" attribute
			String elementName = element.getAttributeValue("name");
			if (elementName == null || elementName.isEmpty()) {
				return;
			}

			// Look up the WOD element to get the type and bindings
			IWodModel wodModel = _cache.getWodEntry().getModel();
			if (wodModel == null) {
				return;
			}

			IWodElement wodElement = wodModel.getElementNamed(elementName);
			if (wodElement == null) {
				return;
			}

			// Get inline binding prefix/suffix from project settings
			String bindingPrefix = "$";
			String bindingSuffix = "";
			if (_parsleyProject != null && _parsleyProject.getBuildProperties() != null) {
				bindingPrefix = _parsleyProject.getBuildProperties().getInlineBindingPrefix();
				bindingSuffix = _parsleyProject.getBuildProperties().getInlineBindingSuffix();
			}

			// Read the "spaces around equals" formatting preference
			boolean spacesAroundEquals = Activator.getDefault().getPreferenceStore()
					.getBoolean(PreferenceConstants.SPACES_AROUND_EQUALS);

			// Build the inline open tag content (everything between < and >)
			String elementType = wodElement.getElementType();
			String inlineOpenTagContent = buildInlineOpenTagContent(
					elementType, wodElement, bindingPrefix, bindingSuffix, spacesAroundEquals);

			// Replace the open tag in the HTML document.
			// The open tag is: <webobject name="X"> or <webobject name="X" />
			// element.getOffset() points to the '<' character.
			// element.getOpenTagLength() is the length from '<' to '>' (inclusive for
			// non-self-closing tags, but for self-closing tags, see below).
			IDocument htmlDocument = _cache.getHtmlEntry().getDocument();
			List<TextEdit> htmlEdits = new LinkedList<>();

			int openTagLength = element.getOpenTagLength();
			if (element.hasCloseTag()) {
				// Replace close tag: </webobject> → </wo:Type>
				// closeNameOffset is relative to the close tag start, pointing past the '/'
				edits_replaceCloseTag(element, elementType, htmlEdits);

				// Replace open tag content (between < and >)
				// +1 to skip the '<', openTagLength to cover up to and including '>'
				htmlEdits.add(new ReplaceEdit(element.getOffset() + 1, openTagLength, inlineOpenTagContent));
			}
			else {
				// Self-closing tag: <webobject name="X"/>
				// openTagLength excludes the final '>' for self-closing tags in the
				// ConvertInlineToWodRefactoring pattern, but we need to replace everything
				// between '<' and '/>'. Subtract 1 from openTagLength to exclude the '>',
				// then our replacement will include '/>'
				htmlEdits.add(new ReplaceEdit(element.getOffset() + 1, openTagLength - 1, inlineOpenTagContent + "/"));
			}

			WodDocumentUtils.applyEdits(htmlDocument, htmlEdits);

			// Remove the WOD entry from the WOD file
			removeWodElement(wodElement);
		}
		catch (Exception e) {
			throw new InvocationTargetException(e);
		}
	}

	/**
	 * Builds the content for the inline open tag (everything between {@code <}
	 * and {@code >}).
	 *
	 * <p>For example, for a WOString with {@code value = name}, this produces:
	 * {@code wo:WOString value = "$name"} (with spaces) or
	 * {@code wo:WOString value="$name"} (without spaces).
	 */
	private static String buildInlineOpenTagContent(String elementType, IWodElement wodElement,
			String bindingPrefix, String bindingSuffix, boolean spacesAroundEquals) throws IOException {
		StringWriter writer = new StringWriter();
		writer.write("wo:");
		writer.write(elementType);
		for (IWodBinding binding : wodElement.getBindings()) {
			binding.writeInlineFormat(writer, bindingPrefix, bindingSuffix, spacesAroundEquals);
		}
		return writer.toString();
	}

	/**
	 * Adds a ReplaceEdit to replace the close tag name
	 * ({@code </webobject>} → {@code </wo:Type>}).
	 */
	private static void edits_replaceCloseTag(FuzzyXMLElement element, String elementType, List<TextEdit> edits) {
		// closeNameOffset is relative to the close tag's '<' character, pointing
		// past the '/'. closeNameLength is the length of the tag name in the close tag.
		int closeTagStart = element.getCloseTagOffset();
		int closeNameRelOffset = element.getCloseNameOffset();
		int closeNameLength = element.getCloseNameLength();
		edits.add(new ReplaceEdit(closeTagStart + closeNameRelOffset + 1, closeNameLength, "wo:" + elementType));
	}

	/**
	 * Removes the WOD element declaration from the WOD document. Also removes
	 * the trailing newline if present, to avoid leaving blank lines.
	 */
	private void removeWodElement(IWodElement wodElement) throws Exception {
		IDocument wodDocument = _cache.getWodEntry().getDocument();
		if (wodDocument == null) {
			return;
		}

		int startOffset = wodElement.getStartOffset();
		int endOffset = wodElement.getFullEndOffset();

		// Try to include the trailing newline to keep the WOD file clean
		if (endOffset < wodDocument.getLength()) {
			char ch = wodDocument.getChar(endOffset);
			if (ch == '\n') {
				endOffset++;
			}
			else if (ch == '\r') {
				endOffset++;
				if (endOffset < wodDocument.getLength() && wodDocument.getChar(endOffset) == '\n') {
					endOffset++;
				}
			}
		}

		// Also consume the preceding newline if this isn't the first element,
		// to avoid double blank lines
		if (startOffset > 0) {
			int pos = startOffset - 1;
			if (wodDocument.getChar(pos) == '\n') {
				startOffset = pos;
				if (startOffset > 0 && wodDocument.getChar(startOffset - 1) == '\r') {
					startOffset--;
				}
			}
		}

		List<TextEdit> wodEdits = new LinkedList<>();
		wodEdits.add(new DeleteEdit(startOffset, endOffset - startOffset));
		WodDocumentUtils.applyEdits(wodDocument, wodEdits);
	}

	/**
	 * Finds the nearest WOD-reference element ({@code <webobject>} or
	 * {@code <wo name="...">}) at or above the given element in the tree.
	 *
	 * <p>{@code getElementByOffset} returns the innermost element at the cursor.
	 * If the cursor is inside a child of a {@code <webobject>} tag, this method
	 * walks up the parent chain to find the enclosing WOD-reference tag.
	 *
	 * @return the WOD-reference element, or {@code null} if none found
	 */
	private static FuzzyXMLElement findWodReferenceElement(FuzzyXMLElement element) {
		FuzzyXMLNode current = element;
		while (current instanceof FuzzyXMLElement) {
			FuzzyXMLElement el = (FuzzyXMLElement) current;
			String tagName = el.getName();
			if (WodHtmlUtils.isWOTag(tagName) && !WodHtmlUtils.isInline(tagName)) {
				return el;
			}
			current = current.getParentNode();
		}
		return null;
	}
}

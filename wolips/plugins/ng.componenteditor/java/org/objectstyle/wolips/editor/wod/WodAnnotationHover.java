package org.objectstyle.wolips.editor.wod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.ApiextHtmlRenderer;
import org.objectstyle.wolips.bindings.api.ApiextModel;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.bindings.wod.TypeCache;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * Hover provider for the template editor that shows two kinds of information:
 *
 * <ol>
 *   <li><b>Validation errors</b> — when the cursor is over squiggly-underlined
 *       text, the annotation message is shown (e.g. "There is no key 'nme'").</li>
 *   <li><b>Component documentation</b> — when the cursor is over a
 *       {@code <wo:ComponentName>} tag, the component's API is shown: its
 *       bindings, which are required, and their types/defaults.</li>
 * </ol>
 *
 * <p>If both an error and documentation apply at the same position, both are
 * shown with the error first.
 *
 * <p>All hover text uses plain text with Unicode characters for formatting,
 * since Eclipse's default hover control doesn't render HTML.
 *
 * <p>Implements {@link IAnnotationHover} for the vertical ruler and
 * {@link ITextHover} for the editor body.
 */
public class WodAnnotationHover implements IAnnotationHover, ITextHover, ITextHoverExtension, ITextHoverExtension2 {
	private IAnnotationModel _annotationModel;

	/**
	 * Parser cache for resolving element types to API definitions.
	 * May be null if the editor hasn't fully initialized or if we
	 * couldn't obtain the cache — documentation just won't appear.
	 */
	private WodParserCache _parserCache;

	/**
	 * Creates a hover provider with annotation support only (no component documentation).
	 */
	public WodAnnotationHover(IAnnotationModel annotationModel) {
		this(annotationModel, null);
	}

	/**
	 * Creates a hover provider with both annotation and component documentation support.
	 *
	 * @param annotationModel the annotation model for error markers
	 * @param parserCache the parser cache for resolving element types to API
	 *        definitions, or null for annotation-only mode
	 */
	public WodAnnotationHover(IAnnotationModel annotationModel, WodParserCache parserCache) {
		_annotationModel = annotationModel;
		_parserCache = parserCache;
	}

	@Override
	public String getHoverInfo(ISourceViewer sourceViewer, int lineNumber) {
		String hoverInfo = null;
		List<Annotation> annotationsList = getAnnotationsForLine(sourceViewer, lineNumber);
		if (annotationsList != null) {
			List<String> messagesList = new ArrayList<>();
			for (Annotation annotation : annotationsList) {
				String message = annotation.getText();
				if (message != null) {
					message = message.trim();
					if (message.length() > 0) {
						messagesList.add(message);
					}
				}
			}
			if (messagesList.size() == 1) {
				hoverInfo = messagesList.get(0);
			} else if (messagesList.size() > 1) {
				hoverInfo = formatMessages(messagesList);
			}
		}
		return hoverInfo;
	}

	/**
	 * Legacy {@link ITextHover} entry, kept for compatibility. The active path is
	 * {@link #getHoverInfo2}, which the framework prefers because we implement
	 * {@link ITextHoverExtension2}.
	 */
	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		return buildHoverHtml(textViewer, hoverRegion);
	}

	/**
	 * Active hover entry. Returns the hover HTML, which {@link ApiHoverControl#setInput}
	 * loads into its browser. (We implement {@link ITextHoverExtension2} so the framework
	 * uses {@code getHoverInfo2}/{@code setInput} rather than the legacy String path.)
	 */
	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		return buildHoverHtml(textViewer, hoverRegion);
	}

	/**
	 * Builds the single HTML document for a hover position: an optional validation-error
	 * block (plain, escaped) followed by the component documentation — rich {@code .apiext}
	 * HTML when available, otherwise the escaped plain-text {@code .api} preview.
	 *
	 * @return the HTML, or null if there's nothing to show at this position
	 */
	private String buildHoverHtml(ITextViewer textViewer, IRegion hoverRegion) {
		String annotationText = getAnnotationInfo(hoverRegion);
		HoverContent documentation = getComponentDocumentation(textViewer, hoverRegion);

		if (annotationText == null && documentation == null) {
			return null;
		}

		// One well-formed document (no nested <html>/<body>). The head carries the
		// .apiext card styling plus a rule that lets the body scroll when its content is
		// taller than the popup — the fix for tall cards being clipped, since the popup
		// opens at a fixed height that can't fit every element's full documentation.
		StringBuilder html = new StringBuilder();
		html.append("<html><head><style>")
				.append("html,body{margin:0;padding:0;}")
				.append("body{overflow-x:hidden;overflow-y:auto;}")
				.append(ApiextHtmlRenderer.css())
				.append("</style></head><body>");
		if (annotationText != null) {
			// Errors stay plain — escaped, whitespace preserved.
			html.append("<pre style=\"margin:0 0 .5em;white-space:pre-wrap;color:#b00020;\">")
					.append(escapeHtml(annotationText)).append("</pre>");
		}
		if (documentation != null) {
			if (documentation.isHtml) {
				html.append(documentation.text);
			}
			else {
				html.append("<pre style=\"margin:0;white-space:pre-wrap;\">")
						.append(escapeHtml(documentation.text)).append("</pre>");
			}
		}
		html.append("</body></html>");
		return html.toString();
	}

	/**
	 * Hover payload that knows whether it is already HTML (the rich {@code .apiext}
	 * rendering) or plain text (the classic {@code .api} preview / error message, which
	 * the caller escapes into a {@code <pre>}).
	 */
	private static final class HoverContent {
		final String text;
		final boolean isHtml;

		HoverContent(String text, boolean isHtml) {
			this.text = text;
			this.isHtml = isHtml;
		}
	}

	// -----------------------------------------------------------------------
	// HTML hover control (ITextHoverExtension / ITextHoverExtension2)
	// -----------------------------------------------------------------------

	/**
	 * Creator for the <em>transient</em> hover (shown on mouse-over). The control it
	 * creates ({@link ApiHoverControl}) advertises an enriched/sticky variant via its own
	 * {@code getInformationPresenterControlCreator()}, so moving the mouse toward it
	 * promotes it to a resizable, scrollable, selectable popup instead of dismissing it —
	 * the same UX as JDT's Javadoc hover.
	 */
	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new AbstractReusableInformationControlCreator() {
			@Override
			protected IInformationControl doCreateInformationControl(Shell parent) {
				return new ApiHoverControl(parent, EditorsUI.getTooltipAffordanceString());
			}
		};
	}

	private static String escapeHtml(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
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
			default:
				b.append(c);
			}
		}
		return b.toString();
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		// First check for annotation overlap
		IRegion annotationRegion = getAnnotationRegion(offset);
		if (annotationRegion != null) {
			return annotationRegion;
		}

		// Then check for wo: tag at offset
		IRegion tagRegion = getWoTagRegion(textViewer, offset);
		if (tagRegion != null) {
			return tagRegion;
		}

		return null;
	}

	// -----------------------------------------------------------------------
	// Annotation hover (existing functionality)
	// -----------------------------------------------------------------------

	/**
	 * Returns annotation text if the hover region overlaps any annotations.
	 */
	private String getAnnotationInfo(IRegion hoverRegion) {
		if (_annotationModel == null) {
			return null;
		}
		Iterator<?> annotationsIter = _annotationModel.getAnnotationIterator();
		while (annotationsIter.hasNext()) {
			Annotation annotation = (Annotation) annotationsIter.next();
			Position position = _annotationModel.getPosition(annotation);
			if (position != null && position.overlapsWith(hoverRegion.getOffset(), hoverRegion.getLength())) {
				String text = annotation.getText();
				if (text != null && text.trim().length() > 0) {
					return text;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the annotation region if the offset overlaps any annotation.
	 */
	private IRegion getAnnotationRegion(int offset) {
		if (_annotationModel == null) {
			return null;
		}
		Iterator<?> annotationsIter = _annotationModel.getAnnotationIterator();
		while (annotationsIter.hasNext()) {
			Annotation annotation = (Annotation) annotationsIter.next();
			Position position = _annotationModel.getPosition(annotation);
			if (position != null && position.overlapsWith(offset, 0)) {
				String text = annotation.getText();
				if (text != null && text.trim().length() > 0) {
					return new Region(position.offset, position.length);
				}
			}
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Component documentation hover
	// -----------------------------------------------------------------------

	/**
	 * If the hover region is inside a {@code <wo:ComponentName>} tag, resolves
	 * the component type and formats its API bindings as documentation text.
	 *
	 * @return formatted documentation, or null if not hovering over a wo: tag
	 *         or if the component's API can't be resolved
	 */
	private HoverContent getComponentDocumentation(ITextViewer textViewer, IRegion hoverRegion) {
		if (_parserCache == null) {
			return null;
		}

		try {
			IDocument document = textViewer.getDocument();
			if (document == null) {
				return null;
			}

			String elementTypeName = findWoElementTypeName(document, hoverRegion.getOffset());
			if (elementTypeName == null) {
				return null;
			}

			return resolveAndFormatDocumentation(elementTypeName);
		}
		catch (Exception e) {
			// Don't let hover errors propagate — just return no documentation
			return null;
		}
	}

	/**
	 * Resolves an element type name to its API definition and formats the
	 * bindings as a readable documentation string.
	 *
	 * <p>The element type name may be a direct class name (like "WOForm"),
	 * a tag shortcut (like "form"), or a project component name (like
	 * "MyWidget"). All three paths are handled:
	 * <ol>
	 *   <li>Try resolving via {@link BindingReflectionUtils#findElementType}
	 *       (handles class names and the element name cache)</li>
	 *   <li>If that fails, check if it's a tag shortcut via
	 *       {@link ApiCache#getTagShortcutNamed} and resolve the actual
	 *       class name</li>
	 *   <li>Fall back to the global {@code WebObjectDefinitions.xml} for
	 *       built-in WO components</li>
	 * </ol>
	 */
	private HoverContent resolveAndFormatDocumentation(String elementTypeName) {
		try {
			IJavaProject javaProject = _parserCache.getJavaProject();
			if (javaProject == null) {
				return null;
			}

			TypeCache typeCache = WodParserCache.getTypeCache();

			// The display name shown in the hover header — may differ from the
			// lookup name if the user typed a tag shortcut
			String displayName = elementTypeName;

			// Resolve the element type name to a Java type
			IType elementType = BindingReflectionUtils.findElementType(
					javaProject, elementTypeName, false, typeCache);

			// If direct resolution failed, check if it's a tag shortcut
			// (e.g. "form" → "WOForm", "if" → "WOConditional")
			String resolvedClassName = elementTypeName;
			if (elementType == null) {
				TagShortcut shortcut = ApiCache.getTagShortcutNamed(elementTypeName);
				if (shortcut != null) {
					resolvedClassName = shortcut.getActual();
					displayName = elementTypeName + " \u2192 " + resolvedClassName;
					elementType = BindingReflectionUtils.findElementType(
							javaProject, resolvedClassName, false, typeCache);
				}
			}

			// .apiext first: if a parsable extended-API sibling exists for this element,
			// render its richer content and use it INSTEAD of the .api preview. The format
			// is in flux, so an unparsable .apiext yields null here and we fall through to
			// the classic .api path below — .api still drives everything else.
			if (elementType != null) {
				byte[] apiextBytes = ApiUtils.findApiextBytes(elementType);
				if (apiextBytes != null) {
					ApiextModel apiext = ApiextModel.parse(apiextBytes);
					if (apiext != null) {
						return new HoverContent(ApiextHtmlRenderer.renderBody(displayName, apiext), true);
					}
				}
			}

			// Bundled .apiext for a built-in element: a curated <Element>.apiext in the
			// plugin's apiext/ folder takes precedence over BOTH the framework .api and the
			// terse global WebObjectDefinitions.xml. It fully replaces them (the built-in
			// elements are frozen, so a complete curated file is authoritative). This lets us
			// enrich built-in element docs and is the staging ground for moving element
			// documentation into the frameworks themselves. Try the resolved class name, then
			// the original element name; an unparsable file falls through to the .api path.
			byte[] globalApiext = ApiUtils.findGlobalApiextBytes(resolvedClassName);
			if (globalApiext == null && !resolvedClassName.equals(elementTypeName)) {
				globalApiext = ApiUtils.findGlobalApiextBytes(elementTypeName);
			}
			if (globalApiext != null) {
				ApiextModel apiext = ApiextModel.parse(globalApiext);
				if (apiext != null) {
					return new HoverContent(ApiextHtmlRenderer.renderBody(displayName, apiext), true);
				}
			}

			ApiSnapshot api = null;

			// Try project/classpath .api file first
			if (elementType != null) {
				try {
					api = ApiUtils.findApiSnapshot(elementType, typeCache.getApiCache(javaProject));
				}
				catch (ApiModelException e) {
					// fall through to global lookup
				}
			}

			// Fall back to global WebObjectDefinitions.xml for built-in components.
			// Try both the resolved class name and the original element name.
			if (api == null) {
				api = ApiUtils.findGlobalApiSnapshotByClassName(resolvedClassName);
			}
			if (api == null && !resolvedClassName.equals(elementTypeName)) {
				api = ApiUtils.findGlobalApiSnapshotByClassName(elementTypeName);
			}

			if (api == null) {
				// No API found — just show the resolved name so the user at
				// least sees what the shortcut maps to
				return new HoverContent(displayName, false);
			}

			// Render the .api through the SAME template as .apiext (an .api is just an
			// .apiext with the extension fields empty), so both hovers look identical apart
			// from the source badge and the richer content .apiext can carry.
			return new HoverContent(ApiextHtmlRenderer.renderBody(displayName, ApiextModel.fromApiSnapshot(displayName, api)), true);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Finds the element type name at the given offset if the offset is
	 * inside a {@code <wo:ElementTypeName} tag.
	 *
	 * <p>Scans backward from the offset to find {@code <wo:} or {@code </wo:},
	 * then forward to find the end of the element type name (terminated by
	 * whitespace, {@code >}, {@code /}, or end of document).
	 *
	 * @return the element type name (e.g. "WOString"), or null if not inside
	 *         a wo: tag
	 */
	private String findWoElementTypeName(IDocument document, int offset) {
		try {
			String content = document.get();
			int length = content.length();

			if (offset < 0 || offset >= length) {
				return null;
			}

			// Scan backward to find the start of <wo: or </wo:
			int scanStart = Math.max(0, offset - 200);
			int woColonIndex = -1;
			for (int i = offset; i >= scanStart; i--) {
				char c = content.charAt(i);
				if (c == '>') {
					return null;
				}
				if (c == '<') {
					String ahead = content.substring(i, Math.min(length, i + 5)).toLowerCase();
					if (ahead.startsWith("<wo:") || ahead.startsWith("</wo:")) {
						woColonIndex = ahead.startsWith("</wo:") ? i + 5 : i + 4;
					}
					break;
				}
			}

			if (woColonIndex < 0 || woColonIndex >= length) {
				return null;
			}

			// Find the end of the element type name
			int nameEnd = woColonIndex;
			while (nameEnd < length) {
				char c = content.charAt(nameEnd);
				if (Character.isWhitespace(c) || c == '>' || c == '/') {
					break;
				}
				nameEnd++;
			}

			if (nameEnd <= woColonIndex) {
				return null;
			}

			return content.substring(woColonIndex, nameEnd);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns a region covering the {@code <wo:ElementTypeName} tag at the
	 * given offset, or null if the offset is not inside a wo: tag.
	 *
	 * <p>The region covers from the opening {@code <} through the end of the
	 * element type name, so the hover tooltip appears when the cursor is
	 * anywhere on the tag name or the {@code wo:} prefix.
	 */
	private IRegion getWoTagRegion(ITextViewer textViewer, int offset) {
		if (_parserCache == null) {
			return null;
		}

		try {
			IDocument document = textViewer.getDocument();
			if (document == null) {
				return null;
			}

			String content = document.get();
			int length = content.length();

			if (offset < 0 || offset >= length) {
				return null;
			}

			// Scan backward to find < that starts a wo: tag
			int scanStart = Math.max(0, offset - 200);
			int tagStart = -1;
			int woColonIndex = -1;
			for (int i = offset; i >= scanStart; i--) {
				char c = content.charAt(i);
				if (c == '>') {
					return null;
				}
				if (c == '<') {
					String ahead = content.substring(i, Math.min(length, i + 5)).toLowerCase();
					if (ahead.startsWith("<wo:") || ahead.startsWith("</wo:")) {
						tagStart = i;
						woColonIndex = ahead.startsWith("</wo:") ? i + 5 : i + 4;
					}
					break;
				}
			}

			if (woColonIndex < 0 || woColonIndex >= length) {
				return null;
			}

			// Find the end of the element type name
			int nameEnd = woColonIndex;
			while (nameEnd < length) {
				char c = content.charAt(nameEnd);
				if (Character.isWhitespace(c) || c == '>' || c == '/') {
					break;
				}
				nameEnd++;
			}

			if (nameEnd <= woColonIndex) {
				return null;
			}

			// The hover region covers from < through the end of the type name
			return new Region(tagStart, nameEnd - tagStart);
		}
		catch (Exception e) {
			return null;
		}
	}

	// -----------------------------------------------------------------------
	// Utility methods
	// -----------------------------------------------------------------------

	private String formatMessages(List<?> messages) {
		StringBuilder buffer = new StringBuilder();
		for (Object message : messages) {
			buffer.append("- ");
			buffer.append(message);
			buffer.append('\n');
		}
		return buffer.toString();
	}

	private List<Annotation> getAnnotationsForLine(ISourceViewer viewer, int line) {
		List<Annotation> annotationsList = new ArrayList<>();
		IDocument document = viewer.getDocument();
		IAnnotationModel model = viewer.getAnnotationModel();
		if (model != null) {
			Iterator<?> annotationsIter = model.getAnnotationIterator();
			while (annotationsIter.hasNext()) {
				Annotation annotation = (Annotation) annotationsIter.next();
				Position position = model.getPosition(annotation);
				if (position != null) {
					try {
						int annotationLine = document.getLineOfOffset(position.getOffset());
						if (annotationLine == line) {
							annotationsList.add(annotation);
						}
					} catch (BadLocationException e1) {
						// ignore
					}
				}
			}
		}
		return annotationsList;
	}
}

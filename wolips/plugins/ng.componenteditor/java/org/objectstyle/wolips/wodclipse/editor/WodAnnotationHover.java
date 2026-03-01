package org.objectstyle.wolips.wodclipse.editor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ApiModelException;
import org.objectstyle.wolips.bindings.api.ApiSnapshot;
import org.objectstyle.wolips.bindings.api.ApiUtils;
import org.objectstyle.wolips.bindings.api.ApiValidation;
import org.objectstyle.wolips.bindings.api.IApiBinding;
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
public class WodAnnotationHover implements IAnnotationHover, ITextHover {
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

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		String annotationText = getAnnotationInfo(hoverRegion);
		String documentationText = getComponentDocumentation(textViewer, hoverRegion);

		if (annotationText != null && documentationText != null) {
			return annotationText + "\n\n" + documentationText;
		}
		if (annotationText != null) {
			return annotationText;
		}
		return documentationText;
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
	private String getComponentDocumentation(ITextViewer textViewer, IRegion hoverRegion) {
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
	private String resolveAndFormatDocumentation(String elementTypeName) {
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
				return displayName;
			}

			return formatApiDocumentation(displayName, api);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Formats an {@link ApiSnapshot} definition into plain-text hover content
	 * using Unicode characters for visual structure.
	 *
	 * <p>Layout:
	 * <pre>
	 * repetition → WORepetition  (has content)
	 * ────────────────────────────────────────
	 *  ▸ item                       — settable
	 *  ▸ list
	 *  ▸ count
	 *    index
	 *    identifier
	 * ────────────────────────────────────────
	 *  • 'list' must not be a constant
	 *  • exactly one of 'count' or 'list' must be bound
	 *  • 'item' must be bound when 'list' is bound
	 * </pre>
	 *
	 * Required bindings are marked with ▸, optional with plain indent.
	 * Validation rules are shown below a separator.
	 */
	private String formatApiDocumentation(String elementTypeName, ApiSnapshot api) {
		List<IApiBinding> bindings = api.getBindings();

		StringBuilder sb = new StringBuilder();

		// Header line
		sb.append(elementTypeName);
		if (api.isComponentContent()) {
			sb.append("  (has content)");
		}

		if (bindings.isEmpty()) {
			sb.append("\n(no bindings defined)");
			return sb.toString();
		}

		// Separator line below the header
		sb.append('\n');
		appendSeparator(sb, 40);

		// Separate required from optional bindings
		List<IApiBinding> required = new ArrayList<>();
		List<IApiBinding> optional = new ArrayList<>();

		for (IApiBinding binding : bindings) {
			if (binding.isRequired()) {
				required.add(binding);
			}
			else {
				optional.add(binding);
			}
		}

		// Find the longest binding name for alignment
		int maxNameLen = 0;
		for (IApiBinding binding : bindings) {
			maxNameLen = Math.max(maxNameLen, binding.getName().length());
		}

		// Required bindings first (marked with ▸)
		for (IApiBinding binding : required) {
			sb.append('\n');
			sb.append(" \u25B8 "); // ▸ right-pointing triangle
			appendBindingLine(sb, binding, maxNameLen);
		}

		// Blank line between sections if both exist
		if (!required.isEmpty() && !optional.isEmpty()) {
			sb.append('\n');
		}

		// Optional bindings (plain indent)
		for (IApiBinding binding : optional) {
			sb.append('\n');
			sb.append("   ");
			appendBindingLine(sb, binding, maxNameLen);
		}

		// Validation rules
		List<ApiValidation> validations = api.getValidations();
		if (!validations.isEmpty()) {
			boolean hasMessages = false;
			for (ApiValidation validation : validations) {
				String message = validation.getMessage();
				if (message != null && !message.isEmpty()) {
					if (!hasMessages) {
						sb.append('\n');
						appendSeparator(sb, 40);
						hasMessages = true;
					}
					sb.append('\n');
					sb.append(" \u2022 "); // bullet
					sb.append(message);
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Appends a single binding line with the name left-aligned and
	 * annotations (type info) right-aligned after padding.
	 */
	private void appendBindingLine(StringBuilder sb, IApiBinding binding, int maxNameLen) {
		String name = binding.getName();
		sb.append(name);

		String typeInfo = getBindingTypeInfo(binding);
		if (typeInfo != null) {
			// Pad to align the annotation column
			int padding = maxNameLen - name.length() + 2;
			for (int i = 0; i < padding; i++) {
				sb.append(' ');
			}
			sb.append("\u2014 "); // em dash
			sb.append(typeInfo);
		}
	}

	/**
	 * Appends a horizontal separator line using box-drawing characters.
	 */
	private void appendSeparator(StringBuilder sb, int length) {
		for (int i = 0; i < length; i++) {
			sb.append('\u2500'); // ─ box drawing light horizontal
		}
	}

	/**
	 * Returns a short type/annotation string for a binding, combining the
	 * defaults (value type) and settable flag.
	 *
	 * @return annotation like "Actions", "Boolean, settable", or null if
	 *         no type information is available
	 */
	private String getBindingTypeInfo(IApiBinding binding) {
		List<String> parts = new ArrayList<>();

		if (binding.isRequired()) {
			parts.add("required");
		}

		String defaults = binding.getDefaults();
		if (defaults != null && !"Undefined".equals(defaults)) {
			parts.add(defaults);
		}

		if (binding.isWillSet()) {
			parts.add("settable");
		}

		if (parts.isEmpty()) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
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

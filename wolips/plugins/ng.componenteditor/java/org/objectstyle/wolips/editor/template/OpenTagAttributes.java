package org.objectstyle.wolips.editor.template;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The attributes of the tag the caret is in, read from the text around it. Completion runs while
 * the tag is being typed — unclosed, half an attribute long — where the template parser has no
 * element for it, so this reads the text directly: back to the tag's {@code <}, forward to its
 * {@code >} (or the next tag, or the end), quoted values skipped.
 */
final class OpenTagAttributes {

	/** {@code name="value"} or {@code name='value'}; a name may carry a {@code :}/{@code ?} prefix or a namespace. */
	private static final Pattern ATTRIBUTE = Pattern.compile( "([:?]?[A-Za-z_][\\w.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')" );

	private OpenTagAttributes() {
	}

	/** The open tag's attributes by name, in order; empty when the offset isn't inside a tag. */
	static Map<String, String> at( String text, int offset ) {
		final Map<String, String> attributes = new LinkedHashMap<>();
		final String tag = tagAt( text, offset );
		if( tag == null ) {
			return attributes;
		}
		final Matcher matcher = ATTRIBUTE.matcher( tag );
		while( matcher.find() ) {
			attributes.putIfAbsent( matcher.group( 1 ), matcher.group( 2 ) != null ? matcher.group( 2 ) : matcher.group( 3 ) );
		}
		return attributes;
	}

	/** The text of the tag around the offset, from its {@code <}; null when the offset is outside a tag. */
	static String tagAt( String text, int offset ) {
		final int start = text.lastIndexOf( '<', Math.max( 0, offset - 1 ) );
		if( start < 0 ) {
			return null;
		}
		char quote = 0;
		for( int i = start + 1; i < text.length(); i++ ) {
			final char c = text.charAt( i );
			if( quote != 0 ) {
				if( c == quote ) {
					quote = 0;
				}
			}
			else if( c == '"' || c == '\'' ) {
				quote = c;
			}
			else if( c == '>' || c == '<' ) {
				// The tag ends here; a '>' before the offset means the offset is past it.
				return i < offset && c == '>' ? null : text.substring( start, i );
			}
		}
		return text.substring( start );
	}
}

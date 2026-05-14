package jp.aonir.fuzzyxml;

/**
 * A region of text identified by its offset and length, both measured in
 * characters from the start of a containing document.
 *
 * <p>This is a pure-Java replacement for Eclipse's {@code IRegion} so that
 * FuzzyXML doesn't need to depend on {@code org.eclipse.jface.text}.
 * Callers in an Eclipse environment can trivially adapt to/from
 * {@code IRegion} / {@code Region} at the boundary.
 *
 * @param offset the zero-based character offset of the region's start
 * @param length the number of characters in the region (may be zero)
 */
public record TextRegion(int offset, int length) {

	/**
	 * @return the offset one past the last character in this region.
	 */
	public int endOffset() {
		return offset + length;
	}
}

package jp.aonir.fuzzyxml;

public interface FuzzyXMLText extends FuzzyXMLNode, FuzzyXMLFormat {

	/** Returns the decoded text value (entities resolved to characters). */
	public String getValue();

	/**
	 * Returns the raw source text with entities intact, exactly as written
	 * by the author. Used by the formatter to avoid encode/decode roundtrips.
	 */
	public String getRawValue();

	public void setEscape(boolean escape);

	public boolean isEscape();

	public boolean hasLineBreaks();

}

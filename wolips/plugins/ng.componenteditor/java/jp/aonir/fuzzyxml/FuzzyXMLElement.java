package jp.aonir.fuzzyxml;


public interface FuzzyXMLElement extends FuzzyXMLNode, FuzzyXMLFormat {
	
	public String getName();
	
  public int getNameOffset();
  
  public int getNameLength();

  public int getOpenTagLength();

  public boolean hasCloseTag();
  
  public int getCloseTagOffset();

  public int getCloseTagLength();

  public int getCloseNameOffset();

  public int getCloseNameLength();
  
  public FuzzyXMLNode getChild(int index);

  public FuzzyXMLElement getChildElement(int index);

	public FuzzyXMLNode[] getChildren();
	
	public boolean hasChildren();
	
	public boolean isEmpty();
	
	public void appendChild(FuzzyXMLNode node);
	
	public void insertBefore(FuzzyXMLNode newChild,FuzzyXMLNode refChild);
	
	public void insertAfter(FuzzyXMLNode newChild, FuzzyXMLNode refChild);
	
	public void replaceChild(FuzzyXMLNode newChild,FuzzyXMLNode refChild);
	
	public void removeChild(FuzzyXMLNode oldChild);
	
	
	public FuzzyXMLAttribute[] getAttributes();
	
	public void setAttribute(FuzzyXMLAttribute attr);
	
	public boolean hasAttribute(String name);
	
	public FuzzyXMLAttribute getAttributeNode(String name);
	
	public String getAttributeValue(String name);
	
	public void removeAttributeNode(FuzzyXMLAttribute attr);
	
	public void setAttribute(String name, String namespace, String value);
	
	public void removeAttribute(String name);
	
	public String getValue();
	
	public void removeAllChildren();

	/**
	 * Computes a selection region for this element, given a cursor offset
	 * and the source text of the containing document.
	 *
	 * @param offset the cursor offset, in characters from the start of the document
	 * @param source the source text of the document, or {@code null} if no
	 *               text is available (some line-end heuristics are skipped)
	 * @param regionForInsert when true, returns a tight insertion-point region
	 *                        appropriate for placing a caret rather than a
	 *                        selection covering the element
	 * @return the region to use
	 */
	public TextRegion getRegionAtOffset(int offset, String source, boolean regionForInsert);
	
	public boolean isSelfClosing();
  
  public boolean isForbiddenFromHavingChildren();
  
  public void setSynthetic(boolean synthetic);
}

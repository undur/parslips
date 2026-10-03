package jp.aonir.fuzzyxml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * How an attribute name is split into namespace and name: only a colon AFTER something is a
 * namespace separator. A leading colon is part of the name — {@code :id}, a route parameter,
 * is not the attribute {@code id} in an empty namespace.
 */
public class FuzzyXMLParserAttributeNamespaceTest {

	private static FuzzyXMLAttribute attribute(String source, int index) {
		FuzzyXMLDocument doc = new FuzzyXMLParser(false, true).parse(source);
		FuzzyXMLElement element = (FuzzyXMLElement)doc.getDocumentElement().getChildren()[0];
		return element.getAttributes()[index];
	}

	@Test
	public void leadingColonIsPartOfTheName() {
		FuzzyXMLAttribute attr = attribute("<wo:route :id=\"42\" id=\"x\">a</wo:route>", 0);
		assertNull(attr.getNamespace());
		assertEquals(":id", attr.getName());
		assertEquals(":id", attr.getNamespaceName());
	}

	@Test
	public void leadingColonNamePositionCoversTheColon() {
		String source = "<wo:route :id=\"42\">a</wo:route>";
		FuzzyXMLAttribute attr = attribute(source, 0);
		assertEquals(":id", source.substring(attr.getNameOffset(), attr.getNameOffset() + attr.getNameLength()));
	}

	@Test
	public void aRealNamespaceStillSplits() {
		FuzzyXMLAttribute attr = attribute("<p xml:lang=\"is\">a</p>", 0);
		assertEquals("xml", attr.getNamespace());
		assertEquals("lang", attr.getName());
	}

	@Test
	public void questionMarkPrefixIsPartOfTheName() {
		FuzzyXMLAttribute attr = attribute("<wo:route ?highlight=\"yes\">a</wo:route>", 0);
		assertNull(attr.getNamespace());
		assertEquals("?highlight", attr.getName());
	}
}

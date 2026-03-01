package org.objectstyle.wolips.wodclipse.core.woo;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link WooUtils#encodingNameFromObjectiveC(String)} — the mapping
 * from Objective-C/NeXTStep encoding names to Java encoding names, used when
 * reading legacy {@code .woo} files.
 */
public class WooUtilsTest {

	@Test
	public void isoLatin1() {
		assertEquals("ISO-8859-1", WooUtils.encodingNameFromObjectiveC("NSISOLatin1StringEncoding"));
	}

	@Test
	public void macOSRoman() {
		assertEquals("MacRoman", WooUtils.encodingNameFromObjectiveC("NSMacOSRomanStringEncoding"));
	}

	@Test
	public void ascii() {
		assertEquals("US-ASCII", WooUtils.encodingNameFromObjectiveC("NSASCIIStringEncoding"));
	}

	@Test
	public void nextstep() {
		assertEquals("ISO-8859-1", WooUtils.encodingNameFromObjectiveC("NSNEXTSTEPStringEncoding"));
	}

	@Test
	public void japaneseEUC() {
		assertEquals("EUC_JP", WooUtils.encodingNameFromObjectiveC("NSJapaneseEUCStringEncoding"));
	}

	@Test
	public void utf8() {
		assertEquals("UTF-8", WooUtils.encodingNameFromObjectiveC("NSUTF8StringEncoding"));
	}

	@Test
	public void symbol() {
		assertEquals("MacSymbol", WooUtils.encodingNameFromObjectiveC("NSSymbolStringEncoding"));
	}

	@Test
	public void nonLossyASCII() {
		assertEquals("US-ASCII", WooUtils.encodingNameFromObjectiveC("NSNonLossyASCIIStringEncoding"));
	}

	@Test
	public void shiftJIS() {
		assertEquals("SJIS", WooUtils.encodingNameFromObjectiveC("NSShiftJISStringEncoding"));
	}

	@Test
	public void isoLatin2() {
		assertEquals("ISO-8859-2", WooUtils.encodingNameFromObjectiveC("NSISOLatin2StringEncoding"));
	}

	@Test
	public void unicode() {
		assertEquals("Unicode", WooUtils.encodingNameFromObjectiveC("NSUnicodeStringEncoding"));
	}

	@Test
	public void windowsCP1251() {
		assertEquals("Cp1251", WooUtils.encodingNameFromObjectiveC("NSWindowsCP1251StringEncoding"));
	}

	@Test
	public void windowsCP1252() {
		assertEquals("Cp1252", WooUtils.encodingNameFromObjectiveC("NSWindowsCP1252StringEncoding"));
	}

	@Test
	public void windowsCP1253() {
		assertEquals("Cp1253", WooUtils.encodingNameFromObjectiveC("NSWindowsCP1253StringEncoding"));
	}

	@Test
	public void windowsCP1254() {
		assertEquals("Cp1254", WooUtils.encodingNameFromObjectiveC("NSWindowsCP1254StringEncoding"));
	}

	@Test
	public void windowsCP1250() {
		assertEquals("Cp1250", WooUtils.encodingNameFromObjectiveC("NSWindowsCP1250StringEncoding"));
	}

	@Test
	public void iso2022JP() {
		assertEquals("ISO2022JP", WooUtils.encodingNameFromObjectiveC("NSISO2022JPStringEncoding"));
	}

	@Test
	public void unknownEncoding_passedThrough() {
		assertEquals("CustomEncoding", WooUtils.encodingNameFromObjectiveC("CustomEncoding"));
	}

	@Test
	public void emptyString_passedThrough() {
		assertEquals("", WooUtils.encodingNameFromObjectiveC(""));
	}
}

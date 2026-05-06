package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link BindingReflectionUtils#toLowercaseFirstLetter}, the helper
 * that converts a method name (with KVC prefix already stripped) into a
 * binding key.
 *
 * <p>The expected rule matches Cocoa / WebObjects KVC reverse-lookup
 * convention:
 *
 * <ul>
 *   <li>Empty string → unchanged</li>
 *   <li>Single character → lowercased</li>
 *   <li>First two characters both uppercase → unchanged (preserves acronyms
 *       like {@code URL}, {@code HTTPServer})</li>
 *   <li>First character uppercase, second lowercase → first character
 *       lowercased ({@code Title} → {@code title})</li>
 *   <li>First character already lowercase → unchanged</li>
 * </ul>
 *
 * <p>This rule mirrors {@code valueForKey()} at runtime: looking up key
 * {@code "URL"} finds method {@code getURL()} or {@code URL()}; looking up
 * key {@code "uRL"} would not. So the binding key for those methods is
 * {@code "URL"}, not {@code "uRL"}.
 */
public class BindingReflectionUtilsTest {

	// =========================================================================
	// The simple cases — should already work
	// =========================================================================

	@Test
	public void emptyString() {
		assertEquals("", BindingReflectionUtils.toLowercaseFirstLetter(""));
	}

	@Test
	public void alreadyLowercase() {
		assertEquals("title", BindingReflectionUtils.toLowercaseFirstLetter("title"));
	}

	@Test
	public void mixedCaseStartingUpper() {
		assertEquals("title", BindingReflectionUtils.toLowercaseFirstLetter("Title"));
	}

	@Test
	public void singleUppercase() {
		assertEquals("a", BindingReflectionUtils.toLowercaseFirstLetter("A"));
	}

	@Test
	public void singleLowercase() {
		assertEquals("a", BindingReflectionUtils.toLowercaseFirstLetter("a"));
	}

	// =========================================================================
	// Acronym preservation — currently broken
	// =========================================================================

	@Test
	public void acronymOnly_preserved() {
		// A property named just "URL" stays "URL".
		assertEquals("URL", BindingReflectionUtils.toLowercaseFirstLetter("URL"));
	}

	@Test
	public void acronymPrefix_preserved() {
		// "URLString" should stay "URLString" — the leading "UR" are both
		// uppercase, so the first char is left alone.
		assertEquals("URLString", BindingReflectionUtils.toLowercaseFirstLetter("URLString"));
	}

	@Test
	public void httpServer_preserved() {
		// The exact case from the user report.
		assertEquals("HTTPServerUpdateClicked",
				BindingReflectionUtils.toLowercaseFirstLetter("HTTPServerUpdateClicked"));
	}

	@Test
	public void twoLetterAcronym_preserved() {
		assertEquals("IDValue", BindingReflectionUtils.toLowercaseFirstLetter("IDValue"));
	}

	@Test
	public void allUppercase_preserved() {
		// "HTML" — every char uppercase, second is uppercase, leave alone.
		assertEquals("HTML", BindingReflectionUtils.toLowercaseFirstLetter("HTML"));
	}

	// =========================================================================
	// Negative checks — make sure the existing behavior still works
	// =========================================================================

	@Test
	public void singleUppercaseFollowedByLowercase_lowercased() {
		assertEquals("name", BindingReflectionUtils.toLowercaseFirstLetter("Name"));
	}

	@Test
	public void singleUppercaseFollowedByDigit_lowercased() {
		// "X1" — second char isn't a letter at all, but it's not uppercase
		// either (Character.isUpperCase('1') is false), so the first char
		// gets lowercased.
		assertEquals("x1", BindingReflectionUtils.toLowercaseFirstLetter("X1"));
	}
}

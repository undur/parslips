package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.junit.Test;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;

/**
 * Tests for {@link BindingReflectionUtils#isSystemBindingValueKey} — the filter
 * that decides whether a reflected binding key is a framework "system" binding
 * and should be hidden from completion.
 *
 * <p>Regression coverage for the bug where underscore-prefixed bindings derived
 * from {@code WOComponent}'s DirectToWeb plumbing setters
 * ({@code set_componentUnroll} → binding {@code "_componentUnroll"},
 * {@code set_unroll} → {@code "_unroll"}) leaked into attribute completion. The
 * underscore check was an {@code else if} after the system-type branch, so for a
 * key whose declaring type IS a system type the underscore rule was never reached
 * — and these underscore-bearing names aren't in the (non-underscore)
 * {@code _uselessSystemBindings} blocklist either (that blocklist catches the
 * bare {@code _unroll()}/{@code _componentUnroll()} *accessor*-derived names
 * {@code "unroll"}/{@code "componentUnroll"}). The fix hoists the underscore
 * check ahead of the system-type branch so it applies regardless of declaring
 * type; the two checks are complementary.
 *
 * <p>{@link BindingValueKey} reads only its member's declaring-type element name
 * and its binding name in this code path, so we build it from lightweight
 * dynamic-proxy stubs (same idiom as {@code StubProject}) rather than a live
 * JDT model.
 */
public class IsSystemBindingValueKeyTest {

	/**
	 * Builds a {@link BindingValueKey} with the given binding name whose member
	 * reports a declaring {@link IType} with the given simple element name.
	 */
	private static BindingValueKey keyOn(String bindingName, String declaringTypeSimpleName) {
		IType type = (IType) Proxy.newProxyInstance(
				IsSystemBindingValueKeyTest.class.getClassLoader(),
				new Class<?>[] { IType.class },
				new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) {
						if ("getElementName".equals(method.getName())) {
							return declaringTypeSimpleName;
						}
						if ("toString".equals(method.getName())) {
							return "StubType(" + declaringTypeSimpleName + ")";
						}
						throw new UnsupportedOperationException("StubType: " + method.getName());
					}
				});

		IMember member = (IMember) Proxy.newProxyInstance(
				IsSystemBindingValueKeyTest.class.getClassLoader(),
				new Class<?>[] { IMember.class },
				new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) {
						if ("getDeclaringType".equals(method.getName())) {
							return type;
						}
						if ("toString".equals(method.getName())) {
							return "StubMember";
						}
						throw new UnsupportedOperationException("StubMember: " + method.getName());
					}
				});

		// project + cache are unused by isSystemBindingValueKey.
		return new BindingValueKey(bindingName, type, member, null, null);
	}

	// =========================================================================
	// The bug: underscore-prefixed bindings declared on a system type
	// =========================================================================

	@Test
	public void underscoreBindingOnSystemType_isFiltered_componentUnroll() {
		// The exact case from the user report: WOComponent.set_componentUnroll.
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("_componentUnroll", "WOComponent"), true));
	}

	@Test
	public void underscoreBindingOnSystemType_isFiltered_unroll() {
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("_unroll", "WOComponent"), true));
	}

	@Test
	public void underscoreBindingOnNGComponent_isFiltered() {
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("_internalThing", "NGComponent"), true));
	}

	// =========================================================================
	// Underscore rule still applies on non-system types (unchanged behavior)
	// =========================================================================

	@Test
	public void underscoreBindingOnUserType_isFiltered() {
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("_private", "MyReusableComponent"), true));
	}

	// =========================================================================
	// Real bindings are NOT filtered (no over-filtering)
	// =========================================================================

	@Test
	public void normalBindingOnUserType_isNotFiltered() {
		assertFalse(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("title", "MyReusableComponent"), true));
	}

	@Test
	public void normalBindingOnSystemType_isNotFiltered() {
		// A non-blocklisted, non-underscore name declared on a system type
		// (e.g. a hypothetical useful accessor) is kept.
		assertFalse(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("title", "WOComponent"), true));
	}

	// =========================================================================
	// Existing blocklist behavior still works (bare names on system types)
	// =========================================================================

	@Test
	public void blocklistedBareNameOnSystemType_isFiltered() {
		// "componentUnroll" (no underscore) is in _uselessSystemBindings.
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("componentUnroll", "WOComponent"), true));
	}

	@Test
	public void blocklistedBareNameOnUserType_isNotFiltered() {
		// Same name but declared on a user type — not a system binding.
		assertFalse(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("componentUnroll", "MyReusableComponent"), true));
	}

	@Test
	public void usefulBindingHiddenWhenNotShown() {
		// "session" is in _usefulSystemBindings — filtered when not showing them.
		assertTrue(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("session", "WOComponent"), false));
	}

	@Test
	public void usefulBindingKeptWhenShown() {
		assertFalse(BindingReflectionUtils.isSystemBindingValueKey(
				keyOn("session", "WOComponent"), true));
	}
}

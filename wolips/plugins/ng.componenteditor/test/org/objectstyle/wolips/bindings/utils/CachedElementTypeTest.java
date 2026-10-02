package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.Map;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.junit.Test;
import org.objectstyle.wolips.bindings.api.ApiCache;

/**
 * Tests for {@link BindingReflectionUtils#cachedElementType}, the cache-hit half of
 * {@code findElementType}.
 *
 * <p>Regression coverage for issue #11: after a component class moved to another package, the
 * cached fully qualified name no longer resolved, and the null was reported as "class missing"
 * forever (until Eclipse restarted). A stale entry must now be dropped so the caller searches
 * again.
 */
public class CachedElementTypeTest {

	private static IType typeNamed(String fullyQualifiedName) {
		return (IType) Proxy.newProxyInstance(
				CachedElementTypeTest.class.getClassLoader(),
				new Class<?>[] { IType.class },
				(proxy, method, args) -> {
					if ("getFullyQualifiedName".equals(method.getName())) {
						return fullyQualifiedName;
					}
					throw new UnsupportedOperationException("StubType: " + method.getName());
				});
	}

	/** A project whose classpath holds exactly the given types, keyed by fully qualified name. */
	private static IJavaProject projectWith(Map<String, IType> types) {
		return (IJavaProject) Proxy.newProxyInstance(
				CachedElementTypeTest.class.getClassLoader(),
				new Class<?>[] { IJavaProject.class },
				(proxy, method, args) -> {
					if ("findType".equals(method.getName()) && args.length == 1) {
						return types.get(args[0]);
					}
					throw new UnsupportedOperationException("StubProject: " + method.getName());
				});
	}

	@Test
	public void noEntryAnswersNull() throws Exception {
		ApiCache apiCache = new ApiCache();
		assertNull(BindingReflectionUtils.cachedElementType(projectWith(Map.of()), apiCache, "WOString"));
	}

	@Test
	public void entryThatResolvesAnswersTheType() throws Exception {
		IType type = typeNamed("com.example.components.PageLayout");
		ApiCache apiCache = new ApiCache();
		apiCache.setElementTypeForName(type, "PageLayout");

		IJavaProject project = projectWith(Map.of("com.example.components.PageLayout", type));
		assertSame(type, BindingReflectionUtils.cachedElementType(project, apiCache, "PageLayout"));
		assertSame("com.example.components.PageLayout", apiCache.getElementTypeNamed("PageLayout"));
	}

	@Test
	public void movedClassDropsTheStaleEntry() throws Exception {
		ApiCache apiCache = new ApiCache();
		apiCache.setElementTypeForName(typeNamed("com.example.old.PageLayout"), "PageLayout");

		// The class now lives in another package; the old name no longer resolves.
		IJavaProject project = projectWith(Map.of("com.example.moved.PageLayout", typeNamed("com.example.moved.PageLayout")));
		assertNull(BindingReflectionUtils.cachedElementType(project, apiCache, "PageLayout"));
		assertNull("stale entry must be forgotten so the next lookup searches", apiCache.getElementTypeNamed("PageLayout"));
	}

	@Test
	public void runtimeKeyedEntriesHealToo() throws Exception {
		ApiCache apiCache = new ApiCache();
		apiCache.setElementTypeForName(typeNamed("com.example.old.PageLayout"), "NG:PageLayout");

		assertNull(BindingReflectionUtils.cachedElementType(projectWith(Map.of()), apiCache, "NG:PageLayout"));
		assertNull(apiCache.getElementTypeNamed("NG:PageLayout"));
	}
}

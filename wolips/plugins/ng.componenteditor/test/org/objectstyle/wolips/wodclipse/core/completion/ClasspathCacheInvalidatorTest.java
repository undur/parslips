package org.objectstyle.wolips.wodclipse.core.completion;

import static org.junit.Assert.*;

import org.eclipse.jdt.core.IJavaElementDelta;
import org.junit.Test;

/**
 * Which project deltas count as a classpath change for {@link ClasspathCacheInvalidator}: a change
 * to the declared classpath and a change to the resolved one (m2e resolving its container, a
 * dependency updated) both do; other project changes don't.
 */
public class ClasspathCacheInvalidatorTest {

	@Test
	public void resolvedClasspathChangeCounts() {
		assertTrue(ClasspathCacheInvalidator.isClasspathChange(IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED));
	}

	@Test
	public void declaredClasspathChangeCounts() {
		assertTrue(ClasspathCacheInvalidator.isClasspathChange(IJavaElementDelta.F_CLASSPATH_CHANGED));
	}

	@Test
	public void classpathChangeAmongOtherFlagsCounts() {
		assertTrue(ClasspathCacheInvalidator.isClasspathChange(IJavaElementDelta.F_CHILDREN | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED));
	}

	@Test
	public void otherProjectChangesDoNotCount() {
		assertFalse(ClasspathCacheInvalidator.isClasspathChange(0));
		assertFalse(ClasspathCacheInvalidator.isClasspathChange(IJavaElementDelta.F_CHILDREN | IJavaElementDelta.F_CONTENT));
		assertFalse(ClasspathCacheInvalidator.isClasspathChange(IJavaElementDelta.F_OPENED | IJavaElementDelta.F_CLOSED));
	}
}

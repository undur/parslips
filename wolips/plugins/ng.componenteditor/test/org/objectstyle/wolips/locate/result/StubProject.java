package org.objectstyle.wolips.locate.result;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.core.resources.IProject;

/**
 * Minimal stub {@link IProject} for unit tests that need a non-null
 * project reference but don't call any IProject methods.
 *
 * <p>Uses a dynamic proxy to avoid implementing the entire IProject
 * interface. Any method call on this stub throws
 * {@link UnsupportedOperationException}.
 */
class StubProject {

	/** Shared stub instance — safe to reuse since it's stateless. */
	static final IProject INSTANCE = createStub();

	private static IProject createStub() {
		return (IProject) Proxy.newProxyInstance(
				StubProject.class.getClassLoader(),
				new Class<?>[] { IProject.class },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) {
						if ("toString".equals(method.getName())) {
							return "StubProject";
						}
						throw new UnsupportedOperationException(
								"StubProject does not implement " + method.getName());
					}
				});
	}
}

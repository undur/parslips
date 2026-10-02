package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

/**
 * The endpoints that used to answer a bare "ok" to a request they couldn't act on now name the
 * problem. These cover the missing-parameter case, which is answered before any workspace access,
 * so the handlers can run without Eclipse. (Not-found refusals need a workspace and were verified
 * against a running dev server.)
 */
public class MissingParameterTest {

	private static void assertNamesMissing(String response, String parameter) {
		IndexHandlerTest.assertWellFormed(response);
		assertTrue(response, response.startsWith("{\"error\":"));
		assertTrue(response, response.contains("'" + parameter + "'"));
	}

	@Test
	public void openComponentWithoutComponent() throws Exception {
		assertNamesMissing(new OpenComponentHandler().handle(Map.of()), "component");
	}

	@Test
	public void openJavaFileWithoutClassName() throws Exception {
		assertNamesMissing(new OpenJavaFileHandler().handle(Map.of("lineNumber", "12")), "className");
	}

	@Test
	public void refreshWithoutPath() throws Exception {
		assertNamesMissing(new RefreshHandler().handle(Map.of()), "path");
	}

	@Test
	public void emptyValueCountsAsMissing() throws Exception {
		assertNamesMissing(new RefreshHandler().handle(Map.of("path", "")), "path");
	}
}

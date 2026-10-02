package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

/**
 * A call that is itself wrong (a missing or invalid parameter) answers {@code {"error": ...}}
 * naming the parameter — never a blind "ok", and never a {@code reason}, which is reserved for
 * a valid call the workspace refused (see {@link DevServerHandler}'s conventions). These cases
 * are answered before any workspace access, so the handlers run without Eclipse; the
 * workspace refusals were verified against a running dev server.
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

	@Test
	public void stopWithoutApp() throws Exception {
		assertNamesMissing(new StopHandler().handle(Map.of()), "app");
	}

	@Test
	public void importProjectWithoutPath() throws Exception {
		assertNamesMissing(new ImportProjectHandler().handle(Map.of()), "path");
	}

	@Test
	public void createProjectWithBadInputIsAnError() throws Exception {
		final String noName = new CreateProjectHandler().handle(Map.of("template", "maven"));
		IndexHandlerTest.assertWellFormed(noName);
		assertTrue(noName, noName.startsWith("{\"error\":"));

		final String noTemplate = new CreateProjectHandler().handle(Map.of("name", "Valid"));
		IndexHandlerTest.assertWellFormed(noTemplate);
		assertTrue(noTemplate, noTemplate.startsWith("{\"error\":") && noTemplate.contains("template"));
	}
}

package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.regex.Matcher;

import org.junit.Test;

/**
 * The forms {@code RouteKeys.of( X.class )} is recognised in: a method body, a field initializer,
 * spaced or not, imported or qualified. Group 1 is the constants class as written.
 */
public class RouteKeysSupportTest {

	private static String constantsClass(String source) {
		final Matcher matcher = RouteKeysSupport.OF.matcher(source);
		return matcher.find() ? matcher.group(1) : null;
	}

	@Test
	public void methodBody() {
		assertEquals("Routes", constantsClass("default Object routes() {\n\t\treturn RouteKeys.of( Routes.class );\n\t}"));
	}

	@Test
	public void fieldInitializer() {
		assertEquals("Web", constantsClass("RouteKeys web = RouteKeys.of( Web.class );"));
	}

	@Test
	public void unspacedAndNested() {
		assertEquals("Routes.Web", constantsClass("RouteKeys web = RouteKeys.of(Routes.Web.class);"));
	}

	@Test
	public void qualifiedRouteKeys() {
		final Matcher matcher = RouteKeysSupport.OF.matcher("return er.extensions.routing.RouteKeys.of( Routes.class );");
		assertEquals(true, matcher.find());
		assertEquals("er.extensions.routing.RouteKeys.of( Routes.class )", matcher.group(0));
		assertEquals("Routes", matcher.group(1));
	}

	@Test
	public void somethingElseIsNotRouteKeys() {
		assertFalse(RouteKeysSupport.OF.matcher("return MyKeys.of( Routes.class );").find());
		assertFalse(RouteKeysSupport.OF.matcher("return RouteKeys.of( routesClass );").find());
	}
}

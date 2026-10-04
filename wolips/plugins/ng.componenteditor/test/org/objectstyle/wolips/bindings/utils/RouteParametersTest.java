package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.Test;

/**
 * A plain route's parameters read from its declaration: the placeholders of the pattern it's
 * mapped under and of the groups the receiver came from, however those groups are reached.
 */
public class RouteParametersTest {

	/** The parameters declared by the first reference to {@code Routes.<constant>} in the source. */
	private static List<String> declared(String source, String constant) {
		final CompilationUnit unit = RouteParameters.parse(source);
		final String reference = "Routes." + constant;
		final int offset = source.indexOf(reference) + "Routes.".length();
		return RouteParameters.declaredAt(unit, offset, constant.length());
	}

	private static String declarations(String body) {
		return "class Declarations {\n\tvoid declare( final RouteGroup application ) {\n" + body + "\n\t}\n}";
	}

	@Test
	public void placeholders() {
		assertEquals(List.of("club", "book"), RouteParameters.placeholders("/clubs/{club}/books/{book}"));
		assertEquals(List.of("id"), RouteParameters.placeholders("/items/{id}.json"));
		assertEquals(List.of("path"), RouteParameters.placeholders("/files/{path*}"));
		assertEquals(List.of(), RouteParameters.placeholders("/news/*"));
	}

	@Test
	public void mapOnTheRootGroup() {
		assertEquals(List.of("key"), declared(declarations("application.map( \"/go/{key}\", Routes.go, new GoRoute() );"), "go"));
	}

	@Test
	public void groupPrefixThroughLocals() {
		final String source = declarations("""
				final RouteGroup routes = application.group( "", TrailingSlash.REDIRECT );
				final RouteGroup app = routes.group( "/app/{token}" );
				app.map( "/receipts/{receipt}", Routes.receipt, new ReceiptRoute() );
				""");
		assertEquals(List.of("token", "receipt"), declared(source, "receipt"));
	}

	@Test
	public void groupPrefixThroughAChain() {
		final String source = declarations("""
				final RouteGroup club = application.group( "/clubs/{club}" ).parameter( "club", Club.class ).named( "club" );
				final RouteGroup admin = club.group( "/admin" );
				admin.map( "/members/{member}", Routes.member, MemberPage.class );
				""");
		assertEquals(List.of("club", "member"), declared(source, "member"));
	}

	@Test
	public void groupPrefixThroughALambda() {
		final String source = declarations("""
				application.group( "/clubs/{club}", club -> {
					club.map( "/books/{book}", Routes.book, BookPage.class );
				} );
				""");
		assertEquals(List.of("club", "book"), declared(source, "book"));
	}

	@Test
	public void groupPrefixThroughAField() {
		final String source = """
				class Declarations {
					private final RouteGroup club = Router.root().group( "/clubs/{club}" );
					void declare() {
						club.map( "/books/{book}", Routes.book, BookPage.class );
					}
				}
				""";
		assertEquals(List.of("club", "book"), declared(source, "book"));
	}

	@Test
	public void qualifiedConstant() {
		final String source = declarations("application.map( \"/news/{item}\", Routes.Web.news, NewsPage.class );");
		final CompilationUnit unit = RouteParameters.parse(source);
		final int offset = source.indexOf("news,");
		assertEquals(List.of("item"), RouteParameters.declaredAt(unit, offset, "news".length()));
	}

	@Test
	public void aRedirectIsNotTheRoutesDeclaration() {
		assertEquals(List.of(), declared(declarations("application.redirect( \"/book/{old}\", Routes.book );"), "book"));
	}

	@Test
	public void aUseIsNotADeclaration() {
		assertEquals(List.of(), declared(declarations("String url = Routes.book.url( Map.of(), context );"), "book"));
	}

	@Test
	public void aJoinedGroupKeepsOnlyItsOwnPattern() {
		final String source = declarations("application.join( \"club\" ).map( \"/events/{event}\", Routes.event, EventPage.class );");
		assertEquals(List.of("event"), declared(source, "event"));
	}
}

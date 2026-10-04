package org.objectstyle.wolips.bindings.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

/**
 * The parameters of a wonder-slim route constant, for completing a route link's {@code :}
 * attributes ({@code <wo:route to="$routes.book" :book="…">}). Read from the source, statically —
 * the editor doesn't need the application running:
 *
 * <ul>
 * <li>A <b>typed</b> route ({@code Route<Books> books = Route.of( Books.class )}): its parameters
 * are the record's components, whatever its pattern says.</li>
 * <li>A <b>plain</b> route ({@code PlainRoute news = Route.plain()}): its parameters are the
 * placeholders of the pattern it's mapped under — {@code group.map( "/news/{item}", Routes.news, … )},
 * found by searching the constant's references, with the prefixes of the groups the receiver came
 * from ({@code routes.group( "/clubs/{club}" ).named( "club" )}, through local variables, fields and
 * {@code group( prefix, club -> … )} lambdas). A group that arrives as a method parameter, or is
 * {@code join}ed by name, has a prefix declared elsewhere; its placeholders aren't found. A
 * constant mapped several times (in several tables) answers the parameters of all of them.</li>
 * </ul>
 *
 * {@code redirect( pattern, route )} doesn't count: its pattern is the old URL's, and the constant
 * is where it redirects to. Completion only — nothing here is used to report a problem, since a
 * declaration this can't follow would make one wrong.
 */
public final class RouteParameters {

	public static final String ROUTE = "er.extensions.routing.Route";
	public static final String PLAIN_ROUTE = "er.extensions.routing.PlainRoute";

	/** A pattern's named parameter, {@code {name}}, or a named wildcard, {@code {name*}}; group 1 is the name. */
	private static final Pattern PLACEHOLDER = Pattern.compile( "\\{([A-Za-z_][A-Za-z0-9_]*)\\*?\\}" );

	/** RouteGroup methods that answer the group they're called on, so the prefix passes through them. */
	private static final Set<String> SAME_GROUP = Set.of( "named", "parameter", "wrap", "fallback", "notFound" );

	/** How far a receiver is followed back: far beyond any real declaration, short of a cycle. */
	private static final int MAX_DEPTH = 32;

	private RouteParameters() {
	}

	/**
	 * The route constant's parameter names, in declaration order, or an empty list when the field
	 * isn't a route, or its declarations weren't found.
	 */
	public static List<String> of( IField route ) {
		try {
			final IType declaringType = route.getDeclaringType();
			if( declaringType == null ) {
				return List.of();
			}
			final String signature = route.getTypeSignature();
			final String erasure = qualified( declaringType, Signature.toString( Signature.getTypeErasure( signature ) ) );
			if( ROUTE.equals( erasure ) ) {
				return typed( declaringType, signature );
			}
			if( PLAIN_ROUTE.equals( erasure ) ) {
				return plain( route );
			}
		}
		catch( final CoreException e ) {
			// Completion offers nothing rather than failing.
		}
		return List.of();
	}

	/** {@code Route<X>}: X's record components. */
	private static List<String> typed( IType declaringType, String signature ) throws JavaModelException {
		final String[] arguments = Signature.getTypeArguments( signature );
		if( arguments.length != 1 ) {
			return List.of();
		}
		final String recordName = qualified( declaringType, Signature.toString( Signature.getTypeErasure( arguments[0] ) ) );
		final IType record = recordName == null ? null : declaringType.getJavaProject().findType( recordName );
		if( record == null || !record.isRecord() ) {
			return List.of();
		}
		final List<String> names = new ArrayList<>();
		for( final IField component : record.getRecordComponents() ) {
			names.add( component.getElementName() );
		}
		return names;
	}

	/** A plain route: the placeholders of every {@code map} the constant is passed to, in source. */
	private static List<String> plain( IField route ) throws CoreException {
		final Map<ICompilationUnit, List<int[]>> references = new java.util.LinkedHashMap<>();
		final SearchPattern pattern = SearchPattern.createPattern( route, IJavaSearchConstants.REFERENCES );
		final SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch( SearchMatch match ) {
				if( match.getElement() instanceof IJavaElement element && element.getAncestor( IJavaElement.COMPILATION_UNIT ) instanceof ICompilationUnit unit ) {
					references.computeIfAbsent( unit, u -> new ArrayList<>() ).add( new int[] { match.getOffset(), match.getLength() } );
				}
			}
		};
		// The application's own sources and every project it can see: the declarations may live in
		// a framework of the workspace, as the constants may.
		new SearchEngine().search( pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, SearchEngine.createWorkspaceScope(), requestor, new NullProgressMonitor() );
		final Set<String> names = new LinkedHashSet<>();
		for( final Map.Entry<ICompilationUnit, List<int[]>> entry : references.entrySet() ) {
			final String source = entry.getKey().getSource();
			if( source == null ) {
				continue;
			}
			final CompilationUnit unit = parse( source );
			for( final int[] reference : entry.getValue() ) {
				names.addAll( declaredAt( unit, reference[0], reference[1] ) );
			}
		}
		return new ArrayList<>( names );
	}

	/** Parses source, syntax only: what follows reads the declarations' shape, not their types. */
	static CompilationUnit parse( String source ) {
		final ASTParser parser = ASTParser.newParser( AST.getJLSLatest() );
		final Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions( JavaCore.latestSupportedJavaVersion(), options );
		parser.setCompilerOptions( options );
		parser.setKind( ASTParser.K_COMPILATION_UNIT );
		parser.setSource( source.toCharArray() );
		parser.setResolveBindings( false );
		return (CompilationUnit)parser.createAST( null );
	}

	/**
	 * The parameters declared by the reference at the offset, when it's the route argument of a
	 * {@code map( "pattern", route, … )}: the placeholders of the receiver's prefix and the
	 * pattern. Anything else — the constant's own declaration, a use in code, a redirect — none.
	 */
	static List<String> declaredAt( CompilationUnit unit, int offset, int length ) {
		ASTNode node = NodeFinder.perform( unit, offset, length );
		// A qualified reference (Routes.Web.news) is found as its last name: up to the whole expression.
		while( node != null && node.getParent() instanceof Expression parent && !( parent instanceof MethodInvocation ) ) {
			node = parent;
		}
		if( node == null || !( node.getParent() instanceof MethodInvocation invocation ) ) {
			return List.of();
		}
		final List<?> arguments = invocation.arguments();
		if( !"map".equals( invocation.getName().getIdentifier() ) || arguments.size() < 2 || arguments.get( 1 ) != node || !( arguments.get( 0 ) instanceof StringLiteral pattern ) ) {
			return List.of();
		}
		return placeholders( prefix( invocation.getExpression(), 0 ) + pattern.getLiteralValue() );
	}

	/** The parameter names of a path pattern, in order: {@code {name}} and {@code {name*}}. */
	static List<String> placeholders( String pattern ) {
		final Set<String> names = new LinkedHashSet<>();
		final Matcher matcher = PLACEHOLDER.matcher( pattern );
		while( matcher.find() ) {
			names.add( matcher.group( 1 ) );
		}
		return new ArrayList<>( names );
	}

	/**
	 * The path prefix a group expression stands for, as far as the source says: the patterns of
	 * the {@code group( "…" )} calls it came from, outermost first. Where it can't be followed
	 * (a method parameter, a {@code join}, anything unrecognised) it's the empty prefix.
	 */
	private static String prefix( Expression group, int depth ) {
		if( group == null || depth > MAX_DEPTH ) {
			return "";
		}
		if( group instanceof ParenthesizedExpression parenthesized ) {
			return prefix( parenthesized.getExpression(), depth + 1 );
		}
		if( group instanceof MethodInvocation invocation ) {
			final String name = invocation.getName().getIdentifier();
			if( "group".equals( name ) ) {
				return prefix( invocation.getExpression(), depth + 1 ) + literal( invocation, 0 );
			}
			if( SAME_GROUP.contains( name ) ) {
				return prefix( invocation.getExpression(), depth + 1 );
			}
			return "";
		}
		if( group instanceof FieldAccess access && access.getExpression() instanceof ThisExpression ) {
			return prefixOfField( access, access.getName().getIdentifier(), depth );
		}
		if( group instanceof SimpleName name ) {
			return prefixOfName( name, depth );
		}
		return "";
	}

	/**
	 * A name's prefix: the variable it names, found by walking out from the use — a local declared
	 * earlier in an enclosing block, a lambda's parameter, a method's parameter, a field.
	 */
	private static String prefixOfName( SimpleName name, int depth ) {
		final String identifier = name.getIdentifier();
		ASTNode child = name;
		for( ASTNode scope = name.getParent(); scope != null; child = scope, scope = scope.getParent() ) {
			if( scope instanceof Block block ) {
				for( final Object statement : block.statements() ) {
					if( statement == child ) {
						break; // only what's declared before the use
					}
					if( statement instanceof VariableDeclarationStatement declaration ) {
						for( final Object fragment : declaration.fragments() ) {
							if( fragment instanceof VariableDeclarationFragment variable && variable.getName().getIdentifier().equals( identifier ) ) {
								return prefix( variable.getInitializer(), depth + 1 );
							}
						}
					}
				}
			}
			else if( scope instanceof LambdaExpression lambda ) {
				for( final Object parameter : lambda.parameters() ) {
					if( parameter instanceof VariableDeclaration variable && variable.getName().getIdentifier().equals( identifier ) ) {
						// group( "/prefix", inner -> … ): the parameter is the new group.
						if( lambda.getParent() instanceof MethodInvocation invocation && "group".equals( invocation.getName().getIdentifier() ) ) {
							return prefix( invocation.getExpression(), depth + 1 ) + literal( invocation, 0 );
						}
						return "";
					}
				}
			}
			else if( scope instanceof MethodDeclaration method ) {
				for( final Object parameter : method.parameters() ) {
					if( parameter instanceof VariableDeclaration variable && variable.getName().getIdentifier().equals( identifier ) ) {
						return ""; // a group handed in: its prefix is the caller's
					}
				}
			}
			else if( scope instanceof AbstractTypeDeclaration ) {
				return prefixOfField( scope, identifier, depth );
			}
		}
		return "";
	}

	/** A field's prefix, from its initializer, looked up in the type enclosing the node. */
	private static String prefixOfField( ASTNode node, String identifier, int depth ) {
		ASTNode type = node;
		while( type != null && !( type instanceof AbstractTypeDeclaration ) ) {
			type = type.getParent();
		}
		if( type instanceof AbstractTypeDeclaration declaration ) {
			for( final Object member : declaration.bodyDeclarations() ) {
				if( member instanceof FieldDeclaration field ) {
					for( final Object fragment : field.fragments() ) {
						if( fragment instanceof VariableDeclarationFragment variable && variable.getName().getIdentifier().equals( identifier ) ) {
							return prefix( variable.getInitializer(), depth + 1 );
						}
					}
				}
			}
		}
		return "";
	}

	/** The invocation's argument at the index when it's a string literal, otherwise empty. */
	private static String literal( MethodInvocation invocation, int index ) {
		final List<?> arguments = invocation.arguments();
		return arguments.size() > index && arguments.get( index ) instanceof StringLiteral literal ? literal.getLiteralValue() : "";
	}

	/** A simple or qualified name as the declaring type's source sees it, qualified; null when it doesn't resolve. */
	private static String qualified( IType declaringType, String name ) throws JavaModelException {
		final String[][] resolved = declaringType.resolveType( name );
		if( resolved == null || resolved.length != 1 ) {
			return null;
		}
		return resolved[0][0].isEmpty() ? resolved[0][1] : resolved[0][0] + "." + resolved[0][1];
	}
}

package org.objectstyle.wolips.bindings.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.objectstyle.wolips.bindings.wod.TypeCache;

/**
 * wonder-slim's {@code RouteKeys}, understood: key-value coding can't read static fields, so
 * an application gives templates its route constants through an object whose keys are a
 * class's public static fields —
 *
 * <pre>
 * default Object routes() { return RouteKeys.of( Routes.class ); }   // $routes.…
 * RouteKeys web = RouteKeys.of( Web.class );                         // $routes.web.…
 * </pre>
 *
 * Statically that's an {@code Object} (or a {@code RouteKeys}), so a keypath through it stopped
 * there: every route link carried a "no key" warning, and a misspelled route name went
 * unnoticed until the page rendered. Its keys are what {@code RouteKeys.valueForKey} answers —
 * {@code Class.getField}: X's public fields, inherited ones included, static only — so they
 * can be resolved: this finds the class X in the member's source ({@code RouteKeys.of( X.class )}
 * in a method body or a field initializer) and looks keys up among X's constants.
 *
 * <p>A member without source (in a jar) or written any other way isn't recognised, and its
 * keypath behaves as before.
 */
public final class RouteKeysSupport {

	public static final String ROUTE_KEYS = "er.extensions.routing.RouteKeys";

	/** {@code RouteKeys.of( X.class )}, optionally qualified; group 1 is X as written. */
	static final Pattern OF = Pattern.compile( "(?:\\b[\\w.]*\\.)?\\bRouteKeys\\s*\\.\\s*of\\s*\\(\\s*([A-Za-z_][\\w.]*)\\s*\\.\\s*class\\s*\\)" );

	private RouteKeysSupport() {
	}

	/**
	 * The class whose constants the member's value answers as keys, or null when the member
	 * isn't a RouteKeys: its declared type must be {@code RouteKeys} or {@code Object}, and its
	 * source (method body or field initializer) must say {@code RouteKeys.of( X.class )}.
	 */
	public static IType constantsClass( IMember member ) {
		try {
			if( member == null || member.isBinary() ) {
				return null;
			}
			final IType declaringType = member.getDeclaringType();
			final String typeSignature = member instanceof IMethod method ? method.getReturnType() : member instanceof IField field ? field.getTypeSignature() : null;
			if( declaringType == null || typeSignature == null ) {
				return null;
			}
			final String declared = qualified( declaringType, Signature.toString( Signature.getTypeErasure( typeSignature ) ) );
			if( !ROUTE_KEYS.equals( declared ) && !"java.lang.Object".equals( declared ) ) {
				return null;
			}
			final String source = member.getSource();
			if( source == null ) {
				return null;
			}
			final Matcher matcher = OF.matcher( source );
			if( !matcher.find() ) {
				return null;
			}
			// The RouteKeys in the source must be wonder-slim's, not a class that shares the name:
			// written fully qualified, or imported (resolved through the declaring type).
			final String expression = matcher.group( 0 );
			final boolean wonderSlims = expression.startsWith( ROUTE_KEYS + "." )
					|| ( expression.startsWith( "RouteKeys" ) && ROUTE_KEYS.equals( qualified( declaringType, "RouteKeys" ) ) );
			if( !wonderSlims ) {
				return null;
			}
			final String constantsClassName = qualified( declaringType, matcher.group( 1 ) );
			return constantsClassName == null ? null : declaringType.getJavaProject().findType( constantsClassName );
		}
		catch( final JavaModelException e ) {
			return null;
		}
	}

	/**
	 * The constant named {@code name} among the class's — what {@code Class.getField} finds:
	 * public fields, inherited ones included, static only. An interface's fields are public and
	 * static whatever their flags say (JDT reports only the modifiers written).
	 */
	public static IField constant( IType constantsClass, String name, TypeCache cache ) throws JavaModelException {
		for( final IField field : constants( constantsClass, cache ) ) {
			if( field.getElementName().equals( name ) ) {
				return field;
			}
		}
		return null;
	}

	/** Every constant of the class, by the same rule; for suggestions and completion. */
	public static List<IField> constants( IType constantsClass, TypeCache cache ) throws JavaModelException {
		final List<IField> constants = new ArrayList<>();
		final Set<String> seen = new LinkedHashSet<>();
		for( final IType type : cache.getSupertypesOf( constantsClass ) ) {
			for( final IField field : type.getFields() ) {
				final boolean isConstant = type.isInterface() || ( Flags.isPublic( field.getFlags() ) && Flags.isStatic( field.getFlags() ) );
				if( isConstant && seen.add( field.getElementName() ) ) {
					constants.add( field );
				}
			}
		}
		return constants;
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

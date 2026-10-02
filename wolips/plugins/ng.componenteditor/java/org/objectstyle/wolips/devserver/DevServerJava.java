package org.objectstyle.wolips.devserver;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;

/**
 * Java model facts as JSON, for the endpoints that report keys and their declarations
 * ({@code /componentApi}, {@code /keypath}, {@code /find}): a key's type, and where a member is
 * declared as a workspace path and 1-based line an agent can open or read.
 */
final class DevServerJava {

	private DevServerJava() {
	}

	/**
	 * The type a key yields, as a readable name: the resolved type's qualified name (generics
	 * resolved, as completion resolves them), else the member's declared type ({@code int},
	 * {@code List<String>}; a setter's parameter type), else null.
	 */
	static String typeName(BindingValueKey key) {
		try {
			final IType next = key.getNextType();
			if (next != null) {
				return next.getFullyQualifiedName('.');
			}
		}
		catch (final Exception e) {
			// Fall back to the declared signature below.
		}
		final IMember member = key.getBindingMember();
		try {
			if (member instanceof IMethod method) {
				// A mutator (setLabel(String)) yields what it takes; an accessor what it returns.
				final String[] parameters = method.getParameterTypes();
				return qualified(parameters.length == 1 ? parameters[0] : method.getReturnType(), member.getDeclaringType());
			}
			if (member instanceof IField field) {
				return qualified(field.getTypeSignature(), member.getDeclaringType());
			}
		}
		catch (final JavaModelException e) {
			// Unknown.
		}
		return null;
	}

	/**
	 * A signature as a readable type name, qualified where it can be: a source signature names
	 * types as written ({@code String}), so a plain (non-generic, non-array) one is resolved
	 * against the declaring type's imports to {@code java.lang.String}, matching the names the
	 * resolved path gives.
	 */
	private static String qualified(String signature, IType declaringType) throws JavaModelException {
		final String readable = Signature.toString(signature);
		if (declaringType == null || signature.charAt(0) != Signature.C_UNRESOLVED || readable.contains("<")) {
			return readable;
		}
		final String[][] resolved = declaringType.resolveType(readable);
		if (resolved == null || resolved.length != 1) {
			return readable;
		}
		return resolved[0][0].isEmpty() ? resolved[0][1] : resolved[0][0] + "." + resolved[0][1];
	}

	/**
	 * Where a member (or type) is declared: {@code {"file":…,"line":N}} for source in the
	 * workspace, {@code {"binary":"jar path"}} for a member of a library — where there is no line
	 * to open, but knowing it's framework code is the useful part — or null when unknown.
	 */
	static JsonObject location(IMember member) {
		if (member == null) {
			return null;
		}
		try {
			if (member.isBinary()) {
				return new JsonObject().put("binary", member.getPath().toString());
			}
			final IResource resource = member.getResource();
			final ISourceRange name = member.getNameRange();
			final JsonObject location = new JsonObject().put("file", resource == null ? null : resource.getFullPath().toString());
			final int line = lineOf(member.getTypeRoot(), name == null ? -1 : name.getOffset());
			if (line > 0) {
				location.put("line", line);
			}
			return location;
		}
		catch (final JavaModelException e) {
			return null;
		}
	}

	/** The 1-based line of an offset in a compilation unit's source, or -1. */
	private static int lineOf(ITypeRoot root, int offset) throws JavaModelException {
		if (root == null || offset < 0) {
			return -1;
		}
		final String source = root.getSource();
		if (source == null || offset > source.length()) {
			return -1;
		}
		int line = 1;
		for (int i = 0; i < offset; i++) {
			if (source.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/** "method" or "field" — whether the key is read through an accessor or straight off a field. */
	static String memberKind(IMember member) {
		return member instanceof IMethod ? "method" : member instanceof IField ? "field" : null;
	}
}

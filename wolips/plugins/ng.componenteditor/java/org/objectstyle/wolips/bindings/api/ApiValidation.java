package org.objectstyle.wolips.bindings.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.objectstyle.wolips.bindings.Activator;

/**
 * Immutable representation of a validation rule from a {@code .api} file.
 *
 * <p>Replaces the 14-class DOM-backed validation hierarchy ({@code Validation},
 * {@code And}, {@code Or}, {@code Not}, {@code Count}, {@code Bound},
 * {@code Unbound}, {@code Settable}, {@code Unsettable}, {@code Gettable},
 * {@code Ungettable}) for the read path with a single class using an enum
 * discriminator.
 *
 * <p>A validation tree consists of a top-level {@code ApiValidation} with
 * {@link Kind#VALIDATION} (corresponding to a {@code <validation>} element)
 * which has a message and child nodes. Child nodes can be leaf predicates
 * ({@code BOUND}, {@code UNBOUND}, etc.) or composite operators ({@code AND},
 * {@code OR}, {@code NOT}, {@code COUNT}).
 *
 * <p>Evaluation is pure-functional: given a map of binding name to value,
 * each node evaluates to true/false with no side effects or DOM access.
 * All fields are final and all collections are unmodifiable, making instances
 * inherently thread-safe.
 */
public final class ApiValidation {

	/** The type of validation node — determines evaluation semantics. */
	public enum Kind {
		/** Top-level {@code <validation>} — AND semantics with a message. */
		VALIDATION,
		/** {@code <and>} — all children must be true. */
		AND,
		/** {@code <or>} — at least one child must be true. */
		OR,
		/** {@code <not>} — all children must be false. */
		NOT,
		/** {@code <count>} — compares count of true children against a threshold. */
		COUNT,
		/** {@code <bound>} — binding IS present in the bindings map. */
		BOUND,
		/** {@code <unbound>} — binding is NOT present in the bindings map. */
		UNBOUND,
		/** {@code <settable>} — binding value is NOT a constant (not quoted, or quoted tilde). */
		SETTABLE,
		/** {@code <unsettable>} — binding value IS a constant (quoted, not quoted tilde). */
		UNSETTABLE,
		/** {@code <gettable>} — same logic as SETTABLE. */
		GETTABLE,
		/** {@code <ungettable>} — same logic as UNSETTABLE. */
		UNGETTABLE
	}

	private final Kind _kind;

	/** Non-null only for {@link Kind#VALIDATION}. The error message shown when validation fails. */
	private final String _message;

	/** Non-null only for leaf predicates (BOUND, UNBOUND, SETTABLE, etc.). The binding name to test. */
	private final String _bindingName;

	/** Non-null only for {@link Kind#COUNT}. The test expression, e.g. {@code ">2"}, {@code "==1"}. */
	private final String _countTest;

	/** Child validation nodes. Unmodifiable. Empty for leaf predicates. */
	private final List<ApiValidation> _children;

	/**
	 * Creates a leaf predicate node (BOUND, UNBOUND, SETTABLE, etc.).
	 */
	public ApiValidation(Kind kind, String bindingName) {
		this(kind, null, bindingName, null, Collections.emptyList());
	}

	/**
	 * Creates a composite node with children (VALIDATION, AND, OR, NOT, COUNT).
	 *
	 * @param kind the node type
	 * @param message the error message (only for VALIDATION, null otherwise)
	 * @param countTest the test expression (only for COUNT, null otherwise)
	 * @param children the child validation nodes
	 */
	public ApiValidation(Kind kind, String message, String countTest, List<ApiValidation> children) {
		this(kind, message, null, countTest, children);
	}

	private ApiValidation(Kind kind, String message, String bindingName, String countTest, List<ApiValidation> children) {
		_kind = kind;
		_message = message;
		_bindingName = bindingName;
		_countTest = countTest;
		_children = Collections.unmodifiableList(children);
	}

	public Kind getKind() {
		return _kind;
	}

	/**
	 * Returns the error message for this validation rule.
	 * Only meaningful for {@link Kind#VALIDATION} nodes.
	 */
	public String getMessage() {
		return _message;
	}

	/**
	 * Returns the binding name tested by this leaf predicate.
	 * Only meaningful for leaf predicates (BOUND, UNBOUND, etc.).
	 */
	public String getBindingName() {
		return _bindingName;
	}

	/** Returns the count test expression. Only meaningful for {@link Kind#COUNT}. */
	public String getCountTest() {
		return _countTest;
	}

	/** Returns the child validation nodes. Empty for leaf predicates. */
	public List<ApiValidation> getChildren() {
		return _children;
	}

	/**
	 * Returns true if this validation node references the given binding name,
	 * either directly (leaf predicate) or through any descendant.
	 */
	public boolean isAffectedByBindingNamed(String name) {
		if (_bindingName != null) {
			return _bindingName.equals(name);
		}
		for (ApiValidation child : _children) {
			if (child.isAffectedByBindingNamed(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Evaluates this validation node against the given bindings map.
	 *
	 * <p>The bindings map contains binding name → binding value entries for
	 * all bindings currently set on the component instance being validated.
	 * Leaf predicates check for the presence or value format of their named
	 * binding; composite operators combine child results with boolean logic.
	 *
	 * @param bindings the current binding name → value map
	 * @return true if the validation condition is met (which for top-level
	 *         VALIDATION nodes means the constraint is satisfied — i.e. the
	 *         validation does NOT fail)
	 */
	public boolean evaluate(Map<String, String> bindings) {
		switch (_kind) {
			case VALIDATION:
			case AND:
				return evaluateAnd(bindings);
			case OR:
				return evaluateOr(bindings);
			case NOT:
				return evaluateNot(bindings);
			case COUNT:
				return evaluateCount(bindings);
			case BOUND:
				return bindings.containsKey(_bindingName);
			case UNBOUND:
				return !bindings.containsKey(_bindingName);
			case SETTABLE:
			case GETTABLE:
				return evaluateSettableOrGettable(bindings);
			case UNSETTABLE:
			case UNGETTABLE:
				return evaluateUnsettableOrUngettable(bindings);
			default:
				return true;
		}
	}

	/**
	 * AND semantics: all children must evaluate to true.
	 * Short-circuits on first false.
	 */
	private boolean evaluateAnd(Map<String, String> bindings) {
		for (ApiValidation child : _children) {
			if (!child.evaluate(bindings)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * OR semantics: at least one child must evaluate to true.
	 * Short-circuits on first true.
	 */
	private boolean evaluateOr(Map<String, String> bindings) {
		for (ApiValidation child : _children) {
			if (child.evaluate(bindings)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * NOT semantics: all children must evaluate to false.
	 * This matches the original WOLips behavior where NOT with multiple
	 * children ANDs the negations together.
	 */
	private boolean evaluateNot(Map<String, String> bindings) {
		for (ApiValidation child : _children) {
			if (child.evaluate(bindings)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * COUNT semantics: counts how many children evaluate to true, then
	 * compares the count against a threshold expression like {@code ">2"},
	 * {@code "==1"}, {@code "!=0"}, etc.
	 *
	 * <p>The test expression is parsed into an operator and a numeric value.
	 * Supported operators: {@code ==}, {@code =}, {@code >}, {@code <},
	 * {@code >=}, {@code =>}, {@code <=}, {@code =<}, {@code !=}.
	 * If no operator is present, {@code ==} is assumed.
	 */
	private boolean evaluateCount(Map<String, String> bindings) {
		int count = 0;
		for (ApiValidation child : _children) {
			if (child.evaluate(bindings)) {
				count++;
			}
		}

		if (_countTest == null) {
			return true;
		}

		// Parse operator and value from the test expression.
		// Operator characters (<, >, =, !) come first, then digits.
		StringBuilder operatorBuf = new StringBuilder();
		StringBuilder valueBuf = new StringBuilder();
		for (int i = 0; i < _countTest.length(); i++) {
			char ch = _countTest.charAt(i);
			if (ch == '<' || ch == '>' || ch == '=' || ch == '!') {
				operatorBuf.append(ch);
			} else if (ch != ' ' && ch != '\t') {
				valueBuf.append(ch);
			}
		}

		if (valueBuf.length() == 0) {
			return true;
		}

		int value = Integer.parseInt(valueBuf.toString());
		String operator = operatorBuf.length() == 0 ? "==" : operatorBuf.toString();

		if ("=".equals(operator) || "==".equals(operator)) {
			return value == count;
		} else if (">".equals(operator)) {
			return count > value;
		} else if ("<".equals(operator)) {
			return count < value;
		} else if (">=".equals(operator) || "=>".equals(operator)) {
			return count >= value;
		} else if ("<=".equals(operator) || "=<".equals(operator)) {
			return count <= value;
		} else if ("!=".equals(operator)) {
			return count != value;
		} else {
			Activator.getDefault().log("ApiValidation.evaluateCount: Unknown operator '" + operator + "' in test '" + _countTest + "'");
			return true;
		}
	}

	/**
	 * SETTABLE / GETTABLE: binding value is NOT a constant.
	 * A value is considered non-constant if it does not start with a quote,
	 * or if it starts with {@code "~} (quoted OGNL expression).
	 */
	private boolean evaluateSettableOrGettable(Map<String, String> bindings) {
		String value = bindings.get(_bindingName);
		return value != null && (!value.startsWith("\"") || value.startsWith("\"~"));
	}

	/**
	 * UNSETTABLE / UNGETTABLE: binding value IS a constant.
	 * A value is considered constant if it starts with a quote but does not
	 * start with {@code "~} (quoted OGNL expression).
	 */
	private boolean evaluateUnsettableOrUngettable(Map<String, String> bindings) {
		String value = bindings.get(_bindingName);
		return value != null && value.startsWith("\"") && !value.startsWith("\"~");
	}
}

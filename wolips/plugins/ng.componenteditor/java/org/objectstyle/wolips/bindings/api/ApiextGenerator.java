package org.objectstyle.wolips.bindings.api;

import java.util.ArrayList;
import java.util.List;

import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableType;

/**
 * Adds bindings found by reflecting on a component's class to its {@code .apiext} ("Generate API").
 * Only bindings the file doesn't have are added; what's there, including anything written by hand,
 * is left as it is. A new binding gets its type in the directions the component's class supports:
 * pull for a key the parent can set into it, push as well when the component writes it back.
 */
public final class ApiextGenerator {

	/**
	 * A key found on the component's class.
	 *
	 * @param name the binding name
	 * @param type the key's type, fully qualified (or a primitive), or null when unknown
	 * @param pushed whether the component writes the value back to its parent (it synchronizes
	 *        its variables with its bindings)
	 */
	public record FoundBinding(String name, String type, boolean pushed) {
	}

	private ApiextGenerator() {
	}

	/**
	 * Adds the found bindings the model doesn't have, in the order found.
	 *
	 * @return the names of the bindings added
	 */
	public static List<String> addMissing(MutableApiextModel model, List<FoundBinding> found) {
		final List<String> added = new ArrayList<>();
		for (final FoundBinding binding : found) {
			if (has(model, binding.name())) {
				continue;
			}
			final MutableBinding mutable = new MutableBinding();
			mutable.name = binding.name();
			if (binding.type() != null && !binding.type().isEmpty()) {
				mutable.pull.add(new MutableType(binding.type(), null));
				if (binding.pushed()) {
					mutable.push.add(new MutableType(binding.type(), null));
				}
			}
			model.bindings.add(mutable);
			added.add(binding.name());
		}
		if (!added.isEmpty()) {
			model.markAsDirty();
		}
		return added;
	}

	private static boolean has(MutableApiextModel model, String name) {
		for (final MutableBinding binding : model.bindings) {
			if (name.equals(binding.name)) {
				return true;
			}
		}
		return false;
	}
}

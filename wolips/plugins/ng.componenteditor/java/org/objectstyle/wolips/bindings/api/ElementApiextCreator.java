package org.objectstyle.wolips.bindings.api;

import java.util.List;

import org.eclipse.core.resources.IFile;

/**
 * Writes an element's first {@code .apiext} when there's no template to generate it from (a
 * dynamic element class): from its hand-written {@code .api} when it has one, else empty. Saving
 * an {@code .apiext} rewrites the {@code .api} beside it ({@link ApiCompanion}), so starting from
 * nothing would erase what the {@code .api} says. When a rule there has no {@code .apiext} form,
 * nothing is written ({@link ApiToApiext}).
 */
public final class ElementApiextCreator {

	private ElementApiextCreator() {
	}

	/**
	 * @param apiextFile the {@code .apiext} to write; must not exist
	 * @param apiFile the element's {@code .api}, or null when it has none
	 * @return the descriptions of the {@code .api}'s rules that have no {@code .apiext} form, in
	 *         which case nothing was written; empty when the file was written
	 */
	public static List<String> create(IFile apiextFile, IFile apiFile) throws ApiModelException {
		final MutableApiextModel model = new MutableApiextModel(apiextFile);
		if (apiFile != null && apiFile.exists() && apiFile.getLocation() != null) {
			final List<String> untranslatable = ApiToApiext.seed(model, ApiParser.parseFile(apiFile.getLocation().toFile()), List.of());
			if (!untranslatable.isEmpty()) {
				return untranslatable;
			}
		}
		model.saveChanges();
		return List.of();
	}

	/** The sentence telling the developer why the {@code .apiext} wasn't written. */
	public static String refusal(IFile apiextFile, IFile apiFile, List<String> untranslatable) {
		return apiFile.getName() + " has rules with no .apiext form, which would be lost: \"" + String.join("\", \"", untranslatable)
				+ "\". Write " + apiextFile.getName() + " by hand, carrying them over as you see fit.";
	}
}

/**
 * Editor-facing wrappers over the parsed model, plus hyperlink navigation.
 *
 * <p>{@code DocumentWodModel} / {@code DocumentWodBinding} present the parsed
 * state through editor-oriented APIs, and the hyperlink providers
 * ({@code WodBindingNameHyperlink}, {@code WodElementTypeHyperlink},
 * {@code WodBindingValueHyperlink}) implement go-to-declaration from an editor
 * selection. The adaptation layer between the engine's parsed state and the WOD
 * editor UI. See {@link org.objectstyle.wolips.wodclipse} for the layer map.
 */
package org.objectstyle.wolips.wodclipse.core.document;

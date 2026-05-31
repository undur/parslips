/**
 * Template mutations — the engine's refactoring frontend.
 *
 * <p>The largest subpackage, but conceptually peripheral: it sits on top of the
 * cache ({@code core.completion}) and the bridges ({@code core.util}) and performs
 * coordinated multi-file edits. Operations include rename binding / rename
 * component, change element type, extract component/wrapper, and the format
 * conversions between inline ({@code <wo:…>}) and WOD ({@code Foo { … }}) styles.
 * Most operations share helpers (e.g. {@code TemplateRefactoring},
 * {@code WodDocumentUtils}) rather than re-touching the parser directly. Wired
 * into Eclipse's LTK refactoring participants. See
 * {@link org.objectstyle.wolips.wodclipse} for the layer map.
 */
package org.objectstyle.wolips.wodclipse.core.refactoring;

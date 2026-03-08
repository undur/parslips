package org.objectstyle.wolips.locate.result;

/**
 * Describes the template format for an element.
 *
 * <ul>
 *   <li>{@link #BUNDLE} — traditional {@code .wo} folder containing
 *       {@code .html}, {@code .wod}, and optionally {@code .woo} files.
 *       Bindings are expressed via {@code <webobject name="X">} references
 *       into the WOD file.</li>
 *   <li>{@link #STANDALONE} — single {@code .html} file with inline bindings
 *       ({@code <wo:Type binding="value">}). The ng-objects default.</li>
 *   <li>{@link #NONE} — no template. The element is a Java class (e.g. a
 *       {@code WODynamicElement} subclass) with an optional {@code .api}
 *       file but no HTML template.</li>
 * </ul>
 */
public enum TemplateFormat {
	BUNDLE,
	STANDALONE,
	NONE
}

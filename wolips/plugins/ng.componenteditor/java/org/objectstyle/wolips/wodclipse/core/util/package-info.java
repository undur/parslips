/**
 * Bridges and helpers — the WOD ↔ HTML glue of the engine.
 *
 * <p>Small in count but central in importance. {@code WodHtmlUtils} is the
 * authority on "what is this, in a template?" — distinguishing {@code <wo:…>}
 * tags, inline {@code value="$keyPath"} bindings, {@code <webobject>} references,
 * and {@code <p:…>} parser directives, and extracting binding values/namespaces.
 * {@code FuzzyXMLWodElement} adapts the parsed HTML DOM into the
 * {@code bindings.wod} model's {@code IWodElement} interface, so HTML inline tags
 * and {@code .wod} entries can be validated/refactored uniformly. Also
 * {@code WodModelUtils}, {@code WodDocumentUtils}, {@code CursorPositionSupport}.
 * See {@link org.objectstyle.wolips.wodclipse} for the layer map.
 */
package org.objectstyle.wolips.wodclipse.core.util;

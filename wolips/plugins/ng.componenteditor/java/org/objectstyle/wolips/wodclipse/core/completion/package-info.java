/**
 * The engine's hub: parsed-state cache + validation.
 *
 * <p>{@code WodParserCache} is the central class of the whole template-binding
 * engine (and its largest public surface — most external code that touches the
 * engine goes through it). It holds the parsed state of a component bundle as
 * three linked cache entries — {@code WodCacheEntry} ({@code .wod}),
 * {@code HtmlCacheEntry} ({@code .html} template), {@code WooCacheEntry}
 * ({@code .woo}) — and drives validation: {@code TemplateValidator} walks the HTML
 * DOM, finds inline bindings and {@code <wo:…>} tags, and checks them against the
 * {@code bindings.wod} model, producing problems/markers.
 *
 * <p>Despite living under "completion", this is the parse+cache+validate core, not
 * only content-assist. See {@link org.objectstyle.wolips.wodclipse} for the layer
 * map.
 */
package org.objectstyle.wolips.wodclipse.core.completion;

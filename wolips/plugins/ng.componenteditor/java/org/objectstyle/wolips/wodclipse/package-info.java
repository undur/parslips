/**
 * The <b>template-binding engine</b> — parsing, caching, validation and
 * refactoring for WebObjects/ng component templates.
 *
 * <h2>What this tree is</h2>
 *
 * Despite the legacy name ("wodclipse", inherited from the old WOLips "WODClipse"
 * plugin), this is <em>not</em> only about {@code .wod} files. It is the engine
 * that handles a component's template as a whole: the {@code .wod} bindings file,
 * the {@code .html} template (including inline {@code <wo:…>} tags and
 * {@code value="$keyPath"} attribute bindings), and the {@code .woo} sidecar — as
 * one unified, round-trip-editable system with validation and refactoring.
 *
 * <p>The name is kept for now because there is no clearly-better one: the obvious
 * candidates ({@code template.*}, {@code editor.*}) collide with packages that
 * already mean something else (see the layer map below). "wodclipse" is at least
 * distinctive and unambiguous. If a genuinely clearer name emerges, the rename is
 * mechanical — but it should clarify, not just re-spell.
 *
 * <h2>Where it sits — the three layers</h2>
 *
 * <pre>
 *   org.objectstyle.wolips.bindings.*    MODEL   — what a binding/element *is*
 *                                                  (IWodElement, IWodBinding,
 *                                                  IWodModel, validation rules)
 *
 *   org.objectstyle.wolips.wodclipse.*   ENGINE  — parse/cache/validate/refactor
 *                                                  templates against that model
 *                                                  (this tree)
 *
 *   org.objectstyle.wolips.editor.*      UI      — the editors that drive the
 *                                                  engine (editor.wod, editor.template,
 *                                                  editor.component, …)
 * </pre>
 *
 * The separation is clean: the model doesn't know about parsing; this engine
 * doesn't duplicate the model; the editors consume the engine. {@code wodclipse}
 * sits in the middle and is the reason {@code template.*}/{@code editor.*} would be
 * misleading names for it — it is neither the model nor the UI.
 *
 * <h2>The pipeline (the real structure)</h2>
 *
 * The subpackages form a horizontal pipeline, not a "core + peripheral" split.
 * Note that {@code core} in the package path is an inherited namespace level
 * (it once meant "non-UI"); it carries no architectural meaning today — the whole
 * tree is non-UI infrastructure.
 *
 * <ul>
 *   <li>{@code core.parser} — lexer/tokenizer for WOD syntax (the {@code WodScanner}
 *       and its rules); feeds editor syntax colouring.</li>
 *   <li>{@code core.completion} — the hub. {@code WodParserCache} holds the parsed
 *       state of the whole bundle (WOD + HTML + WOO cache entries) and runs
 *       validation. This is the engine's main public surface (by far the
 *       most-imported class).</li>
 *   <li>{@code core.util} — bridges, most importantly {@code WodHtmlUtils} (decides
 *       what's a {@code <wo:…>} tag, an inline binding, a {@code <webobject>}, a
 *       {@code <p:…>} directive) and {@code FuzzyXMLWodElement} (adapts the HTML DOM
 *       to the {@code bindings.wod} model interfaces).</li>
 *   <li>{@code core.document} — editor-facing wrappers over the parsed model, plus
 *       hyperlink (go-to-declaration) providers.</li>
 *   <li>{@code core.refactoring} — mutations: rename binding/component, change
 *       element type, convert inline↔WOD, extract component. Coordinates the
 *       multi-file (HTML + WOD) edits.</li>
 *   <li>{@code core.woo}, {@code core.quickfix}, {@code core.search},
 *       {@code core.builder}, {@code core.preferences}, {@code action} — supporting
 *       features and Eclipse-platform integration on top of the above.</li>
 * </ul>
 */
package org.objectstyle.wolips.wodclipse;

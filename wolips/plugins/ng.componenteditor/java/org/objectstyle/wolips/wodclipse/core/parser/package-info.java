/**
 * WOD-syntax lexer/tokenizer — the first stage of the template-binding engine.
 *
 * <p>{@code WodScanner} and its rule classes tokenize {@code .wod} source
 * (element names/types, binding names, operators, string literals, comments,
 * OGNL) into coloured/positioned tokens. Highly cohesive — single concern (WOD
 * lexing). Consumed mainly by the WOD editor's syntax colouring. See
 * {@link org.objectstyle.wolips.wodclipse} for how this fits the pipeline.
 */
package org.objectstyle.wolips.wodclipse.core.parser;

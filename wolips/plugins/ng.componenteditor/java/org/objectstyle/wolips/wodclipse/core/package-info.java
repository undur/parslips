/**
 * Namespace root for the template-binding engine's subpackages.
 *
 * <p>The {@code core} level is an inherited namespace (it historically meant
 * "non-UI") and carries no architectural meaning — the entire {@code wodclipse}
 * tree is non-UI infrastructure. The real structure is the pipeline across the
 * {@code core.*} subpackages (parser → completion → util/document → refactoring);
 * see {@link org.objectstyle.wolips.wodclipse} for the full picture.
 */
package org.objectstyle.wolips.wodclipse.core;

/**
 * Parslips Template Tooling — IDE-agnostic template editing intelligence.
 *
 * <p>This package is the foundation for template-aware editing features
 * (completion, validation, navigation, hover) without any dependency on
 * Eclipse, LSP, or any other IDE framework. All IDE integrations (the Eclipse
 * component editor, the LSP server, future IntelliJ support) consume this
 * library.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Zero IDE dependencies.</b> This bundle depends only on standard Java
 *       and (eventually) ng-template-parser for its AST model.</li>
 *   <li><b>Project context abstraction.</b> IDE-specific type resolution
 *       (classpath scanning, source analysis) is provided through a
 *       TemplateProjectContext interface that each consumer implements.</li>
 *   <li><b>Stateless where possible.</b> Editing operations take an AST +
 *       context and return results, rather than maintaining mutable global
 *       caches.</li>
 * </ul>
 *
 * <h2>Planned Contents</h2>
 * <ul>
 *   <li>TemplateProjectContext — interface for type/element resolution</li>
 *   <li>Completion engine — tag and attribute completion proposals</li>
 *   <li>Validation engine — binding validation, element existence checks</li>
 *   <li>Hover provider — documentation lookups for elements and bindings</li>
 *   <li>Navigation provider — definition lookups</li>
 * </ul>
 */
package parslips.tooling;

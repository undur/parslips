# JetBrains web-types

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Tooling only

**Artifact / file shape:** A single hand-authored or generated JSON file (conventionally `web-types.json`) shipped inside an npm package and pointed to by the package's `package.json` "web-types" field (a path or array of paths); third parties can also publish descriptors for libraries they don't own under the `@web-types/*` npm scope. The file has a flat top-level identity block (name, version, framework, js-types-syntax, description-markup, default-icon, framework-config) plus a single `contributions` object. `contributions` branches into three namespaces (html, css, js); each namespace maps symbol-kind names (elements, attributes, events, properties, classes, parts, …) to arrays of contribution objects, and every contribution may recursively nest further sub-contributions either in its own namespace (e.g. an element's `attributes`) or in another via nested `html`/`css`/`js` objects.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `$schema` | Points the JSON file at the web-types JSON Schema so editors validate the document and offer completion while authoring it. | string (URL) | — | IDE | `https://json.schemastore.org/web-types` |
| `name` | Identity of the library this descriptor documents. Also the implicit default 'module' name used when a source/type reference omits its module. | string | ✔ | IDE | `"vuetify"` |
| `version` | Version of the documented library. Used with since/deprecated-since fields to show version-gated availability of symbols. | string (semver) | ✔ | IDE | `"3.5.1"` |
| `framework` | The framework these contributions belong to (e.g. vue, angular). Gates activation and unlocks framework-specific symbol kinds (vue-components, ng-custom-events). | string | — | IDE | `"vue"` |
| `js-types-syntax` | Declares the syntax used in every 'type' field across the document so the IDE parses type strings correctly. Currently only TypeScript is defined. | enum: "typescript" | — | IDE | `"typescript"` |
| `description-markup` | The markup language used in all description and description-sections values so the IDE renders doc popups correctly. Defaults to none (plain text). | enum: "html" \| "markdown" \| "none" (default "none") | — | IDE | `"markdown"` |
| `default-icon` | Fallback icon shown in completion/structure UI for any contribution that does not specify its own 'icon'. Path or inline SVG. | string (relative path or raw SVG) | — | IDE | `"icons/component.svg"` |
| `framework-config` | Configures framework templating: enablement gating plus name-conversion rules (canonical-names, match-names, name-variants) relating e.g. camelCase props to kebab-case attributes. | object { enable-when, disable-when, canonical-names, match-names, name-variants } | — | IDE |  |
| `contexts-config` | Configuration for Web Types contexts — proximity-based activation of contribution sets depending on project/file context. | object | — | IDE |  |
| `required-context` | Top-level gate: contributions only activate when the given context (e.g. a node-package present) is satisfied. Supports boolean composition. Replaces deprecated 'context'. | required-context object: { kind, name } \| { anyOf:[...] } \| { allOf:[...] } \| { not:{...} } | — | IDE | `{ "kind": "package-name", "name": "vuetify" }` |
| `context` | DEPRECATED predecessor of required-context. Same shape; retained for backward compatibility. | required-context object | — | IDE |  |
| `contributions` | Root of the symbol tree. Object keyed by the three namespaces (html, css, js); each namespace keyed by symbol-kind names whose values are arrays of contribution objects. | object { html?, css?, js? } | — | IDE | `{ "html": { "elements": [...] } }` |
| `framework-config.canonical-names / match-names / name-variants` | Name-conversion rule sets bridging naming conventions: canonical form for comparison, the set of forms that should match a symbol, and the variants offered in completion (prop maxLength <-> attribute max-length). | name-conversion-rules objects | — | IDE |  |
| `framework-config.enable-when / disable-when` | Context gating for the whole library: enable when node-packages/ruby-gems/file-extensions/file-name-patterns/ide-libraries/project-tool-executables present; disable on certain extensions/patterns. Each matched rule yields a proximity score for ranking. | enablement-rules / disablement-rules objects | — | IDE | `{ "node-packages": ["vue"], "file-extensions": [".vue"] }` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `contributions.html` | HTML-namespace symbols. Direct kinds: elements, attributes, and (deprecated) events. Framework kinds also live here (vue-components, vue-directives, vue-file-top-elements). | object { elements?:[], attributes?:[], events?:[] (deprecated), <framework-kinds>:[] } | — | IDE |  |
| `contributions.css` | CSS-namespace symbols. Kinds: properties (custom properties), pseudo-elements, pseudo-classes, functions, classes, parts. | object { properties?:[], pseudo-elements?:[], pseudo-classes?:[], functions?:[], classes?:[], parts?:[] } | — | IDE |  |
| `contributions.js` | JS-namespace symbols. Kinds: events (non-deprecated home for events), properties (object/DOM properties), symbols (resolvable JS symbols). Framework kinds like ng-custom-events live here. | object { events?:[], properties?:[], symbols?:[] } | — | IDE |  |
| `pattern.template / pattern.or` | The structured pattern body: an ordered array mixing literal strings and references to other symbol lists. 'or' provides alternative templates. Names assembled compositionally (prefix + referenced event names). | name-pattern-template (array of strings / nested patterns / references) | cond. | IDE | `["v-on:", { "$ref": "/js/events" }]` |
| `pattern.items` | References a list of contributions whose names fill the pattern slot — e.g. expand a pattern over every event in /js/events. | list-reference: reference \| array<reference> | — | IDE | `{ "path": "/js/events" }` |
| `pattern.delegate` | Delegates resolution of the matched sub-name to another contribution, so the matched portion is looked up against a different symbol set. | reference | — | IDE |  |
| `pattern.regex / case-sensitive` | Regex matching variant: 'regex' is the pattern, 'case-sensitive' (default true) toggles case folding. Used for free-form dynamic names. | { regex: string (required), case-sensitive: bool (default true) } | cond. | IDE | `{ "regex": "--v-.*", "case-sensitive": false }` |
| `pattern.required / unique / repeat` | Pattern-part modifiers: whether the part must match, must be unique among siblings, and whether it may repeat — for multi-modifier syntaxes like '.stop.prevent'. | boolean each | — | IDE | `repeat: true` |
| `type-reference (name / module)` | References a TypeScript symbol to import as a type: 'name' is the exported symbol, 'module' defaults to the library name. Lets 'type' fields point at real declared types. | { name: string (required), module?: string } | cond. | IDE | `{ "name": "ButtonProps", "module": "vuetify" }` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The symbol identifier — the actual tag/attribute/prop name matched against source code (unless 'pattern' overrides matching). Base field on every contribution. | string | cond. | IDE | `"v-btn"` |
| `description` | Short documentation text shown in the completion popup and quick-doc. Interpreted per description-markup. | string | — | IDE | `"Primary action button."` |
| `description-sections` | Named extra documentation sections appended to the quick-doc popup. Object keys are section titles, values are the (markup) body. | object<string,string> | — | IDE | `{ "Since": "Available in v3" }` |
| `doc-url` | External link surfaced in the doc popup pointing to the symbol's online documentation. | string (URL) | — | IDE | `"https://vuetifyjs.com/components/buttons"` |
| `icon` | Per-symbol icon (path or inline SVG) overriding default-icon in completion/structure views. | string (relative path or raw SVG) | — | IDE |  |
| `source` | Links the documented symbol back to its real definition for go-to-declaration: either file+offset, or exported module+symbol. Decouples descriptor from code while keeping navigation. | oneOf { file:string, offset:int } \| { module?:string, symbol:string } | — | IDE | `{ "module": "vuetify", "symbol": "VBtn" }` |
| `since` | Library version in which this symbol first became available; IDE can warn if used against an older installed version. | string | — | IDE | `"3.2.0"` |
| `deprecated` | Marks the symbol deprecated. Boolean true, or a string carrying the deprecation message/replacement guidance shown in popup and as strike-through in completion. | boolean \| string (default false) | — | IDE | `"Use v-btn instead"` |
| `deprecated-since` | Version in which the symbol was deprecated; complements 'deprecated'. | string | — | IDE | `"3.4.0"` |
| `obsolete` | Stronger than deprecated — symbol is removed/non-functional. Boolean or message string. | boolean \| string (default false) | — | IDE |  |
| `obsolete-since` | Version in which the symbol became obsolete. | string | — | IDE |  |
| `experimental` | Marks the symbol as experimental/unstable; surfaced as a warning in docs/completion. Boolean or message. | boolean \| string (default false) | — | IDE | `true` |
| `priority` | Coarse precedence bucket controlling both completion ordering AND resolution: higher-priority symbols rank above and win over lower-priority ones on name collision. | enum: "lowest" \| "low" \| "normal" \| "high" \| "highest" | — | IDE | `"high"` |
| `proximity` | Fine-grained tiebreaker within a priority bucket — an integer nudging relative ordering/relevance (lower = closer/more relevant). Lets generated libraries fine-tune ranking without new priority levels. | integer | — | IDE | `1` |
| `virtual` | Marks a synthetic contribution that exists in tooling but is erased/absent at framework runtime (framework sugar). Lets the IDE model things the real DOM never sees. | boolean | — | IDE | `true` |
| `abstract` | Declares a contribution never matched directly — it exists only to be extended via 'extends'. Enables a reusable base/mixin without polluting completion. | boolean | — | IDE | `true` |
| `extends` | Inherits fields (attributes, type, docs) from another contribution referenced by name/path. Core reuse mechanism — a component can extend an abstract base or another element. | reference (string path) \| list-reference | — | IDE | `"/html/elements/BaseButton"` |
| `extension` | Marks this contribution as ADDING to an existing same-named contribution (this or another library) rather than redefining it — additive merge, not replacement. | boolean | — | IDE | `true` |
| `pattern` | Replaces literal-name matching with a computed pattern: regex, or a 'template' of literal parts + references to other symbol lists ('items'), with optional 'delegate'. Powers dynamic names like v-on:<event>. | name-pattern-root: { template, or, items, delegate, regex, case-sensitive, required, unique, repeat, priority, proximity, deprecated } | — | IDE | `{ "regex": "v-[a-z-]+" }` |
| `attributes` | Sub-contributions: the attributes/props accepted by this element. Array of attribute contribution objects. | array<attribute-contribution> | — | IDE |  |
| `events` | DEPRECATED on html elements — events emitted by the element. Modern usage nests these under the element's 'js' object as /js/events. | array<event-contribution> | — | IDE |  |
| `exclusive-contributions` | Declares THIS symbol the final/closed host for the listed qualified kinds — e.g. an element rejecting all standard HTML attributes lists ['/html/attributes'] so the IDE stops merging inherited globals. | array<string> (namespaced kind paths) | — | IDE | `["/html/attributes"]` |
| `html / css / js` | Nested namespace objects letting any contribution carry sub-contributions in a DIFFERENT namespace — e.g. an html element contributing its /js/events and /css/parts inline. | object (same shape as top-level html/css/js) | — | IDE | `{ "js": { "events": [...] } }` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `slots` | Named content-projection slots the element exposes (Vue/web-component slots). Each slot is a contribution (name, description, scoped-slot 'vue-properties' payload). Surfaced via framework/Vue kinds rather than a generic base field. | array<slot-contribution> | — | IDE | `[{ "name": "prepend", "description": "Leading content" }]` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `value` | Describes the attribute's accepted value: whether it takes a value at all (kind), its type, requiredness, default. Models valueless boolean attrs, plain-string attrs, and expression-bound attrs distinctly. | html-attribute-value: { kind, type, required, default } | — | IDE | `{ "kind": "expression", "type": "boolean" }` |
| `value.kind` | Whether/how the attribute carries a value: no-value (boolean presence attr), plain (literal string), or expression (framework-evaluated binding). | enum: "no-value" \| "plain" \| "expression" | — | IDE | `"expression"` |
| `value.type` | The type of the attribute's value, referencing the type system named by js-types-syntax (a string type, or a type-reference into a module). | html-value-type: string \| type-reference \| array | — | IDE | `"string \| number"` |
| `value.required` | Whether a value must be supplied for the attribute. | boolean | — | IDE | `true` |
| `value.default` | Default value applied when the attribute is present without an explicit value (documentation/completion hint). | string | — | IDE | `"true"` |
| `default` | Default value of the attribute/prop itself (generic-contribution level), shown in docs. | string | — | IDE | `"medium"` |
| `required` | Whether the attribute/prop must be present on the element. | boolean | — | IDE | `true` |
| `type` | Type of the symbol's value (generic/typed contribution). One type or a union list, parsed per js-types-syntax. Drives type-aware completion and binding validation. | type-list: string \| type-reference \| array<string\|type-reference> | — | IDE | `["string", "number"]` |
| `attribute-value` | Generic-contribution variant of 'value' — configures HTML attribute value semantics when the contribution is used in an HTML attribute position. | html-attribute-value object | — | IDE |  |
| `vue-argument` | DEPRECATED Vue-specific: describes the directive argument (part after ':' in v-on:click). Superseded by pattern-based /html/argument. | object | — | IDE |  |
| `vue-modifiers` | DEPRECATED Vue-specific: the directive modifiers (the '.stop'/'.prevent' suffixes). Superseded by /html/modifiers. | array | — | IDE |  |
| `read-only` | For a JS 'properties' contribution, marks the property read-only (no assignment completion). | boolean | — | IDE | `true` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `events (name + base fields)` | An emitted event. Lives at /js/events (preferred) or deprecated /html/events. Built on generic-js-contribution, carrying name/description/doc-url/deprecated plus 'type' (the event payload type). | generic-js-contribution { name, type, description, ... } | — | IDE | `{ "name": "click", "type": "MouseEvent" }` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `js/symbols.kind` | For a JS 'symbols' contribution, the kind of JS entity it represents — drives completion icon and semantics. | enum: Variable \| Function \| Namespace \| Class \| Interface \| Enum \| Alias \| Module (default Variable) | — | IDE | `"Function"` |

### `css` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `css/properties.values` | For a CSS custom-property contribution, the enumerated set of accepted values offered in completion. | array<string> | — | IDE | `["flex", "grid"]` |
| `css kinds: classes/parts/pseudo-elements/pseudo-classes/functions` | Each is an array of contributions (base fields) describing a CSS class, ::part(), pseudo-element, pseudo-class, or function the library exposes — giving CSS completion inside selectors/declarations. | array<base-contribution> | — | IDE |  |

## Multi-library / composition

Designed multi-library from the ground up. The IDE discovers many web-types files at once — bundled inside dependencies (each package's package.json "web-types" path), centrally published under the @web-types npm scope for libraries that don't ship their own, and contributed by IDE plugins via the polySymbols.webTypes extension point. All discovered contributions are merged into one symbol model keyed by namespace + kind + name. Conflicts are resolved structurally rather than by file order: (1) `priority` (lowest..highest) is the primary precedence axis for both completion ranking and resolution — a higher-priority symbol wins a name collision; (2) `proximity` (integer) is the within-priority tiebreaker; (3) `required-context`/`framework-config.enable-when` gate whole libraries by project context, and each context match produces a proximity score (0.0 = perfect file-extension/name match) so the closest-matching library's contributions dominate; (4) `extension:true` makes a contribution ADD to an existing same-named one (additive merge) instead of replacing it, and `extends` pulls in fields from a referenced (often `abstract`) base; (5) `exclusive-contributions` lets a symbol declare itself the closed/final host for a kind, stopping inherited globals (e.g. standard HTML attributes) from being merged in. There is no single "last-wins" rule — composition is explicit via these fields.

## What a greenfield ng/WO format should take from this

Several mechanisms here are genuinely worth adopting for a new element descriptor. (1) The three-axis precedence model — coarse `priority` enum + integer `proximity` tiebreaker + context-`proximity` scoring — is a clean, declarative answer to "two libraries define the same tag," far better than file-order or silent override; a tag-library format should steal this. (2) `extension:true` vs full redefinition, plus `abstract`+`extends`, gives real composition: you can ship a base element and let downstream libraries augment it additively without forking — directly relevant to layering Wonder/ng/app-specific tag sets. (3) `exclusive-contributions` is a sharp idea: a symbol can declare it does NOT accept the usual inherited attributes, which is exactly the kind of "this element is closed" statement WO/ng elements often need. (4) The `source` split (file+offset OR module+symbol) cleanly decouples the descriptor from the implementation while preserving go-to-declaration — better than embedding code or hard-coupling to a class. (5) Rich lifecycle metadata (`since`, `deprecated`/`deprecated-since`, `obsolete`, `experimental` as boolean-or-message) is low-cost, high-value documentation that a binding-validation tool can surface. (6) `description-markup` declared once at the top is a tidy way to avoid per-field markup ambiguity. What to avoid: the `pattern` machinery (template/items/delegate/regex) is powerful but complex and Vue-driven — only adopt if dynamic/compositional names are truly needed. And the schema's accreted Vue baggage (vue-argument, vue-modifiers, html/events) is a cautionary tale — keep framework-specific kinds out of the core and behind a `framework` discriminator from day one.

## Documented pitfall / regret

The format openly carries the scars of being retrofitted from a Vue-only format into a framework-agnostic one. The IntelliJ docs explicitly acknowledge that it "was originally created to facilitate the contribution of statically defined symbols for the Vue framework, which may explain the presence of some deprecated properties in the schema." Concrete fallout: `events` under `/html` is deprecated in favor of `/js/events`; `vue-argument` and `vue-modifiers` are deprecated in favor of generic pattern-based `/html/argument` and `/html/modifiers`; and the top-level `context` field was renamed to `required-context`. The namespace model is also still admittedly incomplete — the docs note contributions are limited to html/css/js namespaces and "in the future this limitation will be lifted." The lesson for a greenfield format: pick the general model first and discriminate framework-specifics behind an explicit `framework` flag, rather than baking one framework's concepts into core fields and having to deprecate them later.

## Primary sources

- <https://raw.githubusercontent.com/JetBrains/web-types/master/schema/web-types.json>
- <https://plugins.jetbrains.com/docs/intellij/polysymbols-web-types.html>
- <https://github.com/JetBrains/web-types>

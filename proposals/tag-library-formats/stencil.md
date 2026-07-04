# Stencil JsonDocs (docs-json output target)

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Tooling only

**Artifact / file shape:** A single generated `docs.json` file emitted by the Stencil compiler's `docs-json` output target (configured in `stencil.config.ts` or via `stencil build --docs-json path`). It is build output, never hand-authored — the source of truth is TSX component source plus JSDoc comments and CSS annotations, which the compiler distills into JSON. The top-level shape is `JsonDocs` { timestamp, compiler, components[], typeLibrary }, where `components` is a flat array of `JsonDocsComponent` descriptors, each carrying nested arrays of props/methods/events/listeners/styles/slots/parts/customStates plus a precomputed dependency graph. It is consumed downstream by documentation site generators, design-system catalogs, and editor tooling — explicitly a documentation/tooling artifact, not loaded by the runtime."

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `timestamp` | ISO timestamp recording when the docs JSON was generated. Build provenance/freshness marker for the whole document. | string (ISO datetime) | ✔ | IDE | `"2024-01-15T12:00:00Z"` |
| `compiler` | Provenance block identifying the toolchain that produced the doc: nested object with name, version, typescriptVersion. Lets consumers gate on generator version. | object { name: string; version: string; typescriptVersion: string } | ✔ | IDE | `{ name: "@stencil/core", version: "4.x", typescriptVersion: "5.x" }` |
| `components` | The actual payload: a flat array of every documented component descriptor in the project. No nesting/namespacing — one flat list keyed by tag. | JsonDocsComponent[] | ✔ | both | `[ { tag: "my-button", ... } ]` |
| `typeLibrary` | A side-table of TypeScript type definitions referenced by component public APIs (Prop/Event/Watch types), populated only when supplementalPublicTypes is configured. Decouples rich type info from each member so members reference into a shared library instead of inlining full type graphs. | JsonDocsTypeLibrary = Record<string, ComponentCompilerReferencedType> | — | IDE | `{ "MyEnum": { ... } }` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `tag` | The custom-element HTML tag name the component registers as. Primary identity/key for the component. | string (custom element name) | ✔ | both | `"my-button"` |
| `encapsulation` | Style/DOM encapsulation strategy for the element — drives whether shadow DOM, scoped class rewriting, or nothing is applied. Critical because it determines whether slots/parts/css-vars even apply. | 'shadow' \| 'scoped' \| 'none' | ✔ | both | `"shadow"` |
| `readme` | Full user-authored markdown from the component's readme.md (hand-written prose, not generated). Long-form docs kept separate from the per-member docs. | string (markdown) | ✔ | IDE | `"# My Button\nA button..."` |
| `docs` | The description text from the JSDoc block immediately above the component class declaration. The short component-level summary. | string | ✔ | both | `"A reusable button component"` |
| `overview` | The class-level JSDoc text for the component, if present — distinct from `docs`. A second descriptive slot. | string | — | IDE | `"Overview paragraph"` |
| `docsTags` | Open-ended array of arbitrary JSDoc tags on the component (name + optional text). The extensibility escape hatch: any unknown @tag the author writes is captured here rather than dropped. | JsonDocsTag[] (each { name: string; text?: string }) | ✔ | both | `[ { name: "category", text: "forms" } ]` |
| `deprecation` | Component-level deprecation message — the text following an @deprecated tag. Presence signals the whole element is deprecated; text is the migration/reason note. | string | — | both | `"Use my-button-v2 instead"` |
| `usage` | Map of usage-example filename → file contents, harvested from the component's usage/ directory. Lets multiple named example snippets travel with the descriptor. | JsonDocsUsage = Record<string, string> | ✔ | IDE | `{ "basic.md": "<my-button>..." }` |
| `props` | Array of property/attribute descriptors (from @Prop). The core of the public configurable API. | JsonDocsProp[] | ✔ | both | `[ { name: "disabled", ... } ]` |
| `methods` | Array of imperative public method descriptors (from @Method). Documents the JS-callable API surface beyond attributes. | JsonDocsMethod[] | ✔ | both | `[ { name: "focusInput", ... } ]` |
| `events` | Array of custom-event descriptors (from @Event). First-class declaration of the events a component emits. | JsonDocsEvent[] | ✔ | both | `[ { event: "myChange", ... } ]` |
| `listeners` | Array of event-listener descriptors (from @Listen) — events the component subscribes to. Documents inbound event wiring, not just outbound. | JsonDocsListener[] | ✔ | both | `[ { event: "click", capture: false, passive: false } ]` |
| `styles` | Array of CSS-styling descriptors — primarily documented CSS custom properties (CSS variables) parsed from JSDoc-style annotations in the stylesheet. | JsonDocsStyle[] | ✔ | IDE | `[ { name: "--color", docs: "text color", annotation: "@prop", mode: undefined } ]` |
| `slots` | Array of named-slot descriptors (from @slot tags). Documents the named content-projection points of the element. | JsonDocsSlot[] | ✔ | IDE | `[ { name: "", docs: "default content" } ]` |
| `parts` | Array of CSS Shadow Parts descriptors (from @part tags) — the ::part() exposed styling hooks. Documents the externally-stylable internals of a shadow-DOM component. | JsonDocsPart[] | ✔ | IDE | `[ { name: "label", docs: "the button label" } ]` |
| `customStates` | Array of custom element states declared via @AttachInternals states — exposed as :state() pseudo-class selectors. Documents custom CSS state hooks. | JsonDocsCustomState[] | ✔ | both | `[ { name: "loading", initialValue: false, docs: "..." } ]` |
| `dependents` | Flat list of tag names of components that USE this component (reverse edges). Precomputed graph data for impact analysis. | string[] (tag names) | ✔ | IDE | `[ "my-form" ]` |
| `dependencies` | Flat list of tag names of components this component USES (forward edges). Precomputed for bundling/tree-shaking and docs cross-linking. | string[] (tag names) | ✔ | both | `[ "my-icon" ]` |
| `dependencyGraph` | Full transitive coupling tree as an adjacency map: tagName → array of tags it depends on. The complete dependency graph, not just immediate edges. | JsonDocsDependencyGraph = Record<string, string[]> | ✔ | IDE | `{ "my-button": ["my-icon"], "my-icon": [] }` |
| `dirPath / fileName / filePath / readmePath / usagesDir` | Source-location provenance fields: directory, filename, full path, readme path, usage dir of the component on disk. Let tools jump-to-source and regenerate docs. | string (each optional) | — | IDE | `filePath: "src/components/my-button/my-button.tsx"` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The JavaScript property name of the prop (the DOM/JSX property identity). | string | ✔ | both | `"disabled"` |
| `attr` | The HTML attribute name that configures this prop, if the prop is attribute-backed. SEPARATES the JS property name from the HTML attribute name — a key dual-naming concept. | string | — | both | `"disabled" (prop maxValue → attr "max-value")` |
| `type` | The prop's type rendered as a TypeScript-type string (the simplified/display form, as opposed to JS or HTML notions of type). | string (TS type expression) | ✔ | both | `"boolean"` |
| `complexType` | Structured resolved type info (original text, resolved type, and references into typeLibrary) for props whose type isn't primitive. Richer machine-readable companion to the `type` string. | ComponentCompilerPropertyComplexType (original/resolved/references) | — | both | `{ original: "Mode", resolved: "'ios'\|'md'", references: {...} }` |
| `mutable` | Whether the prop was declared mutable (component may reassign its own prop). Encodes write-direction policy of the binding. | boolean | ✔ | both | `false` |
| `reflectToAttr` | Whether the prop reflects its value back to the HTML attribute (keeps attribute in sync with property). Declares two-way prop↔attribute reflection. | boolean | ✔ | both | `true` |
| `optional` | Whether the prop's TS type was declared with `?` (type-level optionality). Distinct from `required`. | boolean | ✔ | both | `true` |
| `required` | Whether the prop was declared with `!` (definite-assignment / must-be-provided). Note: Stencil distinguishes `optional` and `required` as two independent booleans rather than one tri-state. | boolean | ✔ | both | `false` |
| `default` | The default value of the prop, captured as a source string. Documents fallback when attribute/prop unset. | string | — | both | `"false"` |
| `values` | Enumeration of the constituent type members of the prop — each a { value?, type } pair. For union types this lists each allowed literal/type, giving IDEs the autocomplete value set. | JsonDocsValue[] (each { value?: string; type: string }) | ✔ | both | `[ { value: "ios", type: "string" }, { value: "md", type: "string" } ]` |
| `docs` | The JSDoc description text for the prop. Per-member documentation string. | string | ✔ | both | `"Disables the button"` |
| `docsTags` | Arbitrary JSDoc tags attached to this prop. Per-member extensibility (same open-tag pattern as component level). | JsonDocsTag[] | ✔ | both | `[ { name: "since", text: "2.0" } ]` |
| `deprecation` | Per-prop deprecation text (follows @deprecated). Lets individual attributes be deprecated independently of the element. | string | — | both | `"use `variant` instead"` |
| `getter / setter` | Whether the prop is backed by a get()/set() accessor pair. Documents computed/intercepted props vs plain fields. | boolean (each) | ✔ | both | `getter: true, setter: false` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `event` | The event name string emitted (the type passed to addEventListener). Identity of the custom event. | string | ✔ | both | `"myChange"` |
| `bubbles` | Whether the emitted CustomEvent bubbles up the DOM tree. Directly mirrors the DOM Event init dictionary. | boolean | ✔ | both | `true` |
| `cancelable` | Whether the event is cancelable via preventDefault(). DOM-faithful event semantics in the descriptor. | boolean | ✔ | both | `true` |
| `composed` | Whether the event crosses shadow-DOM boundaries (composed flag). Captures shadow-DOM event propagation policy explicitly. | boolean | ✔ | both | `true` |
| `detail` | The TypeScript type (as a string) of the event's `detail` payload. Documents the shape of data the event carries. | string (TS type) | ✔ | both | `"{ value: string }"` |
| `complexType` | Structured resolved type info for the event detail payload, with references into typeLibrary. Machine-readable companion to `detail`. | ComponentCompilerEventComplexType | ✔ | both | `{ resolved: "...", references: {...} }` |
| `docs / docsTags / deprecation` | Per-event description, arbitrary tags, and deprecation text — same documentation triad as props. | string / JsonDocsTag[] / string | cond. | both | `docs: "Fired on value change"` |
| `event (listener)` | On JsonDocsListener: the event name the component listens for. | string | ✔ | both | `"resize"` |
| `target (listener)` | The listen target scope: e.g. 'window','document','body','parent'. Declares WHERE the listener attaches, not just what event. | string ('window'\|'document'\|'body'\|'parent' etc.) | — | both | `"window"` |
| `capture / passive (listener)` | Standard addEventListener options surfaced declaratively: capture phase and passive flag. | boolean (each) | ✔ | both | `capture: false, passive: true` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The public method name. | string | ✔ | both | `"open"` |
| `signature` | The full method signature rendered as a string, including parameters and return type. Human- and tool-readable call shape in one field. | string | ✔ | both | `"open(animated?: boolean) => Promise<void>"` |
| `returns` | Structured return descriptor: the return type plus its JSDoc text. Separates return type from prose explanation. | JsonDocsMethodReturn ({ type: string; docs: string }) | ✔ | both | `{ type: "Promise<void>", docs: "resolves when open" }` |
| `parameters` | Array of parameter descriptors for the method (name, type, docs per param). Documents each argument individually. | JsonDocMethodParameter[] | ✔ | both | `[ { name: "animated", type: "boolean", docs: "..." } ]` |
| `complexType` | Structured resolved type info for the method signature (parameters/return/references into typeLibrary). | ComponentCompilerMethodComplexType | ✔ | both | `{ signature: "...", references: {...}, return: "..." }` |
| `docs / docsTags / deprecation` | Per-method description, arbitrary tags, deprecation text. | string / JsonDocsTag[] / string | cond. | both | `docs: "Opens the overlay"` |

### `css` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name (style)` | On JsonDocsStyle: the name of the styling entity — typically the CSS custom property name (e.g. --background). | string | ✔ | IDE | `"--background"` |
| `docs (style)` | The description/type text associated with the CSS variable. | string | ✔ | IDE | `"Background color of the button"` |
| `annotation (style)` | Which JSDoc annotation produced this style entry (e.g. "@prop"). Records the source tag so different style-doc conventions can coexist. | string | ✔ | IDE | `"@prop"` |
| `mode (style)` | The theming 'mode' the style belongs to (Ionic-style ios/md multi-mode theming). Allows the same CSS-var name to be documented per design mode. | string \| undefined | — | IDE | `"ios"` |
| `name (part)` | On JsonDocsPart: the CSS Shadow Part name exposed via part= and targetable by ::part(). | string | ✔ | IDE | `"label"` |
| `docs (part)` | Textual description of the shadow part. | string | ✔ | IDE | `"The text label inside the button"` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name (slot)` | The slot name; empty string denotes the default unnamed slot. Identity of a content-projection point. | string | ✔ | IDE | `"start"` |
| `docs (slot)` | Textual description of what content goes in the slot. | string | ✔ | IDE | `"Content placed before the label"` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name/initialValue/docs (customState)` | JsonDocsCustomState: a custom element state's name (dashless), its boolean initial value, and description. Documents :state() CSS hooks. | { name: string; initialValue: boolean; docs: string } | ✔ | both | `{ name: "loading", initialValue: false, docs: "..." }` |
| `JsonDocsTag.name / text` | The generic open-tag pair used everywhere docsTags appears: a tag name and optional free text. The universal extensibility primitive of the whole format. | { name: string; text?: string } | cond. | both | `{ name: "internal" }` |
| `JsonDocsValue.value / type` | One member of a union/enum prop type: an optional literal value and its type kind. Building block of the prop `values` array. | { value?: string; type: string } | cond. | both | `{ value: "md", type: "string" }` |

## Multi-library / composition

There is no namespacing or multi-library merge mechanism inside a single docs.json — `components` is one flat array scoped to the project being compiled, and component identity is just the bare `tag` string (custom-element tag names are globally unique by the HTML spec, so collisions are assumed impossible rather than resolved). Cross-component relationships within the project are captured structurally via the precomputed `dependents`, `dependencies`, and full transitive `dependencyGraph` (adjacency map tag→tags), but these reference components by bare tag name only and stop at the project boundary — a component consuming another library's elements would list those tags with no resolution back to a foreign descriptor. Combining multiple Stencil libraries' docs is left entirely to downstream consumers (merge multiple docs.json files yourself); the format provides no library id, per-component version field, or conflict-resolution rule. Per-member type sharing IS solved, but only within one document, via the document-level `typeLibrary` table that members reference by key.

## What a greenfield ng/WO format should take from this

Several mechanisms are worth stealing for a greenfield element-descriptor format. (1) The strict JS-property-name vs HTML-attribute-name separation (`name` + `attr`, plus `reflectToAttr` for sync direction and `mutable` for write policy) is a clean, explicit model of binding direction that beats conflating the template attribute with the underlying value identity. (2) First-class, separately-typed member arrays — `props`, `events`, `listeners`, `methods`, `slots`, `parts`, `styles`, `customStates` — recognize that an element exposes distinct *kinds* of surface (inbound config, outbound events, imperative API, content-projection points, styling hooks) rather than one undifferentiated bag of bindings; a Parsley tag library should similarly model slots and styling hooks as their own categories, not fold everything into attributes. (3) The universal `docsTags`/`JsonDocsTag` open `{name,text?}` extension primitive at every level is an excellent forward-compatibility move: unknown annotations are preserved verbatim instead of dropped, so the format never has to gatekeep vocabulary. (4) Precomputed `dependencyGraph`/`dependents` ship reverse and transitive coupling data so tools needn't re-derive it — valuable for impact analysis and dead-element detection. (5) Carrying a `default`, an enumerated `values` set (union members), and a structured `complexType`/`typeLibrary` reference gives editors real autocomplete/validation data. Things it does poorly and a new format should avoid: it is purely generated documentation with no runtime authority, so the descriptor can silently diverge from reality (Stencil itself notes it does NOT verify documented slots actually exist); the `optional`/`required` pair as two independent booleans derived from TS `?`/`!` is leaky and confusing; theming via a stringly-typed `mode` field is Ionic-specific bleed-through that doesn't generalize; and source-path fields baked into the artifact couple it to one machine's layout.

## Documented pitfall / regret

Documented as an explicit caveat in the official docs: Stencil does NOT verify that the slots, parts, or CSS custom properties you document actually exist in the component's JSX/stylesheet — `@slot`/`@part`/`@prop` annotations are taken at face value, so the descriptor can claim a styling hook or named slot that the implementation never renders. Relatedly, the `usage` examples are static snapshots that require manual updates when a component's API changes and will silently go stale. This stems from the format being generated-but-unvalidated documentation rather than an enforced contract — a design point a new format should fix by validating descriptor claims against the implementation.

## Primary sources

- <https://stenciljs.com/docs/docs-json>
- <https://github.com/ionic-team/stencil/blob/main/src/declarations/stencil-public-docs.ts>
- <https://raw.githubusercontent.com/ionic-team/stencil/main/src/declarations/stencil-public-docs.ts>

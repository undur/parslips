# Custom Elements Manifest (custom-elements.json)

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Tooling only

**Artifact / file shape:** A single JSON file, conventionally named custom-elements.json, generated (not hand-authored) from component source by an analyzer such as @custom-elements-manifest/analyzer (`cem analyze`) or framework tools (Lit, Stencil, FAST). Published in the npm package and discovered via the `customElements` field in package.json. Top-level shape is a Package object with `schemaVersion`, optional `readme`, and a `modules` array; each module is a JavaScriptModule holding `declarations` and `exports`. The tree is a graph of declarations cross-linked by Reference objects (name + package + module).

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `schemaVersion` | The version of the CEM schema this file conforms to. Versioned with semver independently of any package; current is 2.1.0. Lets consumers branch on schema shape. | string (required) | ✔ | IDE | `"2.1.0"` |
| `readme` | Markdown for the package readme, embeddable by doc viewers/catalogs without a second fetch. | string | — | IDE | `"# my-button"` |
| `modules` | The list of JavaScript modules that make up the package. Primary container; every declaration and export lives under a module. | Array<Module> (Module = JavaScriptModule) (required) | ✔ | IDE | `[{ "kind": "javascript-module", "path": "button.js" }]` |
| `deprecated` | Whether the whole package is deprecated; a string carries the reason. This boolean\|string pattern repeats on nearly every node. | boolean \| string | — | IDE | `"use @scope/new-pkg instead"` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `kind` | Discriminant on JavaScriptModule, fixed value 'javascript-module'. Future-proofs for other module kinds. | 'javascript-module' (required) | ✔ | IDE | `"javascript-module"` |
| `path` | Path to the JS file to import to use the module's exports. The import specifier an IDE uses for auto-import. | string (required) | ✔ | IDE | `"my-element.js"` |
| `summary` | Markdown summary 'suitable for display in a listing' — explicitly the short form, distinct from description. Present on module, declaration, member, attribute, event, slot, css part/prop/state. | string | — | IDE | `"A pressable button."` |
| `description` | Fuller markdown description for contexts needing more than a listing line. The summary/description split is a deliberate two-tier doc model. | string | — | IDE | `"Renders a button that..."` |
| `declarations` | Declarations made in a module (classes, functions, variables, mixins, custom elements). Declaration is decoupled from export. | Array<Declaration> | — | IDE | `[{ "kind": "class", "name": "MyButton", "customElement": true }]` |
| `exports` | Module exports: JavaScriptExport (a JS symbol) or CustomElementExport (a tag-name registration). Separating declaration from export lets one class be exported under multiple names/tag names. | Array<Export> | — | IDE | `[{ "kind": "custom-element-definition", "name": "my-button" }]` |
| `customElement` | On a declaration, literal `true` flag distinguishing a custom-element class from a plain JS class. Discriminator for the CustomElement member set. | true (required on CustomElement) | ✔ | IDE | `true` |
| `tagName` | On a CustomElement declaration, the tag name IF the element self-registers (e.g. a decorator calling define()). Optional because registration may instead be recorded via a CustomElementExport. | string | — | IDE | `"my-button"` |
| `attributes` | HTML attributes the element observes. The core IDE surface for markup autocomplete and validation. | Attribute[] | — | IDE | `[{ "name": "disabled" }]` |
| `members` | Class members: fields and methods (ClassField \| ClassMethod, plus CustomElementField). Captures the JS property API alongside the HTML attribute API. | Array<ClassMember> | — | IDE | `[{ "kind": "field", "name": "value", "attribute": "value" }]` |
| `events` | Custom events the element fires. Each carries the event object Type, enabling hover docs and typed listener generation. | Event[] | — | IDE | `[{ "name": "change", "type": {"text":"CustomEvent"} }]` |
| `slots` | Named (or default, empty-string) <slot>s the element exposes for light-DOM projection. Drives slot-name completion. | Slot[] | — | IDE | `[{ "name": "", "description": "default content" }]` |
| `demos` | Live demos/examples. Each Demo has url (relative if published with package, absolute if hosted), optional description and source. Doc-portal oriented. | Demo[] | — | IDE | `[{ "url": "demo/index.html" }]` |
| `superclass` | Reference to the parent class. Enables inheritance-aware tooling (resolving inherited members, mixin chains) and cross-package base classes (global: HTMLElement). | Reference | — | IDE | `{ "name": "LitElement", "package": "lit" }` |
| `mixins` | References to mixins applied to the class. First-class modeling of the JS mixin pattern so tooling can flatten contributed members. | Array<Reference> | — | IDE | `[{ "name": "Focusable", "module": "mixins.js" }]` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `kind (Export)` | Discriminant on Export: 'js' for a plain JS export, 'custom-element-definition' for a customElements.define() tag registration. The latter records 'tag X resolves to class Y'. | 'js' \| 'custom-element-definition' (required) | ✔ | IDE | `"custom-element-definition"` |
| `name (CustomElementExport)` | For a custom-element-definition export, the registered tag name (NOT the class name). The authoritative tag-name-to-class binding the IDE uses to map markup to a component. | string (required) | ✔ | IDE | `"my-button"` |
| `declaration (Export)` | A Reference from the export name/tag to the Declaration that implements it. Decouples public name from implementing class; allows cross-module/cross-package targets. | Reference (required) | ✔ | IDE | `{ "name": "MyButton", "module": "my-button.js" }` |
| `name (Reference)` | The referenced symbol's name. Reference is the universal cross-link primitive (used by superclass, mixins, inheritedFrom, exports.declaration, type.references). | string (required) | ✔ | IDE | `"HTMLElement"` |
| `package (Reference)` | The npm package the referenced symbol lives in. If undefined, the reference is local to THIS package. The literal 'global:' marks platform globals (Array, HTMLElement, Event). This three-state resolution is the entire cross-package linking mechanism. | string ('global:' \| npm name \| undefined) | — | IDE | `"lit" or "global:"` |
| `module (Reference)` | The module within the package where the symbol is declared. If undefined, the reference is local to the containing module. With package+name, fully addresses any declaration. | string | — | IDE | `"lit-element.js"` |
| `source / href (SourceReference)` | On declarations/members/types: a SourceReference whose single required field `href` is an ABSOLUTE URL (e.g. a GitHub permalink) to the source. Enables 'go to source'. Absoluteness is deliberate so the link survives outside the package. | SourceReference { href: string (required) } | — | IDE | `{ "href": "https://github.com/org/repo/blob/x/button.js#L10" }` |
| `text (Type)` | Full string representation of a type (e.g. a TS type expression). The type system is string-first: canonical form is human text, with optional structured references layered on. | string (required) | ✔ | IDE | `"'primary' \| 'secondary'"` |
| `references (Type)` | Array of TypeReference linking substrings of the type text to declarations. Lets tooling make type names in the string clickable/resolvable. | TypeReference[] | — | IDE | `[{ "name": "Color", "start": 0, "end": 5 }]` |
| `start / end (TypeReference)` | Integer indices into the Type.text string marking the span the reference covers. Enables precise inline linking within a stringified type. | number | — | IDE | `0 / 5` |
| `url (Demo)` | Required URL of a demo: relative if published with the package, absolute if externally hosted. | string (required) | ✔ | IDE | `"demo/basic.html"` |

### `css` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `cssParts` | ::part() shadow-DOM parts the element exposes for external styling. Named styling extension point. | CssPart[] | — | IDE | `[{ "name": "label" }]` |
| `cssProperties` | CSS custom properties (--vars) the element honors as a theming contract, with optional registered syntax and default. | CssCustomProperty[] | — | IDE | `[{ "name": "--btn-bg", "syntax": "<color>", "default": "blue" }]` |
| `cssStates` | CSS custom states (CustomStateSet, matched via :state()) the element can set. Newest CSS extension point (added in 2.1). | CssCustomState[] | — | IDE | `[{ "name": "checked" }]` |
| `name (CssCustomProperty)` | The custom property name INCLUDING the leading `--`. Contrast with cssStates, which omit the `--`. | string (required) | ✔ | IDE | `"--btn-radius"` |
| `syntax` | Expected CSS syntax of the property, a valid CSS Properties-and-Values-API syntax string; defaults to "*". Enables typed CSS-var completion/validation. | string (default "*") | — | IDE | `"<length>"` |
| `default (CssCustomProperty)` | Default value of the CSS custom property, shown in theming docs/completion. | string | — | IDE | `"4px"` |
| `name (CssCustomState)` | The custom state name WITHOUT a leading `--` (unlike custom properties). Maps to CustomStateSet / :state(). | string (required) | ✔ | IDE | `"loading"` |
| `name (CssPart)` | The ::part() name exposed for external styling. | string (required) | ✔ | IDE | `"thumb"` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name (Attribute)` | The attribute's HTML name as written in markup. | string (required) | ✔ | IDE | `"aria-label"` |
| `type (Attribute)` | The Type to/from which the attribute string is (de)serialized — its conceptual data type. Drives value validation and completion. | Type | — | IDE | `{ "text": "'sm' \| 'lg'" }` |
| `default (Attribute)` | Default value of the attribute (as a string). Shown in docs/completion detail. | string | — | IDE | `"md"` |
| `fieldName` | On Attribute: the class field this attribute maps to. The link from HTML attribute surface to JS property surface — inverse of CustomElementField.attribute. | string | — | IDE | `"isDisabled"` |
| `inheritedFrom` | Reference to the superclass/mixin a member/attribute/event was inherited from. Shows provenance and avoids duplicating inherited APIs as locally declared. | Reference | — | IDE | `{ "name": "LitElement", "package": "lit" }` |
| `attribute (CustomElementField)` | On a class field: the corresponding observed-attribute name, if the property is attribute-backed. Forward link from JS property to HTML attribute. | string | — | both | `"disabled"` |
| `reflects` | On a CustomElementField: whether the property reflects its value back to the attribute. Captures the property/attribute reflection contract. | boolean | — | IDE | `true` |
| `optional (Parameter)` | Whether a function parameter is optional; undefined implies non-optional. | boolean | — | IDE | `true` |
| `rest (Parameter)` | Whether the parameter is a rest (...) parameter; only the last may be. Undefined implies single parameter. | boolean | — | IDE | `true` |
| `readonly (PropertyLike)` | Whether a property/variable is read-only. Informs whether tooling offers it as a settable binding target. | boolean | — | IDE | `true` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `kind (ClassMember)` | Discriminant on a member: 'field' (ClassField) or 'method' (ClassMethod). Unifies properties and methods under one members array while keeping them typed. | 'field' \| 'method' (required) | ✔ | IDE | `"method"` |
| `static` | On a field/method: whether it is static (class-level rather than instance-level). | boolean | — | IDE | `false` |
| `privacy` | Visibility of the member: public \| private \| protected. Lets tooling hide internals from completion while still documenting them. | Privacy ('public'\|'private'\|'protected') | — | IDE | `"private"` |
| `parameters` | On FunctionLike (methods/functions/mixins): ordered list of Parameter objects describing the call signature. | Parameter[] | — | IDE | `[{ "name": "opts", "optional": true }]` |
| `return` | On FunctionLike: an inline object { type?, summary?, description? } describing the return value. Note it is anonymous, not a named type — a minor inconsistency vs other nodes. | { type?: Type; summary?: string; description?: string } | — | IDE | `{ "type": { "text": "void" } }` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name (Event)` | The event's type/name as dispatched (the string passed to dispatchEvent's CustomEvent). | string (required) | ✔ | IDE | `"value-changed"` |
| `type (Event)` | REQUIRED Type of the event object (e.g. CustomEvent<Foo>). Unusually, Event.type is required while Attribute.type is optional — events are assumed to always carry a typed payload. | Type (required) | ✔ | IDE | `{ "text": "CustomEvent<string>" }` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name (Slot)` | The slot name, or the empty string for the default slot. Empty-string-as-default is an explicit convention. | string (required) | ✔ | IDE | `"header"` |

## Multi-library / composition

There is one manifest per npm package (located via the `customElements` field in that package's package.json). Cross-package composition is NOT done by merging files but via the Reference object's three-state resolution: `package` undefined = local to this package; `package: "global:"` = a platform global (HTMLElement, Event, Array); `package: "<npm-name>"` + optional `module` = a symbol in another package's manifest. So a subclass in package A can point its superclass/mixins/inheritedFrom at a base class in package B, and tooling resolves by loading B's manifest. There is no namespace/prefix mechanism for tag names and no conflict-resolution rule for two packages defining the same tag name — tag uniqueness is left to the global customElements registry at runtime and to the consuming tool. Aggregators (component catalogs, webcomponents.org) index many per-package manifests rather than concatenating them.

## What a greenfield ng/WO format should take from this

Several mechanisms are worth adopting wholesale. (1) The clean separation of DECLARATION from EXPORT/REGISTRATION (a class declared once, then bound to one or more tag names via CustomElementExport) is exactly the decoupling Parsley's tag-alias work is circling — a tag library should describe the element once and bind names separately, supporting multiple aliases without duplication. (2) The two-tier `summary` vs `description` doc model (short for listings, long for hover) is a small, high-value idea for the Element Reference. (3) Treating attributes, members, events, slots, cssParts, cssProperties, cssStates as SEPARATE typed contracts — not one undifferentiated 'binding' list — is more expressive than a flat .api bindings list; CSS custom-properties/parts/states in particular are extension points WO/.api has no vocabulary for. (4) The Reference primitive with package/module/global: three-state resolution is a genuinely good cross-library linking model worth emulating if Parsley grows tag libraries that extend each other. (5) `inheritedFrom` provenance plus explicit `mixins`/`superclass` let tooling flatten inherited bindings without re-declaring them — directly relevant to component inheritance. (6) `deprecated: boolean|string` on every node is a cheap, uniform deprecation channel worth copying verbatim. What it does POORLY and a new format should avoid: the type system is string-first (Type.text plus brittle start/end index spans into that string), which is fragile and TS-centric; prefer structured type references. It is also strictly tooling-only with no runtime contract, and generated-from-source rather than authored — the format optimizes for emission by analyzers, not human hand-editing, the opposite of what a hand-authored .papi tag library wants, where ergonomic authoring should be a first-class goal.

## Documented pitfall / regret

The format is explicitly tooling-only and assumed to be GENERATED by an analyzer, not hand-authored — authoring or correcting a manifest by hand is awkward, and analyzer coverage gaps (mixins, re-exports, non-decorator definitions) produce incomplete manifests. The string-first type model (Type.text with separate start/end integer offsets for TypeReference) is fragile: references must stay index-synchronized with the text. Attribute-to-field linkage is expressible in two directions (Attribute.fieldName and CustomElementField.attribute) that can disagree. There is also no defined conflict/precedence rule when two packages register the same tag name.

## Primary sources

- <https://raw.githubusercontent.com/webcomponents/custom-elements-manifest/main/schema.d.ts>
- <https://raw.githubusercontent.com/webcomponents/custom-elements-manifest/main/schema.json>
- <https://raw.githubusercontent.com/webcomponents/custom-elements-manifest/main/README.md>
- <https://custom-elements-manifest.open-wc.org/>

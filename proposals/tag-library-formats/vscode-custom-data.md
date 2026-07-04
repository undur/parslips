# VS Code HTML custom data

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Tooling only

**Artifact / file shape:** A single hand-authored JSON file (conventionally `*.html-data.json`) conforming to customData.schema.json (version const 1.1). An extension registers one or more such files via `contributes.html.customData: ["./x.html-data.json"]` in its package.json; workspaces can also point at files via the `html.customData` setting. Top level is a flat object: `version`, plus optional arrays `tags`, `globalAttributes`, and `valueSets`. The file is data-only — there is no code, no runtime hook, no behavior; it exists purely to feed the HTML language service's completion, hover, and validation.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `version` | Schema version of the custom data document. A single const value; bumped <Major>.<Minor> where minor is backwards-compatible and major is breaking. Lets the language service refuse/adapt to documents it doesn't understand. | number, const 1.1 (required) | ✔ | IDE | `"version": 1.1` |
| `tags` | Top-level array of custom HTML element (tag) descriptors. This is the element library payload. | array of Tag objects | — | IDE | `"tags": [ { "name": "my-button" } ]` |
| `globalAttributes` | Attributes that apply to every tag, not just one element. Modeled identically to per-tag attributes (schema $ref to the attribute item type), so a global attribute and a tag-scoped attribute share one shape. This cleanly separates element-specific bindings from universal ones (think id/class/aria-*). | array of Attribute objects (same schema as tags[].attributes items) | — | IDE | `"globalAttributes": [ { "name": "data-track" } ]` |
| `valueSets` | Named, reusable enumerations of attribute values. An attribute references one by name (attribute.valueSet) instead of inlining its values, so a value list (e.g. boolean true/false, or a color palette) is defined once and shared across many attributes. A normalization/DRY mechanism for completion value lists. | array of ValueSet objects | — | IDE | `"valueSets": [ { "name": "b", "values": [ {"name":"true"}, {"name":"false"} ] } ]` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The tag/element name used for completion and matching in templates. | string (required) | ✔ | IDE | `"name": "my-card"` |
| `description` | Human-readable documentation shown in completion list and hover tooltip for the tag. Accepts either a plain string OR a structured MarkupDescription object, enabling rich markdown docs (code samples, links, emphasis) in the IDE. | string \| MarkupDescription { kind, value } | — | IDE | `{ "kind": "markdown", "value": "A **card** container." }` |
| `attributes` | The attributes (bindings/props) accepted by this specific tag. Each entry is an Attribute object. | array of Attribute objects | — | IDE | `"attributes": [ { "name": "variant" } ]` |
| `references` | External documentation links surfaced in hover for the tag. Array of {name,url}. Lets a descriptor point completion UI at spec/MDN/design-system docs rather than embedding everything in description. | array of Reference { name, url } | — | IDE | `[ { "name": "MDN", "url": "https://developer.mozilla.org/..." } ]` |
| `browsers` | Supported browsers for the feature (compat data). Used to annotate completion with browser-support hints. Primarily relevant for the standard MDN-sourced data; of marginal value for app-specific custom components. | array of strings (browser-id pattern, e.g. "C42", "FF40") | — | IDE | `[ "C42", "E12", "FF40" ]` |
| `baseline` | Web-platform Baseline support information for the feature (whether broadly available across browsers, with dates). Annotates completion/hover with availability status. Auto-generated from compat data; not meaningful for bespoke components. | Baseline object { status, baseline_low_date, baseline_high_date } | — | IDE | `{ "status": "high", "baseline_high_date": "2023-01-01" }` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The attribute (binding/prop) name used for completion and matching on the tag. | string (required) | ✔ | IDE | `"name": "variant"` |
| `description` | Documentation shown in completion/hover for the attribute. Plain string or MarkupDescription for markdown. | string \| MarkupDescription { kind, value } | — | IDE | `"Visual style of the element"` |
| `valueSet` | Name of a top-level valueSet whose values become this attribute's completion candidates. Indirection that shares one value enumeration across many attributes (e.g. the built-in "v" boolean set). The key reuse primitive of the format. | string (name of a valueSets[] entry) | — | IDE | `"valueSet": "b"` |
| `values` | Inline enumeration of allowed/suggested values for this attribute (alternative to valueSet indirection). Each entry is an AttributeValue object. | array of AttributeValue objects { name, description, references } | — | IDE | `[ { "name": "primary" }, { "name": "ghost" } ]` |
| `references` | External documentation links surfaced in hover for the attribute. Same {name,url} shape used everywhere. | array of Reference { name, url } | — | IDE | `[ { "name": "spec", "url": "https://..." } ]` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `AttributeValue.name` | A single allowed/suggested value of an attribute, used as a completion candidate. (Item shape of attribute.values and of valueSet.values — the two share one schema via $ref.) | string (required) | ✔ | IDE | `"name": "primary"` |
| `AttributeValue.description` | Documentation for a single attribute value, shown in completion/hover. String or MarkupDescription. | string \| MarkupDescription { kind, value } | — | IDE | `"The default emphasis style"` |
| `AttributeValue.references` | External documentation links for a single attribute value. {name,url}. | array of Reference { name, url } | — | IDE | `[ { "name": "MDN", "url": "https://..." } ]` |
| `ValueSet.name` | The identifier by which an attribute's valueSet field references this shared value enumeration. | string (required) | ✔ | IDE | `"name": "b"` |
| `ValueSet.values` | The list of values belonging to this named set. Reuses the AttributeValue schema (name/description/references) via $ref — one value type serves both inline and shared cases. | array of AttributeValue objects | — | IDE | `[ { "name": "true" }, { "name": "false" } ]` |
| `Reference.name` | Display label for an external documentation link. The shared Reference type appears on tags, attributes, and attribute values uniformly. | string (required) | ✔ | IDE | `"name": "MDN Reference"` |
| `Reference.url` | Target URL for the documentation link; schema-constrained to http(s). | string, pattern ^https?:// (required) | ✔ | IDE | `"url": "https://developer.mozilla.org/"` |
| `MarkupDescription.kind` | Discriminator for how the description value should be rendered in the IDE: as raw text or as markdown. | string enum: "plaintext" \| "markdown" (required) | ✔ | IDE | `"kind": "markdown"` |
| `MarkupDescription.value` | The actual description text (markdown source when kind=markdown). Shown in completion and hover. | string (required) | ✔ | IDE | `"value": "Renders a **primary** button."` |
| `Baseline.status` | Web-platform Baseline availability level for the feature. | string enum: "high" \| "low" \| false | — | IDE | `"status": "high"` |
| `Baseline.baseline_low_date` | Date the feature became newly (Baseline low) available across core browsers. | string, date YYYY-MM-DD | — | IDE | `"2022-03-14"` |
| `Baseline.baseline_high_date` | Date the feature became widely (Baseline high) available. | string, date YYYY-MM-DD | — | IDE | `"2024-09-30"` |

## Multi-library / composition

Multiple custom-data files are simply additive: an extension lists several paths in `contributes.html.customData`, many extensions can each contribute their own files, and the user's `html.customData` setting layers on more. The HTML language service loads them all into one merged provider set — there is no namespace, no per-library prefix, no precedence/priority field, and no documented conflict resolution. Two files defining the same tag or attribute name both contribute completion entries (effectively last-writer/merge behavior with no authoring control). The README explicitly notes there is no documentation of merge/conflict semantics. The only de-facto namespacing is the HTML convention of hyphenated custom-element names. Within a single file, `valueSets` provides intra-file reuse (an attribute's `valueSet` points at a named set) but this does not cross files in any guaranteed way. This is the format's biggest weakness as a library mechanism: it scales by accretion, not by composition.

## What a greenfield ng/WO format should take from this

Worth adopting: (1) The shared `references[] {name,url}` type attached uniformly to tags, attributes, AND values — a tiny, cheap field that lets a descriptor link out to canonical docs (spec/MDN/design-system) instead of cramming everything into description; a Parsley element/binding descriptor should carry this everywhere. (2) `description` as a union of plain string OR `MarkupDescription {kind, value}` with explicit markdown — markdown hover docs are now table stakes for good completion UX; the explicit `kind` discriminator is cleaner than guessing. (3) `valueSets` + attribute `valueSet` indirection: defining an enum of binding values once and referencing it by name is a genuinely good DRY primitive that a binding descriptor should steal (e.g. shared enumerations for component-name bindings, boolean-ish values, action lists). (4) `globalAttributes` modeled with the *same* schema as tag attributes (via $ref) — one attribute type, two scopes — is elegant; Parsley has an analogous need (universal bindings vs element-specific bindings). (5) Strict separation of tooling concern from runtime: this format proves a useful editor descriptor needs astonishingly little. Where it falls short for our goals: no events, no slots, no children/content-model, no value *types* (everything is a string completion candidate — no boolean/number/binding-expression typing), no required/optional flag on attributes, no default values, no element relationships, and critically no namespacing or library identity. A modern component descriptor for Parsley should keep the link/markdown/value-set ideas but add a real type system for binding values, slot/content modeling, and explicit library identity + versioning that this format deliberately omits.

## Documented pitfall / regret

No format-specific documented regret was found in the primary sources. The closest is an operational caveat in vscode-custom-data: generated data must be checked so new properties don't ship with blank descriptions (MDN gaps require manual supplementation via mdn-documentation.js), implying description quality is a recurring maintenance burden. The notable design *gap* (not a stated regret): the task brief expected `deprecated`/`deprecatedMessage` fields, but the current HTML customData.schema.json (version 1.1) defines NO `deprecated` or `deprecatedMessage` field anywhere — deprecation must be conveyed informally inside `description`. The CSS sibling schema instead expresses lifecycle via a `status` enum (\"standard\"|\"experimental\"|\"nonstandard\"|\"obsolete\") plus a `relevance` sort weight; the HTML schema lacks both, so HTML custom data has no first-class way to mark an element/attribute deprecated or to influence completion ordering.

## Primary sources

- <https://github.com/microsoft/vscode-html-languageservice/blob/main/docs/customData.schema.json>
- <https://raw.githubusercontent.com/microsoft/vscode-html-languageservice/main/docs/customData.schema.json>
- <https://code.visualstudio.com/api/extension-guides/custom-data-extension>
- <https://github.com/microsoft/vscode-custom-data>
- <https://raw.githubusercontent.com/microsoft/vscode-css-languageservice/main/docs/customData.schema.json>

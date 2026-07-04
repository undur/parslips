# Vue (vue-component-meta / defineProps)

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Tooling only

**Artifact / file shape:** There is no descriptor file. vue-component-meta is a static-extraction library: you point a ComponentMetaChecker (built via createChecker(tsconfigPath, options) or createCheckerByJson) at a .vue / .tsx / .jsx source file, and it returns an in-memory ComponentMeta object computed by the TypeScript type checker plus the Vue compiler. The 'descriptor' is therefore the component source itself (defineProps/defineEmits/defineSlots/defineExpose macros + JSDoc), and the ComponentMeta JSON tree is a derived projection of it. The library is part of vuejs/language-tools and powers Volar/IDE features and tools like Histoire/Storybook docgen.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `createChecker / createCheckerByJson` | Factory entry points that build a ComponentMetaChecker bound to a tsconfig (path or inline JSON) plus MetaCheckerOptions. The whole format is a STATIC EXTRACTION pipeline: there is no descriptor file on disk — the descriptor is derived on demand from the .vue/.tsx source by the TypeScript type checker. This is the 'code-is-the-descriptor' model. | createChecker(tsconfigPath: string, options?: MetaCheckerOptions); createCheckerByJson(rootPath, json, options?) | ✔ | IDE | `const checker = createChecker('/path/tsconfig.json', { schema: true })` |
| `ComponentMeta` | Top-level descriptor object for ONE component. Composed entirely of arrays of per-member descriptors plus identity fields. Note the flat, member-keyed-by-array shape: props/events/slots/exposed are sibling arrays, not one merged attribute bag. | interface { name?; description?; type: TypeMeta; props: PropertyMeta[]; events: EventMeta[]; slots: SlotMeta[]; exposed: ExposeMeta[] } | ✔ | IDE |  |
| `MetaCheckerOptions.schema` | Controls deep schema expansion. Either a boolean master switch or an object with an `ignore` list. Crucial because unbounded type expansion is expensive/recursive — this is the explicit escape valve. | MetaCheckerSchemaOptions = boolean \| { ignore?: (string \| ((name, type, typeChecker) => boolean\|void\|undefined\|null))[] } | — | IDE | `{ ignore: ['HTMLElement', n => n.startsWith('Internal')] }` |
| `MetaCheckerOptions.schema.ignore` | List of type names (or predicate functions) to NOT expand into schema. Predicates receive (name, type, typeChecker) so expansion can be cut dynamically — the documented guard against runaway/circular type recursion and huge built-in types. | (string \| function)[] | — | IDE | `['Date', 'HTMLElement']` |
| `MetaCheckerOptions.printer` | TypeScript ts.PrinterOptions controlling how types are rendered into the `type`/`signature` strings (e.g. removeComments, indentation). | ts.PrinterOptions | — | IDE | `{ removeComments: true }` |
| `MetaCheckerOptions.noDeclarations` | When true, skips computing Declaration source ranges — a performance toggle for callers that don't need go-to-definition. | boolean | — | IDE | `true` |
| `MetaCheckerOptions.forceUseTs` | Forces TypeScript-based analysis even for components that might otherwise be treated as plain JS — controls the analysis backend. | boolean | — | IDE | `true` |
| `MetaCheckerOptions.rawType` | When true, attaches the raw ts.Type objects (rawType field) to each member. Off by default because holding ts.Type objects is heavy. | boolean | — | IDE | `false` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `getComponentMeta(componentPath, exportName?)` | Returns the ComponentMeta for one component. The optional exportName selects which export of a multi-component file to describe — this is how a single source file carrying several components is disambiguated. | (componentPath: string, exportName?: string) => ComponentMeta | ✔ | IDE | `checker.getComponentMeta('/Foo.vue', 'default')` |
| `getExportNames(componentPath)` | Lists the named exports in a file so a caller can enumerate every component a single module exposes, then call getComponentMeta per export. This is the multi-library/multi-component fan-out mechanism. | (componentPath: string) => string[] | — | IDE | `checker.getExportNames('/components.ts')` |
| `PropertyMeta.getDeclarations()` | Lazy accessor that returns the Declaration[] on demand — the lazy counterpart to the eager declarations field, used to avoid computing source ranges unless needed. | () => Declaration[] | ✔ | IDE |  |
| `PropertyMeta.getTypeObject()` | Lazy accessor returning the underlying ts.Type. Lazy form of rawType so the expensive type object is only materialized on demand. | () => ts.Type | ✔ | IDE |  |
| `ExposeMeta.name` | Name of a publicly exposed instance member (method or property reachable via template ref). | string | ✔ | IDE | `"focus"` |
| `ExposeMeta.description` | JSDoc description of the exposed member. | string | ✔ | IDE | `"Programmatically focus the input"` |
| `ExposeMeta.type` | Printed type of the exposed member (function signature or property type). | string | ✔ | IDE | `"() => void"` |
| `ExposeMeta.tags / schema / declarations / rawType / getDeclarations() / getTypeObject()` | Identical descriptor shape to SlotMeta: JSDoc tags, deep schema, source declarations, raw type, and lazy accessors. Uniform member shape across slot/expose reduces consumer special-casing. | {name;text?}[]; PropertyMetaSchema; Declaration[]; ts.Type?; ()=>... | ✔ | IDE |  |
| `SFC: defineExpose` | Author-side macro mapping to ExposeMeta[]; only members passed here appear as exposed — explicit public-API opt-in. | defineExpose({ focus, value }) | cond. | both | `defineExpose({ focus: () => el.focus() })` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | Component name. Optional because it is best-effort inferred from the export/file, not authored — the format does not force a stable identifier. | string (optional) | — | IDE | `"MyButton"` |
| `description` | Component-level human description, sourced from the leading JSDoc block on the component definition. | string (optional) | — | IDE | `"A primary action button."` |
| `type` | Discriminates how the component is implemented so tooling can branch on it. An enum, not free text. | TypeMeta enum: Unknown=0, Class=1, Function=2 | ✔ | IDE | `TypeMeta.Function` |
| `props` | All declared props as an array of PropertyMeta. Derived from defineProps / props option / withDefaults. | PropertyMeta[] | ✔ | IDE |  |
| `events` | All emitted events as EventMeta[]. Derived from defineEmits. | EventMeta[] | ✔ | IDE |  |
| `slots` | All named/default slots as SlotMeta[], including each slot's typed payload. Derived from defineSlots or usage inference. | SlotMeta[] | ✔ | IDE |  |
| `exposed` | Public instance members reachable via template ref. Derived from defineExpose. A first-class, separate channel from props/events — the imperative public API is described distinctly from the declarative one. | ExposeMeta[] | ✔ | IDE |  |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `PropertyMeta.name` | Prop name as written in the template/attribute. | string | ✔ | IDE | `"variant"` |
| `PropertyMeta.description` | Description text extracted from the prop's JSDoc, excluding tags. The text-before-tags convention. | string | ✔ | IDE | `"Visual style of the button"` |
| `PropertyMeta.type` | Human-readable resolved TypeScript type rendered as a string (via the TS printer). This is the surface type for hovers/completion. | string | ✔ | IDE | `"'primary' \| 'secondary'"` |
| `PropertyMeta.default` | Default value as a source string. Populated from withDefaults / @default JSDoc tag. Optional because not every prop has one. | string (optional) | — | IDE | `"'primary'"` |
| `PropertyMeta.required` | Whether the prop must be supplied. A real boolean derived from type optionality, not a string flag. | boolean | ✔ | both | `true` |
| `PropertyMeta.global` | Marks props that come from the global/intrinsic attribute set (e.g. inherited HTML/global attributes) rather than the component's own declaration — lets tooling separate authored props from ambient ones. | boolean | ✔ | IDE | `false` |
| `PropertyMeta.tags` | All JSDoc tags on the prop, preserved as structured name/text pairs. This is the open extensibility hatch: arbitrary @custom annotations survive into the descriptor without the schema having to know them. | { name: string; text?: string }[] | ✔ | IDE | `[{ name: 'deprecated', text: 'use color' }]` |
| `PropertyMeta.schema` | Deep, recursively-expanded structural type for the prop (see PropertyMetaSchema). Lets tooling drive enum dropdowns, nested object editors, etc. from the type's internal shape, not just its printed string. | PropertyMetaSchema | ✔ | IDE | `{ kind: 'enum', type: "'a'\|'b'", schema: ['a','b'] }` |
| `PropertyMeta.declarations` | Source locations where the prop's type is declared, enabling go-to-definition. | Declaration[] | ✔ | IDE | `[{ file: '/Foo.vue', range: [120,134] }]` |
| `PropertyMeta.rawType` | The raw TypeScript ts.Type object, attached only when the rawType option is enabled. Lets advanced consumers do their own type analysis beyond the printed string/schema. | ts.Type (optional) | — | IDE |  |
| `SFC: defineProps / withDefaults` | Author-side macro. Each declared prop becomes a PropertyMeta; withDefaults / `= value` populates PropertyMeta.default; optionality (`?` or default) drives `required`. The descriptor is GENERATED from this — there is no separate prop manifest to keep in sync. | defineProps<{ x: T }>() or runtime object form | cond. | both | `const props = withDefaults(defineProps<{variant?: 'a'\|'b'}>(), { variant: 'a' })` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `EventMeta.name` | Event name as emitted/listened-to. | string | ✔ | IDE | `"update:modelValue"` |
| `EventMeta.description` | JSDoc description of the event. | string | ✔ | IDE | `"Fired when value changes"` |
| `EventMeta.type` | Printed type of the event payload tuple. | string | ✔ | IDE | `"[value: string]"` |
| `EventMeta.signature` | Full callable signature of the handler the event expects — distinct from `type`, which is just the argument payload. Gives tooling the exact listener shape. Events get a richer descriptor than props precisely because they are functions. | string | ✔ | IDE | `"(event: 'change', value: string): void"` |
| `EventMeta.tags` | JSDoc tags on the event declaration. | { name: string; text?: string }[] | ✔ | IDE |  |
| `EventMeta.schema` | Deep schema of EACH payload argument — an ARRAY of PropertyMetaSchema (one per tuple element), unlike props/slots/exposed which carry a single schema. Reflects that an event payload is positional/multi-arg. | PropertyMetaSchema[] | ✔ | IDE | `[ 'string', { kind:'object', ... } ]` |
| `EventMeta.declarations / rawType / getDeclarations() / getTypeObject()` | Same source-location + raw-type accessors as PropertyMeta. Note getTypeObject() here returns ts.Type \| undefined (an event may have no resolvable type object), whereas the prop/slot/expose variants are non-optional. | Declaration[]; ts.Type?; ()=>Declaration[]; ()=>ts.Type \| undefined | ✔ | IDE |  |
| `SFC: defineEmits` | Author-side macro mapping to EventMeta[]. The emit call signatures become EventMeta.signature/type/schema. | defineEmits<{ (e:'change', v:string): void }>() | cond. | both | `const emit = defineEmits<{ change: [value: string] }>()` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `SlotMeta.name` | Slot name; default slot is named 'default'. | string | ✔ | IDE | `"header"` |
| `SlotMeta.description` | JSDoc description of the slot. | string | ✔ | IDE | `"Card header area"` |
| `SlotMeta.type` | Printed type of the slot's scoped-props (the payload passed to slot content). Slots are typed as data contracts, not just named holes. | string | ✔ | IDE | `"{ item: Row; index: number }"` |
| `SlotMeta.tags` | JSDoc tags on the slot. | { name: string; text?: string }[] | ✔ | IDE |  |
| `SlotMeta.schema` | Deep expanded schema of the slot's scoped-prop object — drives nested completion for slot bindings. | PropertyMetaSchema | ✔ | IDE |  |
| `SlotMeta.declarations / rawType / getDeclarations() / getTypeObject()` | Same source-location + raw-type accessors as PropertyMeta. | Declaration[]; ts.Type?; ()=>Declaration[]; ()=>ts.Type | ✔ | IDE |  |
| `SFC: defineSlots` | Author-side macro mapping to SlotMeta[]; the slot fn's argument type becomes SlotMeta.type/schema (the scoped payload contract). | defineSlots<{ default(props:{ msg:string }): any }>() | cond. | both | `defineSlots<{ item(p:{ row: Row }): any }>()` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `PropertyMetaSchema` | The recursive type-expansion union — the format's centerpiece. A schema is EITHER a plain printed-type string OR a discriminated node with kind ∈ {enum, array, event, object}. enum/array/event carry a child schema array; object carries a string-keyed map of nested PropertyMeta (so object members recurse back into the full prop descriptor). This lets a single prop's type be walked as a tree for deep UI generation. | string \| { kind:'enum'\|'array'\|'event'; type:string; schema?: PropertyMetaSchema[] } \| { kind:'object'; type:string; schema?: Record<string, PropertyMeta> } | ✔ | IDE | `{ kind:'object', type:'User', schema:{ id:{...PropertyMeta} } }` |
| `Declaration` | A source-location record: which file and which character range a member is declared at. Backs go-to-definition. Decouples the descriptor from any one IDE by using plain file + offset range. | interface { file: string; range: [number, number] } | ✔ | IDE | `{ file:'/Foo.vue', range:[210, 240] }` |
| `SFC: JSDoc tags (@default, @example, @deprecated, custom)` | JSDoc on a definition feeds the descriptor: leading text → description; @default → default; all tags → tags[] (name+text). Documentation and metadata share one source — no second annotation language. | JSDoc comment block | — | IDE | `/** Visual style. @default 'primary' @deprecated use color */` |

## Multi-library / composition

No namespacing or conflict model — that is out of scope because the format is per-file extraction, not a registry. Multiple components are handled per FILE: getExportNames(path) enumerates a module's exports and getComponentMeta(path, exportName) extracts one named export, so a single source file can host many components disambiguated by export name. There is no notion of merging two libraries or resolving name collisions across packages; resolution is delegated entirely to TypeScript's module/type resolution against the supplied tsconfig. The `global` boolean on PropertyMeta is the one place where two provenance sources (the component's own props vs. ambient/global attributes) are distinguished within a single descriptor.

## What a greenfield ng/WO format should take from this

Several ideas are strong enough to adopt wholesale. (1) Code-as-descriptor with static extraction: the type-annotated source is the single source of truth and the machine-readable descriptor is GENERATED, eliminating the perennial drift between a hand-written .api/manifest and the actual element. A Parsley tag-library format should at minimum support deriving descriptors from the component's typed binding declarations rather than only hand-authoring them. (2) The PropertyMetaSchema recursive expansion (kind: enum|array|object|event with object recursing back into full member descriptors) is a genuinely good model: it lets tooling drive enum dropdowns and nested-object editors from the type tree, not just a flat type string — far richer than a single 'type' attribute. (3) Pairing an eager printed-type string with a deep schema AND a lazy getTypeObject()/declarations gives consumers a cheap default and an expensive opt-in — a tiered-detail design worth copying. (4) The MetaCheckerOptions.schema.ignore predicate is an honest admission that deep type expansion is unbounded/recursive and must be cut; any descriptor format with recursive type expansion needs an explicit ignore/depth guard from day one. (5) Open `tags: {name,text}[]` JSDoc passthrough is a clean extensibility hatch — arbitrary @custom annotations survive without schema changes; better than a closed attribute set. (6) Treating events (signature + per-arg schema ARRAY), slots (typed scoped payload), and exposed members (explicit defineExpose opt-in) as FIRST-CLASS, separately-typed channels — not all crammed into one attribute bag — is a cleaner element model than a single undifferentiated binding list. Weaknesses to avoid: it is tooling-only and TS-coupled (every field is computed via ts.Type, ts.PrinterOptions leaks into the API), so it is not a portable on-disk artifact and is unusable without a TypeScript program; a Parsley format should keep a serializable, engine-neutral form. The descriptor also has no stable identity/versioning (name is optional/inferred), no per-binding 'reusable'/visibility concept beyond global, and no cross-library namespacing — all things a real tag-library format must add.

## Documented pitfall / regret

No formal 'design regret' doc, but the schema option itself encodes a known pain: deep type expansion is expensive and can recurse on circular/large built-in types, which is why MetaCheckerOptions.schema accepts an `ignore` list (described in-source as 'A list of type names to be ignored in expending in schema. Can be functions to ignore types dynamically'). The presence of noDeclarations and rawType toggles likewise signals that computing source ranges and holding raw ts.Type objects are costly defaults that callers routinely need to disable. The hard coupling to the TypeScript type checker means the descriptor cannot be produced or consumed without a full TS program and matching tsconfig.

## Primary sources

- <https://github.com/vuejs/language-tools/tree/master/packages/component-meta>
- <https://raw.githubusercontent.com/vuejs/language-tools/master/packages/component-meta/lib/types.ts>
- <https://raw.githubusercontent.com/vuejs/language-tools/master/packages/component-meta/README.md>
- <https://www.npmjs.com/package/vue-component-meta>

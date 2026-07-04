# Svelte 5 (runes + svelte2tsx)

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Code *is* the descriptor (no separate file)

**Artifact / file shape:** Carrier is the .svelte single-file component: instance <script>, optional <script module>, template markup, <style>. Contract is hand-authored as runes/constructs; no separate descriptor the author edits. svelte2tsx transpiles .svelte to a virtual .tsx so TypeScript type-checks it. For published libraries an optional Foo.svelte.d.ts next to Foo.svelte projects the public typed contract. Code-first, with an optional declaration-file projection.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `.svelte (single-file component)` | Whole contract carrier: instance <script>, optional <script module>, markup, <style>. No separate descriptor file. | File with script/markup/style; props/events/content contract emerges from runes and template constructs inside it. | ✔ | both | `Button.svelte: <script> let { label } = $props(); </script> <button>{label}</button>` |
| `interface Props (TS prop contract)` | Idiomatic v5 way to give the whole prop set an explicit named checkable type; annotates the $props() target. | interface Props {...} then let {...}: Props = $props(). Also inline or JSDoc. | — | IDE | `interface Props { adjective: string } let { adjective }: Props = $props();` |
| `$$Props / $$Events / $$Slots (v4 legacy typing)` | Magic interface names the v4 language server recognized to type props/events/slots when inference fell short. Replaced by interface Props + Snippet typing in v5. | Local interface $$Props {}, interface $$Events {}, interface $$Slots {} in <script lang='ts'>. | — | IDE | `interface $$Props { propA: string } interface $$Events { click: MouseEvent }` |
| `Component<Props, Exports, Bindings> type` | Svelte 5 generic type for a component's full static contract: Props, module Exports, and which prop keys are Bindings (bindable). What tooling synthesizes per component. | Component<Props={}, Exports={}, Bindings extends keyof Props\|''=string>; function type (internals, props) => $set/$on & Exports. | — | both | `interface Component<Props={}, Exports={}, Bindings extends keyof Props\|''=string> { (this:void, internals, props:Props): {...} & Exports; element?: typeof HTMLElement }` |
| `ComponentProps<Comp> / ComponentEvents<Comp>` | Helper types extracting a component's prop type (and legacy event type) from the component itself; consumers derive the contract without restating it. | ComponentProps<typeof MyComp>. ComponentEvents obsolete under v5 Component model (events are props). | — | IDE | `type P = ComponentProps<typeof Button>;` |
| `Foo.svelte.d.ts (declaration file)` | Optional declaration file next to Foo.svelte exporting the public typed contract for library consumers (props/events/slots interfaces + default export). | export default class Foo extends SvelteComponentTyped<FooProps, FooEvents, FooSlots> {} with exported interfaces (v4 style; v5 leans on Component type). | — | IDE | `export interface FooProps { propA: string } export default class Foo extends SvelteComponentTyped<FooProps, FooEvents, FooSlots> {}` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `$props()` | Svelte 5 rune. Declares ALL inbound props via one destructuring assignment. Replaces v4 export let. | let { a, b, c } = $props(). Names map 1:1 to attributes. Readonly by default (mutation discouraged unless bindable). | ✔ | both | `let { adjective } = $props();` |
| `$props() default value` | Per-prop fallback when parent omits the attribute. Optionality is structural via JS default-assignment, not a flag. | let { x = <expr> } = $props(). Fallback values are not turned into reactive state proxies. | — | both | `let { adjective = 'happy' } = $props();` |
| `$props() renaming` | Maps an external attribute name (invalid identifier/keyword) to a local variable. | JS rename: let { super: trouper = '...' } = $props(). | — | both | `let { super: trouper = 'lights are gonna find me' } = $props();` |
| `rest/spread props (...others)` | Passthrough bucket: collects props not explicitly destructured, for forwarding onto a DOM element/child. | let { a, b, ...others } = $props(); spread via {...others}. | — | both | `let { a, b, c, ...others } = $props();` |
| `$bindable()` | Svelte 5 rune marking one prop two-way bindable. THE directionality control: pull-only until $bindable() makes it pull+push. | let { value = $bindable() } = $props() or $bindable('fallback'). Permits child to mutate state it doesn't own. | — | both | `let { value = $bindable() } = $props();` |
| `bind: directive (consumer side)` | Parent opt-in to two-way binding against a child's bindable prop. Parent may also pass a plain value; binding never forced. | <Child bind:value={parentState} />. Valid only if child declared the prop $bindable(). | — | both | `<FancyInput bind:value={message} />` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `$props.id()` | Unique hydration-stable component-instance id. Wires for/aria-labelledby without prop plumbing. | No args; returns string consistent across SSR hydration. | — | RT | `const uid = $props.id();` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `callback props (v5 event model)` | Events modeled as ordinary function-typed props, NOT a separate channel. Parent passes a function; child calls it. Replaces createEventDispatcher. | let { inflate } = $props(); invoked inflate(power). Typed like any prop. | — | both | `let { inflate } = $props(); // inflate(power)` |
| `onevent attribute syntax` | Handlers as plain attributes (colon of on:click dropped); handlers are just properties, unifying events with props. | onclick={fn}. Shorthand property syntax applies. | — | both | `<button onclick={() => count++}>clicks: {count}</button>` |
| `createEventDispatcher (v4 legacy, deprecated)` | Legacy separate event channel: child dispatches named CustomEvents; parent listens via on:name. Deprecated for callback props. | const dispatch = createEventDispatcher<{checked:boolean}>(); dispatch('inflate', power); parent on:inflate. | — | both | `const dispatch = createEventDispatcher(); dispatch('inflate', power);` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `{#snippet name(params)}...{/snippet}` | Svelte 5 reusable parameterized template fragment; templated-content primitive replacing named/scoped slots. Params carry scoped data. | {#snippet name(arg)}markup{/snippet}. Param defaults + destructuring, NOT rest params. References enclosing scope. | — | both | `{#snippet figure(image)}<figure><img src={image.src} alt={image.caption}/></figure>{/snippet}` |
| `{@render snippet(args)}` | Render tag instantiating a snippet; the only way to invoke one. Replaces <slot/>. Supports optional invocation. | {@render name(args)} or {@render children?.()}. | cond. | both | `{@render figure(image)}` |
| `children snippet (implicit default content)` | Default-content prop: markup inside a component tag without {#snippet} becomes a prop named children. Replaces default <slot/>. | children: Snippet; rendered via {@render children?.()}. | — | both | `let { children } = $props(); // <Box>hello</Box>` |
| `snippets as props (explicit + implicit)` | Snippets passed as named props inside the tag (implicit) or as ordinary prop values (explicit). First-class content props; no slot namespace. | Implicit: <Table>{#snippet row(d)}...{/snippet}</Table>. Explicit: <Table {header} {row} />. | — | both | `<Table data={fruits}>{#snippet row(d)}...{/snippet}</Table>` |
| `<slot>/<slot name>/let:/$$slots (v4 legacy)` | Legacy content channel: default/named slots, scoped data via slot props + let:, $$slots to test provided slots. Superseded by snippets. | <slot name='foo' message='hello'/>; <List let:item>; $$slots.foo boolean. | — | both | `<slot name='foo' message='hello' />` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `Snippet<Parameters> type` | TS type from 'svelte' typing a snippet prop incl. its parameter tuple; typed/parameterized content contracts checkable in the IDE. | Snippet (no params) or Snippet<[T]> / Snippet<[any]>. Generics give Snippet<[T]> consistent with a T[] data prop. | — | IDE | `import type { Snippet } from 'svelte'; interface Props { row: Snippet<[any]>; children: Snippet; }` |
| `$$props / $$restProps (legacy runtime)` | Legacy runtime escape hatches: $$props = all props; $$restProps = props not declared via export let (v4 passthrough). Superseded by $props() rest element. | Referenced directly in markup/script; not declared. | — | RT | `<div {...$$restProps}>` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `<script module>` | Module-scoped script (one per module, shared across instances). Carries component-level exports/constants/identity. Renamed from v4 <script context='module'>. | <script module> export const FOO = ...; </script>. Exports become module-level named exports. Module snippets exportable if referencing only module declarations. | — | both | `<script module> export const VERSION = '1.0'; </script>` |

## Multi-library / composition

Svelte has NO library-level descriptor or registry, nothing like a tag-library manifest. Composition is purely JS module resolution: a parent imports a child (import Button from './Button.svelte') and the contract travels with the value. The language server + svelte2tsx discover contracts transitively: each imported .svelte transpiles to a virtual .tsx where props become a typed function parameter and events/slots become typed members, and TypeScript's own module graph links components. Published libraries ship per-component .svelte.d.ts files plus a package exports map / svelte field in package.json so consumers get types without the .svelte source. Net: identity = module path; the 'library' is just an npm package of components; discovery is whatever the TS/module resolver already does. Deliberately no central element catalog to keep in sync.

## What a greenfield ng/WO format should take from this

Best-practice signals for a greenfield ng-objects descriptor: (1) UNIFY THE CHANNELS. Svelte 5's biggest lesson is collapsing props, events, and content into ONE concept: properties. Events are callback props; content is Snippet-typed props; two-way binding is a prop flagged bindable. A modern descriptor should have a single parameter list with orthogonal modifiers (type, direction, is-content/templated, default) rather than separate sections for attributes vs actions vs children; directly informs how a .papi models bindings, actions, content uniformly. (2) DIRECTIONALITY AS EXPLICIT OPT-IN. $bindable() is the cleanest pull-vs-pull+push articulation: one-way by default, two-way only when BOTH sides opt in (child marks bindable, parent writes bind:). For ng's pull/push directionality: default read-only/pull, require an explicit bindable/writeback flag, let the consumer still choose one-way. (3) OPTIONALITY IS STRUCTURAL. A default value IS the optional marker; absence means required; fewer redundant metadata fields. (4) TEMPLATED CONTENT FIRST-CLASS AND TYPED. Snippet<[T]> makes parameterized/scoped content a checkable part of the contract; a strong model for typing repeating/scoped content regions WO/ng never typed. (5) TOOLING-DERIVED, NOT HAND-MAINTAINED. The contract is DERIVED from source by transpile-to-typed-IR (svelte2tsx -> .tsx), so it never drifts from code. A greenfield format should prefer generating its element catalog from authoritative source, OR make the descriptor the single source of truth the runtime also consumes; never two artifacts that drift. (6) NO LIBRARY MANIFEST; lean on the existing module/dependency graph for discovery instead of a separate registry.

## Documented pitfall / regret

The v4->v5 model shift is itself the headline finding about FORMAT EVOLUTION. Svelte 4 had FOUR separate special-purpose mechanisms: export let (props), createEventDispatcher/on: (events), <slot>/let: (content), $$Props/$$Events/$$Slots (typing), each with its own syntax and type-inference path. Svelte 5 deleted three channels and re-expressed everything as props + plain JS/TS (destructuring, function props, Snippet values, interface Props). Lesson: a contract format accretes special-case sections over time, each a separate thing to learn/type/sync; the mature move is collapsing them onto ONE general primitive in the host language's own idioms. Pitfalls a new format should avoid: (a) a separate events namespace distinct from properties (proven unnecessary indirection); (b) magic interface names tied to tooling ($$Props) instead of ordinary explicitly-referenced types; (c) two-way binding as default/ambient rather than a doubly-opt-in flag; (d) maintaining a hand-written declaration file AND the source; they drift, which is why svelte2tsx derives types mechanically. The consolidation cost a breaking major version; designing it in from day one (greenfield ng-objects can) avoids that.

## Primary sources

- <https://svelte.dev/docs/svelte/$props>
- <https://svelte.dev/docs/svelte/$bindable>
- <https://svelte.dev/docs/svelte/snippet>
- <https://svelte.dev/docs/svelte/v5-migration-guide>
- <https://svelte.dev/docs/svelte/svelte (Component, ComponentProps, ComponentEvents, Snippet signatures)>
- <https://github.com/sveltejs/language-tools (svelte2tsx / svelte-language-server / .svelte.d.ts)>
- <https://github.com/sveltejs/language-tools/blob/master/docs/preprocessors/typescript.md ($$Props/$$Events/$$Slots, SvelteComponentTyped)>

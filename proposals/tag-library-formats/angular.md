# Angular component/directive metadata (@Component / @Directive decorators + signal-based input()/output()/model())

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Code *is* the descriptor (no separate file)

**Artifact / file shape:** There is NO separate descriptor file — this is the defining trait of the format. Component/directive metadata is an object literal passed to the @Component or @Directive TypeScript decorator, co-located in the same .ts class file as the implementation. Distribution to other 'libraries' is via compiled npm packages plus emitted .d.ts type-declaration files, from which the Angular compiler and Language Service reconstruct the metadata (selectors, input/output names, types, required-ness). The 'descriptor' is therefore the code itself, statically analyzed; tooling and runtime read the same single source.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `(no library-level descriptor)` | Angular has NO library/manifest level descriptor. There is no file analogous to a tag-library manifest. The unit of declaration is the individual decorated class; 'libraries' are just published TypeScript/JS packages whose exported decorated classes are imported. Discovery across libraries happens purely through the TypeScript module graph plus the per-component `imports` array — never through an enumerated registry of elements. | n/a — distribution is npm packages of compiled classes + .d.ts type declarations | — | both | `import { MatButton } from '@angular/material/button';` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `selector` | The CSS selector that identifies, in a template, which DOM nodes this component/directive applies to. This is the element's public identity and the sole matching key — NOT the class name. A component matched on its selector owns the matched 'host element'. Supports element (`profile-photo`), attribute (`[dropzone]`, `[type="reset"]`), class (`.menu-item`), `:not(...)` negation, and comma-separated alternatives. CSS combinators, namespaces, and other pseudo-classes are NOT supported; 'directives [may not] apply on CSS selectors that cross element boundaries.' | string \| undefined | — | both | `selector: 'app-profile-photo, [appHighlight], .ng-tooltip'` |
| `template` | Inline HTML template source for a @Component (the rendering blueprint). The Language Service type-checks expressions inside it against the component class and resolves child element selectors against imported components/directives. | string | cond. | both | `template: '<h1>{{ title }}</h1>'` |
| `templateUrl` | Path/URL to an external HTML template file, mutually exclusive with `template`. Establishes the link the IDE follows for cross-file template ↔ class type-checking. | string | cond. | both | `templateUrl: './profile.component.html'` |
| `styles` | Inline CSS applied to the component, scoped per the encapsulation policy. | string \| string[] | — | RT | `styles: [':host { display: block; }']` |
| `styleUrl` | Path to a single external stylesheet (singular form, newer than styleUrls). | string | — | RT | `styleUrl: './profile.component.css'` |
| `styleUrls` | Paths to multiple external stylesheets. | string[] | — | RT | `styleUrls: ['./a.css','./b.css']` |
| `inputs` | Array form for declaring data-bound input properties at the decorator level (alternative to the @Input/input() member-level form). Each entry is a string ('name' or 'name: alias') OR an object literal carrying alias/required/transform. This is the decorator-metadata mirror of per-field inputs — useful for inputs inherited from a base class. | (string \| { name: string; alias?: string; required?: boolean; transform?: (value: any) => any; })[] | — | both | `inputs: [{ name: 'value', alias: 'val', required: true, transform: numberAttribute }]` |
| `outputs` | Array form for declaring event-bound output properties at the decorator level. String entries only, with optional 'propName: aliasName' aliasing. The decorator-metadata mirror of per-field outputs. | string[] | — | both | `outputs: ['valueChange', 'internalName: publicName']` |
| `host` | Map of bindings applied to the component/directive's own host element: static attributes, dynamic property/attribute/class/style bindings, and event listeners. Keys encode the binding type via brackets/parens; values are expressions evaluated against the class. The Angular team explicitly recommends this over @HostBinding/@HostListener decorators ('These decorators exist exclusively for backwards compatibility'). | { [key: string]: string } | — | both | `host: { 'role': 'slider', '[attr.aria-valuenow]': 'value', '[class.active]': 'isActive()', '(keydown)': 'onKey($event)' }` |
| `exportAs` | The name under which the directive/component instance is exposed to template reference variables (`#x="exportedName"`), letting templates grab the instance. IDE uses it to type the reference variable. | string | — | both | `exportAs: 'matMenu'` |
| `providers` | DI providers contributed to this component's injector (and its element subtree), mapping tokens to implementations. | Provider[] | — | RT | `providers: [{ provide: TOKEN, useClass: Impl }]` |
| `viewProviders` | DI providers visible only to the component's own view DOM children — NOT to projected (ng-content) content. Distinguishes view children from content children for injection scope. | Provider[] | — | RT | `viewProviders: [MyViewOnlyService]` |
| `standalone` | Whether the component/directive is standalone (self-contained, declares its own template deps via `imports`) vs. requiring an NgModule. Defaults to true in modern Angular (v19+). Governs whether `imports` is meaningful. | boolean | — | both | `standalone: true` |
| `imports` | For standalone components: the explicit set of other components, directives, and pipes whose selectors are made resolvable inside THIS component's template. This is the multi-library resolution mechanism — a template can only match selectors of elements it has imported. The Language Service uses this exact list to scope completion/validation. | (readonly any[] \| Type<any>)[] | — | both | `imports: [CommonModule, MatButton, MyChildComponent]` |
| `hostDirectives` | Directives automatically applied to this component/directive's host element ('directive composition'), optionally re-exposing a subset of the composed directive's inputs/outputs to this component's public API (with optional aliasing via 'orig: alias'). A composition/mixin mechanism without inheritance. | (Type<unknown> \| { directive: Type<unknown>; inputs?: string[]; outputs?: string[]; })[] | — | both | `hostDirectives: [{ directive: CdkDrag, inputs: ['cdkDragDisabled: disabled'] }]` |
| `changeDetection` | Change detection strategy for the component (Default vs OnPush). Purely a runtime performance/semantics concern. | ChangeDetectionStrategy (enum: Default \| OnPush) | — | RT | `changeDetection: ChangeDetectionStrategy.OnPush` |
| `encapsulation` | View/style encapsulation policy controlling how component styles are scoped to the DOM. | ViewEncapsulation (enum: Emulated \| None \| ShadowDom) | — | RT | `encapsulation: ViewEncapsulation.ShadowDom` |
| `preserveWhitespaces` | Whether to retain template whitespace in compiled output (default false — Angular collapses whitespace). | boolean | — | RT | `preserveWhitespaces: false` |
| `animations` | Animation trigger definitions for the component, consumed by Angular's animation runtime. | any[] | — | RT | `animations: [trigger('open', [...])]` |
| `schemas` | Declares tolerated unknown elements/attributes so the compiler/Language Service does NOT error on them (e.g. CUSTOM_ELEMENTS_SCHEMA for web components, NO_ERRORS_SCHEMA). This is an explicit escape hatch from strict selector validation. | SchemaMetadata[] | — | both | `schemas: [CUSTOM_ELEMENTS_SCHEMA]` |
| `interpolation` | Overrides the default {{ }} interpolation delimiters for this component's template. | [string, string] | — | both | `interpolation: ['{%', '%}']` |
| `queries` | Decorator-level configuration of content/view queries (@ContentChild/@ViewChild equivalents), mapping field names to query descriptors. Rarely used directly; usually expressed via member decorators. | { [key: string]: any } | — | RT | `queries: { contentChild: new ContentChild(Pane) }` |
| `jit` | Forces this class to be compiled Just-In-Time, skipping Ahead-Of-Time compilation. Build-tooling concern. | true \| undefined | — | both | `jit: true` |
| `moduleId` | Legacy/deprecated: module id used to resolve relative paths for templateUrl/styleUrls. Effectively obsolete with modern bundlers. | string | — | RT | `moduleId: module.id` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `@Input() / @Input(options)` | Member-level decorator marking a class field as a bindable input ('marks a class field as an input property and supplies configuration metadata'). The field NAME is the default binding name; options refine it. This is the primary, idiomatic per-property form — co-located with the field it configures, eliminating descriptor drift. | @Input(aliasString) \| @Input({ alias?: string; required?: boolean; transform?: (value:any)=>any }) | — | both | `@Input({ required: true, transform: booleanAttribute }) disabled: boolean;` |
| `alias (InputOptions.alias)` | Public binding name exposed in templates, decoupled from the internal field name. Lets the external attribute name differ from the TS property — the IDE offers the alias in completion, not the field name. | string | — | both | `@Input({ alias: 'account-id' }) id: number;` |
| `required (InputOptions.required)` | Marks the input as mandatory: the template-type-checker (and thus the IDE) reports an error if a consumer omits the binding. A declarative required-ness signal consumed at author time, not just runtime. | boolean | — | both | `@Input({ required: true }) bankName!: string;` |
| `transform (InputOptions.transform)` | A pure coercion function applied to the bound value before assignment, letting the accepted template type differ from the stored type. Built-ins `booleanAttribute` (string→boolean, presence-as-true semantics) and `numberAttribute` (string→number) are provided. The IDE widens the accepted input type to the transform's parameter type. | (value: any) => any  (or typed (value: TransformT) => T) | — | both | `@Input({ transform: booleanAttribute }) status: boolean;` |
| `input(initialValue, opts)` | Signal-based input declaration (modern replacement for @Input). Returns a read-only InputSignal you call as a function to read the latest bound value. The presence/absence of initialValue determines whether the type includes undefined. | input<T>(): InputSignal<T\|undefined>; input<T>(initialValue: T, opts?: InputOptionsWithoutTransform<T>): InputSignal<T>; input<T,TransformT>(initialValue, opts: InputOptionsWithTransform<T,TransformT>): InputSignalWithTransform<T,TransformT> | — | both | `value = input(0); name = input<string>();` |
| `input.required(opts)` | Required variant of the signal input — no initial value permitted, consumer MUST bind it or the template type-checker errors. Returns a non-undefined InputSignal<T>. | input.required<T>(opts?: InputOptionsWithoutTransform<T>): InputSignal<T>; input.required<T,TransformT>(opts: InputOptionsWithTransform<T,TransformT>): InputSignalWithTransform<T,TransformT> | — | both | `id = input.required<string>();` |
| `InputOptions.debugName` | Optional human-readable name for the signal input, surfaced in Angular DevTools / debugging. Tooling-facing metadata that does not affect binding. | string | — | IDE | `input(0, { debugName: 'counter' })` |
| `model() / model.required()` | Declares a two-way-bindable property — sugar that simultaneously creates an input AND a matching '<name>Change' output, enabling `[(prop)]` banana-in-a-box binding. Returns a writable ModelSignal<T>. A single declaration generating a coordinated input/output pair. | model<T>(initialValue?, opts?: ModelOptions): ModelSignal<T>; model.required<T>(opts?): ModelSignal<T> | — | both | `checked = model(false); // enables [(checked)]` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `@Output() / output()` | Declares an event-bound output property emitting values to the parent. @Output decorates an EventEmitter field; output() returns an OutputEmitterRef<T> you call .emit() on. The field name is the default event name in templates. | @Output(aliasString?) on EventEmitter<T>; OR output<T = void>(opts?: OutputOptions): OutputEmitterRef<T> | — | both | `nameChange = output<string>(); // template: (nameChange)="..."` |
| `OutputOptions.alias` | Public event name exposed in templates, decoupled from the internal field name (parallels InputOptions.alias). | string | — | both | `output<string>({ alias: 'valueChange' })` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `<ng-content select="...">` | Content-projection slot. The `select` attribute is a CSS selector (same grammar as component selectors) that routes matching projected children into this slot — enabling named/multi-slot projection. A selectorless <ng-content> is the catch-all for unmatched content. Inner markup acts as fallback/default content when nothing is projected. | select: CSS-selector string (element/attribute/class/:not); body = fallback content | — | both | `<ng-content select="card-title">Default</ng-content>  ...  <ng-content></ng-content>` |
| `ngProjectAs` | Authoring-side attribute placed on a projected element to make it match a DIFFERENT selector than its real tag — overrides which slot it lands in, useful for semantic-HTML wrappers (e.g. ng-container). | CSS-selector string | — | both | `<ng-container ngProjectAs="card-title">...</ng-container>` |

### `css` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `host: { '[class.x]' / '[style.y]' / '[style.--var]' }` | Within the host map, class/style/CSS-custom-property bindings on the host element. '[style.--property-name]' binds CSS custom properties that cascade into the encapsulated subtree — a declared styling-contract hook. | string expression value | — | RT | `host: { '[class.active]': 'isActive()', '[style.--accent]': 'color()' }` |

### `method` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `@HostBinding / @HostListener` | Member-decorator alternatives to the host map for declaring host property bindings and event listeners. Retained only for backward compatibility — docs say to always prefer the `host` object. | @HostBinding(propPath: string) on field; @HostListener(eventName: string, args?: string[]) on method | — | both | `@HostListener('click', ['$event']) onClick(e) {}` |

## Multi-library / composition

No global element registry. Resolution is selector-based and scoped per-consumer: a template can only match selectors of components/directives present in that component's `imports` array (standalone) or its NgModule's declarations/exports (legacy). This makes cross-library composition explicit and local — importing @angular/material's MatButton brings exactly its selector into scope, with no global namespace pollution. Conflicts: if two imported directives share an overlapping selector, BOTH apply to a matched element (directives compose additively); components are more constrained (one component per element). Namespacing is by CONVENTION not mechanism — the docs mandate a 'short, consistent prefix' per project (CLI default `app-`), reserve `ng` for the framework ('Never use ng as a selector prefix'), and require a hyphen in custom element names per the HTML custom-elements spec. There is no version field or formal namespace declaration; package boundaries + TypeScript module identity + selector prefixes together substitute for explicit namespacing.

## What a greenfield ng/WO format should take from this

Several ideas worth stealing for a Parsley tag-library format. (1) Co-location / single-source-of-truth: 'metadata IS the code' eliminates descriptor drift entirely — the .api-vs-template divergence problem simply cannot occur. A modern format should at minimum make the descriptor GENERATABLE from (or checkable against) the implementation, even if a separate file is kept for IDE speed. (2) Per-field declaration of input contracts (alias / required / transform) right next to the property is far better ergonomics than a separate flat attribute table; `required` as a first-class, author-time-validated flag is a strong idea Parsley should adopt for bindings. (3) `transform` (esp. booleanAttribute/numberAttribute) cleanly separates the wire type (string attribute) from the bound type — directly relevant to how WO/ng bindings coerce attribute strings. (4) Selector-based identity decoupled from class name, and attribute-selectors that ENHANCE native elements (`[appHighlight]` on a real <input>), is a genuinely better model than tag-name-only elements — Parsley could let a 'tag' attach to existing HTML elements rather than always introducing a new tag. (5) Explicit per-consumer `imports` scoping beats a global element registry for multi-library hygiene and is exactly the 'which tag library is in scope here' question Parsley must answer. (6) `exportAs` (naming an instance for template reference vars) and `hostDirectives` (composition without inheritance) are advanced declarative-API ideas. Things to AVOID: the array-form `inputs`/`outputs` on the decorator duplicates the member-level form and is a known footgun; mixing string-shorthand ('name: alias') with object form invites parsing ambiguity — a new format should pick ONE structured form. Reserving a framework prefix and mandating project prefixes by convention-only (no enforced namespace) is weaker than a real namespace declaration would be.

## Documented pitfall / regret

Documented regrets/migrations: (a) @HostBinding/@HostListener are now explicitly deprecated-in-spirit — 'These decorators exist exclusively for backwards compatibility'; the host metadata object is the blessed path, an admission the two-decorator approach was a mistake. (b) The decorator-level `inputs: ['name: alias']` string-shorthand and array form predates and duplicates the member-level @Input, creating two ways to declare the same thing. (c) `standalone` itself encodes a large architectural reversal: NgModules (the original mandatory grouping/registry layer) were effectively abandoned in favor of per-component `imports`, with standalone flipping to default true in v19 — a tacit admission the global-module registry model was the wrong default. (d) The migration from decorator @Input/@Output (EventEmitter) to signal input()/output()/model() is an ongoing churn that splits the ecosystem across two syntaxes for the same concept. (e) `moduleId` is essentially vestigial. (f) Selector namespacing being convention-only has led to real-world prefix collisions, which is why the docs had to add the explicit 'never use ng' warning.

## Primary sources

- <https://angular.dev/api/core/Component>
- <https://angular.dev/api/core/Directive>
- <https://angular.dev/api/core/Input>
- <https://angular.dev/api/core/input>
- <https://angular.dev/api/core/output>
- <https://angular.dev/guide/components>
- <https://angular.dev/guide/components/host-elements>
- <https://angular.dev/guide/components/content-projection>
- <https://angular.dev/guide/components/selectors>

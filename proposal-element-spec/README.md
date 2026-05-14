# Element Spec Proposal

A survey of the current state and extension points for the `.api` file format, intended as a starting document for designing a shared element-registry library used by both ng-objects (and WO, via the shared template parser) at runtime and Parsley at edit time.

This is **not yet a design proposal**. It's a baseline — what we have, what we already know we want, and where the obvious extension points are. The design decisions follow once we agree on what we're starting from.

## Companion files

Two illustrative examples sit next to this document. They aren't normative — they exist to make the discussion concrete and to demonstrate every feature we've talked about exercised against a realistic component:

- [`AjaxUpdateLink.papi`](./AjaxUpdateLink.papi) — per-element file. What an idealized v2 looks like for a real, complex component (Wonder's `er.ajax.AjaxUpdateLink`, with 20+ bindings, deprecations, semantic value types, enum-typed values, cross-binding rules, and a preview block).
- [`parsley.xml`](./parsley.xml) — project-level config. Namespaces (`wo:`, `p:`, `html:`), inline-binding prefixes (`$`, `~`, `[ ]`), and the project's shortcut policy (additions, overrides, removals) on top of `.papi`-declared defaults.

## 1. The current `.api` format

`.api` is XML. The root element is `<wodefinitions>`, which contains exactly one `<wo>` element. The `<wo>` element has two attributes and two kinds of child elements: `<binding>` and `<validation>`.

> **Aside: the runtime doesn't read these files.** Despite being shipped inside framework jars, `.api` files are never parsed by the WO runtime — `JavaWebObjects` source explicitly skips them (`_WOLipsProject.java` line 99: *"ignore API files ..."*). They're included in framework jars as classpath resources only so that tooling can discover them when the jar shows up as a dependency. The only consumers today are Parsley and WOLips. This gives us substantial design freedom for the successor format.

### 1.1 Minimal example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<wodefinitions>
  <wo class="MyButton" wocomponentcontent="false">
    <binding name="action"/>
    <binding name="label"/>
  </wo>
</wodefinitions>
```

### 1.2 What the format expresses today

**`<wo>` attributes:**

| Attribute | Meaning |
|---|---|
| `class` | The Java class name (simple name in practice). Not strictly required by the parser but always present. |
| `wocomponentcontent` | Whether this component accepts child content (i.e. wraps `<wo:content/>`). Boolean as a string. |

**`<binding>` attributes (subset actually used):**

| Attribute | Meaning |
|---|---|
| `name` | The binding name. Required. |
| `defaults` | Drives autocomplete for binding *values*. Magic enum-string from a hardcoded list (see §1.4). |
| `required` | Boolean. Whether the binding must be bound. |
| `settable` | Boolean. Whether the binding is push-capable (we want to write to it). |
| `passthrough` | Unused in surveyed `.api` files but referenced in the model. |

**`<validation>` rules:**

A `<validation>` element has a `message` attribute and one or more child predicates. The predicates are AND-ed together; if all are true, the validation fires the message as an error. Available predicates seen in the wild:

- `<bound name="X"/>` — binding X is bound
- `<unbound name="X"/>` — binding X is unbound
- `<unsettable name="X"/>` — binding X is bound but not settable
- `<count test="...">` — count-based comparisons (less common)

A real example showing how this composes (from Wonder's `AjaxUpdateLink.api`):

```xml
<validation message="'action' and 'directActionName' cannot both be bound.">
  <bound name="action"/>
  <bound name="directActionName"/>
</validation>

<validation message="If 'effect' is bound, then 'updateContainerID' must also be bound.">
  <bound name="effect"/>
  <unbound name="updateContainerID"/>
</validation>
```

This is genuinely useful and seems underutilized. Most `.api` files in the wild don't have a single `<validation>` element. The Ajax framework was an outlier in using them heavily.

### 1.3 What Parsley's in-memory model carries

`ApiSnapshot` (read path) + `SimpleApiBinding` (per-binding):

- `className`, `componentContent`, `bindings[]`, `validations[]`, `preview`
- Per binding: `name`, `defaults`, `required`, `explicitlyRequired`, `willSet`, `explicitlySettable`

Notes:
- `preview` is a pre-serialized XML chunk — used for live preview / `.api` editor preview tab. Not part of `<wo>` proper.
- The `explicit*` flags exist because some "is this required?" answers come from outside the `.api` file (e.g. inferred from the Java class). The `explicit*` variant means "the `.api` file said so directly."

### 1.4 The `defaults` magic list

From `IApiBinding.ALL_DEFAULTS`:

```
Undefined, $action, Boolean, YES/NO, Date Format Strings,
Number Format Strings, MIME Types, Direct Actions, Direct Action Classes,
Page Names, Frameworks, Resources
```

These are presets that determine what autocomplete offers as binding values. `Boolean` → offers `true`/`false`. `Page Names` → offers a list of WOComponent subclasses in the project. Etc.

This is one of the few places in the current format where binding values (not just binding names) get any kind of typing. It's hardcoded, WO-centric, and string-based — but the *idea* is good and generalizes nicely (see §3).

## 2. What we already want, written down

The runtime side (`NGDynamicElementDescription.java` in ng-objects) has a FIXME list that is essentially the wishlist for v2:

1. Required bindings / valid binding combinations
2. Marking a binding as deprecated (with explanation/migration docs)
3. Marking a binding as "in progress" / "incomplete"
4. Default values (what the binding defaults to if unbound)
5. Directionality (pull / push / both)
6. Additional-attribute policy (allowed → pushed to tag, disallowed, etc.)
7. Allowed binding value types (the Java type)
8. Pushed-back type (an element may *accept* many types but always *push* one — e.g. text field accepts any object, always pushes `String`)
9. Allowed value sets (enum-like list of valid values for a binding)
10. Reusable vs. page-level component distinction (page-level components forbidden in tag context)

The Parsley side (ROADMAP.md "Rich component API model" section) wants:

- **Binding type checking** — Java type validation for binding values
- **Binding directionality** — get/set/both
- **Deprecated bindings** — with docs and quick-fix suggestions
- **Default values** — for hover docs
- **Binding value constraints** — enum-style for autocomplete
- **Valid/invalid binding combinations** — cross-binding rules
- **Unknown binding policy** — strict vs. permissive per component
- **Additional-attribute pass-through behavior** — for `<wo:img>` etc.
- **Required vs. optional** — explicit declaration
- **Per-binding documentation** — for hover help
- **Semantic value types** — CSS class name, URL, date format pattern, etc.

The two lists agree almost perfectly. That's reassuring — it means we already have rough consensus on the destination, even though it hasn't been written down as a single spec.

## 3. The shape of the gap

Mapping the wishlist to today's `.api` format:

| Feature | Today | Notes |
|---|---|---|
| Binding name | ✅ `<binding name="X"/>` | |
| Required | 🟡 `required="true"` attribute exists | Half-implemented; not consistently used |
| Settable / directionality | 🟡 `settable="true"` exists | Push-only direction is implicit (unsettable means "must allow push"); no "pull-only" |
| Documentation per binding | ❌ | Major gap |
| Documentation per element | ❌ | Major gap |
| Java type | ❌ | The `defaults` field hints at this but is a separate concept |
| Default value | ❌ | Different from `defaults` (which is the autocomplete-driver enum) |
| Deprecation | ❌ | |
| Cross-binding rules | ✅ `<validation>` | Exists, works well, underused |
| Allowed-value enum | 🟡 `defaults` field | Hardcoded list of presets — not user-extensible |
| Unknown-binding policy | ❌ | No way to declare "this component is strict" |
| Pass-through attributes | ❌ | |
| Page-level vs. reusable | ❌ | |
| Tag shortcuts | ❌ | Currently in Parsley preferences (per installation), not in `.api` files |

## 4. The structural decision

Two structural questions that the design has to answer first:

### 4.1 Where does an element's metadata live?

Three candidates, not mutually exclusive:

- **Per-element file** (today's model) — `MyButton.api` lives next to `MyButton.java`. Good for project-owned components. Bad for declaring metadata about framework components you don't own.
- **Project-level overrides folder** — somewhere central in the project, where you can add a file declaring metadata for `NGConditional` (a framework component) without forking the framework.
- **Framework-bundled metadata** — framework jars ship their own `.api` files in a known location (e.g. `META-INF/wo/elements/`). The runtime and IDE both discover them at classpath-scan time.

The three roles aren't conflicting. A reasonable end state is **all three coexist**, with a defined precedence order (project local > project override > framework bundled > inferred fallback).

### 4.2 Where do tag shortcuts live?

Currently in Parsley preferences — per installation. This is broken: shortcuts should travel with the project (or with the framework defining them).

Candidates:

- **A project-level file** (e.g. `src/main/resources/wo-shortcuts.xml`) — explicit, central. Easy to find. Doesn't compose well across frameworks.
- **Per-element declaration in the `.api` file** — each component lists its own shortcuts. Composes naturally (framework jars carry their components' shortcuts). Discovery requires scanning all `.api` files.
- **A framework-bundled global file** + project overrides — framework ships its shortcut declarations, project can override or add.

The `NGDynamicElementDescription` model already represents per-element aliases (`<shortcut>if</shortcut>` style). This points toward per-element declaration as the natural fit.

## 5. A possible v2 sketch

Not a proposal yet — just to make the discussion concrete. Imagine this is what `.api` files look like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<element class="ng.appserver.templating.elements.NGConditional">
  <shortcuts>
    <shortcut>if</shortcut>
  </shortcuts>
  <description>
    Wraps content in a template and decides to render it based on a condition.
    If the condition evaluates to false, the contained content will not be
    rendered. If the 'negate' binding is set to true, the condition is flipped.
  </description>
  <accepts-content>true</accepts-content>
  <unknown-bindings>strict</unknown-bindings>
  <bindings>
    <binding name="condition" type="java.lang.Object" required="true" direction="pull">
      <description>The condition to evaluate.</description>
    </binding>
    <binding name="negate" type="java.lang.Boolean" direction="pull">
      <description>Can be set to true to "flip" the condition.</description>
      <default>false</default>
    </binding>
  </bindings>
</element>
```

Notes on this shape:

- The root element is `<element>` (singular). `<wodefinitions>` and the redundant `<wo>` wrapper from today are gone.
- `class` is now the **fully qualified** class name. The runtime needs this; the IDE can use it (JDT resolves it).
- Shortcuts are per-element.
- Each binding has explicit `type`, `required`, `direction`, and `description`. No more magical `defaults` string — value-type constraints come from a dedicated mechanism (see below).
- `unknown-bindings` is `strict` | `permissive` | `passthrough` to a tag. Explicit.
- Cross-binding `<validation>` rules stay as-is (they work).

For value-type constraints (the `defaults` mechanism today), a richer notion:

```xml
<binding name="format" type="java.lang.String">
  <values-from>resource:DateFormatPatterns</values-from>
</binding>
```

`<values-from>` could be:

- A literal enum: `<value>YES</value> <value>NO</value>`
- A reference to a Java enum on the classpath: `<values-from>enum:com.example.MyEnum</values-from>`
- A semantic type: `<values-from>type:date-format</values-from>` (with the IDE/runtime knowing how to resolve `date-format`)
- A project resource type: `<values-from>resource:WebserverImages</values-from>`

This is the bridge to the "semantic value types" item from the roadmap.

For the override scenario:

```xml
<element class="ng.appserver.templating.elements.NGRepetition" overrides="true">
  <bindings>
    <binding name="batchSize" deprecated="true">
      <description>
        Use NGPaginator instead. This binding will be removed in 2.0.
      </description>
    </binding>
  </bindings>
</element>
```

An override file is keyed by `class` and merges with whatever the framework provides. Bindings declared in the override either add new ones or modify existing (in this case, marking deprecated).

## 6. Open questions

Things that will need decisions before any implementation:

1. **Compatibility with existing `.api` files.** Plenty exist in the wild. Do we read v1 and v2 in parallel forever? Auto-migrate on save? Drop v1 support at a major version?

2. **Mandatory vs. inferred.** Components without a `.api` file — strict error, or fall back to reflection-based inference? The "inferred" path is what makes WO/NG feel low-ceremony; the "mandatory" path is what gives build-time guarantees. Likely answer: both modes available, configurable.

3. **What does the runtime actually enforce?** The IDE can warn about a missing required binding. The runtime *could* refuse to render a template with a missing required binding. Should it? At what severity?

4. **Class names: simple or fully qualified?** Today's files use simple names (`class="WOString"`), and the IDE/runtime resolve them by scanning the classpath. Switching to fully qualified is more deterministic but more verbose, and breaks all existing `.api` files. We could allow both: simple name when unambiguous, fully qualified when needed.

5. **Documentation format.** Plain text? Markdown? HTML? The IDE hover wants something it can render; the runtime probably doesn't care.

6. **Versioning.** A namespace, a version attribute, or a separate root element name to distinguish v1 from v2.

7. **Where does the shared library live?** A new ng-objects subproject (e.g. `ng-element-spec`)? An existing one? A separate repo? The library is small in interface but has implications for everyone who depends on it.

8. **Build-time validation.** Maven plugin? Annotation processor? Plain JUnit-style test in the runtime? Each has tradeoffs (plugin = run on every build, processor = runs at compile time, test = runs only when tests run).

## 7. Suggested next steps

1. **Talk through this document** — find anything that's wrong or missing.
2. **Make the structural decisions** (§4) — those gate everything else.
3. **Draft the actual v2 spec** based on the structural decisions.
4. **Prototype in code** — both runtime and IDE consumers, working from the same parser/model library.
5. **Migrate built-in ng-objects elements** to v2.
6. **Migrate Parsley to read v2** (with v1 fallback).
7. **Document the migration path for user-owned components.**

No timeline implied. Each step is its own discussion.

---

## Appendix: where current code touches the format

For reference, the code paths that read/write `.api` files today:

**Runtime side** (`ng-objects`):
- `NGDynamicElementDescription` — in-memory model, hand-coded for built-in elements
- (No runtime parser for `.api` files yet — that's part of what we'd build)

**IDE side** (`ng.componenteditor` in this repo):
- `org.objectstyle.wolips.bindings.api.ApiParser` — reads `.api` XML into `ApiSnapshot` (DOM-based)
- `org.objectstyle.wolips.bindings.api.ApiSerializer` — writes `ApiSnapshot` back to XML
- `org.objectstyle.wolips.bindings.api.ApiSnapshot` — the parsed model
- `org.objectstyle.wolips.bindings.api.SimpleApiBinding` — per-binding model
- `org.objectstyle.wolips.bindings.api.ApiValidation` — cross-binding rule model
- `org.objectstyle.wolips.bindings.api.ApiCache` — per-project cache
- `org.objectstyle.wolips.bindings.api.ApiUtils` — helpers for `getValidValues` etc.
- `org.objectstyle.wolips.apieditor.*` — the form-based `.api` editor
- `org.objectstyle.wolips.bindings.wod.AbstractWodBinding` — uses the parsed model during keypath validation

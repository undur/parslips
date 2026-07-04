# Proposal: Tag-library descriptor format — prior-art survey & design input

**Status:** 🟢 OPEN — design input, not executed. This is the research record and a
set of actionable recommendations for the **Parsley/ng tag-library format**: a
declarative descriptor that is the *single source of truth* for which tags/elements a
project exposes, read by **both** the runtime (to render templates) and IDE tooling
(autocomplete, hover docs, validation).
**Home:** kept here because wolips/`ng.componenteditor` is the coordinating project.
The format itself will ultimately live in **Parsley or ng**, with this editor as one
implementing *consumer* (alongside the runtime and any future LSP client). When Parsley
claims the spec, this doc follows it.
**Origin:** the declarative tag-aliases work (`parsley-tag-aliases.properties`, shared
by the Parsley runtime + wonder-slim + this editor; see `CHANGES.md`) proved the
"one registry, many readers" model in miniature. The tag library generalizes it to the
full element vocabulary. Before designing the format, we surveyed the prior art.

> **Companion deep-dive:** this document is the high-level summary. For the *field-by-field*
> reference — every construct each format declares, across **ten** formats (the seven below
> plus Angular, Blazor/Razor, and Svelte 5), with a cross-format field universe and
> prioritized greenfield recommendations — see
> [`tag-library-formats/overview.md`](tag-library-formats/overview.md) and the per-format
> docs beside it. The deep-dive was run *unbiased*: each format observed as industry
> best-practice for a greenfield ng/WO format, not mapped onto current WO/ng conventions.

---

## How this was researched

Multi-source deep-research pass: 6 search angles → 23 primary sources (actual schemas,
specs, official docs — blogs excluded) → 103 extracted claims → 25 adversarially
verified (23 confirmed, 2 killed). The two **killed** claims were our own framing
assumptions; their refutation is recorded below as findings in their own right.

Primary sources of record (all `quality: primary`):

- Custom Elements Manifest — `schema.d.ts` / `schema.json`
  (github.com/webcomponents/custom-elements-manifest), open-wc analyzer docs.
- JSP TLD — Oracle TLD reference (`docs.oracle.com/cd/E17904_01/.../tld.htm`),
  J2EE tutorial JSPTags, Oracle taglibs packaging doc.
- JSF/Facelets — Oracle/Jakarta VDL docs for `cc:interface` / `cc:attribute`.
- Stencil — `stenciljs.com/docs/docs-json` (`JsonDocs`).
- Vue — `vuejs/language-tools` `component-meta`, `vue-component-meta` (npm).
- JetBrains web-types — `JetBrains/web-types` schema + Polysymbols docs.
- VS Code HTML custom-data — `microsoft/vscode-html-languageservice`
  `customData.schema.json`, VS Code custom-data-format blog.

---

## (a) Field comparison across formats

| Format | Per-attribute / binding fields | Beyond attributes | Identity / versioning | Role |
|---|---|---|---|---|
| **JSP TLD** (`.tld`) | `name`, `required`, `rtexprvalue`, `type`, `fragment` | tag→class via `<tag-class>`; **validation lives in `TagExtraInfo` Java code, not in the descriptor** | `<uri>` (arbitrary identity key, *not* a URL, *not* a version), `<tlib-version>`, `<short-name>`, `<jsp-version>` | runtime **+** tooling |
| **JSF/Facelets** `<cc:interface>` / `<cc:attribute>` | `name`, `type`, `default`, `required`, **`method-signature`**, `targets`, `targetAttributeName`, `displayName`, `shortDescription`, `preferred`, `expert`, `hidden` | explicit IDE-palette metadata (`displayName` / `preferred` / `expert` / `hidden`) | per-component | runtime **+** tooling |
| **Custom Elements Manifest** (CEM) | attribute: `name`, `type`, `default`, `description`, **`deprecated`** | `attributes`, **`events`, `slots`, `cssParts`, `cssProperties`, `cssStates`**, `demos`, `members` (props/methods), `tagName` | per-module / package | **tooling-only** (deliberately not runtime) |
| **Stencil** `JsonDocs` | prop: `name`, `type`, `default`, `required`, `docs`, `deprecation` | `events`, `methods` (full signatures), `slots`, `parts`, `listeners`, `styles`, `encapsulation`, `dependencies` | per-component | tooling (generated) |
| **vue-component-meta** | `PropertyMeta`: name, description, type, … | `props`, **`events` (`EventMeta`), `slots` (`SlotMeta`), `exposed`** | per-component | tooling (extracted) |
| **JetBrains web-types** | symbols under `contributions` | namespaced **`html` / `css` / `js`** | **`name`, `version`, `framework`** at top level | **tooling-only** |
| **VS Code HTML custom-data** | tag → attributes → `valueSets` | `tags`, `globalAttributes`, `valueSets` only | top-level `version` | **tooling-only** |

### The two killed claims (corrected assumptions — these *are* findings)

1. ❌ *"TLD `<uri>` uniquely identifies a specific **version** of the library."*
   Refuted 0–3. The `<uri>` is an **arbitrary identity key** — explicitly *not* a
   version and *not* a physical location. Versioning is the *separate* `<tlib-version>`
   element. → **Identity and version are orthogonal; keep them as separate fields.**
2. ❌ *"TLD attribute carries **exactly** name/required/rtexprvalue/type/fragment."*
   Refuted 0–3. The field set is real, but "exactly / closed" is wrong — later versions
   add fields and `description` is also carried. → **Never spec a closed attribute
   schema; make it additive-only.**

---

## (b) What we lack — adopt / skip / already-ahead

We already have: tag aliases (recursive `name → name`), per-binding accepted types,
**binding directionality (pull/push)**, required flags, defaults, Markdown docs per
element and per binding, cross-binding validation rules, an element-level "wraps
content" flag, and a passthrough flag.

**Adopt — high tooling value, cheap, present in every modern format:**
- **`deprecated` with a reason string**, per element *and* per binding (CEM, Stencil
  carry it as a first-class typed field). Highest value-per-byte for an IDE:
  "don't use this — use X instead."
- **Explicit library identity + version**, as *separate* fields (the TLD lesson).
  web-types puts `name` + `version` at the top of each library file — exactly what
  tooling needs to compose libraries, and also our fix for the precedence problem (§c).
- **An open / additive attribute schema** — never "exactly these fields" (the TLD
  attribute kill).

**Adopt if WO semantics fit:**
- **Method / action signatures** — JSF's `method-signature`, Stencil's method
  descriptors. WO *has* action bindings, so a `signature` / `action` marker on a binding
  is meaningful (better `action=` validation + completion).
- **Named slots** — we have a binary "wraps content" flag; CEM/Vue/Stencil model
  *named* `slots`. The evolution path if Parsley grows multiple content regions.
  Not urgent.

**Skip — decorative for WO or a runtime-model mismatch:**
- **`events`** — central to Web Components/Vue (DOM event model); WO's request/response
  cycle has no equivalent emission. Skip unless a concrete WO concept maps to it.
- **`cssParts` / `cssProperties` / `cssStates`** — Shadow-DOM concepts, no analogue.
- **per-member `@since`** — low priority; revisit only with versioned tag libraries.

**Already ahead (the survey confirms these are rare):**
- **Binding directionality (pull/push)** — *no surveyed format has it.* TLD's
  `rtexprvalue` is the nearest and far weaker ("can this be an expression"). Genuinely
  distinctive to WO's binding model — keep it.
- **Runtime + tooling single source.** CEM, web-types, VS Code custom-data, and
  vue-component-meta are **all tooling-only** — they exist *because* the runtime info
  wasn't readable by tools. We already have what they work backwards toward. (Nuance:
  CEM chose tooling-only *deliberately* to avoid runtime coupling — see pitfall §d.2.)

---

## (c) Multi-library composition / precedence — recommendation

Today we do **first-declaration-wins because classpath order is unreliable at runtime**.
That is the known-weak spot. Prior art offers two proven models:

- **JSP TLD — identity-key resolution.** Each library declares a `<uri>`; consumers
  request a library *by that key*. Multiple libraries coexist in one JAR, resolved by
  URI match — **not** by classpath order. Conflicts can't happen silently because the
  consumer *names* which library it wants.
- **web-types / CEM — namespacing.** web-types scopes every symbol under `html`/`css`/`js`
  namespaces + a top-level library `name`; CEM scopes by module/package. Symbols become
  addressable as `library:symbol`.

**Recommendation:** give every Parsley tag library an explicit **identity** (`name`) and
**version**, declared *in the library* and decoupled from classpath order — the TLD
`<uri>` / `<tlib-version>` split (and, per the kill, keep identity and version as
*separate* fields). Conflict resolution then becomes a *declared policy* instead of a
classpath accident. Two viable policies:

1. **Explicit precedence** — a project-level ordering of library identities
   ("ERExtensions overrides core"), so `WOString → ERXWOString` wins *because it is
   declared to*, not because of jar order.
2. **Namespaced addressing** — let a template disambiguate (`erx:WOString` vs core), so
   both coexist without one silently shadowing the other.

For our case, **explicit precedence by declared library identity** fits best: it
preserves the "ERExtensions replaces core elements" semantics we already ship, makes it
intentional and inspectable (the editor can show *"overridden by library ERExtensions
v8.0.3"*), and removes the classpath-order fragility. The version field then lets
tooling warn on mismatches.

> Note the related runtime gap that is *out of scope* for the descriptor: switch
> components resolve target names at request time, bypassing the parse-time resolver
> (Parsley issue #23). Precedence policy here governs *parse-time* composition; the
> switch bypass is a separate runtime-resolution concern.

---

## (d) Top design pitfalls — each tied to a real format's experience

1. **Behavior in code, not in the descriptor** *(JSP `TagExtraInfo`, verified 3–0).*
   TLD pushed validation logic into Java classes *outside* the descriptor, so tooling
   could never fully understand a tag. **We already avoided this** with declarative
   cross-binding `<validation>` rules — the research strongly validates that call.
   *Resist backsliding:* don't add a "validator class" escape hatch. Keep validation
   declarative.

2. **Two sources of truth that drift** *(verified 3–0 as a documented hazard).*
   The recurring regret: runtime registration and tooling descriptor diverge. CEM's
   entire reason to exist is "ONE machine-readable description consumed by many tools."
   **Our runtime + editor-read-the-same-file design is the thing they all wish they'd
   had** — protect it as the core invariant, especially as Parsley becomes the authority
   and this editor becomes one consumer.

3. **Closed / exact schemas** *(the TLD attribute kill, 0–3).* Don't freeze the field
   set. Every one of these formats grew fields over time; "exactly these" guarantees
   painful migration. Make the descriptor **additive-only**.

4. **Conflating identity with version** *(the TLD `<uri>` kill, 0–3).* `<uri>` (identity)
   and `<tlib-version>` (version) are deliberately separate. Keep them separate fields in
   the Parsley library header.

5. **XML verbosity drove the industry to JSON.** Every *modern* tooling format (CEM,
   web-types, VS Code custom-data, Stencil `JsonDocs`) is JSON, not XML. Our `.apiext` is
   XML and the `.properties` aliases are terse. Not a reason to rewrite today — but if the
   tag-library format is greenfield in Parsley/ng, **JSON (or a terse line format) is
   where the ecosystem converged**, and it makes the descriptor trivially consumable by a
   future VS Code/LSP client (the stated long-term goal).

---

## Bottom line

We are ahead on the two hardest things — the single-source runtime+tooling model, and
binding directionality. The concrete *adds* are small and well-precedented:
**deprecation-with-reason**, **declared library identity + a separate version**, and an
**explicit precedence policy** that retires the classpath-order hack. The *don'ts* are
equally clear, and we already honor the biggest one (declarative validation).

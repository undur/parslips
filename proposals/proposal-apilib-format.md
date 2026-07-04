# Proposal: `.apilib` / `.apiext` v2 — a self-documenting element & tag-library format

**Status:** 🟢 OPEN — design proposal. A concrete, opinionated strawman for the next
generation of the element-API format, presented as a **single self-documenting example
file** (see [`apilib-format/example.apilib`](apilib-format/example.apilib)) rather than a
DTD. The intent is *subtractive review*: read the example, strike what we don't want,
keep the rest.
**Home:** the format spec lives in [`undur/apiext-format`](https://github.com/undur/apiext-format);
this proposal is the coordinating design input from the wolips side. When adopted, it
moves there.
**Builds on:** the [prior-art deep-dive](tag-library-formats/overview.md) (10 formats,
field-by-field) and the [original survey](proposal-tag-library-format.md), and resolves
the [11 open `apiext-format` issues](https://github.com/undur/apiext-format/issues)
into one coherent vocabulary.

---

## The thesis: `.apiext` and `.apilib` are two sides of one coin

`.apiext` describes **one element's** binding API. `.apilib` describes **a library of
elements** — a named, versioned collection of element descriptions plus the library-level
facts (identity, precedence, default tag names) that only make sense when many elements
ship together. The element-description vocabulary is **identical** in both; `.apilib`
just wraps a `<library>` header around a set of `<element>` blocks.

So this proposal defines **one format** with two entry points:

| | `.apiext` | `.apilib` |
|---|---|---|
| Root | `<element-api>` (one element) | `<element-library>` (header + N elements) |
| Identity | the element's class | the library's id + version, then per-element classes |
| Lives | next to a component (`Foo.apiext`) | shipped by a framework (`WonderSlim.apilib`) |
| Element vocabulary | **the same** | **the same** |

A single element file is just a library of one with no header. A consumer reads both with
the same element parser.

## Why a self-documenting example instead of a DTD

A DTD constrains structure, not meaning, and nobody learns a format by reading its grammar.
The deliverable here is **[`example.apilib`](apilib-format/example.apilib)** — a complete,
valid file that:

1. **Documents the vocabulary inline.** It opens with a `<format-guide>` block explaining
   every construct, and each construct's first use carries an explanatory comment. The file
   teaches the format as you read it.
2. **Applies to elements we actually have.** It describes real WO elements — `WOString`,
   `WOConditional`, `WOTextField`, `WOCheckBox`, `WORepetition`, `WOHyperlink`, `WOImage`,
   `WOGenericContainer` — so every feature is grounded in a real binding contract, not a toy.

Read it top to bottom; it's meant to be legible. Then we subtract.

---

## What's new vs. today's `.apiext` (and which issue / format each resolves)

Everything in today's `.apiext` is kept (it's a strict superset): `<doc>` with Markdown,
`<pull>`/`<push>` directionality, `<type interpretation="…">`, cross-binding validation,
`required`. The additions:

| Addition | Resolves | From the deep-dive |
|---|---|---|
| `<library>` header: `id`, `version`, `precedence`, `description` | the `.apilib` half; multi-library composition | web-types `name`/`version`/`priority`; JSP `<uri>`/`<tlib-version>` (identity ≠ version) |
| `class` is **FQN**; tag `name` is separate | [#11](https://github.com/undur/apiext-format/issues/11) | CEM declaration-vs-registration split |
| `unknownAttributes="forbidden\|allowed\|passthrough"` | [#1](https://github.com/undur/apiext-format/issues/1) (replaces `passthrough` bool) | Blazor `CaptureUnmatchedValues` |
| `<renders as="…">` — primary HTML element | [#10](https://github.com/undur/apiext-format/issues/10) | makes passthrough *checkable* |
| `<default>` (singular) — value when unbound | [#3](https://github.com/undur/apiext-format/issues/3) | Stencil/Vue/Angular `default` |
| `<values>` — value-sets: literal enums + dynamic source kinds | [#4](https://github.com/undur/apiext-format/issues/4) | VS Code `valueSets`; web-types |
| `<deprecated>` with `since` + migration `<doc>` | [#5](https://github.com/undur/apiext-format/issues/5) | CEM `deprecated: boolean\|string` (universal) |
| `settable` dropped for `.apiext` authoring (use `<push>`) | [#2](https://github.com/undur/apiext-format/issues/2) | — |
| `<constraint>` — redesigned validation, explicit `when`, message describes the *fix* | [#9](https://github.com/undur/apiext-format/issues/9) | Angular/Blazor declarative-only; **never a code escape hatch** (anti-TagExtraInfo) |
| `scope="public\|framework\|project\|page"` | [#8](https://github.com/undur/apiext-format/issues/8) | Java visibility |
| `reusable="true\|false"` | [#7](https://github.com/undur/apiext-format/issues/7) | — |
| **Typed channels:** `<event>` and `<action>` as siblings of `<binding>` | deep-dive headline | CEM/Stencil/Vue/JSF — "a tag + a bag of attributes is a modeling error" |
| `summary` (short) + `<doc>` (long) two-tier docs | deep-dive | CEM `summary`/`description` |
| `<see href>` reference links | deep-dive | VS Code `references[]` |
| `since` / `experimental` lifecycle on elements & members | deep-dive | web-types |

### Deliberately *not* in the format (kept as consumer/tooling concerns)

- **Tag aliases / shortcuts** — per [#6](https://github.com/undur/apiext-format/issues/6),
  an alias is *consumer-owned*, not a property of the element. The element is the *value*
  in a `name → class` map and is ignorant of how it was named. This stays in the tag-library
  *resolution* layer (today: `parsley-tag-aliases.properties`), **not** in the element
  vocabulary. The `.apilib` header may ship *default* tag names (`<element>`'s `name`), but
  remapping/override lives in the consumer.
- **Framework editorial taxonomy** (AjaxSlim's `update`/`widget` badges) — already removed
  from `.apiext` for the right reason; stays out.
- **Validation as code** — no `<validation-class>` / TagExtraInfo escape hatch, ever. The
  canonical reason `.tld` is awkward for IDEs. All validation stays declarative.

---

## The split that matters: portable claim vs. tool resolution

A recurring tension (most visible in value-sets, #4) is that some declarations are
**portable** (any tool understands a literal enum `true|false`) and some are **tool-coupled**
(only a project-scanning tool can resolve "the resources that exist"). The format's posture,
consistent throughout:

> **The format declares portable *intent* — the source kind. Each tool resolves it.**

`<values kind="resource"/>` is a portable statement ("this binding's values are project
resources"); *which* resources is the tool's job. A literal `<values>true false auto</values>`
is fully portable. This keeps the format honest: a declaration never lies about being
checkable when it isn't.

The same discipline already governs `<type interpretation="truthy">` (the type stays the
real validatable constraint; the interpretation is documentation) and the absent-policy rule
in #1 (present = portable claim every tool honours; absent = tool's discretion).

---

## Open questions worth deciding during review

1. **Serialization.** The deep-dive's strongest [CHANGE] was *JSON over XML*. This strawman
   stays **XML** to remain a literal superset of `.api`/`.apiext` (the one hard constraint:
   existing `.api` files must keep validating). A JSON form is possible as a parallel
   surface, but the superset requirement makes XML the path of least resistance *for now*.
   Flagged for an explicit call — it's the biggest fork in the road.
2. **`<renders>` granularity** (#10): `input` vs `input[type=checkbox]`. The example uses the
   richer form; it's more useful for passthrough-checking but more complex.
3. **`settable` ingestion** (#2): when reading a legacy `.api` with `settable` but no
   `<push>`, treat as a typeless push (uniform rendering) or ignore? The example assumes the
   former so `.apilib` tooling can render plain `.api` files.
4. **Events/actions in WO.** WO has no DOM-event model, but it *does* have action bindings.
   The example models `<action>` (real) and includes `<event>` (speculative, for ng /
   future) so we can see both and cut `<event>` if it earns nothing.

---

## How to review this

Open **[`apilib-format/example.apilib`](apilib-format/example.apilib)**. It's the actual
proposal — this document is just the rationale. Strike features inline; the ones that
survive are the format.

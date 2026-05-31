# Proposal: Package Restructuring for `ng.componenteditor`

**Status:** ⏸ SUPERSEDED / PARTIALLY DONE — the design-cleanup pivot
(`proposal-design-cleanup.md`) absorbed and shipped the `editor.*` consolidation this
proposal originally drove. What remains genuinely open is **Tier 2: the root
namespace rename** (`org.objectstyle.wolips.*` → `parsley.*`), which was deliberately
deferred and has not been done. Kept for that pending work.

> **Pivot (discovered during execution):** Attempting Tier 1 (preferences first)
> revealed that the structural confusions here are mostly **design smells wearing
> package costumes** — e.g. preference *pages* are welded to their feature's
> `PreferenceConstants`, so relocating the pages just adds a package hop over the
> same coupling. Packages are the shadow of the design; reshaping the shadow
> doesn't reshape the object. So: **design cleanup first, packaging trails it.**
> This document remains valid as a *diagnosis*. Execution is paused pending the
> design-cleanup proposal. The genuinely-decoupled relocations (`MavenPreferencePage`
> out of `wizards`; `eomodeler`, `core.resources.types`) can still be done anytime
> as trivial standalone commits.

---

**(original status:) Tier 1 APPROVED — executing in blast-radius order. Discussion document.**

**Decisions taken:**
- Go ahead with **Tier 1**. Tier 2 (`wodclipse` detangle) and root-namespace
  rename remain out of scope for now.
- **Editor consolidation:** `editor.*` with `editor.component` as the multi-tab
  host, and `editor.template` / `editor.wod` / `editor.api` / `editor.woo` as the
  part-editors, plus `editor.actions` / `editor.inspector` / `editor.menu`.
- **Preferences:** one flat `org.objectstyle.wolips.preferences` package; all
  preference pages consolidated there, including `MavenPreferencePage`.
- **Execution order (increasing blast radius, one commit + build + Eclipse
  re-test each):** (1) preferences consolidation, (2) misplaced utilities
  (`eomodeler`, `core.resources.types`), (3) the `editor.*` consolidation.
**Scope:** Package *structure* only. No behavioral changes, no further dead-code
removal (that's been done separately), no class-internal refactoring.

---

## Why now

We've just removed a large amount of dead code (the XML/DTD editor, JSP support,
the Velocity template engine, a pile of orphaned Amateras classes, a dead logging
package). The plugin is much lighter — but we shrank it by *deleting*, never by
*reorganizing*. The package layout still reflects its history (≈18 separate WOLips
plugins + the Amateras HTML editor + a vendored XML parser, all merged into one
bundle) rather than its current purpose.

This proposal diagnoses the structural confusion and proposes a target layout.

## The one constraint that makes this safe

The bundle has **no `Export-Package`** — it exports nothing to other bundles. Its
package names are *purely internal*. The only external references are:

- `feature.xml` → references the **bundle id** `ng.componenteditor` (not packages).
- `parslips.lsp` → mentions `ng.componenteditor` only in **comments**.

So a package rename touches only: the Java `package`/`import` statements, the
**31 distinct package strings** in `plugin.xml` (`class=` attributes), and
`.classpath`. All mechanical, all greppable, no OSGi contract to break, no
downstream consumers. This is far safer than restructuring a library bundle.

(It does mean a meaningful diff and that the user re-tests in Eclipse after — but
no hidden coupling to surprise us.)

---

## Current structure: the three origin layers

The code splits into three top-level namespaces by origin:

| Namespace | Classes | What it is |
|---|---|---|
| `org.objectstyle.wolips.*` | 352 | The plugin's own code (the merged WOLips plugins) |
| `tk.eclipse.plugin.*` | 130 | The Amateras HTML/CSS/JS editor it was extracted from |
| `jp.aonir.fuzzyxml.*` | 45 | A vendored fuzzy/lenient XML parser library |

### The two foreign layers — how coupled?

- **`jp.aonir.fuzzyxml.*` — a clean, self-contained library.** Consumed by ~19
  wolips classes purely through its public API (`FuzzyXMLParser`, the
  `FuzzyXML*` DOM node types). Wolips never subclasses it. It's the parser behind
  template formatting/validation and the WOD refactorings. Minor backwards-coupling
  (3 internal FuzzyXML classes reach into `WodHtmlUtils`/`HTMLPlugin`). **Verdict:
  isolatable; could even be a separate JAR. Low priority to touch.**

- **`tk.eclipse.plugin.*` (Amateras) — load-bearing via inheritance.** The live
  template editor sits *on top* of it: `TemplateEditor extends HTMLEditor`,
  `TemplateSourceEditor extends HTMLSourceEditor`, `TemplateConfiguration extends
  HTMLConfiguration`. ~29 wolips classes import it. Three standalone editors are
  registered from it (`HTMLEditor`, `CSSEditor`, `JavaScriptEditor`). **Verdict:
  cannot be isolated/renamed without rewriting the editor hierarchy. Leave it.**

---

## The diagnosis: what makes the layout hard to understand

Ranked by how much they confuse a newcomer.

### 1. Four parallel "editor" packages with no parent (HIGH)

`componenteditor` (the multi-tab container), `templateeditor`, `apieditor`,
`wooeditor`, plus `wodclipse.editor` (the WOD editor) and `components.editor`
(just interfaces) all sit as **top-level equals**. But they're not equals — the
component editor is a container whose *tabs* embed the HTML/WOD/API/WOO editors.
The flat layout hides that hierarchy. A newcomer asking "where is component
editing?" has to learn that it's spread across 5+ sibling packages with a
`components` vs `componenteditor` name collision in the middle.

### 2. Preferences scattered across 5 packages (HIGH)

Preference pages live in `bindings.preferences`, `componenteditor.preferences`,
`wodclipse.core.preferences`, `devserver`, and `wizards` (the misplaced
`MavenPreferencePage`). Two separate `PreferenceConstants`/`PreferenceInitializer`
pairs. No central settings home; adding a new preference means guessing where it
goes.

### 3. `wodclipse.*` is a 106-class kitchen sink with a legacy name (MEDIUM)

It's *one cohesive subsystem* (the WOD-language editor: parser, completion,
refactoring, document model) but it's the largest tree, its `core.*` split feels
arbitrary, and the name "wodclipse" is pure WOLips heritage — unrelated to the
"Parsley" branding used everywhere in the UI/plugin.xml. It's coherent internally
but its size + naming make it intimidating.

### 4. Misplaced / mislabeled classes (MEDIUM)

- `MavenPreferencePage` in `wizards` — it's a preference page, not a wizard.
- `eomodeler.core.model` (6 classes) sits as a top-level subsystem but is only
  used by `wodclipse.core.woo.WooModel` to parse `.eomodel` plists.
- `core.resources.types` (LRU type-hierarchy caches) — buried under an
  important-sounding `core` that's really just a cache utility.

### 5. Hollow single-class namespace roots (LOW)

`bindings`, `componenteditor`, `components`, `apieditor`, `wodclipse`, `core`,
`preferences`, `editors` each hold ~1 Activator at the root. Standard Eclipse
pattern, but ~10 of them make the tree look more complex than it is. Mostly
cosmetic.

### 6. Naming reflects history, not purpose (LOW, cross-cutting)

`wolips` / `wodclipse` (old) vs `Parsley` (current brand) vs `ng.componenteditor`
(bundle id). Three names for overlapping concepts. A full rename to a `parsley.*`
namespace would be the "honest" end state but is the **highest-churn, highest-risk**
option and is explicitly *out of scope* for this round (see below).

---

## Proposed target structure

A guiding principle: **group by responsibility, make the container/parts
relationship visible, and stop pretending merged-in plugins are still separate.**
Keep the `org.objectstyle.wolips.*` root for now (renaming the root namespace is a
separate, bigger decision — see "Explicitly out of scope").

### Tier 1 — high value, low risk (recommended for this round)

These are the changes that most improve understandability per unit of churn.

1. **Consolidate the editors under one `editor` parent.**
   Group the component editor and its embedded part-editors so the
   container/parts relationship is structural, e.g.:
   ```
   org.objectstyle.wolips.editor            (was: components.editor interfaces + componenteditor root)
   org.objectstyle.wolips.editor.component  (was: componenteditor.part — the container)
   org.objectstyle.wolips.editor.template   (was: templateeditor)
   org.objectstyle.wolips.editor.wod        (was: wodclipse.editor)
   org.objectstyle.wolips.editor.api        (was: apieditor.editor)
   org.objectstyle.wolips.editor.woo        (was: wooeditor)
   org.objectstyle.wolips.editor.actions    (was: componenteditor.actions)
   org.objectstyle.wolips.editor.inspector  (was: componenteditor.inspector)
   org.objectstyle.wolips.editor.menu       (was: componenteditor.editormenu)
   ```
   *Exact shape TBD — the point is one editor home with the container and its
   parts visibly nested, killing the `components` vs `componenteditor` collision.*

2. **One `preferences` home.** Collect the scattered preference pages under a
   single package (or a clear per-area convention), and pull `MavenPreferencePage`
   out of `wizards` into it. Resolves diagnoses #2 and the `MavenPreferencePage`
   half of #4.

3. **Relocate the obviously-misplaced utilities:**
   - `eomodeler.core.model` → next to its only consumer (`wodclipse.core.woo`) or
     a clearly-labeled legacy/util location.
   - `core.resources.types` → a name that says "type cache" rather than "core".

### Tier 2 — meaningful but more churn (discuss separately)

4. **Detangle / flatten `wodclipse.*`.** Either rename it to drop the legacy
   "wodclipse" name, or flatten the somewhat-arbitrary `core.*` split. This is the
   biggest single tree (106 classes) so it's the biggest diff and the most
   plugin.xml refs. Worth doing, but probably its own focused pass.

### Tier 3 — cosmetic

5. Collapse hollow single-class namespace roots where it reads better. Low value;
   only if it falls out naturally from Tier 1.

---

## Explicitly out of scope (for this round)

- **Renaming the root namespace** (`org.objectstyle.wolips.*` → `parsley.*` or
  similar). It's the honest long-term end state, but it's a massive, all-touching
  diff that deserves its own decision and its own day. The "wolips"/"wodclipse"
  heritage naming is noted but not addressed here.
- **Touching `tk.eclipse.plugin.*`** — load-bearing via inheritance; renaming means
  rewriting the editor hierarchy.
- **Isolating `jp.aonir.fuzzyxml.*` into a separate JAR** — possible (it's a clean
  library) but a packaging decision, not a within-bundle restructure.

---

## Suggested execution order (if we proceed)

Package moves are mechanical but touch many files, so do them **one coherent move
per commit**, build + test after each, so any breakage is bisectable:

1. `preferences` consolidation (+ `MavenPreferencePage` relocation) — smallest,
   self-contained, immediate clarity win.
2. The two misplaced utilities (`eomodeler`, `core.resources.types`).
3. The `editor` consolidation — the big readability win, but the most plugin.xml
   refs; do it as its own commit with careful verification.
4. (Later, separate) the `wodclipse` detangle.

Each step: move files → fix `package`/`import` → fix `plugin.xml` `class=` refs →
fix `.classpath` → `mvn verify` (559 tests) → commit. User re-tests in Eclipse.

---

## Open questions for discussion

- **Root namespace:** leave `org.objectstyle.wolips.*` as-is this round (recommended),
  or is biting the bullet on a `parsley.*` rename worth scheduling?
- **`editor` consolidation shape:** is the nested `editor.{component,template,wod,
  api,woo}` model the right mental picture, or do you think of the WOD/API/WOO
  editors differently?
- **How far on `wodclipse`:** rename it (drop the legacy name) or just flatten it,
  and is that this round or its own?
- **Appetite for churn:** Tier 1 only (safe, high-value), or push into Tier 2 now?

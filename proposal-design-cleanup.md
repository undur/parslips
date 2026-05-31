# Proposal: Design Cleanup (precedes package restructuring)

**Status:** APPROVED — executing. Decisions delegated to + owned by the specialist.

**Decisions taken (owned, not blank-check):**
- **Accessor layer: YES, incremental.** Typed accessors introduced one feature at
  a time, callers migrated, build + commit per feature. Every commit is an
  independent net improvement (never a half-done rewrite).
- **Depth: shallow-to-medium.** Accessors + co-locate each key's default with its
  accessor (this dissolves the fragmented-initializer problem). **SKIP** a shared
  base class for the hand-rolled table pages unless duplication actively hurts —
  no "preferences framework" gold-plating.
- **Editor: clarity only, NO re-architecture.** Remove dead
  `IEmbeddedEditorSelected`; document the API/WOO asymmetry. Leave `htmlActive`
  delegation, direct tab instantiation, single store — all pragmatic-correct.
- **`components` → `editor.framework`** (the rename target). Applied during the
  package run, not now.
- **Execution order:** (1) WOD-editor prefs accessor, (2) binding-validation prefs
  accessor, (3) editor clarity fixes, (4) reassess → package run.
- **Cadence:** commit locally + build-verify each step; batch the push at the
  Area-1 milestone for review/test before origin.

---

**(proposal:) discussion document.**
**Relationship:** This supersedes the *execution* of `proposal-package-restructure.md`.
That doc's diagnosis stands, but we discovered the structural confusions are mostly
**design smells wearing package costumes** — so clean the design first, and the
packaging falls out almost for free afterward.

**Guiding principle:** Packages are the *shadow* of the design. Moving packages
without fixing the design just rearranges the tangle into tidier boxes. Fix the
units first; then placement becomes obvious.

**Honesty principle for this doc:** not every "smell" is worth fixing. Some current
designs are pragmatic and correct for a fixed, known problem; "improving" them would
be abstraction for extensibility nobody needs, at real risk. This proposal explicitly
separates *worth fixing* from *leave it alone*.

---

## Area 1 — Preferences (HIGH value, well-bounded) ✅ recommended

### What we found (the good news first)

**It's already a single preference store.** Every Activator
(`bindings.Activator`, `wodclipse.core.Activator`, `ComponenteditorPlugin`,
`HTMLPlugin`) resolves to the **same** `ng.componenteditor` bundle preference
scope. So the scary "merged-from-18-plugins means N preference stores" problem
**does not exist** — they were unified when the bundle id was unified. Good.

### The actual design debt

1. **Raw-key access scattered across ~15 classes.** Business logic reads prefs
   directly as strings: `Activator.getDefault().getPreferenceStore().getString(
   PreferenceConstants.TAG_SHORTCUTS_KEY)`, severity keys read in
   `AbstractWodBinding`, formatting keys read in `WodScanner`, etc. No accessor
   layer, no type safety. Adding/renaming a key ripples across many files.

2. **Two `PreferenceConstants` + two `PreferenceInitializer`** (in
   `bindings.preferences` and `wodclipse.core.preferences`), plus a third
   initializer (`HTMLPreferenceInitializer`). Defaults are fragmented across three
   places; easy to add a key and forget its default. The two constant sets are
   conceptually distinct (binding-validation keys vs. WOD-editor color/format keys)
   and don't collide — but there's no naming convention tying them together.

3. **Two preference-page patterns with no shared base.** Simple pages use
   `FieldEditorPreferencePage` (clean, auto-bound); complex ones
   (`TagShortcutPreferencePage`, `BindingValidationRulePreferencePage`) hand-roll
   table + load/save/sync logic, duplicated between them.

4. **`DevServerPreferences` is the best-of-breed example** already in the tree:
   fully-qualified key names + co-located defaults (`DEFAULT_PORT`,
   `DEFAULT_ENABLED`). It still does raw-key reads, but its *naming discipline* is
   the convention the others should follow.

### Proposed design direction (not final shape — for discussion)

- **A typed accessor layer.** Instead of business logic reading raw keys, expose
  intent-named methods: `WodEditorPreferences.indentSize()`,
  `BindingValidationPreferences.severityFor(problemKind)`, etc. Readers stop
  knowing key strings. This is the single highest-leverage change — it decouples
  every consumer from the storage detail, and *then* moving/renaming keys is free.
- **Standardize on the `DevServerPreferences` convention:** fully-qualified keys +
  defaults co-located with their keys, and (new) accessors alongside.
- **Consolidate the three initializers** into one (or have each accessor class own
  its own defaults, so "the key, its default, and its accessor" live together —
  arguably better than a central initializer).
- **Optionally** a small shared base for the hand-rolled table pages — only if the
  duplication actually bites; not load-bearing.

Once this is done, the *package* question ("where do preference classes live")
mostly answers itself: each feature's preferences (accessor + keys + defaults +
page) form a cohesive unit that can sit with its feature **or** in one preferences
package — and either way there's no "page reaches back across a boundary for its
constants" smell, because the page and its accessor move together.

**Risk:** Low–medium. Accessor introduction is mechanical (wrap existing reads).
Each accessor can be introduced + callers migrated in its own commit, build-verified.

---

## Area 2 — The editor framework (MIXED — fix naming/clarity, leave architecture)

### What we found

The container/parts design is **mostly sound**, with good and questionable parts:

**Genuinely good (leave alone):**
- The part-editors are **decoupled from each other** — zero direct cross-imports.
  HTML↔WOD communicate only through `ComponentEditorInteraction` (a mediator/event
  bus) + document-provider interfaces. That's a clean design.
- The container hosts a **fixed, known set of 4 tab types**. It instantiates them
  directly (`new TemplateEditor()`, etc.).

**Questionable but pragmatic (recommend: LEAVE — do not "fix"):**
- *"No factory / plugin registry for editor creation."* For a fixed set of 4 known
  editors, direct instantiation is **correct**. A factory/registry would be
  speculative extensibility for a plugin point that will never have third-party
  contributors. **Not worth the risk.**
- *The `htmlActive` boolean + active-editor delegation in `HtmlWodTab`/
  `ComponentEditor`.* It's a touch fragile (focus-driven flag is the delegation
  source of truth), but it **works**, we just worked in it successfully (the
  HTML-first default), and rewriting `ComponentEditor`'s `ITextEditor` delegation
  is high-risk for low gain. **Leave it.**

**Worth fixing (clarity, low risk):**
- **The `components` package is a misnomer.** It holds editor *contracts*
  (`IEmbeddedEditor`, `IComponentEditor`, `IHtmlDocumentProvider`, …) + editor
  *input* classes — not "components." Worse, "components" collides with the domain
  concept (WOComponents). It reads as if it's about the things being edited, when
  it's the editor framework. Renaming to something like `editor.framework` /
  `editor.contracts` (in the eventual package run) removes a real conceptual
  trip-hazard. **This is a naming fix, and it's exactly the kind of thing that
  should be decided at the design level before the package move.**
- **`IEmbeddedEditorSelected` is dead** — defined, `instanceof`-checked in
  `ComponentEditorTab`, but never implemented. Either wire it or remove it
  (lean: remove — it's incomplete-refactoring residue). Small, safe.
- **The API/WOO asymmetry is probably correct, just undocumented.** API/WOO editors
  deliberately sit outside the HTML↔WOD interaction bus (they don't share the
  template/wod document model). That's fine — but nothing says so, so it reads as
  "incomplete." A class-doc note ("API/WOO are standalone tabs; only HTML/WOD
  participate in ComponentEditorInteraction") converts a perceived smell into a
  documented decision. Cheap.

### Proposed design direction

- **Don't re-architect the editor.** No factory, no delegation rewrite.
- **Do** the clarity fixes: rename the `components` concept to an editor-framework
  name, remove dead `IEmbeddedEditorSelected`, document the API/WOO asymmetry.
- These are small and mostly *enable* the later package consolidation (the
  `editor.*` grouping) to land cleanly with honest names.

**Risk:** Low. Naming + dead-interface removal + a doc comment.

---

## Area 3 — Cross-cutting infrastructure (LOW, opportunistic)

- **Activators / hollow namespace roots.** Several packages exist only to hold an
  Activator. They resolve to one bundle store anyway (see Area 1). Worth a look at
  whether the multiple Activators can collapse, but **only** if it's clean — this
  is incidental, not a goal.
- **`WOLipsBindingShadow`** straddles `componenteditor.preferences` and is also used
  by `HTMLPlugin`. Note its layer when we touch preferences; not urgent.

---

## Recommended order of work

1. **Preferences design cleanup (Area 1).** The real prize. Introduce typed
   accessors feature-by-feature, migrate callers, consolidate defaults. Each
   accessor its own commit + build. This *dissolves* the preferences package
   tangle that stalled the package run.
2. **Editor clarity fixes (Area 2).** Remove dead `IEmbeddedEditorSelected`,
   document the API/WOO asymmetry, settle the `components`→editor-framework naming
   *decision* (apply the rename during the package run, not before).
3. **THEN the package restructure** (`proposal-package-restructure.md` Tier 1) —
   now the moves are clean: cohesive preference units, honestly-named editor
   framework, no reach-across-boundary coupling. Plus the always-safe trivial
   relocations (`MavenPreferencePage`, `eomodeler`, `core.resources.types`).

## What we are explicitly NOT doing

- No editor re-architecture (factory, delegation rewrite, `htmlActive` removal).
- No root-namespace rename (`wolips.*` → `parsley.*`).
- No touching the Amateras `tk.eclipse.plugin.*` inheritance layer.
- No splitting the preference store (it's already one; keep it).

## Open questions

- **Accessor layer appetite:** introduce typed preference accessors (real but
  bounded work across ~15 call sites), or is that more than you want — would you
  rather just consolidate constants/initializers and standardize naming, leaving
  raw-key reads in place?
- **`components` rename target:** `editor.framework`? `editor.contracts`? something
  else? (Decision now; apply during package run.)
- **Scope of round one:** Area 1 only, or Area 1 + the cheap Area 2 clarity fixes
  together?

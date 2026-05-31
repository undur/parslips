# Proposal: Project-wide revalidation (kill stale markers)

**Status:** Proposal — discussion document. Not executed.
**Origin:** A user fixed a validation error, but the marker lingered in the
Problems view long after the fix. Pulling the thread revealed that *all* Parsley
validation is editor-bounded: a marker only gets re-derived when its file is open
in an editor. Close the file and the marker freezes in whatever state it was last
left in — including "already solved." This proposes the missing project-wide
revalidation path that makes stale markers go away on demand.

## What the investigation found (the honest picture)

**Parsley registers no builders.** There is no `org.eclipse.core.resources.builders`
extension in `plugin.xml` at all. Two builder-*named* classes exist in the source
but neither is wired in:

- `tk.eclipse.plugin.htmleditor.HTMLProjectBuilder` — a genuine
  `IncrementalProjectBuilder` subclass, but unregistered → never runs. (Amateras
  legacy; a zombie.)
- `org.objectstyle.wolips.wodclipse.core.builder.WodBuilder` — **despite the name
  and package, this is not a builder.** The current class is a *static validation
  utility* (its own javadoc says so). It has no `build()` method and does not extend
  any builder base class. The name is a fossil — and we can now prove it.

So there is **no dormant build pass to switch on.** That was my first hypothesis and
it's wrong — worth stating plainly so we don't go looking for a flag that doesn't
exist.

### What the original WOLips confirms (`/Users/hugi/git/wolips-original`)

In the original WOLips, **`WodBuilder` was a real, registered, full+incremental
builder** — the name is a genuine fossil, not an arbitrary misnomer:

- It `extends AbstractFullAndIncrementalBuilder` (a WOLips builder framework class in
  `org.objectstyle.wolips`).
- It was registered via the WOLips-custom extension point
  `org.objectstyle.wolips.builders` (a `<builder>` element in
  `org.objectstyle.wolips.wodclipse.core/plugin.xml`), **not** the raw
  `org.eclipse.core.resources.builders`.
- It was attached to projects through the WOLips project nature
  (`o.o.wolips.core.resources.internal.build.Nature` / `ProjectAdapter`).
- Its build callback did two things per changed resource: **delete the existing
  problems** for the component's `.html`/`.wod`/`.woo` (via
  `WodModelUtils.deleteProblems`), then **re-validate** (`validateComponent`).

That `deleteProblems`-then-revalidate step is **exactly the self-cleaning behaviour
we are missing today** — and it's exactly what got dropped during extraction. When
the plugin was carved out of WOLips, the builder *half* of `WodBuilder` (the
`extends`, the `build()` callback, the registration, the marker-sweep) was stripped,
leaving only the static `validateComponent`/`_validateComponent` leaf logic — and the
editor + resource-listener callers were wired to drive that leaf directly instead.
Our version's javadoc honestly calls it "a static utility" because that *is* all that
survived.

**Two consequences for this proposal:**

1. The original's builder framework (`AbstractFullAndIncrementalBuilder`, the custom
   `org.objectstyle.wolips.builders` extension point, the project nature) was **not**
   carried into this plugin. So Option 2 ("register a real builder") is more than
   flipping a registration on — we'd reconstruct against stock Eclipse
   `org.eclipse.core.resources.builders` + a project nature, rather than revive the
   WOLips framework.
2. But we are **not inventing** — the original `WodBuilder` +
   `AbstractFullAndIncrementalBuilder` is a known-good reference implementation of
   project-wide build-time validation *with* marker sweeping. Both options below can
   lift their core logic (especially the delete-then-revalidate sweep) directly from
   it.

**Validation is editor-driven, everywhere.** Every path that produces our
template/binding markers is ultimately reached from an open editor or a
resource-change listener that only looks at open editors:

- `TemplateSourceEditor.doValidate()` → `cache.validate(true, false)` — runs when a
  component editor reconciles.
- `JavaChangeRevalidator.revalidateOpenComponents()` — fires when Java/API files
  change, but iterates **`page.getEditorReferences()`** only. A component that was
  fixed and then *closed* is never revalidated, even when a dependency it relies on
  changes. This is the precise mechanism behind "old, already-solved errors hanging
  around."
- The Rhino `JavaScriptValidator` — same shape, but worse: its markers are
  builder-less `IMarker.PROBLEM`s, so even a Project → Clean can't sweep them. (Out
  of scope here — see below.)

**The validation engine already self-heals.** This is the good news that makes a fix
cheap. `WodBuilder._validateComponent(resource, …)` does:

```java
WodParserCache cache = WodParserCache.parser(resource);
cache.clearParserCache();
cache.parse();
cache.validate(true, false);   // re-derives markers: fixed errors vanish, current ones reappear
```

`validate()` refreshes the component's markers (via `HtmlCacheEntry` →
`problem.createMarker`). So **revalidating a component is idempotent and
self-correcting** — exactly the property we want. It's also already thread-pooled
(`validationThreadPool`) with a progress job (`ValidationProgressJob`).

**What's actually missing** is therefore small and well-bounded:

1. A way to **enumerate a project's components** (open or closed), and
2. A **trigger** to run `validateComponent` across them.

The enumeration mechanism also already exists: `ComponentLocateScope` +
`Locate`/`ILocateResult` is the same machinery "Open Component" and the NG Explorer
use to find components in a project (with an option to include dependency
projects). We reuse it rather than hand-rolling a resource walk.

## Out of scope (deliberately)

- **The Rhino JavaScript validator.** Its stale markers are a *different* defect
  (builder-less markers, ES3-era engine, false positives on modern/minified JS). It
  should be retired, not revalidated. Tracked separately; this proposal does not
  touch it.
- **Changing the default validation model.** We are not (here) proposing to make
  validation automatic on every build. See option 2 for why that's a separate,
  larger decision.
- **`HTMLProjectBuilder` removal.** Dead zombie; its cleanup belongs with the
  broader Amateras triage, not here.

## Proposed design

The engine is done. Two options for the trigger, smallest first.

### Option 1 (recommended): an explicit "Revalidate" command

A menu action — project context menu in the NG Explorer (and/or a command in the
Parsley menu) — that:

1. Builds a `ComponentLocateScope` for the selected project (optionally including
   dependency projects).
2. Runs `Locate` to collect every component resource in scope.
3. For each, **deletes the component's existing problems then revalidates** —
   `WodBuilder.validateComponent(resource, /*threaded*/ true, monitor)`, reusing the
   existing thread pool and progress job. The explicit delete-first step is lifted
   from the original builder's callback (`WodModelUtils.deleteProblems` →
   `validateComponent`); it guarantees stale markers are swept even for components
   whose validation now produces *nothing* (the pure "already-solved error lingers"
   case — re-validation alone wouldn't remove a marker if the validator no longer
   visits that file).

Result: every component in the project is re-parsed and re-validated, open or
closed; markers for fixed errors disappear, current ones reappear. Wrapped in a
`WorkspaceJob` so it reports progress and doesn't block the UI.

**Why this first:**
- Reuses the existing engine almost entirely — the new code is enumeration + a
  command handler + a menu contribution. No change to the validation logic itself.
- **Opt-in and low-risk:** nothing happens unless the user invokes it. No change to
  build-cycle behaviour, no surprise CPU on large workspaces.
- It directly kills the reported problem ("old solved errors hanging around") with a
  "sweep now" button.
- It *proves* the enumeration and the self-heal behaviour in practice before we
  consider committing to automatic validation.

**Cost / caveats:**
- Revalidating a large project is O(components) parses — fine on demand, which is
  why it's a command and not automatic.
- Needs a sensible default for "include dependency projects?" (lean: yes, matching
  `JavaChangeRevalidator`'s API-change behaviour, since a binding error can come from
  a dependency).

### Option 2 (future): a real incremental builder

Register an actual `IncrementalProjectBuilder` (a real one — not the misnamed
`WodBuilder`) that revalidates changed components at build time, and all components
on a full build. This is the "proper" Eclipse answer: markers become
self-maintaining, no manual sweep needed.

**Why not now:**
- It's a genuine architectural commitment — it changes the build cycle, needs care
  around performance (incremental delta handling, not re-validating the world on
  every keystroke-triggered autobuild), and the "no builders today" baseline is at
  least partly deliberate (editor-driven validation has kept the plugin light).
- It deserves its own proposal once Option 1 has shown the enumeration + self-heal
  path is solid in real use.
- Option 1 is a strict prerequisite of confidence for Option 2, and is independently
  useful even if we never do Option 2.

## Testing

- Unit: enumeration over a fixture project returns the expected component set
  (standalone `.html`, `.wo` bundles, with and without dependency projects).
- Behavioural (manual, since markers are workspace state):
  1. Open a component, introduce a binding error, confirm the marker appears.
  2. Fix it in the editor, confirm the marker clears (existing behaviour).
  3. Re-introduce the error, **close** the editor (marker persists — the bug).
  4. Fix the underlying cause elsewhere (e.g. add the missing key to the Java class),
     run **Revalidate** → confirm the now-stale marker disappears.
  5. Run Revalidate on a clean project → confirm no spurious markers, progress
     reports sanely.

## Suggested execution

1. Add the enumeration helper (thin wrapper over `ComponentLocateScope` + `Locate`
   that returns the component `IResource`s for a project).
2. Add the command handler + `WorkspaceJob` that walks the enumeration and calls
   `WodBuilder.validateComponent`.
3. Register the command + NG Explorer context-menu contribution in `plugin.xml`.
4. Tests + a CHANGES.md entry.

Each step builds and is independently reviewable; nothing changes existing
validation behaviour.

## Open questions

- **Naming.** "Revalidate" vs. "Revalidate Components" vs. "Validate Project." Lean
  "Revalidate" (it *re*-derives; "Validate" implies first-time).
- **Scope default.** Include dependency projects by default? (Lean yes.)
- **Reach.** Project-only for v1, or also offer "revalidate workspace"? (Lean
  project-only; workspace is a trivial extension later.)
- **Does this make the `WodBuilder` rename worth doing?** We now know the name is a
  true fossil — the class *was* a builder in WOLips and the builder half was stripped
  in extraction, leaving a static validator carrying the old name. If we add a *real*
  builder later (Option 2) the collision gets actively confusing (a `WodBuilder`
  that isn't the builder, beside the new one that is). Possibly rename `WodBuilder` →
  `ComponentValidator` (or similar), freeing the `…Builder` name for a future real
  builder — or defer. (Lean defer — it's a 9-importer rename, separable; but it's
  more justified than I first thought.)

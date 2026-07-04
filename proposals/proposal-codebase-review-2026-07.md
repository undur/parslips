# Codebase Review & State of the Project — July 2026

**Status:** 🔶 FINDINGS DOCUMENT (triaged) — a full design + correctness review of
`ng.componenteditor` (Parslips), run as a multi-agent audit (8 subsystem reviewers +
adversarial verification + architecture & docs-drift surveys). Every `bug`/`perf`/`threading`
finding was independently verified by a second agent trying to *refute* it; 2 of 61 were
knocked out. The **Decisions** section below is the opinionated cut — what earns work vs. what
is acknowledged-and-closed; the appendix is the full ledger. Nothing is fixed yet — this is
the map and the plan.
**Origin:** taking stock after a burst of shipping (declarative tag aliases, Element
Reference, dev server, exception-page navigation, keypath navigation, the beachball fix).

---

## The headline

The codebase is in **notably better shape than "extracted legacy WOLips" would predict** —
the recent rewrites (immutable `ApiSnapshot` model, Eclipse-free FuzzyXML, the dev server,
the Element Reference, keypath navigation) are genuinely well-designed and well-commented.
The debt is concentrated, not diffuse, and it clusters in three places:

1. **The lifecycle layer is still "18 plugins in a trenchcoat"** — 13 activator-style
   classes, only one of which OSGi actually runs; the other 12 are lazily self-constructed
   singletons whose `start()`/`stop()` are dead code, registering leaked listeners as side
   effects. Three classes each declare `PLUGIN_ID = "ng.componenteditor"`; 3+ preference
   stores sit over one node.
2. **Resource-change listening is uncoordinated** — 13 independent `addResourceChangeListener`
   sites, several walking the full workspace delta with no `isDerived()` prune on every
   change. This is the same class of problem as the beachball we just fixed.
3. **The tooling/LSP extraction is aspirational, not real** — `parslips.tooling` is one
   `package-info.java`; `parslips.lsp` requires it in the manifest but imports it nowhere.

And a **stale-doc correction worth flagging loudly**: the "WOD validation thread safety /
synchronize on `this.apiModel`" rule in CLAUDE.md **no longer applies** — the shared DOM
singleton was deleted in `455a5e8d7` (March 2026). The verifier caught this refuting a
threading finding. Our own guidance is out of date.

---

## Decisions — the triage cut

The appendix has 94 verified/inventoried findings. "Confirmed" means *survived adversarial
refutation*, **not** *worth fixing* — several confirmed items are in code slated for deletion,
or were flagged by the verifier as "no measurable effect" or "documented trade-off". This
section is the ruthless cut: what actually earns work. Everything not listed here is
**acknowledged and closed** — real, recorded in the appendix, not scheduled.

### Do now — small, high-value, mostly in code we just shipped

| ID | Fix | Why now |
|---|---|---|
| **A11 + A12** | Dev server: reject non-empty `Origin`/`Referer`; validate `pid` is a known-registered app before `kill`. | The only externally-shaped risk — a web page the user visits can launch/stop apps and kill arbitrary PIDs via `localhost:9485`. |
| **A8** | `ElementCatalog.shortcutsByElement`: skip replacement entries in the alias branch (honor its own comment). | `WOString` wrongly shows as a *tag* on `ERXWOString` in the Element Reference — visible wrong data in a shipped feature. |
| **A7** | `resolveForBindings`: probe project/jar `.api`/`.apiext`, not just global. | A replacement element that ships its own `.api` gets the wrong bindings in completion/validation. Latent now, will bite the moment wonder-slim ships one. |
| **A28 + A41** | Keypath nav: fix the F3-vs-click segment disagreement at dot boundaries, and the double-shift on namespaced values (`$ognl:foo.bar`). | Bugs in the feature we shipped *this morning*; small and self-contained. |
| **A9** | `forceValid`: gate on the full `// VALID` marker, not any keypath ending in `VALID`. | One-line correctness fix; silent mis-suppression of validation. |

### Do next — the perf-listener family (one coordinated fix)

The beachball we already fixed was one instance. **A44, A46, A49, A45** (and the design root
**B6/B23**) are the rest: always-on resource listeners walking the full delta with no
`isDerived()` prune. Fix them together — ideally as the **single dispatcher** the architecture
survey recommends (walk the delta once, prune derived, fan out) rather than patching four
visitors. Rolls up A47 (disk read per `getAdapter`) and A48 (per-keystroke UI-thread Locate)
as part of the same "stop doing expensive work on hot paths" pass.

### Fold into the zombie deletion (already planned, sweeps these for free)

Executing the tag-registry removal (inventory in **B20**) plus the dead-code sweep (**B21,
B22, B24, B25, B29, B30, B31, B33, B36**) auto-resolves several "bugs" that live *only* in
about-to-be-deleted code: **A30** (`fromPreferenceString` AIOOBE — zombie), **A6** /
`ContentDescriberWO` (wiring-audit 4.1). Don't fix these in place; delete them. **B19/B9**
(dead activator lifecycle + `LocatePlugin` double-registration) fold into the "collapse the
activator zoo" architecture move.

### Fold into planned architecture work

- **B10, B11** (FuzzyXML extraction blockers) → the "move FuzzyXML into `parslips.tooling`" step.
- **B3, B4** (zero tests on `ParsleyTagAliasResolver`, `BindingValueKeyPath`) → the test-backfill step; **B15** (a `segmentIndexAt` javadoc/impl mismatch) rides along since we own that code.
- **B16, B18, B27** (stale comments/docs incl. the dead apiModel-sync rule) → the CLAUDE.md/docs backfill.

### Acknowledged & closed — real, but not worth scheduling

The remaining ~40 (the FuzzyXML formatter/parser edge bugs A17–A25 among them, the tab-index
and blind-cast robustness nits A13–A16/A31–A39, the threading latency-windows A1–A5, the
micro-perf A42/A43/A50–A56, C1/C2 refuted) stay in the appendix as a searchable ledger. Two
judgment calls worth stating out loud:

- **The FuzzyXML formatter bugs (A19–A25) are genuinely real** (CDATA deletion, entity
  corruption, `<pre>` crash) — but they sit in the code we most want to *extract and harden into
  `parslips.tooling` with a real test suite*. Fixing them ad-hoc now is churn against code
  that's about to move. **Decision: fix them as part of the extraction, test-first, not before.**
  (Exception: **A23**, the `getLength()` infinite recursion / StackOverflow, is a one-line
  guard — fix opportunistically if that path is ever hit in practice; no evidence it is today.)
- **The threading findings A1–A5 are confirmed but low-severity** — latency windows and lost-
  update races on caches that tolerate it (modern `HashMap`, benign recompute), not corruption.
  **Decision: fold the cache ones into the dispatcher/consolidation work** (A1's lock-during-IO
  is the one with real UI-stall potential, so it goes with the perf family); the rest ride the
  same rewrite rather than getting bespoke synchronization.

**Net:** ~5 do-now items, one coordinated perf pass, and the rest absorbed by work already on
the roadmap (zombie deletion, FuzzyXML extraction, test/docs backfill). The 94-item ledger
collapses to roughly **three scheduled efforts plus five quick fixes**.

---

## Confirmed findings — by severity

61 findings verified, 59 confirmed, 2 refuted. Full detail per finding lives in the raw
audit; this is the ranked actionable set.

### Threading (5 confirmed) — real, but mostly latency-window races, not corruption

| # | Where | Issue |
|---|---|---|
| T1 | `ParsleyTagAliasResolver.java:151` | `aliasMap()` holds the global `_cache` lock **across classpath resolution + JAR/workspace I/O**; called from UI *and* background validation threads. Also leaks closed-project keys. |
| T2 | `WodParserCache.java:110` | `invalidateResource()` mutates the access-ordered LRU (`accessOrder=true` → even `get()` is structural) **with no lock**, from the resource-change thread, while `parser()` guards with `static synchronized`. |
| T3 | `ElementHelpView.java:303` | The new background-Job `_reloadTarget` stale-guard covers **project switches but not same-project generations** — two reloads for the same project can land out of order. |
| T4 | `ComponentLocateCache` | Cache `HashMap` mutated + read from multiple threads, unsynchronized. |
| T5 | adapter factory `_cache` | Same pattern — UI + background access to a plain `HashMap`. |

*None causes data corruption today (the maps tolerate it, or the window is tiny), but T1
can stall the UI behind background JAR I/O and T2/T4/T5 are `ConcurrentModification` waiting
to happen.*

### Bugs (high-confidence, correctness) — the ones worth fixing soon

**In code we just shipped:**
- **`WodHtmlUtils` / keypath nav** — F3 keypath-segment mapping is *shifted right by the
  inline-binding prefix* in one path, disagreeing with Cmd+click **at dot boundaries**.
  (We fixed the underline offset; the verifier found a residual off-by-prefix in the F3 vs
  click *segment* mapping at exact-dot positions.) Also: namespaced inline values
  (`$ognl:foo.bar`) get a mis-based `valuePosition` that the provider then double-shifts.
- `ElementCatalog` — `shortcutsByElement` alias branch **doesn't skip replacement entries**,
  contradicting its own comment: `WOString` shows as a *tag* on `ERXWOString`.
- `resolveForBindings` **ignores project/jar `.api`/`.apiext`** — only consults global
  definitions, so binding validation misses locally-defined element APIs.
- `forceValid` triggers on **any keypath ending in `VALID`**, not just the `// VALID`
  suppression comment.

**Legacy, but live and user-reachable:**
- `WodParserCache.parser(IProject,String)` NPEs when the component name resolves to no files.
- `WodEditor.doSave` NPEs when opened standalone (no `ComponentEditorInteraction`).
- `ComponentEditor.init` blind-casts non-`ComponentEditorInput` inputs → CCE.
- `validate(false,…)` on an already-validated cache leaves `_validating` **stuck true**,
  permanently disabling non-forced validation for that cache.
- Auto-rename guard tests a **workspace `IPath` with `java.io.File`** — the existence check
  never fires → `CoreException` spam or unwanted renames.
- Explorer: PRE_CLOSE cache purge is **dead code** (PRE_CLOSE carries no delta) → locate
  cache never invalidated on project close/delete.

**FuzzyXML (the parser/formatter — empirically reproduced against compiled classes):**
- `escapeString` **drops characters** for consecutive/trailing backslashes → corrupts *all*
  downstream offsets.
- Unclosed `<pre>` **crashes the parser** (`StringIndexOutOfBounds`).
- Formatter **silently deletes CDATA sections**; **rewrites attribute entities**
  (`&quot;`→`\"`, `&amp;`→bare `&`); decodes entities inside `<script>`.
- `FuzzyXMLText.getValue()` **double-decodes** entities; attribute double-decode in
  well-formed mode.
- `FuzzyXMLFormatComposite.getLength()` is **infinite recursion** (StackOverflow).
- `getValue(RenderContext, StringBuffer)` copies the whole buffer per element → **O(n²)**.

### Performance (perf/high) — the "small hang on everything" family

The beachball we fixed was one instance of a *pattern*. Still present:
- **`WooeditorPlugin.resourceChanged`** — walks the whole delta on every workspace change,
  always schedules an asyncExec, encoding-only interest but no early prune. *(flagged twice)*
- **`JavaChangeRevalidator`** — full delta walk, **no `isDerived()` prune**, every POST_CHANGE.
- **`ElementHelpView` alias listener** — full catalog rebuild on any delta node named
  `parsley-tag-aliases.properties`, ignoring kind/flags (fires on derived build copies).
- **Explorer delta visitor** — full POST_CHANGE walk, no derived prune, *per open explorer*.
- **HTMLEditor fork** — full FuzzyXML reparse of the whole template **on the UI thread for
  every hyperlink query**; content-assist reparses + multiple full-string copies per keystroke;
  damager-repairers copy the whole document per keystroke.
- **Explorer/wizard** — `getAdapter(ParsleyProject)` reads `build.properties` **from disk on
  every call, even cache hits**; `validatePage` runs a full-tree Locate **on the UI thread per
  keystroke** and pollutes the locate cache with partial-name entries.
- Every `.java` save nukes the per-project element-type cache → full JDT search next completion.

### Dev server — security posture (the one area with a genuine exposure)

Structurally clean, but the loopback-only trust boundary is **not sufficient against
browser-originated requests**:
- **No CSRF/Origin defense** — any web page the user visits can drive the dev server via
  simple `GET`s to `localhost:9485` (open editors, **launch and stop apps**).
- **Arbitrary process kill** — an unvalidated `pid` flows into `kill -9`, reachable via that
  same browser-CSRF vector.
- Unbounded cached thread pool feeding blocking handlers → locally DoS-able.

*This is the finding I'd action first — it's the only one with an external-attacker shape,
even if the attacker is "a malicious web page open in the user's browser."*

---

## Dead code & the zombie-deletion inventory

The audit produced the **complete mechanical deletion list** for the legacy tag-shortcut
registry (now safe — the declarative alias path has soaked):

- **Delete outright:** `TagShortcut.java` (166 lines), `TagShortcutPreferencePage.java`
  (~290, incl. its `plugin.xml:719-723` registration + `TagShortcutTest.java`),
  `PreferenceConstants.TAG_SHORTCUTS_KEY`, `PreferenceInitializer.java:60-130` (the 69
  defaults + import), `ApiCache` `_tagShortcuts`/`getTagShortcutNamed`/`getTagShortcuts`,
  `WodParserCache.getTagShortcutNamed` (already zero callers).
- **Simplify** the `if(aliasesActive){new}else{legacy}` splits (`FuzzyXMLWodElement:36-72`,
  `ElementCatalog`, `TemplateAssistProcessor`).

Plus **12 whole classes (~641 lines) with zero references** anywhere (verified by grep
across java/test/plugin.xml/manifest): `NGComponentEditorPlugin`, `AddActionAction`,
`AddKeyAction`, `CleanWOBuilderElementNamesAction`, `DeleteComponentRefactoring`,
`DeletePage`, `BindingNamespaceRule`, `FieldAssistUtils`, `DefaultLocateResult`,
`ElementFilter`, `EOModelException`, `ITemplateValidationMarkerCreator`.

And whole **dead subsystems** in the Amateras fork: the task-tag nature/builder (never
registered), the palette (`PaletteView` = 795 lines, 773 commented-out, wrapping a no-op),
7 declared extension points with zero extensions, and — pending a load audit — several of the
11 vendored JARs (Rhino `js.jar`, Tidy, dtdparser, jrcs.diff, trang).

---

## Architecture & the extraction plan

**What's genuinely good (protect it):** the immutable `ApiSnapshot` model (zero Eclipse
imports, already extraction-grade); FuzzyXML (~95% Eclipse-free, best-tested code in the
repo); the LSP transport design (real JSON-RPC over piped streams — promoting to a standalone
process is mechanical); the dev server's clean handler-per-endpoint shape; the single-feature
p2 story.

**The extraction is aspirational.** `parslips.tooling` = one `package-info.java`.
`parslips.lsp` requires it in the manifest but **imports it nowhere** (the dependency is
comments). `ng.componenteditor` doesn't require it at all. So *nothing moved into tooling is
consumed by either side* until manifests + call sites change. The "everything extracted is
automatically available to both" story in our docs describes the *end state*, not today.

**The recommended first moves** (from the architecture survey, in order):
1. **Move `jp.aonir.fuzzyxml` into `parslips.tooling` now** — it's 95% ready; the only
   tethers are `WodHtmlUtils.isWOTag` (a pure string predicate to relocate) and one
   `HTMLPlugin.logException` call (make it a pluggable logger). Add `parslips.tooling` to
   `ng.componenteditor`'s Require-Bundle. **This single move makes the three-bundle
   architecture real instead of fiction.**
2. **Extract `ApiSnapshot`+`ApiParser`+`SimpleApiBinding` + an `ElementCatalog` interface**,
   then delete the hardcoded 34-element `KNOWN_ELEMENTS` list in the LSP and serve completions
   from the shared catalog — first user-visible LSP payoff, kills a drift source.
3. **Collapse the activator zoo** to one real activator (rename `HTMLPlugin` → `ParslipsPlugin`?),
   convert the other 12 to plain static services, delete dead `start()`/`stop()` (incl. the
   `LocatePlugin` double-registration path), unify on one preference store.
4. **One resource-change dispatcher** — walk the delta once with `isDerived()` pruning, fan
   out to typed subscribers; retire the 13 independent listeners (fix `PreCloseVisitor` and
   `WooeditorPlugin` first).
5. **A mechanical layering test** in the existing surefire run: nothing under the core
   namespace may import `tk.*` / `ng.componenteditor.*` / `org.eclipse.ui|jface` — cheap
   grep test that stops new upward deps while old ones unwind.

---

## Test health

592 `@Test` across 26 classes — the **pure-logic leaves are well covered** (FuzzyXML
parser+formatter, `.api` parse/serialize/validate, refactoring transformers, quickfix, the
new keypath math). The gap is **systematic**: everything Eclipse-coupled in the middle is
untested. Highest-value missing coverage:
1. `BindingValueKeyPath` (438 lines of keypath math, zero tests).
2. `ParsleyTagAliasResolver` — the **shipped** alias mechanism has **zero tests**, while the
   legacy registry it replaces ironically *has* a test class.
3. `AbstractWodElement.fillInProblems` (validation orchestration) + `FuzzyXMLWodElement`
   (tag→element conversion).
Plus the FuzzyXML formatter bugs above are all **missing-test escapes** — the suite is real
but doesn't hit entity/CDATA/`<pre>`/backslash edges.

---

## Docs drift

Proposal statuses are **accurate** (the index is trustworthy). The drift is elsewhere:
- **CLAUDE.md** — the "synchronize on `this.apiModel`" section is **stale** (DOM deleted
  March). Rewrite to describe the immutable-snapshot design.
- **CHANGES.md** — missing everything after the June-26 tag-registry entry: keypath nav,
  WOD-pane collapse, the beachball fix, alias live-reload, the Zombies move, and several
  smaller commits. Backfill.
- **ROADMAP.md** — the tag-shortcut items and "Rich component API model" section were
  invalidated by shipped work; rewrite + re-sync `roadmap.html`.
- **Pending commits** — the `.apilib` strawman, the tag-library deep-dive, and the two
  `README.md`/`proposal-tag-library-format.md` edits are **untracked**; commit as one unit.

---

## Refuted (verification did its job)

1. *"forProject re-does JAR I/O per element via a fresh TypeCache"* — the per-element
   `new TypeCache()` is real, but `ApiCache` is keyed such that the perf consequence doesn't
   hold as stated. Downgraded.
2. *"Template validation on a request thread → unsynchronized shared-DOM access"* — **the
   premise is obsolete**: the DOM `ApiModel` was deleted in `455a5e8d7`; `MutableApiModel`'s
   own javadoc says "there is no shared mutable DOM tree to synchronize on." This is the
   finding that flagged the stale CLAUDE.md rule.

---

## Suggested sequencing

1. **Dev-server CSRF/Origin + pid validation** — only externally-shaped risk.
2. **The shipped-code bugs** — keypath F3 off-by-prefix, `resolveForBindings` local `.api`,
   the `WOString`-as-tag alias bug (these are in *current* features).
3. **The perf listener family** — `WooeditorPlugin`, `JavaChangeRevalidator`, explorer
   visitor: add `isDerived()` pruning (same fix shape as the beachball).
4. **Zombie deletion** — mechanical, inventoried above, low risk.
5. **Extraction move #1** (FuzzyXML → tooling) — makes the architecture real.
6. **Docs backfill** — CLAUDE.md sync, CHANGES.md, ROADMAP, commit pending proposals.
7. **Test backfill** — `ParsleyTagAliasResolver`, `BindingValueKeyPath`, FuzzyXML edges.

---

## Appendix — complete findings ledger

Every finding from the audit, as a **self-contained block** — location, the failure scenario, verbatim code evidence, a suggested fix, and (for verified items) the adversarial verifier's correction. The intent is that any single item can be picked up and acted on without re-reading the code first. Line numbers are best-effort at audit time and may have drifted. Findings the same issue was reported from two review angles are merged with both areas cited (*also seen in:*).

### A. Confirmed findings (56 after merging) — adversarially verified

#### A1. aliasMap holds the global _cache lock across classpath resolution and jar/workspace I/O; cache also leaks closed-project keys

**threading** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ParsleyTagAliasResolver.java`:151

aliasMap does the load() inside synchronized(_cache): load() calls project.getResolvedClasspath(true), stats/opens jars (JarFile), and reads workspace IFiles. isActiveFor/resolve/resolveChain are called on hot paths (every FuzzyXMLWodElement construction, completion, hover, ElementCatalog) from both UI and background validation threads — after any clearCache() (fired workspace-wide by WodParserCacheInvalidator on ANY file named parsley-tag-aliases.properties, including derived target/classes copies on every build), the first resolver call per project re-does the I/O while ALL other threads' alias lookups for ALL projects block on the same lock. The lifecycle itself is consistent (two clearCache callers — WodParserCacheInvalidator.visit line 46 and ElementHelpView's _aliasFileListener — both just wipe the map; maps are immutable once published, so mixed old/new maps mid-resolution are benign). Separately, _cache is keyed by IJavaProject and entries are never removed on project close/delete — only wholesale clears — a small retained-handle leak.

**Evidence:**
```java
synchronized (_cache) { Map<String, String> cached = _cache.get( project ); if (cached == null) { cached = load( project ); _cache.put( project, cached ); }
```

**Fix:** Load outside the lock (compute-then-putIfAbsent, or per-project lock/ConcurrentHashMap.computeIfAbsent with narrow scope); optionally clear per-project on project close.

**Verifier's correction:** Finding stands as stated, with one nuance: the aggressive workspace-wide clearing on derived build-output copies is documented intent — the visit() comment says "The alias map is cheap to rebuild (~10ms/project), so clearing the whole cache is fine" — but that comment addresses only rebuild cost (assuming warm JDT caches), not holding the global _cache monitor across classpath resolution and jar/workspace I/O while UI-thread completion/hover and background validation for ALL projects block on it. Also, since isActiveFor caches an empty map even for alias-less projects, after each build-triggered clear every open project's first lookup re-runs the classpath+jar probe serially under the same lock.

#### A2. ElementHelpView _reloadTarget stale-guard only covers project switches, not same-project generations

**threading** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/help/ElementHelpView.java`:303

KNOWN-ISSUE AREA (a) — CONFIRMED gap. reload()'s guard is `if (t.isDisposed() || _reloadTarget != target) return;` (identity compare on the captured IJavaProject). If reload() runs twice for the SAME project — exactly what the alias-file listener does (it fires reload() after ParsleyTagAliasResolver.clearCache() while an earlier catalog build for the same project may still be in flight, e.g. two quick saves of parsley-tag-aliases.properties, or the initial editor-activation reload racing an alias change) — both jobs carry the same `target` reference, both pass the guard, and whichever asyncExec lands LAST wins. A slow first build that captured the pre-clearCache alias map can complete after the fresh build and clobber _entries with stale alias/override data. The project-switch and disposal cases ARE handled correctly.

**Evidence:**
```java
if (t.isDisposed() || _reloadTarget != target) {
	return;
}
_entries = built;
```

**Fix:** Use a monotonically increasing generation counter (set on UI thread in reload(), captured by the job) instead of comparing the project reference

#### A3. Locate cache HashMap is mutated and read from multiple threads with no synchronization

**threading** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/locate/cache/ComponentLocateCache.java`:70

'private Map<String, Map<String, LocalizedComponentsLocateResult>> projects = new HashMap<>()' is read/written by: (a) resource-change notification threads via PreDeleteVisitor.forgetCacheForFile() and the 'projects = new HashMap...' reset in resourceChanged(); (b) background validation (WodParserCache.java:74,226), refactoring processors (RenameComponentProcessor, RenameBindingKeyProcessor), search participants; (c) the UI thread (WOComponentCreationPage.validatePage on every keystroke, editors). Concurrent put/remove during iteration-free get is still a data race on HashMap internals: lost updates, stale reads, and (during resize concurrent with put) corrupted buckets. Nested per-project maps have the same problem. Failure scenario: validation job populates the cache while a POST_CHANGE delta prunes it → HashMap.put/remove race → missing or corrupt entries, potentially a hung entry lookup.

**Evidence:**
```java
private Map<String, Map<String, LocalizedComponentsLocateResult>> projects = new HashMap<String, Map<String, LocalizedComponentsLocateResult>>();
```

**Fix:** Use ConcurrentHashMap for both levels (computeIfAbsent for the per-project map), or synchronize all public methods on a lock.

**Verifier's correction:** Two minor sharpenings: (1) the 'projects = new HashMap...' reset in resourceChanged only runs in the catch(CoreException) blocks (lines 135/144), so it is a rare path and, being a single reference assignment, the least dangerous mutation — the live race is PreDeleteVisitor.forgetCacheForFile (remove) vs addToCache (put/resize) vs get; (2) the PRE_CLOSE branch (line 130) requires getDelta() != null but PRE_CLOSE events carry no delta, so PreCloseVisitor/forgetCacheForProject is likely dead code and not part of the race.

#### A4. Adapter factory _cache HashMap accessed from UI and background threads without synchronization

**threading** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/variables/ParsleyProjectAdapterFactory.java`:20

IAdapterFactory.getAdapter is invoked from whatever thread calls project.getAdapter(...): SWT thread (decorators: WOComponentDecorator.decorateImage, ProjectDecorator.decorateImage), background validation (WodParserCache), refactoring guards (ParsleyProject.shouldRefactor), resource-change handlers. _cache.get/_cache.put on a plain HashMap race across these threads; a put-triggered resize concurrent with get can return null or a wrong entry, and two threads can interleave stamp comparison and put. Impact is mostly redundant object churn, but HashMap corruption under resize is possible on a hot path that runs constantly.

**Evidence:**
```java
private Map<IProject, ParsleyProject> _cache = new HashMap<IProject, ParsleyProject>();
```

**Fix:** Use ConcurrentHashMap and compute-if-stale semantics.

**Verifier's correction:** Finding stands as stated; two sharpenings. (1) The realistic failure on modern JVMs is lost updates and `get()` returning null during a concurrent resize (causing redundant BuildProperties/ParsleyProject reconstruction — benign churn since the code treats null as a miss), plus cross-thread visibility staleness; the Java-7-style linked-list corruption/infinite-loop is essentially gone in Java 8+ HashMap, and unsafe publication of ParsleyProject itself is mitigated because both its fields are final and BuildProperties' accessors are synchronized. So severity is "constant redundant work plus a data race that violates the JMM," not likely crashes. (2) The heaviest concurrent load is not decorators vs. UI but WodBuilder's own validation pool: 2×CPU worker threads all calling getAdapter per validated component. A secondary latent issue in the same map: entries are never evicted when projects are closed/deleted, so `_cache` also leaks ParsleyProject instances for the session.

#### A5. invalidateResource() mutates the access-ordered LRU map without synchronization

**threading** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:110

WodParserCache._parsers is a LimitedLRUCache (LinkedHashMap with accessOrder=true, so even get() is a structural modification). All access in parser() is guarded by 'static synchronized', but invalidateResource() calls _parsers.remove(key) with no lock at all. invalidateResource() runs on the resource-change notification thread (WodParserCacheInvalidator.visit lines 115/118/121) while parser() is called concurrently from the UI thread (hyperlink detection, completion) and from WodBuilder's validation thread pool. Concurrent structural modification of a non-threadsafe LinkedHashMap can corrupt the linked list (lost entries, or the classic infinite loop in traversal). Concrete scenario: delete a file inside a .wo folder while a background validation batch is running — remove() races the pool threads' parser() calls.

**Evidence:**
```java
Object cacheEntry = parser(resource, false);
      if (cacheEntry != null) {
        String key = getCacheKey(resource);
        _parsers.remove(key);
```

**Fix:** Make invalidateResource() (or the remove) synchronized on WodParserCache.class like parser().

#### A6. ContentDescriberWO.ANSWER global mutable static toggled around editor construction

**threading** · confidence: medium · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/template/TemplateEditor.java`:60

TemplateEditor's constructor sets the global `ContentDescriberWO.ANSWER = IContentDescriber.VALID` and createHTMLSourceEditor() later flips it to INVALID. This is a process-wide flag manipulated as a side-channel between two lifecycle calls; Eclipse content-type describers run on arbitrary threads (e.g. during workspace validation or editor-input probing), so any content-description happening between the two assignments — or if an exception aborts construction between them — observes the wrong answer, mis-detecting file content types. It is also simply fragile legacy global state in a refactored codebase.

**Evidence:**
```java
public TemplateEditor() {
    super();
    ContentDescriberWO.ANSWER = IContentDescriber.VALID;
```

**Fix:** Replace the static handshake with an explicit parameter or thread-local, or remove ContentDescriberWO if it is vestigial

**Verifier's correction:** The defect is real but the mechanics are misstated. `createHTMLSourceEditor` runs inside the `HTMLEditor` super-constructor, i.e. BEFORE the `ANSWER = VALID` assignment in TemplateEditor's constructor body — the INVALID write at TemplateEditor.java:130 is effectively a dead-code reaffirmation of the default, not a "close the window" reset. Actual behavior: (1) before any TemplateEditor is opened, all `.html` files describe as INVALID for the wohtml content type; (2) after the first TemplateEditor construction, ANSWER latches VALID forever, so every InputStream-based content description of ANY `.html` file in the workspace (WO project or not) matches the high-priority "Html with WO tags" content type for the rest of the session; (3) each later TemplateEditor construction re-opens a brief INVALID window (the reverse of the claimed race). Compounding it, the field is a non-volatile static written on the UI thread and read by content-describer threads with no synchronization, so visibility is unspecified. The correct characterization: a legacy WOLips hack whose reset half never worked due to constructor-chaining order, making `.html` content-type detection session-history-dependent rather than content-dependent.

#### A7. resolveForBindings ignores project/jar .api and .apiext files — hasBindingDefinition only consults GLOBAL definitions

**bug** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ParsleyTagAliasResolver.java`:131

resolveForBindings walks the alias chain from the end back to 'the nearest element with a definition', but hasBindingDefinition(name) checks only ApiUtils.findGlobalApiSnapshotByClassName (bundled WebObjectDefinitions.xml) and ApiUtils.findGlobalApiextBytes (plugin-bundled apiext/). It never checks the element's own .api/.apiext shipped in a framework jar (Resources/X.api) or workspace project. Failure scenario: aliases declare WOString -> ERXWOString and ERExtensions ships ERXWOString.api (adding e.g. 'valueWhenEmpty'); hasBindingDefinition("ERXWOString") returns false, the walk falls back to WOString, so FuzzyXMLWodElement (line 41) sets the element type to WOString for the whole validation/completion pipeline — the replacement's own bindings never appear in completion, its API validations never run, and findElementType resolves the wrong IType for deprecation checks. Note hasBindingDefinition also can't take an IType (it only has a name), so fixing it requires a findElementType + findApiSnapshot probe.

**Evidence:**
```java
if (ApiUtils.findGlobalApiSnapshotByClassName( name ) != null) { return true; } return ApiUtils.findGlobalApiextBytes( name ) != null;
```

**Fix:** In hasBindingDefinition, also probe project-resolvable definitions (findElementType + findApiSnapshot/findApiextBytes), or only walk up when the resolved element resolves to no definition of any kind.

**Verifier's correction:** The mechanism and failure scenario are correct, but this is a documented deliberate trade-off, not an oversight: the resolveForBindings javadoc and the FuzzyXMLWodElement comment explicitly assume replacement elements "have no .api" and choose the cheaply-cached ancestor for performance. The real defect is that this assumption fails silently when a framework or workspace project does ship .api/.apiext for a replacement element — a latent design gap (no evidence wonder-slim currently ships e.g. ERXWOString.api) rather than active breakage today.

#### A8. shortcutsByElement alias branch does not skip replacement entries, contradicting its own comment — 'WOString' shows as a tag on ERXWOString

**bug** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ElementCatalog.java`:181

The comment says: 'Skip keys that ARE element names (the replacement entries like WOString -> ERXWOString) so only the friendly shortcuts (str, foreach, …) show as tags.' But the code only skips when alias.equalsIgnoreCase(finalResolvedElement). For aliases {str=WOString, WOString=ERXWOString}: alias 'WOString' resolves recursively to 'ERXWOString', which is not equal to 'WOString', so it is added — the Element Reference Tags column shows 'WOString' as a tag on ERXWOString's row alongside 'str'. The legacy branch (lines 188-199) has the same comparison but against the DIRECT target, where it does what the comment claims. Confirms drift between the aliasesActive/legacy dual branches.

**Evidence:**
```java
// tags. Skip keys that ARE element names ... if (alias.equalsIgnoreCase(element)) { continue; }
```

**Fix:** Skip aliases that appear anywhere in the chain as an element class name (e.g. skip when the alias is itself a key that another element resolves through, or when it matches a known element simple name).

#### A9. forceValid triggers on ANY keypath ending in 'VALID', not just the '// VALID' suppression comment

**bug** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/wod/BindingValueKeyPath.java`:113

The gate is `endsWith("VALID")` but the strip is `replaceFirst("\\s*//\\s*VALID", "")`. A binding value like `user.statusVALID` (a genuinely misspelled key that happens to end in VALID) sets forceValid=true — so _valid is forced true (line 210) and the invalid key is silently accepted — while the key name itself is left unstripped. The intended feature is clearly the trailing `// VALID` marker; the endsWith check should match the full marker. Low real-world frequency, but it silently disables validation exactly where the user typo'd.

**Evidence:**
```java
if (bindingKeyNames[bindingKeyNames.length - 1].endsWith("VALID")) { bindingKeyNames[...] = ...replaceFirst("\\s*//\\s*VALID", ""); forceValid = true; }
```

**Fix:** Gate on a regex matching the '// VALID' suffix (e.g. matches(".*\\s*//\\s*VALID$")) instead of endsWith("VALID").

**Verifier's correction:** Finding is accurate as stated; one sharpening: endsWith is case-sensitive, so common camelCase keys (isValid, statusValid) are unaffected — only last segments ending in literal uppercase "VALID" (statusVALID, IS_VALID) trigger it. Also, for .wod files the intended "// VALID" marker is independently handled as a comment token in DocumentWodModel.setValidate(false), so the constructor's marker handling serves inline-binding/raw-string callers, and the WOD path gets only the bug, never the feature. Fix: gate on the full marker, e.g. a regex match for \s*//\s*VALID$ before stripping.

#### A10. PRE_CLOSE branch is unreachable: PRE_CLOSE events never carry a delta, so PreCloseVisitor is dead code and project caches are never evicted on close/delete

**bug** · confidence: high · area: `deadcode-tests` · also seen in: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/locate/cache/ComponentLocateCache.java`:130

KNOWN-ISSUE VERIFY (PreCloseVisitor) — CONFIRMED the code walks every node (PreCloseVisitor.visit() calls forgetCacheForProject() on every delta node and returns true unconditionally, PreCloseVisitor.java:78-86), but SHARPENED: the branch never executes at all. Per the Eclipse platform javadoc (verified against org.eclipse.core.resources.source-3.23.100 IResourceChangeEvent.java: 'Event type is PRE_CLOSE, and getResource returns the project being closed' — only PRE_BUILD/POST_BUILD/POST_CHANGE carry deltas), event.getDelta() is always null for PRE_CLOSE, so 'getDelta() != null && getType() == PRE_CLOSE' is always false. Consequences: (1) PreCloseVisitor.java is dead code, deletable; (2) the intended eviction never happens — the cache is keyed by project NAME (forgetCacheForProject uses project.getName()), so close/delete a project and later create/import a different project with the same name, and getLocalizedComponentsLocateResult() serves stale LocalizedComponentsLocateResult objects holding IFile handles into the old project. The fix is to key the branch on getResource() (event.getType() == PRE_CLOSE || PRE_DELETE, then forgetCacheForProject((IProject) event.getResource())), no visitor needed.

**Evidence:**
```java
if (event.getDelta() != null && event.getType() == IResourceChangeEvent.PRE_CLOSE) { ... event.getDelta().accept(new PreCloseVisitor(this));
```

**Fix:** Replace the delta-visitor branch with a direct getResource() check for PRE_CLOSE|PRE_DELETE; delete PreCloseVisitor.java

**Verifier's correction:** The PRE_CLOSE branch in ComponentLocateCache.resourceChanged() (wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/locate/cache/ComponentLocateCache.java:130-137) is unreachable — PRE_CLOSE/PRE_DELETE events always have getDelta() == null — so PreCloseVisitor.java is deletable dead code. But this is a dead-code finding, not a stale-cache bug: cache eviction on project close/delete still happens through the POST_CHANGE branch (PreDeleteVisitor sees every member reported as REMOVED in the post-close/post-delete delta and evicts each entry via forgetCacheForFile). The real residue is only an empty per-project-name HashMap left in the `projects` map, plus a misleadingly named PreDeleteVisitor that actually runs on every POST_CHANGE. Cleanup suggestion stands: delete PreCloseVisitor and the PRE_CLOSE branch, optionally replace with `if (event.getType() == PRE_CLOSE || PRE_DELETE) forgetCacheForProject((IProject) event.getResource())` to also drop the empty map, but no stale-result failure scenario exists today.

#### A11. No CSRF/Origin defense: any web page can drive the dev server via simple GET requests

**bug** · confidence: high · area: `devserver`

**Location:** `java/org/objectstyle/wolips/devserver/DevServer.java`:37

SECURITY POSTURE (area a). The documented security model (DevServer.java lines 37-51) is that loopback binding IS the security boundary: 'Anything that can reach a server on the loopback interface is already running code on the machine.' This is materially wrong for the browser vector. Every endpoint is registered as a plain GET context (createContext lines 85-93) and RequestHandler.handle (line 132) dispatches purely on query params with NO Origin, Referer, Host, or token check (grep confirms none exist anywhere in the package). Cross-origin GET fetch/`<img src>`/form navigation to http://127.0.0.1:9485/... are 'simple requests' that browsers send WITHOUT a CORS preflight; the absence of Access-Control-Allow-Origin only blocks the attacker from READING the response, not from triggering the side effect. So any web page the developer merely visits can blind-fire: /stop?app=NAME (terminates a running app via Eclipse, StopHandler line 56), /launch?config=NAME (starts an Eclipse launch, LaunchHandler line 64), /refreshProject with NO params (refreshes+rebuilds EVERY open project, RefreshProjectHandler lines 79-82 — zero knowledge needed), /openComponent & /openJavaFile (force-open arbitrary editors). App names are guessable, and the developer need not be tricked into anything beyond loading a page. The 'no password because loopback' decision (lines 46-51) removed the one thing that would have stopped browser CSRF.

**Evidence:**
```java
we simply ignore any {@code pw} parameter ... loopback binding <em>is</em> the security boundary
```

**Fix:** Reject requests whose Origin/Referer header is present and not null/loopback, and/or require a per-session token the runtime learns via a local handshake; at minimum gate mutating endpoints (/stop,/launch,/refreshProject,/registerApp).

#### A12. Arbitrary process kill: unvalidated pid flows into `kill -9`, remotely reachable via browser CSRF

**bug** · confidence: high · area: `devserver`

**Location:** `java/org/objectstyle/wolips/devserver/StopHandler.java`:109

SECURITY (area a). StopHandler.killJson (line 109) runs ProcessBuilder("kill", hard?"-9":"-15", pid) where `pid` is whatever string was stored in AppRegistry. RegisterAppHandler (line 39-40) accepts the `pid` param as an ARBITRARY string with no numeric validation (only `name` and a positive `port` are checked, lines 30-37). Chain: a malicious local process — or, combined with the CSRF finding, any web page — issues two simple GETs: (1) /registerApp?name=x&port=1&pid=<VALUE> then (2) /stop?app=x&force=true. Because ProcessBuilder passes argv directly there is no shell-injection, but the VALUE need not be a real child pid: pid='-1' yields `kill -9 -1`, which SIGKILLs every process the user can signal (session-wide kill); pid='0' targets the whole process group; any specific pid kills that process. force+pid bypasses the Eclipse-launch check entirely (StopHandler lines 46-52). Result: a visited web page can kill arbitrary processes, up to and including the user's entire session. port is also only checked >0 (no upper bound), a lesser issue.

**Evidence:**
```java
new ProcessBuilder("kill", hard ? "-9" : "-15", pid)
```

**Fix:** Validate pid is a positive integer in RegisterAppHandler before storing; in killJson refuse non-positive pids; and only kill a pid the dev server itself observed as a child/registered-and-reachable process.

**Verifier's correction:** Minor: `kill -9 -1` / `kill -9 0` also terminate the Eclipse/JVM process issuing the kill (it is among the user's signalable processes), so the effect is a session-wide mass-kill/DoS rather than a surgical kill that spares the attacker's own dev-server host. The security impact (arbitrary/mass process termination via browser CSRF against an unvalidated pid) stands as stated.

#### A13. WodEditor.doSave NPEs when opened standalone (no ComponentEditorInteraction)

**bug** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/wod/WodEditor.java`:125

doSave guards with `if (_editorInteraction == null || _editorInteraction.embeddedEditorWillSave(...))` but then unconditionally calls `_editorInteraction.fireWebObjectChanged()` inside the branch. WodEditor is registered as a standalone editor in plugin.xml (id ng.componenteditor.WodEditor), reachable via Open With; in that case initEditorInteraction() is never called and _editorInteraction stays null, so every save throws NPE after super.doSave (file saves, then an error surfaces). performRevert() (line 152) and performSaveAs() (line 159) dereference _editorInteraction with no null check at all. TemplateEditor.doSave has the correct `if (_editorInteraction != null)` guard — WodEditor was never given the same fix.

**Evidence:**
```java
if (_editorInteraction == null || _editorInteraction.embeddedEditorWillSave(progressMonitor)) {
	super.doSave(progressMonitor);
	updateValidation();
	_editorInteraction.fireWebObjectChanged();
}
```

**Fix:** Null-guard fireWebObjectChanged in doSave/performRevert/performSaveAs as TemplateEditor.doSave does

#### A14. ComponentEditor.init blind-casts non-ComponentEditorInput inputs to FileEditorInput

**bug** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/ComponentEditor.java`:134

`FileEditorInput fileEditorInput = (FileEditorInput) editorInput;` — any IEditorInput that is neither ComponentEditorInput nor FileEditorInput (e.g. FileStoreEditorInput when a user does Open With > Components Editor on a file outside the workspace, or another plugin's IFileEditorInput implementation) throws ClassCastException out of init, so the editor fails to open with a raw exception instead of a graceful fallback or message.

**Evidence:**
```java
FileEditorInput fileEditorInput = (FileEditorInput) editorInput;
IFile file = fileEditorInput.getFile();
```

**Fix:** Adapt via input.getAdapter(IFile.class) and throw a descriptive PartInitException when no IFile is available

#### A15. restoreSashWeights parses persisted weights with unguarded Integer.parseInt

**bug** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/HtmlWodTab.java`:339

The sash-weight preference string is split and fed to Integer.parseInt with no try/catch; a corrupted or hand-edited preference value ('NaN', empty segment from a trailing comma, etc.) throws NumberFormatException out of restoreSashWeights → createTab → createPages, aborting component editor construction entirely (the editor fails to open until the pref is cleared). saveSashWeights writes only ints, so this needs external corruption — but the blast radius (editor cannot open) is disproportionate.

**Evidence:**
```java
sashWeights[sashWeightNum] = Integer.parseInt(sashWeightStrs[sashWeightNum]);
```

**Fix:** Wrap parsing in try/catch and fall back to default weights on malformed prefs

**Verifier's correction:** restoreSashWeights (HtmlWodTab.java:339) feeds persisted sash weights to Integer.parseInt unguarded; a hand-corrupted preference value (non-numeric token, leading comma, embedded whitespace, or > Integer.MAX_VALUE — though NOT a trailing comma, which split() drops) throws NumberFormatException out of createTab → ComponentEditorPart.createPages. Eclipse's part-creation wrapper turns this into an error editor page rather than a crash, but the component editor reproducibly fails to open until the preference is cleared; the same unguarded call at line 267 (expandWod) also breaks the collapsed-WOD-bar expand gesture.

#### A16. Working-set mode only filters WO-style pulled-up folders at src/main, so ng-style resource folders appear twice

**bug** · confidence: high · area: `explorer-wizards-locate`

**Location:** `ng/componenteditor/explorer/NGWorkingSetAwareContentProvider.java`:71

getChildren() pulls up all folders from NGPackageExplorerContentProvider.getSourceFolders() (both WO-style src/main/* and ng-style folders discovered anywhere under src/main/resources/), but getFolderContent() only filters children when the folder's path equals exactly "src/main". NGPackageExplorerContentProvider.getFolderContent (lines 136-141) additionally filters any folder at/under src/main/resources; that branch is missing here. Failure scenario: switch the Parsley Explorer to 'Working Sets' root mode with an ng-objects project (components at src/main/resources/ng/app/components) → the components/app-resources/webserver-resources folders are shown BOTH pulled up at the project root AND at their physical location when expanding src/main/resources/ng/app.

**Evidence:**
```java
if ("src/main".equals(folder.getProjectRelativePath().toString())) {
```

**Fix:** Reuse NGPackageExplorerContentProvider's filterPulledUpChildren / path checks (src/main OR under NG_RESOURCES_ROOT) instead of the hardcoded "src/main" comparison.

#### A17. escapeString drops characters for consecutive or trailing backslashes, corrupting all subsequent parse offsets

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLUtil.java`:119

escapeString() preprocesses the source before _parse(), and _parse's offsets are applied back to _originalSource — so the preprocessed string MUST stay length-identical to the input. The backslash branch does `escape = true; continue;` without appending anything; a following backslash re-enters the same branch, so two consecutive backslashes inside a quoted attribute emit only the compensation space of the next char (2 output chars for 3 input chars). A trailing backslash at EOF is also silently dropped. Every offset after that point shifts, so tag boundaries computed on the preprocessed string slice the wrong text out of _originalSource. Verified: parsing `<img alt="a\\b"><span>x</span>` produces an element literally named `><span` at offset 15 (harness output: escapeString len in=16 out=15; span name=[><span]). Any template with a Windows path or regex like value="a\\b" in an attribute silently corrupts the whole document model (validation, outline, hyperlinks all misaligned).

**Evidence:**
```java
if ((flag == 1 || flag == 2) && c == '\\') {
  escape = true;
  continue;
}
```

**Fix:** When consuming a backslash, append a placeholder space immediately (and one for the escaped char), guaranteeing output length == input length; flush pending escape at loop end.

#### A18. Unclosed `<pre>` tag crashes the parser with StringIndexOutOfBoundsException

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/FuzzyXMLParser.java`:388

handlePreTag splits the remaining source on `</pre>`. When no close tag exists, content[0] is the entire remainder, and `_originalSource.substring(offset, end + text.length() + 1)` asks for one char past end-of-string. Verified: `new FuzzyXMLParser(false, true).parse("<pre>oops")` and `parse("<div><pre>abc</div>")` both throw StringIndexOutOfBoundsException. HtmlCacheEntry._parse() has no try/catch around parser.parse(), so the moment a user types `<pre>` before its close tag exists, the whole document model build fails — validation, completion and outline break until the tag is closed. Contrast with handlePRawTag/handlePCommentTag which explicitly handle the missing-close-tag case.

**Evidence:**
```java
String preBlock = _originalSource.substring(offset, end + text.length() + 1);
```

**Fix:** Clamp to _originalSource.length() (mirror handlePRawTag's hasCloseTag branch) and add a parser test for unclosed `<pre>`.

#### A19. Formatter silently deletes CDATA sections

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLCDATAImpl.java`:16

FuzzyXMLCDATAImpl extends FuzzyXMLElementImpl passing name="" (`super(parent, "", offset, length, -1)`). FuzzyXMLElementImpl.isHidden() returns true for empty names, so WOHTMLRenderDelegate.renderNode() treats the CDATA node as hidden whitespace and returns false without rendering it — its toXMLString (which would emit `<![CDATA[...]]>`) is never called. Verified via the exact FormatRefactoring render path: `format("<div><![CDATA[a < b]]></div>")` returns `"<div></div>"` — the CDATA block and its content are deleted from the document by a reformat. FuzzyXMLFormatterTest has no CDATA coverage.

**Evidence:**
```java
super(parent, "", offset, length, -1);  // combined with FuzzyXMLElementImpl.isHidden(): return getName() == null || getName().equals("");
```

**Fix:** Override isHidden() to return false in FuzzyXMLCDATAImpl (and add a formatter round-trip test for CDATA).

**Verifier's correction:** Finding stands as stated, with one scope refinement: CDATA inside `<script>` and `<style>` elements is NOT deleted, because the parser stores script/style content as raw text (FuzzyXMLScriptImpl/FuzzyXMLStyleImpl, FuzzyXMLParser.java:826-831) rather than as FuzzyXMLCDATAImpl nodes — verified: format("`<script>`\n<![CDATA[\nvar a = 1;\n]]>\n`</script>`") preserves the CDATA text. The silent deletion affects CDATA sections in ordinary markup context (the FuzzyXMLParser.handleCDATA path), e.g. XML-ish templates or CDATA directly in the body.

#### A20. Formatter rewrites attribute entities: &quot; becomes \" and &amp; becomes bare &

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLAttributeImpl.java`:210

Attribute rendering uses the DECODED value (getValue()) instead of _rawValue, then backslash-escapes only the quote character. Verified through the formatter path: `<p title="a&quot;b">` formats to `title="a\"b"` — backslash is not an escape in HTML, so browsers terminate the attribute at the second quote; and `<a href="x?a=1&amp;b=2">` formats to `href="x?a=1&b=2"`, un-escaping the ampersand (and if the raw text was `&amp;lt;`, a subsequent reparse decodes it further to `<`, i.e. progressive corruption across format cycles). Text nodes were fixed to render _rawValue for exactly this reason (see FuzzyXMLTextImpl javadoc), but attributes never got the same treatment. FuzzyXMLFormatterTest covers entities in text/script but has zero attribute-entity cases.

**Evidence:**
```java
String value = getValue();
for (int i = 0; i < value.length(); i++) { ... else if (!inNestedTag && _quote == c) { xmlBuffer.append('\\'); }
```

**Fix:** Render _rawValue (entities intact) when it is available and unchanged, matching FuzzyXMLTextImpl; add attribute entity round-trip tests.

**Verifier's correction:** Finding stands as written; one sharpening: the progressive corruption is worse than stated — after `&amp;lt;` degrades to a literal `<` inside the attribute value, the next format cycle doesn't just decode further, it destroys the attribute entirely (`<p title="a&amp;lt;b">` becomes `<p>` after three format passes).

#### A21. FuzzyXMLText.getValue() double-decodes entities

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLTextImpl.java`:46

The parser already decodes text when constructing the node (FuzzyXMLParser.handleText line 524: `new FuzzyXMLTextImpl(getParent(), FuzzyXMLUtil.decode(text, _isHTML), ...)`), and getValue() decodes _value AGAIN. Verified: for source text `&amp;lt;` (which means the literal string "&lt;"), getValue() returns "<". Rendering is unaffected (uses _rawValue), but every consumer of getValue() — validation, isNonBreaking whitespace checks, refactoring — sees wrongly decoded text. Same double-decode pattern exists for attributes in well-formed mode (separate finding).

**Evidence:**
```java
return FuzzyXMLUtil.decode(_value, getDocument().isHTML());  // _value was already decoded by the parser
```

**Fix:** Store the raw text as _value and decode once in getValue(), or decode in the constructor only and return _value directly.

**Verifier's correction:** Finding is correct as stated, with one fix-relevant nuance: getValue()'s decode is not redundant on every construction path. Raw-content text nodes (FuzzyXMLParser.java:428, built from undecoded rawText) and FuzzyXMLDocumentImpl.createText (line 74) store undecoded text and rely on getValue() for their single decode. The double-decode occurs specifically for text nodes created via handleText (the normal parse path), so the fix must not simply delete the decode in getValue().

#### A22. Attribute values double-decoded when wellFormedRequired mode is on

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/FuzzyXMLParser.java`:850

parseAttributeContents already sets `attr.value = FuzzyXMLUtil.decode(attr.rawValue, _isHTML)` (line 984). createFuzzyXMLAttribute then decodes attrInfo.value a second time in the _wellFormedRequired branch. Verified: `new FuzzyXMLParser(true, false).parse("<p title=\"&amp;lt;\"></p>")` yields getAttributeValue("title") == "<" instead of "&lt;". This mode is reachable in production: HtmlCacheEntry/TemplateSourceEditor/TemplateOutlinePage pass `buildProperties.isWellFormedTemplateRequired()` as the flag, so projects with that setting get corrupted attribute values in validation and binding inspection.

**Evidence:**
```java
FuzzyXMLAttributeImpl attr = new FuzzyXMLAttributeImpl(element, namespace, name, FuzzyXMLUtil.decode(attrInfo.value, false), ...)
```

**Fix:** Pass attrInfo.value directly (it is already decoded), same as the non-wellformed branch.

**Verifier's correction:** Finding stands as written. Two sharpening notes: (1) this is not a recent refactoring regression — git log shows the double-decode dates to the initial ng import, i.e. inherited legacy WOLips behavior; (2) the second decode at line 850 hardcodes isHTML=false, so in HTML mode only XML-entity names (lt, gt, amp, quot, apos) and numeric references get double-decoded — e.g. title="&amp;lt;" becomes "<" but "&amp;nbsp;" survives as "&nbsp;" — making the corruption selective rather than uniform.

#### A23. FuzzyXMLFormatComposite.getLength() is infinite recursion (StackOverflowError)

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLFormatComposite.java`:43

The method calls itself instead of the delegate: `if (delegate != null) { return getLength(); }`. Verified: calling it on any composite with a non-null delegate throws StackOverflowError. Currently dormant — no callers in the codebase — but it sits on the public API surface of a class the formatter uses everywhere, and will detonate the first time someone uses it (e.g. during the parslips.tooling extraction). Note getOffset() directly below shows the intended pattern.

**Evidence:**
```java
public int getLength() {
    if (delegate != null) {
      return getLength();
    }
    return 0;
}
```

**Fix:** return delegate.getLength();

#### A24. Formatter decodes entities inside `<script>` content

**bug** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLScriptImpl.java`:16

FuzzyXMLScriptImpl.getValue runs `FuzzyXMLUtil.decode(super.getValue(rc, xmlBuffer), rc.isHtml())` over the rendered script body. Verified through the formatter path: `<script>var u = 'a?x=1&amp;y=2';</script>` formats to `var u = 'a?x=1&y=2';` — the author's `&amp;` is silently un-escaped. Since script children already render via FuzzyXMLTextImpl._rawValue (entities intact), the decode call is a leftover from when rendering escaped text and now only corrupts. FuzzyXMLFormatterTest covers quotes in scripts but not entities.

**Evidence:**
```java
String contents = FuzzyXMLUtil.decode(super.getValue(rc, xmlBuffer), rc.isHtml());
```

**Fix:** Drop the decode() wrapper; add a formatter test for &amp; inside script.

#### A25. save()/load() leak file streams (Properties.store/load do not close)

**bug** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/HTMLProjectParams.java`:206

'props.store(new FileOutputStream(file), ...)' (line 206) and 'props.load(new FileInputStream(file))' (line 266) never close their streams; Properties.store/load leave the stream open per their javadoc. load() runs on every HTMLSourceEditor.doValidate() and dispose() (new HTMLProjectParams(project) each time), so file handles accrue until GC finalization. On Windows the leaked write handle can also block subsequent saves of '.amateras'.

**Evidence:**
```java
props.store(new FileOutputStream(file), "EclipseHTMLEditor configuration file");
```

**Fix:** Use try-with-resources around both streams.

**Verifier's correction:** save() (HTMLProjectParams.java:206) and load() (line 266) leak their FileOutputStream/FileInputStream since Properties.store/load leave streams open. But: (1) load() opens a stream only when the project has a `.amateras` file (configFile.exists() guard, line 263) — projects that never used the HTML property page leak nothing; (2) the hot path is not TemplateSourceEditor.doValidate() (overridden, bypasses HTMLProjectParams) but HTMLSourceEditor.dispose()/JavaScriptEditor.dispose() on every editor close, JavaScriptValidator.doValidate(), JavaScriptAssistProcessor, and JavaScriptHyperlinkDetector; (3) handles are reclaimed at GC via Cleaner, and the Windows "blocks subsequent saves" claim is dubious since Java opens files with full share flags on Windows. Net: real but low-severity resource leak; fix with try-with-resources on both call sites.

#### A26. validate(false, …) on an already-validated cache leaves _validating stuck true, permanently disabling non-forced validation

**bug** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:353

validate() first sets _validating=true when 'force || !_validating', then only runs (and only eventually resets _validating=false in _validate()'s finally) when 'force || !_validated'. If called with force=false while _validated==true — which happens routinely: WodCompletionProcessor → HtmlCacheEntry.getHtmlElementCache() → validate(false,true), and AbstractCacheEntry.getModel() → validate(false,true) — the body is skipped and _validating stays true forever. From then on: (a) _setValidated(false) is silently ignored ('if (validated || !_validating)'), so document edits no longer mark the cache dirty, and (b) every subsequent validate(false,…) is a no-op ('force || !_validating' is false). Only a force=true call (editor save/reconcile) resets the flags, which masks the bug in editor workflows but leaves all lazy validation paths (getModel(), getHtmlElementCache()) dead in between. The state machine is simply wrong: the flag is set without a guarantee it will be cleared.

**Evidence:**
```java
if (force || !_validating) {
        _validating = true;
        validate = true;
      }
    }

    if (validate) {
      if (force || !_validated) {
```

**Fix:** Only set _validating=true on the path that actually runs validation (fold the _validated check into the synchronized block), and reset it if the body is skipped.

**Verifier's correction:** Minor wording only: "stuck true forever/permanently" holds only until the next force=true validation (editor reconcile/save, WodBuilder), which fully resets both flags — so lazy validation and dirty-marking are dead between forced validations rather than irrecoverably; the reviewer already acknowledged this masking, and the underlying state-machine defect is exactly as claimed.

#### A27. Auto-rename guard tests a workspace IPath with java.io.File — existence check never fires, causing CoreException spam or unwanted renames

**bug** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCacheInvalidator.java`:101

The .wo-folder ADDED branch auto-renames any xml/html/xhtml/wod/woo file added to a .wo bundle to match the bundle name. The guard '!newPath.toFile().exists()' (and the re-check inside asyncExec) converts a workspace-absolute path (e.g. /MyProject/Components/Main.wo/Main.html) straight to a java.io.File, which points at the OS filesystem root — it essentially never exists, so the guard always passes. Failure scenario: copy 'Extra.html' into Main.wo (which already has Main.html) → the visitor schedules oldFile.move(Main.html) → move throws 'resource already exists' CoreException, logged on every such delta; and when the destination does NOT exist the file is silently renamed, e.g. dragging a differently-named template into a bundle mangles its name with no user consent. Should be ResourcesPlugin.getWorkspace().getRoot().getFile(newPath).exists(). Separately, performing resource moves from inside a delta visitor (via Display.asyncExec) is a side-effectful listener anti-pattern.

**Evidence:**
```java
if (file.getFileExtension().matches("(xml|html|xhtml|wod|woo)") && 
              !file.getFullPath().equals(newPath) && !newPath.toFile().exists()) {
```

**Fix:** Use workspace root getFile(newPath).exists() — or delete this legacy auto-rename behavior entirely.

**Verifier's correction:** The guard '!newPath.toFile().exists()' at WodParserCacheInvalidator.java:101 (and the asyncExec re-check at line 106) tests a workspace-absolute IPath against the OS filesystem root, so it never fires; the real consequence is limited to the collision case: adding e.g. Extra.html to Main.wo that already contains Main.html schedules oldFile.move(), which throws a "resource already exists" CoreException that is caught and logged on every such add. Fix: use ResourcesPlugin.getWorkspace().getRoot().getFile(newPath).exists(). The silent rename when the destination does NOT exist is deliberate legacy behavior (documented in RenameComponentProcessor's javadoc as the auto-rename mechanism for .wo folder renames), not a consequence of this bug — a correct guard would still perform it. The asyncExec-from-delta-visitor side-effect anti-pattern stands as a valid secondary observation (that same javadoc notes it "doesn't fire reliably during LTK refactoring transactions").

#### A28. F3 keypath-segment mapping is shifted right by the inline-binding prefix length; disagrees with Cmd+click at dot boundaries

**bug** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/editor/template/OpenDeclarationHandler.java`:80

OpenDeclarationHandler passes the absolute caret offset to WodBindingValueHyperlink.toBindingValueHyperlink, which computes offsetInKeyPath = caretOffset - valuePosition.getOffset(). But for inline elements (FuzzyXMLWodElement, non-namespaced branch line 88) valuePosition spans the RAW attribute value including the '$' prefix, while wodBinding.getValue() is the prefix-stripped keypath. So every offset fed to segmentIndexAt is over by the prefix length (1 for '$', more for multi-char prefixes). Concrete failure: in value="$foo.bar", place the caret between 'foo' and the dot and press F3 — offsetInKeyPath=4 counts the dot, opening 'bar', while Cmd+click at the same spot (InlineWodElementHyperlinkProvider correctly subtracts prefixLength) opens 'foo' per the documented 'dot belongs to the preceding segment' convention. The .wod editor path is unaffected because DocumentWodBinding value positions are exactly the keypath.

**Evidence:**
```java
hyperlink = WodBindingValueHyperlink.toBindingValueHyperlink(wodElement, binding.getName(), cache, offset);
```

**Fix:** In toBindingValueHyperlink, subtract the keypath's start within the value position (same keyPathStartInValue logic already used for the underline), or have the handler pre-shift the offset for inline elements.

**Verifier's correction:** Finding stands as stated. Two sharpening details: (1) the wrong-segment behavior only manifests when the caret sits within prefixLength characters before a dot (elsewhere the shifted offset still lands in the same segment), which is why it is easy to miss; (2) the F3 path additionally builds the hyperlink with keyPathStartInValue=0 (the 4-arg toBindingValueHyperlinkForSegment overload, WodBindingValueHyperlink.java:107-109), so its underline region is also shifted left by the prefix — cosmetically irrelevant for F3 since it opens immediately, but a fix should pass the prefix length the way InlineWodElementHyperlinkProvider.java:68 does. The namespaced-value branch (FuzzyXMLWodElement.java:81-85) is unaffected, as the reviewer noted.

#### A29. parser(IProject, String) NPEs when the component name resolves to no files

**bug** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:79

parser(IProject project, String componentName) takes locateResult.getFirstWodFile(), falls back to getFirstHtmlFile(), and passes the result to parser(resource, true) with no null check. If the component has neither (mistyped name in a WOD '// inherit Foo' comment, or a components-only-in-jar case), getCacheKey(null) → isStandaloneFile(null) is false → getWoFolder(null) dereferences resource.getParent() → NPE. The only caller, DocumentWodModel's WOD-inheritance parsing (line 157), catches Throwable and turns it into the unhelpful parse problem "WOD inheritance of 'Foo' failed: null." — masking the real cause (component not found).

**Evidence:**
```java
IFile resource = locateResult.getFirstWodFile();
  	if (resource == null) {
  		resource = locateResult.getFirstHtmlFile();
  	}
    WodParserCache parserCache = WodParserCache.parser(resource, true);
```

**Fix:** Return null (or throw LocateException with the component name) when both files are missing; report 'component not found' in DocumentWodModel.

**Verifier's correction:** Only a cosmetic detail is off: on the project's Java 21 target, helpful NPE messages are enabled by default, so the diagnostic reads "WOD inheritance of 'Foo' failed: Cannot invoke \"org.eclipse.core.resources.IResource.getParent()\" because \"resource\" is null." rather than literally "failed: null." The substance of the finding (unguarded null from locate result causing NPE in getWoFolder via getCacheKey, swallowed by catch(Throwable)) is fully correct.

#### A30. fromPreferenceString throws ArrayIndexOutOfBoundsException on odd-length attribute lists (dangling attribute key)

**bug** · confidence: medium · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/wod/TagShortcut.java`:145

The attribute loop does `attributes.put(split[attributeNum], split[attributeNum + 1])` with the loop condition only checking `attributeNum < split.length`. A preference line with an odd token count after shortcut+actual (e.g. 'str\tWOString\tescapeHTML' from a hand-edited or truncated preference export) reads split[3] of a length-3 array → AIOOBE inside the static synchronized ApiCache.getTagShortcuts(), which propagates unchecked into every legacy-path completion/validation call (FuzzyXMLWodElement line 44, ElementCatalog line 188, AbstractWodElement line 454) and, because _tagShortcuts is never assigned, repeats on every call. Zombie code pending deletion, so a fix may be moot — but worth knowing the legacy path has this landmine until it's deleted.

**Evidence:**
```java
for (int attributeNum = 2; attributeNum < split.length; attributeNum += 2) { attributes.put(split[attributeNum], split[attributeNum + 1]); }
```

**Fix:** Bound the loop at split.length - 1 (or delete the legacy registry as planned).

**Verifier's correction:** The AIOOBE itself fires only once per malformed preference value. Because ApiCache.getTagShortcuts() sets _tagShortcutsStr (line 164) before calling TagShortcut.fromPreferenceString (line 165), the exception leaves the cached string updated but _tagShortcuts unset; every subsequent call skips the parse and returns _tagShortcuts, which is null on first load — so callers like FuzzyXMLWodElement:44, AbstractWodElement:454, and ElementCatalog:188 then NPE in their for-each on every legacy-path invocation (or silently use a stale shortcut list if a prior parse succeeded). Same landmine, but the steady-state symptom is NPE/stale data, not repeated AIOOBE.

#### A31. getEditor(int pageIndex) ignores its argument and indexes by getActivePage()

**bug** · confidence: medium · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/ComponentEditorPart.java`:433

`public IEditorPart getEditor(int pageIndex) { return componentEditorTabs[this.getActivePage()].getActiveEmbeddedEditor(); }` — the pageIndex parameter is discarded. MultiPageEditorPart's own machinery calls getEditor(newPageIndex) (e.g. from pageChange) where it accidentally works because the active page already equals the argument, but any call for a non-active page returns the wrong editor, and if getActivePage() returns -1 (before the first page is selected, or during teardown) this throws ArrayIndexOutOfBoundsException; componentEditorTabs is also null if createPages() bailed early. getActiveEditor() (line 437) has the -1 guard this method lacks.

**Evidence:**
```java
public IEditorPart getEditor(int pageIndex) {
	return componentEditorTabs[this.getActivePage()].getActiveEmbeddedEditor();
}
```

**Fix:** Index by pageIndex with bounds/null guards, or delegate to getActiveEditor() only when pageIndex == getActivePage()

**Verifier's correction:** The defect is real but latent, not a live bug: `getEditor(int)` (wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/editor/component/ComponentEditorPart.java:433-435) ignores its pageIndex argument and lacks the -1 guard that getActiveEditor() has, violating the MultiPageEditorPart contract on a public method. No currently reachable caller — framework or plugin — ever passes a non-active index or reaches it with getActivePage() == -1, so no wrong-editor or ArrayIndexOutOfBoundsException occurs today. It becomes a real bug the moment anything calls the inherited removePage(int), findEditors(IEditorInput), or setActiveEditor(IEditorPart) (all of which loop or index over arbitrary pages via this override), or calls the public getEditor directly. Correct fix is trivial and worth making: `int i = pageIndex; if (componentEditorTabs == null || i < 0 || i >= componentEditorTabs.length) return null; return componentEditorTabs[i].getActiveEmbeddedEditor();`

#### A32. htmlPageId/wodPageId overwritten per language loop while htmlWodTab() always returns tab 0

**bug** · confidence: medium · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/ComponentEditorPart.java`:209

createPages() reassigns htmlPageId/wodPageId on every iteration of the multi-language loop, so they end up pointing at the LAST language's page, while htmlWodTab() returns htmlWodTabs[0]. For a localized component (multiple .wo language variants → multiple Component tabs), switchToHtml() calls htmlWodTabs[0].setHtmlActive() but then activates the last language's page — the activated tab's html/wod split state is untouched and the first tab's state is mutated invisibly. Everything routed through htmlWodTab() (getTemplateEditor, getWodEditor, gotoMarker, OpenDeclarationHandler, drag-and-drop init in ComponentEditor.createPages which only wires tab 0's viewer) operates on the first tab regardless of which language tab the user is on. Only affects multi-language bundle components; single-component and standalone cases are consistent.

**Evidence:**
```java
htmlPageId = this.addPage(htmlWodTab);
wodPageId = htmlPageId;
this.setPageText(tabIndex, language + "Component");
```

**Fix:** Track the active HtmlWodTab (by current page) instead of hardcoding htmlWodTabs[0] and last-write page ids

**Verifier's correction:** Finding stands as stated, with one sharpening: the most visible symptom is the reveal-on-open step at the end of createPages() (ComponentEditorPart.java lines 301-309), which routes through switchToHtml()/switchToWod() — so a localized component always opens on the LAST language's Component tab rather than the first, with tab 0's html/wod split flag mutated invisibly. Also note wodPageId == htmlPageId is itself by design (HTML and WOD share one split tab); the bug is only the last-vs-first tab mismatch.

#### A33. showEditorInput maps inputs to tabs by hardcoded indices 0-3, wrong for multi-language and standalone layouts

**bug** · confidence: medium · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/ComponentEditorPart.java`:534

showEditorInput switches on the raw index i of componentEditorInput.getInput() with cases 0=html, 1=wod, 2=woo, 3=api. For localized components the input array has 3 entries per language, so a match at i>=3 (second language's files) either falls into the api case (i==3) or the silent default; for standalone-html inputs the array layout differs as well. Result: 'show this input' can activate the wrong tab or do nothing. It also blind-casts to FileEditorInput (line 535) like ComponentEditor.init.

**Evidence:**
```java
switch (i) {
case 0:
	switchToHtml();
	break;
case 1:
	switchToWod();
```

**Fix:** Resolve the target tab by comparing the file against each tab's inputs (like gotoMarker does) instead of positional indices

**Verifier's correction:** The core claim stands as stated; two sharpenings. (1) Concrete wrong-tab map — multi-language bundle template (N lproj variants, getInput() = [html0,wod0,woo0,...,html(N-1),wod(N-1),woo(N-1),api]): second language's html (i=3) activates the Api tab; its wod/woo (i=4,5) silently do nothing; the .api file itself (i=3N) silently does nothing. Standalone template (getInput() = [html,api]): the .api file (i=1) calls switchToWod() and lands on the HTML tab instead of the Api tab. (2) The blind cast ((FileEditorInput) editorInput).getFile() at line 535 is currently latent, not failing: every live path reaches showEditorInput via the matching strategy's FileEditorInput branch (all openEditor call sites in the repo pass IFile/FileEditorInput; nothing calls openEditor with a ComponentEditorInput), so no ClassCastException occurs today — it would only fire if someone later opens the editor programmatically with a ComponentEditorInput that equals an open editor's input. The fix should match on file identity against the tab inputs (or compare against componentEditorInput.getComponentEditors()/getApiEditor()/getStandaloneHtmlEditor()) rather than raw array position.

#### A34. add() silently overwrites htmlFile on any matching .html anywhere in the project — no duplicate detection unlike java/groovy

**bug** · confidence: medium · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/locate/result/LocalizedComponentsLocateResult.java`:161

The 'html' branch does 'htmlFile = file;' unconditionally, while the java branch validates via type hierarchy and the groovy/api branches detect duplicates. ComponentLocateScope matches '<name>.html' and '<name>.wo.html' anywhere in the project (Locate walks the whole tree). Failure scenario: a project has component Main (Main.wo/Main.html or standalone components/Main.html) plus an unrelated static file webserver-resources/Main.html — whichever the depth-first walk visits LAST wins, so getFirstHtmlFile() can return the static resource. The editor/validator then opens or validates the wrong file, and because the result is cached in ComponentLocateCache the wrong binding persists. Also note resolveFilesFromFolder (line 182) sets htmlFile only if null, but this branch overwrites even a value already resolved from the .wo bundle.

**Evidence:**
```java
} else if ("html".equals(extension)) {
	// Standalone .wo.html file or any HTML file
	htmlFile = file;
```

**Fix:** Prefer html files inside located .wo folders or in recognized components folders; at minimum, don't overwrite a non-null htmlFile and flag duplicates like the other branches.

**Verifier's correction:** Minor sharpening only: the wrong file wins only when the unrelated .html sorts after the real component files in the depth-first members() traversal (order is formally unspecified in Eclipse, alphabetical in practice); if the real component's html is visited last it wins by luck. The bug is the silent last-wins assignment with no duplicate alert, asymmetric with the groovy/api/java branches.

#### A35. getParent() returns IProject for pulled-up folders, but their actual tree parent is the IJavaProject node

**bug** · confidence: medium · area: `explorer-wizards-locate`

**Location:** `ng/componenteditor/explorer/NGPackageExplorerContentProvider.java`:153

getChildren() adds pulled-up folders as children of an IJavaProject element (line 109: 'parentElement instanceof IJavaProject'), and PulledUpFolderRefresher accordingly refreshes JavaCore.create(project). But getParent() maps a pulled-up folder to folder.getProject() — the IProject, which is a different element (not .equals to the IJavaProject) and is not present in the tree when the explorer shows projects as roots. Failure scenario: any viewer operation that resolves an element's path bottom-up through getParent — reveal/setSelection on a not-yet-materialized pulled-up folder, expandToLevel, or link-with-editor targeting the folder itself — walks folder → IProject → (no widget found) and gives up or falls back, which matches the 'works on the second try' flakiness the revealBundle() javadoc (lines 295-305) works around by force-expanding. NGWorkingSetAwareContentProvider.getParent (line 88-90) has the same mismatch.

**Evidence:**
```java
if (isSourceFolder(folder)) {
	return folder.getProject();
}
```

**Fix:** Return JavaCore.create(folder.getProject()) so the logical parent matches the element actually used as the tree parent.

**Verifier's correction:** The finding is accurate as stated for both NGPackageExplorerContentProvider.getParent (line 153) and NGWorkingSetAwareContentProvider.getParent (lines 88-92); the fix is to return JavaCore.create(folder.getProject()) (the IJavaProject) instead of folder.getProject(). One sharpening: the failure is conditional on the project node not yet being expanded — once expanded, the folder widget exists and getParent is never consulted — and revealBundle()'s force-expand workaround itself depends on the same broken getParent chain, so it too silently no-ops on a fully collapsed project node.

#### A36. getBuildPropertiesFile() NPEs for projects with null resource location; load() converts it to a RuntimeException thrown out of getAdapter

**bug** · confidence: medium · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/variables/BuildProperties.java`:84

getBuildPropertiesEclipseFile().getLocation() returns null for a closed project, a project not yet fully created, or one backed by a non-local filesystem (EFS); .toFile() then throws NPE. load() is invoked from the BuildProperties constructor, which ParsleyProjectAdapterFactory.getAdapter runs on every adapter lookup; load() wraps the NPE in a RuntimeException and rethrows it out of getAdapter. ParsleyProject's static helpers (shouldHandleProject/isParsleyProject/shouldRefactor) swallow this via catch(Exception), but direct adapter callers do not — e.g. WOComponentCreationPage.resolveDefaultFormat (line 1051) and ParsleyProject.forProject → getComponentClass (WOComponentCreationPage.java:558) would propagate the exception into wizard createControl. Trigger: workspace-path race where a project closes between an isOpen() check and the adapter call, or an EFS-backed project.

**Evidence:**
```java
File file = getBuildPropertiesEclipseFile().getLocation().toFile();
```

**Fix:** Null-check getLocation() and treat missing location as 'no build.properties' (empty Properties) instead of throwing.

**Verifier's correction:** BuildProperties.getBuildPropertiesFile() (BuildProperties.java:84) can NPE and load() rethrows it as a RuntimeException out of ParsleyProjectAdapterFactory.getAdapter into unguarded callers — but only when IFile.getLocation() is null, which happens for a NON-EXISTENT project (e.g. deleted concurrently), an EFS/non-local-filesystem project, or an undefined-path-variable project location. Closed projects are NOT a trigger: Eclipse guarantees non-null getLocation() for members of an existing project whether open or closed (Resource.getLocation() only checks project.exists()), so the claimed isOpen()-race and closed-project wizard scenarios cannot fire. This is a latent robustness bug (low practical likelihood), best fixed with a null check in getBuildPropertiesFile()/getModificationStamp(); note ParsleyProject.findComponentsFolder (ParsleyProject.java:343) catches only CoreException and would also leak the RuntimeException.

#### A37. Java source path derived via absolute-location segment stripping breaks for linked source folders; first-source-root heuristic can pick a non-java root

**bug** · confidence: medium · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/wizards/WOComponentCreator.java`:292

findJavaSourcePath() returns root.getCorrespondingResource().getLocation() (an absolute filesystem path) and callers convert it to a project handle via componentJavaPath.removeFirstSegments(project.getLocation().segmentCount()) (lines 177-179, 248-250). For a linked source folder (location outside the project directory) the segment stripping produces a garbage relative path, and prepareFolder() then creates a wrong folder tree inside the project before writing the .java file there. Separately, 'first K_SOURCE root wins' means that in projects where the .classpath lists src/main/resources (m2e resource root) before src/main/java, the generated .java lands in the resources tree. Both are plausible with m2e-managed projects; the wizard offers no source-folder choice to correct it.

**Evidence:**
```java
return root.getCorrespondingResource().getLocation();
```

**Fix:** Work with workspace-relative paths (root.getCorrespondingResource().getProjectRelativePath()) and prefer a root whose path ends with 'java'.

**Verifier's correction:** Finding stands as stated; two sharpenings: (1) the identical findJavaSourcePath + removeFirstSegments pattern is duplicated in wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/editor/actions/ExtractComponentAction.java (lines ~262 and ~447-455) and ExtractWrapperAction.java (lines ~271 and ~382-390), so Extract Component / Extract Wrapper are equally affected, not just the wizard; (2) two extra failure modes beyond linked folders: if the linked folder's path variable is unresolved, getLocation() returns null and the code NPEs, and if the first K_SOURCE root is the project itself with an empty package name, removeFirstSegments yields the empty path and IProject.getFolder(empty path) throws an assertion failure.

#### A38. getAttributeValueOffset off-by-one guard allows StringIndexOutOfBoundsException

**bug** · confidence: medium · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/HTMLHyperlinkDetector.java`:177

The skip loop guards with 'if (source.length() == offset + 1) break;' BEFORE reading source.charAt(offset). If the '=' found by indexOf is the last character of the document (user is mid-typing '<a href=' at EOF and Cmd-hovers), offset++ makes offset == source.length(), the guard (length == offset+1) is false, and charAt(offset) throws StringIndexOutOfBoundsException. detectHyperlinks() has no try/catch, so the exception propagates into the JFace hyperlink manager (logged error, hyperlinks broken for that gesture).

**Evidence:**
```java
offset++;
if (source.length() == offset + 1) {
  break;
}
c = source.charAt(offset);
```

**Fix:** Change the guard to 'if (offset >= source.length()) break;' before charAt.

**Verifier's correction:** The off-by-one guard in getAttributeValueOffset is a real StringIndexOutOfBoundsException (charAt(source.length()) when the '=' found by indexOf is the document's last character), but the claimed trigger is wrong: an unclosed '<a href=' at EOF produces no FuzzyXMLAttribute at all (the parser drops the trailing char of an unclosed tag and never emits attributes without a parsed value), so getAttributeValueOffset is never called in that scenario. The reachable trigger is FuzzyXML's missing-'=' quote-recovery attribute (e.g. '<a href"foo">'), whose source text contains no '='; indexOf('=', attr.getOffset()) then finds an unrelated '=' further along, and if that '=' is the last character of the document (e.g. '<a href"foo">x</a>a='), hovering anywhere in the element throws SIOOBE, propagating uncaught out of detectHyperlinks.

#### A39. dispose() NPEs when the editor part was never fully created

**bug** · confidence: medium · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/editors/HTMLSourceEditor.java`:427

dispose() unconditionally calls 'fProjectionSupport.dispose(); _pairMatcher.dispose();' — both fields are only assigned in createPartControl(). If editor init fails earlier (doSetInput/document provider exception) Eclipse still calls dispose(), which then throws NPE and masks the original failure. getAdapter() null-checks fProjectionSupport (line 467), showing the field is known to be nullable.

**Evidence:**
```java
fProjectionSupport.dispose();
    _pairMatcher.dispose();
```

**Fix:** Null-guard both dispose calls.

**Verifier's correction:** dispose() does NPE when the part was never fully created (init/doSetInput failure via the workbench-registered HTMLEditor, or a partial createPartControl failure), but in the E4 workbench CompatibilityPart logs the original PartInitException first and wraps wrapped.dispose() in try/catch, so nothing is masked and no crash escapes. The actual damage is: (1) a spurious secondary NPE in the error log, and (2) the NPE aborts dispose() before super.dispose(), skipping AbstractTextEditor cleanup (document-provider disconnect, listener deregistration) and HTMLEditor's own super.dispose() — a resource leak on every failed editor open. Note the embedded ComponentEditorPart path does not hit this: HtmlWodTab.createTab() catches PartInitException and still calls createPartControl, and ComponentEditorPart.dispose() guards on componentEditorTabs != null. Fix is the standard JDT pattern: null-check both fields in dispose() (as getAdapter already does for fProjectionSupport).

#### A40. LRU eviction (size 10) splits open editors from the shared cache — stale-contents completion/hyperlinks and duplicate validation

**bug** · confidence: medium · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:92

The parser cache holds at most 10 WodParserCache instances (LimitedLRUCache<String,WodParserCache>(10)). Editors permanently hold their instance (TemplateSourceEditor.getParserCache caches _cache; WodEditor same) and attach their live IDocument to it (WodEditor:254 getParserCache().getWodEntry().setDocument(document); TemplateReconcilingStrategy:44 cache.getHtmlEntry().setDocument(_document)). But completion and hyperlink code re-resolve via WodParserCache.parser(file) on every invocation (WodCompletionProcessor:248, InlineWodElementHyperlinkProvider:28, WodElementHyperlinkDetector:34). Once the map evicts an editor's entry — e.g. more than 10 component editors open, or JavaChangeRevalidator revalidating all open editors on an .api save (each WodBuilder._validateComponent does parser(resource), churning the LRU) — parser(file) silently creates a SECOND WodParserCache for the same component that parses from DISK, not the editor's dirty document. Result: completions/hyperlink offsets computed against stale saved contents while the user has unsaved edits, and two cache instances validating/deleting the same files' markers.

**Evidence:**
```java
WodParserCache._parsers = new LimitedLRUCache<String, WodParserCache>(10);
```

**Fix:** Pin caches with an attached document (skip eviction while a document is set), or have editors re-resolve through parser() instead of caching the instance.

**Verifier's correction:** Two sharpenings: (1) In InlineWodElementHyperlinkProvider the hyperlink offsets come from the FuzzyXMLDocument passed in by the HTML editor, so offsets are correct on that path — the stale second cache there corrupts resolution context, not positions; stale offsets genuinely occur in WodElementHyperlinkDetector (wod model parsed from disk vs. live-viewer regions) and stale content in WodCompletionProcessor's getHtmlElementCache(). (2) An .api-save revalidation alone only evicts editor entries when the combined key set (open editors plus inherited parent components parsed during validation via DocumentWodModel:157) exceeds 10; the reliable eviction triggers are >10 open component editors, builder validation sweeps, and inheritance-chain parses. Also note the active editor's key is kept warm by its own parser(file) calls (map.get refreshes LRU order), so the victim is typically a background editor with unsaved edits.

#### A41. Namespaced inline binding values ($ognl:foo.bar) get a mis-based valuePosition, and the hyperlink provider double-shifts it

**bug** · confidence: medium · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/util/FuzzyXMLWodElement.java`:85

In the valueNamespace branch, valuePosition is computed as valueNamespacePosition.offset + valueOffset, mixing bases: valueOffset is an index into originalValue (already including the prefix and namespace) while valueNamespacePosition.offset is a document offset — the result is too far right by valueNamespaceOffset (the prefix length). Worse, this branch makes valuePosition start (approximately) at the keypath, whereas the plain branch (line 88) spans the whole raw value including '$' — two different conventions in one class. InlineWodElementHyperlinkProvider:68 assumes the raw-value convention and passes keyPathStartInValue = attrValue.indexOf(keyPath) (e.g. 6 for "$ognl:foo.bar"), so toBindingValueHyperlinkForSegment adds that prefix AGAIN on top of a position already at the keypath: the Cmd+hover underline lands ~prefix+1 characters past the keypath start, outside the actual segment. Validation markers using this valuePosition are also off by one prefix-width. Failure scenario: any template using a value namespace, e.g. <wo:WOString value="$ognl:person.name"/> — hover/underline and problem-marker positions are visibly displaced.

**Evidence:**
```java
int valueOffset = originalValue.indexOf(value.getValue(), valueNamespaceOffset + value.getValueNamespace().length());
        valuePosition = new Position(valueNamespacePosition.offset + valueOffset, attribute.getValueDataLength() - valueOffset);
```

**Fix:** Compute valuePosition as element.getOffset() + attribute.getValueDataOffset() + 1 + valueOffset, and document/unify the value-position convention across both branches.

**Verifier's correction:** The valuePosition mis-base (off right by the inline-prefix length, keypath-only span) and the hyperlink double-shift (underline lands keyPathStartInValue + prefixLength ≈ 7 chars past the keypath start for "$ognl:foo.bar", i.e. on/past the closing quote) are both real and reachable. However, the claim that validation markers are also off by one prefix-width is wrong: AbstractWodBinding.fillInBindingProblems skips keypath validation entirely for any non-null value namespace (checkKeyPath = false for "var" and all others), and all WodBindingValueProblem sites that use getValuePosition() are inside that skipped block — so no problem markers are ever placed for namespaced binding values. The mis-based position could instead affect ChangeBindingValueRefactoring (wodclipse/core/refactoring/ChangeBindingValueRefactoring.java:37), which replaces text at getValuePosition().

#### A42. Accessor/mutator key caching conditions are asymmetric — mutator lookups never cache empty results, accessors do

**perf** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/wod/TypeCache.java`:239

getBindingValueAccessorKeys caches when `_type.getTypeParameters().length == 0 || bindingValueAccessorKeys.size() == 0` (non-generic, OR empty result even for generics), but getBindingValueMutatorKeys caches only when `length == 0 && bindingValueMutatorKeys.size() > 0`. Both carry the identical comment about generic types, so one condition is inverted. Consequence: for a non-generic component, a keypath whose last key is getter-only (the common case — BindingValueKeyPath line 150 probes mutators exactly when accessors were found empty at the last key, and isSettable probes them too) re-runs the full BindingReflectionUtils.getBindingKeys supertype-walking reflection on every validation pass instead of hitting the cache.

**Evidence:**
```java
if (_type.getTypeParameters().length == 0 && bindingValueMutatorKeys.size() > 0) { _bindingValueMutatorKeys.put(name, bindingValueMutatorKeys); }
```

**Fix:** Make the mutator condition mirror the accessor one: `length == 0 || size() == 0`.

**Verifier's correction:** The caching asymmetry is real and empty mutator results are indeed never cached, but the failure scenario is misstated. Getter-only last keys (the claimed common case) never hit getBindingValueMutatorKeys: BindingValueKeyPath.java line 148 only probes mutators (line 150) when the accessor list is EMPTY at the last key, and isSettable() — the only other mutator-probing call site, line 313 — is dead code with zero callers ("Currently unused" per its javadoc). The actual repeated-reflection cost applies to last keys with neither getter nor setter — i.e., invalid bindings and partially-typed keypaths during live editing — where each validation pass re-runs the MUTATORS_ONLY supertype-walking reflection (the matching empty accessor result is cached, the mutator one is not). Additionally, for generic types even non-empty mutator results behave the same as accessors (neither cached), so the asymmetry is strictly the empty-result case on the mutator side. Real perf defect, lower severity than claimed.

#### A43. Deprecation check builds a second BindingValueKeyPath for every binding, duplicating the resolution already done and running even for literal/OGNL values

**perf** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/wod/AbstractWodBinding.java`:382

fillInBindingProblems constructs a BindingValueKeyPath at line 261 (inside the isKeyPath()/checkKeyPath guard), then unconditionally constructs a second identical one at line 382 for the deprecation check whenever the deprecation severity isn't Ignore — including for bindings whose value is a quoted literal, a number, OGNL, or a 'var' namespace (BindingValueKeyPath's internal short-circuit only skips quote/digit-prefixed values, so `$someOgnl~expr` style values still resolve). Each construction walks the keypath through cache.getBindingValueAccessorKeys per segment. Reusing the first instance (or gating on isKeyPath()) halves keypath resolutions per binding per validation pass.

**Evidence:**
```java
BindingValueKeyPath bindingValueKeyPath = new BindingValueKeyPath(bindingValue, javaFileType, javaProject, cache);
```

**Fix:** Hoist a single BindingValueKeyPath (computed once when isKeyPath()) and share it between the validity and deprecation checks.

**Verifier's correction:** The finding stands structurally but overstates the cost of the second construction in the common case: TypeCache.TypeCacheEntry memoizes getBindingValueAccessorKeys/getBindingValueMutatorKeys per (type, key) (TypeCache.java:217-245) and BindingValueKey caches its resolved _nextType (BindingValueKey.java:89-100), so the first construction at line 261 warms the cache and the second walk at line 382 is mostly HashMap hits, not repeated JDT resolution — "halves keypath resolutions" is only literally true at the walk level, not the JDT-work level. The genuinely duplicated heavy work is (a) segments on types with generic type parameters, which TypeCache deliberately refuses to cache (TypeCache.java:222-226 — full BindingReflectionUtils.getBindingKeys supertype-hierarchy reflection re-runs), and (b) the wholly wasted resolutions for non-keypath values that escape the constructor's quote/digit short-circuit: unquoted `~`-OGNL, `^`-caret, and var-namespace values (quoted literals and numbers, including quoted `"~ognl"`, short-circuit and cost only an allocation). Note also that plain instance reuse slightly changes behavior for var-namespace bindings, which currently (and spuriously) get a deprecation check by resolving the var name against the component type; gating on the same checkKeyPath conditions is the cleaner behavior-preserving fix.

#### A44. WooeditorPlugin.resourceChanged walks the entire workspace delta on every POST_CHANGE with no derived/build pruning

**perf** · confidence: high · area: `deadcode-tests` · also seen in: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/woo/WooeditorPlugin.java`:143

KNOWN-ISSUE VERIFY — CONFIRMED. The visitor returns true (descend) for every node that is not an ENCODING change: 'if (visitingDelta.getKind() != IResourceDelta.CHANGED || (visitingDelta.getFlags() & IResourceDelta.ENCODING) == 0) { return true; }' (line 143-145). The 'build' path prune (line 149) and .wo-folder filter are only reached for nodes whose ENCODING flag IS set, i.e. almost never — so on every workspace change (including full builds touching thousands of derived files) the visitor descends the entire tree doing nothing. There is no isDerived() check anywhere. Once registered (see next finding) this runs for the rest of the session. Failure scenario: open one .woo file once, then every subsequent full build of a large workspace pays a full-tree delta walk in the notification thread.

**Evidence:**
```java
if (visitingDelta.getKind() != IResourceDelta.CHANGED || (visitingDelta.getFlags() & IResourceDelta.ENCODING) == 0) {
            return true;
          }
```

**Fix:** Prune derived resources and non-.wo folders early (return false), and only register while a WooEditor is open

**Verifier's correction:** Two sharpenings: (1) the walk covers the entire workspace *delta* (all changed resources, e.g. thousands of derived files in a full build), not the whole workspace resource tree, and per-node work is only two flag checks — the cost is O(delta size) with cheap constants; (2) the listener lifetime is worse than stated: WooeditorPlugin is not the bundle activator (HTMLPlugin is), so stop() never runs and removeResourceChangeListener (line 222) is dead code — once getDefault() registers the listener it can never be unregistered.

#### A45. Alias-file listener triggers a full catalog rebuild on any delta node named parsley-tag-aliases.properties, ignoring kind/flags

**perf** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/help/ElementHelpView.java`:260

aliasFileChanged() walks the entire POST_CHANGE delta and reports a hit for ANY IResourceDelta whose resource name matches — it never checks getKind() or the CONTENT flag, and the visitor deliberately descends into derived output folders ('incl. into derived output folders'). Consequence: every project build that copies parsley-tag-aliases.properties into bin/ (delta CHANGED even when content is identical), and even marker-only deltas on that file, cause ParsleyTagAliasResolver.clearCache() plus a full ElementCatalog.forProject rebuild (classpath-wide type enumeration + .api/.apiext JAR reads). A source save followed by the build's derived copy also yields two back-to-back rebuilds — which additionally feeds the stale-guard race above. Every other non-matching workspace change still pays the full-tree walk on the resource-notification thread.

**Evidence:**
```java
if (res != null
		&& ParsleyTagAliasResolver.ALIASES_RESOURCE.equals(res.getName())) {
	hit[0] = true;
```

**Fix:** Require kind CHANGED/ADDED/REMOVED with the CONTENT flag (keep derived descent only if the resolver reads from output folders), and debounce reloads

**Verifier's correction:** Core finding stands: aliasFileChanged() reports a hit for any delta node named parsley-tag-aliases.properties without checking getKind() or the CONTENT flag, so marker-only deltas trigger clearCache() + a full ElementCatalog.forProject rebuild, and a source save followed by the builder's derived copy yields two back-to-back rebuilds (both pass the _reloadTarget stale-guard since the project is unchanged). Corrections: (1) descending into derived output folders is deliberate AND necessary, not part of the bug — ParsleyTagAliasResolver.load()/readFromOutput() reads the alias file from the output folder, so the derived copy is the file the editor actually consumes; the redundant trigger is the SOURCE copy in src/main/resources, which the resolver never reads. (2) "every project build that copies the file into bin/" overstates it — the JDT incremental builder only recopies the resource when its source changed; identical-content recopies happen on clean/full builds, not every build. (3) The per-change cost for non-matching changes is a walk of the delta tree (changed resources + ancestors), not the whole workspace tree, on the resource-notification thread; the rebuild itself runs on a background Job, so the cost is wasted classpath-wide enumeration, not UI blocking. (4) Scope mitigation: the listener only exists while the Element Reference view is open and a project is tracked.

#### A46. JavaChangeRevalidator walks the full delta with no isDerived() prune on every POST_CHANGE

**perf** · confidence: high · area: `editor-ui` · also seen in: `htmleditor-fork`

**Location:** `org/objectstyle/wolips/editor/component/JavaChangeRevalidator.java`:67

KNOWN-ISSUE CLASS — CONFIRMED for this listener. The visitor descends every container node in every workspace delta (it returns false only at IFile leaves), with no isDerived() check, so build-output churn is traversed on the resource-notification thread on every workspace change. Mitigations that DO exist: validation itself is dispatched to WodBuilder's thread pool (threaded=true), not run on the UI thread, and asyncExec fires only when a .java/.api content change matched. Secondary gap: revalidateOpenComponents only inspects `window.getActivePage()`, so component editors open on a window's non-active workbench pages are silently skipped (line 110). Also any .api content change anywhere revalidates ALL open component editors (documented as intentional).

**Evidence:**
```java
public boolean visit(IResourceDelta d) throws CoreException {
	IResource resource = d.getResource();
	if (resource instanceof IFile) { ... return false; }
	return true;
```

**Fix:** Prune derived containers (return false when resource.isDerived()) and iterate window.getPages() instead of getActivePage()

**Verifier's correction:** Main finding stands as stated. One secondary point should be softened: the "revalidateOpenComponents only inspects window.getActivePage(), skipping non-active pages" gap (line 110) is theoretical — in the Eclipse IDE each IWorkbenchWindow has exactly one IWorkbenchPage, so no editors are skipped in practice. Additional sharpening: the visitor also lacks any early-out when zero component editors are open, so the full delta walk runs even in workspaces where the revalidation could never apply.

#### A47. Every getAdapter(ParsleyProject/BuildProperties) call reads build.properties from disk, even on cache hit

**perf** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/variables/ParsleyProjectAdapterFactory.java`:29

getAdapter() unconditionally executes 'new BuildProperties(project)', whose constructor calls load() → FileInputStream + Properties.load of build.properties (BuildProperties.java:132-153), plus a File.lastModified() stat. The '_cache' only avoids recreating the ParsleyProject wrapper — the disk I/O happens on EVERY adapter lookup. This adapter is hit constantly and often on the UI thread: WOComponentDecorator.decorateImage → ParsleyProject.shouldHandleProject(project) for every .wo folder label, ProjectDecorator.decorateImage (twice per project label: shouldHandleProject + getAdapter), NGPackageExplorerPart.openComponentBundle, editor-association checks, refactoring guards. Painting a tree with N .wo bundles does N file opens + reads of build.properties synchronously on the SWT thread.

**Evidence:**
```java
BuildProperties newBuildProperties = new BuildProperties(project);

if (cached == null || cached.getBuildProperties().getModificationStamp() != newBuildProperties.getModificationStamp()) {
```

**Fix:** Stat the file (lastModified) first and only construct/load BuildProperties when the stamp differs from the cached one; or invalidate via a resource listener on build.properties instead of polling.

#### A48. validatePage runs a full project-tree Locate on the UI thread per keystroke and permanently pollutes the locate cache with partial-name entries

**perf** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/wizards/WOComponentCreationPage.java`:463

validatePage() is invoked by WizardNewFileCreationPage on every modification of the file-name field. It calls LocatePlugin.getLocalizedComponentsLocateResult(project, getFileName()), which on cache miss builds a ComponentLocateScope and runs Locate.locate() — a full recursive walk of the project's resource tree (Locate.java:78-113; only build/dist/target/derived pruned) — synchronously on the SWT thread. Typing 'MyComponent' performs 11 full traversals and, via LocatePlugin.java:137 addToCache(), permanently caches 11 junk entries ('M', 'My', 'MyC', ...) per project in ComponentLocateCache; these are only evicted if a file whose name-without-extension exactly matches is later added/removed. On large projects this makes the name field visibly laggy and leaks memory for the session.

**Evidence:**
```java
LocalizedComponentsLocateResult result = locatePlugin.getLocalizedComponentsLocateResult(project, getFileName());
```

**Fix:** Debounce the duplicate check or check only the destination folder/known components folders for an existing file; avoid caching results initiated from validation.

**Verifier's correction:** Two minor sharpenings: (1) the walk is pruned to the target project (Locate.locate() enumerates all workspace projects but ProjectLocateScope.ignoreContainer skips non-target ones at the root), and the prune list also includes folders named 'framework' and 'woa', not just build/dist/target/derived; (2) 'leaks memory' is technically true but minor — each junk entry is an empty LocalizedComponentsLocateResult, so the real cost is the ~11 synchronous UI-thread project-tree walks per typed name (one per distinct prefix), not heap growth.

#### A49. Delta visitor walks the entire POST_CHANGE delta with no isDerived() prune, per open explorer instance

**perf** · confidence: high · area: `explorer-wizards-locate`

**Location:** `ng/componenteditor/explorer/PulledUpFolderRefresher.java`:101

KNOWN-ISSUE ADJACENT (the '~9 listeners each walking the full delta' theme): confirmed for this listener. visit() returns true for the workspace root, all open projects, all files' parents, and all non-matching folders — including derived output trees (bin/, target/, build/), which Case 1 deliberately keeps descending into ('return true' at line 120 and the fallthrough at line 135). A full workspace build that touches thousands of derived files walks the complete delta once per PulledUpFolderRefresher instance — and one instance is created per NGPackageExplorerContentProvider / NGWorkingSetAwareContentProvider (one per open Parsley Explorer view/window, plus a new one after each root-mode switch since createContentProvider() is called again). Secondary: Case 2 calls _viewer.refresh(folder) — a full subtree refresh of the pulled-up folder — for any content change beneath it, e.g. every save of a template file inside src/main/components. Also minor: isPulledUpCandidate()'s javadoc claims it 'can't use isSourceFolder directly' but the method body just delegates to it, and JavaCore.create(project) at line 160 never returns null for a non-null project, so that guard is dead.

**Evidence:**
```java
if (!(resource instanceof IFolder)) {
	// Continue descending into the workspace root and projects
	return true;
}
```

**Fix:** Return false for resource.isDerived() early; short-circuit projects that have no recognized layout; consider IResourceDelta.findMember on the few known paths instead of a full accept().

**Verifier's correction:** One overstatement: root-mode switches do not add extra listeners. PackageExplorerPart.rootModeChanged() swaps providers via JFace ContentViewer.setContentProvider(), which disposes the old provider — and both providers' dispose() call _refresher.dispose(), unregistering the workspace listener. A new refresher replaces the old one; steady state is exactly one PulledUpFolderRefresher per open Parsley Explorer view, not an accumulating count.

#### A50. getValue(RenderContext, StringBuffer) copies the entire accumulated output buffer for every element — O(n^2) rendering

**perf** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLElementImpl.java`:506

`StringBuffer sb = new StringBuffer(xmlBuffer)` clones everything rendered so far (so delegate lookbehind checks like hasNewLineEnd see context), children render into the clone, then `sb.delete(0, length)` strips the prefix and the caller appends the result via `xmlBuffer.append(getValue(renderContext, xmlBuffer))` (line 616). Each nested element therefore copies the whole document prefix twice. For a large flat-ish template of size N with E elements this is O(N*E) character copies — formatting large templates (the FormatRefactoring path runs this on every Cmd+Shift+F) degrades quadratically. Not a correctness issue, but the formatter runs on the UI-adjacent refactoring path.

**Evidence:**
```java
StringBuffer sb = new StringBuffer(xmlBuffer);
int length = xmlBuffer.length();
...
sb.delete(0, length);
```

**Fix:** Render children directly into xmlBuffer and have callers capture start-index/substring instead of cloning the buffer.

**Verifier's correction:** Minor: the prefix is copied once per element (the constructor clone), not twice — sb.delete(0, length) shifts only the child content (O(childLen)), and toString()+append() copy the child content two further times; nesting additionally re-copies each element's content once per ancestor level. The O(N·E)/quadratic conclusion stands, and is actually slightly understated: childless self-closing elements (line 589) also pay the full prefix copy for zero output.

#### A51. Full FuzzyXML reparse of the whole template on the UI thread for every hyperlink query

**perf** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/HTMLHyperlinkDetector.java`:89

detectHyperlinks() -> detectHyperlink() runs 'new FuzzyXMLParser(false).parse(editor.getHTMLSource())' every time the JFace hyperlink manager asks for links — i.e. on every mouse move while Cmd/Ctrl is held, and on F3. On a large template each mouse move reparses the entire document on the UI thread, then constructs FuzzyXMLWodElement/WodParserCache lookups per provider. Same beachball class as the recently-fixed ElementHelpView issue. No caching keyed on document modification stamp.

**Evidence:**
```java
FuzzyXMLDocument document = new FuzzyXMLParser(false).parse(editor.getHTMLSource());
```

**Fix:** Cache the parsed FuzzyXMLDocument keyed on the document's modification stamp (or reuse WodParserCache's parse).

**Verifier's correction:** Finding stands as stated, with two sharpenings: (1) the per-query cost is actually a full parse PLUS two full-document String copies (getHTMLSource() at line 89 and doc.get() at line 102, the latter only when hovering inside an attribute); (2) the "WodParserCache lookups per provider" part is minor — WodParserCache.parser(file) is a cached static-map lookup and FuzzyXMLWodElement wraps a single element — the dominant cost is the whole-document reparse. Natural fix: reuse the already-cached parsed document in WodParserCache's HtmlCacheEntry, or cache the FuzzyXMLDocument keyed on the IDocument modification stamp.

#### A52. Content assist reparses the entire document and makes multiple full-string copies per invocation

**perf** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/assist/HTMLAssistProcessor.java`:265

computeCompletionProposals() does 'document.get().substring(0, documentOffset)' (line 237), 'document.get().substring(documentOffset)' (line 262), and 'this._doc = new FuzzyXMLParser(false).parse(document.get())' (line 265) on every completion request — including auto-activated ones on typing trigger chars. All on the UI thread. TemplateAssistProcessor (the subclass used by the template editor) inherits this. For large templates this compounds with the equally unconditional reparse in HTMLHyperlinkDetector.

**Evidence:**
```java
this._doc = new FuzzyXMLParser(false).parse(document.get());
```

**Fix:** Reuse a cached parse (WodParserCache already maintains one for template files) and avoid the substring copies via document.get(offset,len).

**Verifier's correction:** The core finding stands, but "including auto-activated ones on typing trigger chars" only applies if the user opts in: HTMLPreferenceInitializer.java:34 sets PREF_ASSIST_AUTO default to false and HTMLConfiguration.java:236 gates `_assistant.enableAutoActivation()` on that preference (with a 0ms delay default from PREF_ASSIST_TIMES), so in a default install the full reparse + triple full-string copy happens only on explicit Ctrl+Space, not on every keystroke. Users who enable auto-assist in the HTML assist preference page (trigger chars default `</"`) hit the worst-case per-trigger-char behavior described. (The unconditional `enableAutoActivation(true)` at WodSourceViewerConfiguration.java:113 is the separate WOD editor path using WodCompletionProcessor, not this class.)

#### A53. Damager repairers copy the full document string on every keystroke

**perf** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/editors/HTMLConfiguration.java`:436

HTMLTagDamagerRepairer.getDamageRegion (lines 434-458) and JavaScriptDamagerRepairer (lines 470-490) call 'String source = fDocument.get()' then 'source.substring(0, e.getOffset()).lastIndexOf(...)' on every DocumentEvent — two O(document-length) allocations plus scans per keystroke in the syntax-coloring hot path.

**Evidence:**
```java
String source = fDocument.get();
int start = source.substring(0, e.getOffset()).lastIndexOf('<');
```

**Fix:** Scan backwards with fDocument.getChar(i) (no string materialization), or bound the search window.

**Verifier's correction:** Sharpening only: JavaScriptDamagerRepairer is registered for both JAVASCRIPT and HTML_CSS partitions; getDamageRegion can be called up to twice per keystroke (event start and end partitions); and because HTMLTagDamagerRepairer covers IDocument.DEFAULT_CONTENT_TYPE it fires on plain-text typing too, not just inside tags. Impact scales with document length — trivial on small templates, meaningful on large ones.

#### A54. Every .java save nukes the per-project element-type cache, forcing a full JDT type search on next completion/validation (known-issue family: confirmed)

**perf** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCacheInvalidator.java`:82

Confirms/refines two 'known issues on the radar': (1) unlike WooeditorPlugin, THIS listener does prune derived resources (line 49 'if (resource.isDerived()) return false'), so that specific criticism doesn't apply here — but visit() still returns true for everything else and walks the full delta of every project on every POST_CHANGE. (2) The .java CHANGED/ADDED/REMOVED branches unconditionally call WodCompletionUtils.clearElementTypeCacheForProject and TemplateAssistProcessor.clearTagInfoCacheForProject for ANY Java file in the project — even files that are not WO elements. Rebuilding the element-type cache is expensive: getElementTypeCollector runs BindingReflectionUtils.findMatchingElementClassNames("", R_PREFIX_MATCH, …), a full JDT hierarchy search for all element types. Failure scenario: save any .java file in a large project, then trigger tag completion — the full search re-runs every time, on top of the per-type TypeCache clearing that already handles correctness for the changed CU.

**Evidence:**
```java
WodCompletionUtils.clearElementTypeCacheForProject(file.getProject());
          TemplateAssistProcessor.clearTagInfoCacheForProject(file.getProject());
```

**Fix:** Only clear the element-type name cache when types are added/removed (ADDED/REMOVED kinds), not on every CHANGED save; type-name lists don't change on content edits.

**Verifier's correction:** Two refinements, neither fatal: (a) the blanket clear on CHANGED is deliberate coarse correctness behavior, not an accident — the javadoc on clearElementTypeCacheForProject (WodCompletionUtils.java:41-44) says it "should be called when Java files are added, removed, or changed", and a CU edit CAN change element-type membership (e.g. changing a superclass to/from WOElement/NGElement), so a fix must compare old/new membership rather than simply dropping the clear; (b) the expensive re-run is not a full type-hierarchy recomputation every time — SubTypeHierarchyCache (24-entry LRU, self-invalidating listener) often caches the ITypeHierarchy — but a fresh WOHierarchyScope is still constructed per rebuild (the static scope cache is only populated inside WodBuilder build cycles, and hierarchyScope() never puts new entries in the map), plus a full-index searchAllTypeNames with an empty prefix under WAIT_UNTIL_READY_TO_SEARCH. Also, getElementTypeCollector is static synchronized, so the rebuild serializes element-type completion across all projects.

#### A55. Unbounded cached thread pool feeds heavy, blocking handlers — DoS-able

**perf** · confidence: medium · area: `devserver`

**Location:** `java/org/objectstyle/wolips/devserver/DevServer.java`:97

THREAD-POOL BOUNDS / UI DISCIPLINE (area b). The executor is Executors.newCachedThreadPool (line 97), which is UNBOUNDED — it creates a new thread per concurrent request with no ceiling. Several handlers are expensive and long-blocking on their request thread: RefreshProjectHandler.handle refreshes+rebuilds projects then calls waitForBuildToSettle() which joins FAMILY_MANUAL_BUILD/AUTO_BUILD/etc. (lines 99, 110-130) and can block for the full duration of a workspace build; StopHandler.handle (line 56) and LaunchHandler.handle (line 61) each block the request thread on Display.getDefault().syncExec waiting for the UI thread. Combined with the lack of any origin check (see CSRF finding), a single malicious page can issue many concurrent /refreshProject requests, each spawning a fresh thread that pins itself on a full-workspace build — saturating CPU/IO and the workbench. Even absent an attacker, concurrent syncExec-blocking handlers plus unbounded thread creation is a fragile combination. A small fixed/bounded pool (the comment on lines 95-96 even claims requests 'arrive one at a time in practice') would bound the blast radius.

**Evidence:**
```java
Executors.newCachedThreadPool(runnable -> { Thread t = new Thread(runnable, "Parsley Dev Server Request");
```

**Fix:** Use a small fixed thread pool (e.g. newFixedThreadPool(4)) or a bounded queue so concurrent requests can't spawn unbounded threads or stack up full builds.

**Verifier's correction:** Finding stands with one mechanical sharpening: concurrent /refreshProject requests do not run N parallel builds — Eclipse workspace operations and the job-family joins serialize on the workspace scheduling rule. The actual failure mode is (a) unbounded accumulation of blocked "Parsley Dev Server Request" threads (each pinned in refreshLocal/build/join or syncExec), risking thread/memory exhaustion, and (b) a backlog of full-workspace refresh+incremental-build cycles that execute back-to-back and keep the workbench churning long after the request flood stops. Also note a bounded pool alone only fixes (a); requests would still queue in the executor, so (b) additionally wants request coalescing or a busy-reject for the heavy endpoints.

#### A56. showSelectedCard renders on the UI thread including .api/.apiext lookups that read JAR entries

**perf** · confidence: medium · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/help/ElementHelpView.java`:470

Every table selection calls ElementCatalog.renderCardBody(entry, _project) synchronously on the UI thread; that path calls ApiUtils.findApiextBytes(type), findGlobalApiextBytes, and findApiSnapshot(type, new TypeCache().getApiCache(project)) — file/JAR I/O plus a fresh TypeCache per click (ElementCatalog.java line 283). For elements whose definitions live inside framework JARs this is disk I/O per selection while arrowing through the list. Contrast: reload() was explicitly moved to a background Job for exactly this class of I/O. Related smell in the same class: ElementCatalog.definitionKindFor also builds `new TypeCache()` per element inside the catalog loop (line 228), though that runs on the background job.

**Evidence:**
```java
final String body = ElementCatalog.renderCardBody(entry, _project);
_browser.setText(...)
```

**Fix:** Render the card on a small background job with the same stale-guard, or cache rendered bodies per entry

**Verifier's correction:** Finding is accurate; one sharpening: the per-click I/O is not limited to JAR-hosted definitions — because renderCardBody constructs a fresh empty TypeCache/ApiCache each call, every non-framework-private element re-locates AND re-parses its .api file on every selection (only the global WebObjectDefinitions.xml snapshots are statically cached), and the Entry's already-computed DefinitionKind is not reused.


### B. Cleanup & design notes (36 after merging) — inventory (not verification-gated)

#### B1. Dual-path drift: alias resolution is case-sensitive with no mismatch diagnostic; legacy shortcut path is case-insensitive with a dedicated warning

**design** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/util/FuzzyXMLWodElement.java`:41

Legacy branch (lines 44-57) matches shortcuts equalsIgnoreCase, expands anyway, and records setTagShortcutCaseMismatch so AbstractWodElement.fillInProblems (line 302) emits a targeted 'Did you mean repetition?' warning while validation still works against the expanded type. Alias branch calls ParsleyTagAliasResolver.resolveForBindings, whose aliasMap lookup is exact-case (LinkedHashMap.get), so `<wo:Str>` with alias 'str' resolves to itself, fails findElementType, and produces the generic 'class is missing' error path instead — bindings on the tag are not validated at all. The case-mismatch diagnostic machinery (getTagShortcutCaseMismatch) is dead under aliases. This may be intentional (mirroring the runtime's case sensitivity), but a template that validated cleanly under legacy shortcuts degrades when a project adopts parsley-tag-aliases.properties.

**Evidence:**
```java
namespaceElementName = org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver.resolveForBindings(javaProject, namespaceElementName);
```

**Fix:** In the alias branch, detect a case-insensitive alias hit and record the case mismatch (reusing setTagShortcutCaseMismatch) while still resolving, matching the legacy UX.

#### B2. isActiveFor doc/impl drift: a present-but-empty aliases file leaves the legacy mechanism active

**design** · confidence: medium · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ParsleyTagAliasResolver.java`:59

The javadoc (and the class-level 'all-or-nothing' contract) says the new mechanism takes over when 'at least one parsley-tag-aliases.properties is on its classpath', but the implementation is `!aliasMap(project).isEmpty()` — a file that exists but contains only blank/invalid entries (mergeFirstWins skips blank keys/values) reports inactive, silently reviving the 69 legacy preference shortcuts for a project that has explicitly adopted Parsley aliases. Minor, but it's exactly the kind of transitional-dual-path edge that will be confusing to debug: a user emptying their aliases file to 'turn off shortcuts' gets the legacy set back instead.

**Evidence:**
```java
public static boolean isActiveFor(IJavaProject project) { return !aliasMap(project).isEmpty(); }
```

**Fix:** Track file presence separately from map contents (e.g. load() returns a marker for 'file found but empty'), or align the javadoc with the emptiness semantics.

#### B3. ParsleyTagAliasResolver — the shipped replacement mechanism — has zero tests, while the legacy registry it replaces has a dedicated test class

**design** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/bindings/api/ParsleyTagAliasResolver.java`:43

TEST GAP #1 (highest value). grep of test/ finds no reference to ParsleyTagAliasResolver. Untested pure logic includes: recursive fixed-point resolution ('str -> WOString -> ERXWOString'), the cycle guard the javadoc promises ('Cycles are broken defensively'), resolveChain ordering, resolveForBindings (walk-back-to-documented-ancestor used by validation and completion — FuzzyXMLWodElement.java:41, InlineWodTagInfo.bindingSourceElementName), isActiveFor's all-or-nothing per-project switch, and multi-file alias-map merge precedence. Meanwhile test/org/objectstyle/wolips/bindings/wod/TagShortcutTest.java exercises the legacy TagShortcut class that is slated for deletion. The resolution core operates on a plain Map<String,String> internally; extracting resolveChain to take the map (instead of IJavaProject) would make it unit-testable today and doubles as the first step of the planned parslips.tooling extraction. Every alias-active code path (validation, completion, hover, Element Reference, F3) funnels through this one untested class.

**Evidence:**
```java
public static String resolve(IJavaProject project, String name) {
		final java.util.List<String> chain = resolveChain( project, name );
```

**Fix:** Extract the map-based resolution core behind a package-private seam and add tests for chains, cycles, and resolveForBindings fallback

#### B4. The validation core is untested: BindingValueKeyPath (438 lines of keypath math), AbstractWodElement.fillInProblems, and FuzzyXMLWodElement have zero test references

**design** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/bindings/wod/BindingValueKeyPath.java`:1

TEST GAP #2-4. grep of test/ finds no reference to BindingValueKeyPath, fillInProblems, FuzzyXMLWodElement, or ElementCatalog. What exists nearby is misleadingly narrow: ApiValidationTest (40 tests) covers only ApiValidation.evaluate(Map) — the pure .api constraint engine; BindingValidationRuleTest (19 tests) covers only preference-string serialization of rules, not rule application; BindingReflectionUtilsTest has 12 tests against a 938-line class. The riskiest untested logic, in priority order: (a) BindingValueKeyPath — keypath segmentation, operator handling (@sum etc.), partial-path validity — this is the math behind every 'no such key' marker and the recent F3/Cmd-click segment navigation; (b) AbstractWodElement.fillInProblems (507-line class) — the orchestration deciding which problems get reported, including the alias-vs-legacy suggestion candidates at line 446-460; (c) FuzzyXMLWodElement — conversion of a parsed <wo:...> tag into a WodElement including offset/Position arithmetic ('element.getOffset() + element.getNameOffset() + "wo:".length() + 1') that drives marker placement, and the dual alias/legacy branch; (d) ElementCatalog's alias/legacy merge feeding the Element Reference. Since FuzzyXML is already Eclipse-free (per the decoupling work), (c)'s offset math is testable now; (a) needs only IType stubs or a seam.

**Evidence:**
```java
test/ contains no occurrence of 'BindingValueKeyPath', 'fillInProblems', 'FuzzyXMLWodElement', or 'ElementCatalog' (grep -rl, exit 1)
```

**Fix:** Start with BindingValueKeyPath segmentation/operator tests and FuzzyXMLWodElement offset tests (both feasible without a workspace)

#### B5. ElementHelpView listener/Job lifecycle checks out — known-issue concern largely refuted, one residual gap

**design** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/editor/help/ElementHelpView.java`:501

KNOWN-ISSUE VERIFY — mostly REFUTED as a defect. The view registers two listeners (EditorActivationListener via getPartService().addPartListener at line 179, and _aliasFileListener via addResourceChangeListener POST_CHANGE-only at line 201) and dispose() (501-515) removes both and disposes all six allocated Colors. The background reload Job guards against stale clobbering via '_reloadTarget != target' and isDisposed checks before applying results (lines 302-303), and the alias listener proactively calls ParsleyTagAliasResolver.clearCache() so it doesn't depend on listener ordering. Residual gap worth knowing: _reloadTarget is written on the UI thread (reload(), line 286) and read inside the Job's asyncExec-applied closure — the read happens back on the UI thread so there is no torn read, but if reload() is called twice quickly, two Jobs both run buildCatalog (duplicate work, last-schedule wins correctly). Not a bug; at most a Job-family cancel optimization.

**Evidence:**
```java
if (t.isDisposed() || _reloadTarget != target) { ... } // stale-guard present; dispose() removes both listeners
```

**Fix:** Optionally cancel the prior reload Job (Job family) before scheduling a new one

#### B6. WooeditorPlugin.getDefault() lazily constructs an unmanaged activator and registers a leaked resource listener

**design** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/woo/WooeditorPlugin.java`:91

Same latent pattern as the LocatePlugin known issue, here in the editor area. getDefault() does `plugin = new WooeditorPlugin(); plugin.workspace.addResourceChangeListener(plugin);` if plugin is null. If it ever runs before OSGi activates the bundle, it creates a second, non-OSGi-managed activator instance whose listener is registered and never removed (stop() only removes `this` on the OSGi instance); the constructor's `plugin = this` then silently swaps the singleton when OSGi constructs the real one, double-registering. In practice lazy bundle activation makes the path nearly dead — which is exactly why it should be deleted rather than kept as a trap. start() (line 205) is the one legitimate registration.

**Evidence:**
```java
public static synchronized WooeditorPlugin getDefault() {
    if (plugin == null) {
      plugin = new WooeditorPlugin();
      plugin.workspace.addResourceChangeListener(plugin);
    }
```

**Fix:** Make getDefault() return the field only; let start()/stop() own listener registration

#### B7. Per-editor workspace listener registered with default mask but only handles PRE_CLOSE; project deletion not handled

**design** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/component/ComponentEditorPart.java`:109

Every ComponentEditorPart instance registers itself via addResourceChangeListener(this) with the default mask (PRE_CLOSE | PRE_DELETE | POST_CHANGE), so with N open component editors every POST_CHANGE event fans out to N listeners that immediately no-op (resourceChanged only acts on PRE_CLOSE). PRE_DELETE is received but ignored, so deleting (rather than closing) a project leaves its component editors open on vanished files. The PRE_CLOSE async runnable also dereferences componentEditorInput and getSite() — if it runs after the editor was disposed (listener removed, but the asyncExec already queued) getSite() can return null → NPE. Registration in the constructor (before init sets componentEditorInput) is a further footgun: a PRE_CLOSE arriving in that window NPEs on componentEditorInput.getInput().

**Evidence:**
```java
public ComponentEditorPart() {
	super();
	ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
```

**Fix:** Register with an explicit PRE_CLOSE|PRE_DELETE mask, handle PRE_DELETE, and guard the async runnable against disposal

#### B8. alert() pops a modal error dialog (null shell) from library-level locate code on any duplicate, including background validation and per-keystroke wizard validation

**design** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/locate/result/LocalizedComponentsLocateResult.java`:239

alert() does Display.getDefault().asyncExec(() -> MessageDialog.openError(null, "", message)) and is called whenever a duplicate .java/.groovy/.api or unknown extension is located. Locate runs from background validation jobs (WodParserCache), refactoring, search, and from WOComponentCreationPage.validatePage on every keystroke. Failure scenario: a project legitimately containing two Foo.java files reachable by the scope (e.g. main+test source trees) triggers a modal 'Duplicate located' error dialog with an empty title and no parent shell every time any code locates 'Foo' — including repeatedly while the user types in the New Component wizard (each new prefix that collides re-runs locate). Model/locate code should never raise UI.

**Evidence:**
```java
private void alert(final String message) {
	Display.getDefault().asyncExec(() -> MessageDialog.openError(null, "", message));
```

**Fix:** Replace the dialog with logging (LocatePlugin.log) and let callers decide whether to surface it.

#### B9. CorePlugin (and the WizardsPlugin sibling pattern) are lazily self-constructed Plugin objects that never participate in the OSGi lifecycle

**design** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/core/CorePlugin.java`:87

Like LocatePlugin, CorePlugin is not the bundle activator (HTMLPlugin is) and nothing calls its start(); getDefault() manufactures 'new CorePlugin()' on demand purely to serve as a logger (TypeNameCollector.acceptType logs through it on every collection error). getBundle() on such an instance is null, so log() depends on the AbstractBaseUIActivator try/catch fallback to stderr — errors bypass the Eclipse error log entirely. stop() (line 79) is dead code. This 'phantom activator' pattern is repeated across the merged bundle and is exactly the kind of refactoring residue CLAUDE.md flags for removal.

**Evidence:**
```java
public static synchronized CorePlugin getDefault() {
	if (plugin == null) {
		plugin = new CorePlugin();
```

**Fix:** Replace these plugin shells with a small static logger that targets the real bundle's ILog (Platform.getLog(FrameworkUtil.getBundle(...))).

#### B10. Eclipse and wolips couplings that block extraction into parslips.tooling

**design** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLElementImpl.java`:18

The package is otherwise pure Java (TextRegion replaced IRegion, no org.eclipse imports), but four couplings remain: (1) FuzzyXMLElementImpl imports tk.eclipse.plugin.htmleditor.HTMLPlugin — an AbstractUIPlugin subclass pulling SWT/JFace/PlatformUI — solely for `HTMLPlugin.logException(e)` in toXMLString's catch block (line 628); (2) FuzzyXMLParser imports org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils (line 14) for isWOTag checks — and WodHtmlUtils itself imports WodParserCache/ParsleyProject, dragging half the plugin along; (3) FuzzyXMLAttributeImpl (line 3) and FuzzyXMLFormatComposite (line 3) also import WodHtmlUtils for isInline/isWOTag; (4) XPath.java plus the jp.aonir.fuzzyxml.xpath package depend on vendored commons-jxpath-1.2.jar, but the only live consumer is HTMLUtil.getXPathValue(element, "/") in CSSAssistProcessor — effectively dead weight. Fixing (1) needs an injectable/no-op logger; (2)-(3) need the tiny isWOTag/isInline/isParserDirective predicates extracted into a dependency-free class; (4) can likely be deleted outright.

**Evidence:**
```java
import tk.eclipse.plugin.htmleditor.HTMLPlugin;  /  import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;
```

**Fix:** Extract isWOTag/isInline predicates into a pure-Java helper inside jp.aonir.fuzzyxml, replace HTMLPlugin.logException with a pluggable logger, delete the XPath/jxpath subpackage.

#### B11. FuzzyXMLParser is single-use but nothing enforces or documents it

**design** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/FuzzyXMLParser.java`:34

_stack, _roots, _docType, _nonCloseElements and _originalSource accumulate across parse() calls and are never reset, so calling parse() twice on one instance merges the first document's leftover state (roots, unclosed elements) into the second result. All current call sites happen to construct a fresh parser per parse (HtmlCacheEntry, TemplateSourceEditor, TemplateOutlinePage, htmleditor classes), so this is latent — but it is an API-surface trap for the planned parslips.tooling extraction where the parser becomes a library entry point for LSP callers.

**Evidence:**
```java
private Stack<FuzzyXMLNode> _stack = new Stack<FuzzyXMLNode>();
private String _originalSource;
private List<FuzzyXMLNode> _roots;
```

**Fix:** Reset per-parse state at the top of parse(String), or move it into a private per-invocation context object.

#### B12. createAssistProcessor rethrows as RuntimeException during viewer configuration

**design** · confidence: medium · area: `htmleditor-fork`

**Location:** `org/objectstyle/wolips/editor/template/TemplateConfiguration.java`:128

Integration seam: TemplateConfiguration.createAssistProcessor() wraps any failure of WodParserCache.parser(file) / FileEditorInput cast in 'throw new RuntimeException("Failed to create assist processor.", e)'. This is invoked from HTMLConfiguration.getContentAssistant() during source-viewer configuration, i.e. during editor part creation — a cache failure (file in a closed/just-deleted project, non-file input) turns into a broken editor instead of degraded assist. The base class handles the non-file case gracefully; the override should too.

**Evidence:**
```java
catch (Exception e) {
      throw new RuntimeException("Failed to create assist processor.", e);
    }
```

**Fix:** Log and fall back to 'new HTMLAssistProcessor()' (or a TemplateAssistProcessor with null cache) instead of throwing.

#### B13. jseditor is only partially dead: standalone .js editor registration is removable scope, but inner-JS support depends on the package

**design** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/jseditor/editors/JavaScriptEditor.java`

Answering the orchestrator's 'is jseditor dead?' question with nuance: the standalone Parsley JavaScript Editor is registered for ALL .js files (plugin.xml:145-151), bringing along the Rhino-based JavaScriptValidator whose markers were problematic enough that JS validation was defaulted off (HTMLProjectParams.java:32-40 comment). However the htmleditor fork actively imports jseditor classes for embedded `<script>` support: InnerJavaScriptAssistProcessor imports jseditor.editors.JavaScriptAssistProcessor, InnerJavaScriptScanner imports JavaScriptScanner, and (dead) JavaScriptTaskTagDetector imports JavaScriptComment/JavaScriptModel. So: the .js editor registration + JavaScriptEditor/Outline/Validator/HyperlinkDetector/EditorContributor/Configuration are removable as a unit, but JavaScriptScanner/AssistProcessor/Model must stay (or move) for inner-JS assist. Same applies to the CSS editor registration at plugin.xml:138-144 vs InnerCSSScanner/InnerCSSAssistProcessor.

**Evidence:**
```java
class="tk.eclipse.plugin.jseditor.editors.JavaScriptEditor" ... extensions="js"
```

**Fix:** Drop the .js/.css editor registrations from plugin.xml, then prune the editor-only classes while keeping the scanner/assist classes used by the HTML editor.

#### B14. WooCacheEntry.validate() calls lazy getModel() (re-entering cache.parse/validate) unlike its siblings, and re-reads the file as a side effect

**design** · confidence: medium · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WooCacheEntry.java`:21

HtmlCacheEntry.validate() and WodCacheEntry.validate() use _getModel() (no side effects); WooCacheEntry.validate() calls getModel(), which re-enters synchronized(cache) → cache.parse() → cache.validate(false, true) from INSIDE WodParserCache._validate(). It only terminates because the _validating flag short-circuits the nested validate — the same fragile flag shown broken above. It then additionally calls wooModel.loadModelFromStream(wooFile.getContents()) purely to surface load errors, re-reading the .woo from disk during every validation even when the parsed model came from the editor document. No concrete misbehavior today, but it is the only re-entrant path through the validation state machine and will deadlock-or-loop the moment the flag logic is 'fixed' naively.

**Evidence:**
```java
WooModel wooModel = getModel();
```

**Fix:** Use _getModel() like the other entries and surface load errors from the parse step instead of re-reading the file.

#### B15. segmentIndexAt javadoc claims 'counts unescaped dots' but the code counts every dot; operator segments clamp silently

**design** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/util/WodHtmlUtils.java`:117

The javadoc says the method 'counts unescaped dots', but the loop counts all '.' characters with no escape handling — for a keypath the distinction may never matter in practice, but the comment overpromises. Also verified the operator interplay the focus asked about: for 'items.@count', segmentIndexAt returns 1 for a cursor on '@count', while BindingValueKeyPath strips the operator before splitting (indexOf('@'), line 104-108 of BindingValueKeyPath), so getBindingKeys() has length 1; memberForSegment clamps 1 → 0 and opens 'items' — the documented degrade-to-last-resolvable behavior, matching the old whole-path behavior. Not a bug, but worth recording that textual segment indices and resolved-key indices diverge for @operators and |helpers by design, held together only by the clamp.

**Evidence:**
```java
// Count the dots strictly before the offset; that's the segment index.
```

**Fix:** Drop 'unescaped' from the javadoc (or implement backslash-escape skipping if Parsley ever allows escaped dots).

#### B16. Known issue REFUTED/stale: the shared-DOM apiModel synchronization rule no longer applies — the DOM singleton is gone

**cleanup** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/MutableApiModel.java`:26

The known-issue list (and CLAUDE.md) says all validation access to the global WebObjectDefinitions.xml must be synchronized on this.apiModel because it is a shared Xerces DOM. I verified this is now obsolete: the read path parses into immutable ApiSnapshot POJOs (ApiParser javadoc: 'does not retain the DOM'), the global file is parsed once inside the synchronized ensureGlobalApiSnapshots() (ApiUtils.java:151) into a Map<String,ApiSnapshot>, and the write path is MutableApiModel, which parses its own file per editor instance. grep shows zero remaining `synchronized (apiModel)` sites and no DOM-backed ApiModel class. The only residual caveat: ApiSnapshot still has mutation methods (addBinding/removeBinding/setComponentContent), and the same instances are shared via ApiCache and the global map — today the only mutators are MutableApiModel consumers (ApiEditor, GenerateAPIAction) which own private snapshots, so it's safe, but nothing enforces that cached/global snapshots stay unmutated. Recommend updating CLAUDE.md and either splitting read/write types or documenting the invariant on ApiSnapshot.

**Evidence:**
```java
Unlike the old DOM-backed model, there is no shared mutable DOM tree to synchronize on.
```

**Fix:** Update CLAUDE.md/known-issues; consider making read-path ApiSnapshot truly immutable (separate mutable builder for the editor).

#### B17. Both getBindingProblems overloads are dead code; the instance one also discards its typeCache parameter for a fresh TypeCache

**cleanup** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/wod/AbstractWodBinding.java`:521

grep across java/ and test/ finds no callers of the static getBindingProblems(String,String,IType,TypeCache,HtmlElementCache) (line 212) or the instance getBindingProblems(String,IType,TypeCache,HtmlElementCache) (line 521) other than each other. Additionally the instance method passes `new TypeCache()` to fillInBindingProblems while using the `typeCache` parameter only for element.getApi — a latent perf trap if ever resurrected. Per the project's dead-code convention these should be removed (with a build to verify).

**Evidence:**
```java
fillInBindingProblems(element, apiBinding, javaFileType.getJavaProject(), javaFileType, problems, new TypeCache(), htmlCache);
```

**Fix:** Delete both overloads; rebuild to confirm.

#### B18. Stale comment claims validations mark bindings required — no such pass exists in the .apiext parser

**cleanup** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ApiextModel.java`:309

fromDocument's comment says 'First pass: collect bindings named in validations, so we can mark required. (A binding the rules say must be bound is shown as required in the preview.)' but the code parses <validation> into a message-only Validation record and parseBinding derives required exclusively from the `required` attribute. Contrast with ApiParser (the .api path), which really does compute implicit required from single-<unbound> validation patterns (hasImplicitUnboundValidation). So either the .apiext renderer is missing the implicit-required behavior the comment promises, or the comment describes a plan that was dropped — either way it misleads in an 'in-flux format' where the parsability gate makes silent divergence easy to miss.

**Evidence:**
```java
// First pass: collect bindings named in validations, so we can mark required.
```

**Fix:** Delete the comment, or implement the validation-derived required flag to match ApiParser's semantics.

#### B19. LocatePlugin.start()/stop() are dead code; getDefault()'s lazy registration is the only real path, and start() is a latent double-registration if ever wired

**cleanup** · confidence: high · area: `deadcode-tests` · also seen in: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/locate/LocatePlugin.java`:90

KNOWN-ISSUE VERIFY — CONFIRMED and sharpened. MANIFEST.MF declares 'Bundle-Activator: tk.eclipse.plugin.htmleditor.HTMLPlugin' and nothing anywhere calls LocatePlugin.start()/stop() (grep over java/ finds no caller), so lines 90-103 never run. The sole initialization is getDefault() (line 107): it constructs the plugin and registers a ComponentLocateCache listener that is never removed. The 'latent double-registration' is real but currently unreachable: if start() were ever invoked after getDefault(), it would overwrite componentsLocateCache with a NEW instance and register it while the getDefault()-registered one stays registered forever (and stop() would only remove the newer one). Same dead-lifecycle pattern exists on WooeditorPlugin (start():202/stop():212 never called; getDefault():91 lazily registers), and on the other ~8 sub-plugin activator classes (ComponenteditorPlugin, CorePlugin, WodclipsePlugin, EditorsPlugin, ComponentsPlugin, ApieditorPlugin, bindings/Activator, wodclipse.core/Activator) whose start()/stop() also never run. Deleting the dead lifecycle methods (or converting these classes to plain lazy singletons that don't pretend to be activators) removes the trap.

**Evidence:**
```java
public void start(BundleContext context) throws Exception { ... ResourcesPlugin.getWorkspace().addResourceChangeListener(componentsLocateCache); (never called; Bundle-Activator is HTMLPlugin)
```

**Fix:** Delete the never-called start()/stop() on all non-Bundle-Activator plugin classes; keep only the lazy getDefault() path

#### B20. Legacy TagShortcut registry removal inventory: the complete, mechanical deletion list

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/bindings/wod/TagShortcut.java`:10

KNOWN-ISSUE VERIFY (pending deletion) — CONFIRMED, full inventory. DELETE OUTRIGHT: (1) bindings/wod/TagShortcut.java (whole class, 166 lines); (2) preferences/TagShortcutPreferencePage.java (whole class incl. inner TagShortcutDialog, ~290 lines) plus its plugin.xml registration (plugin.xml:719-723, page id 'ng.componenteditor.preferences.TagShortcuts' under the Zombies category); (3) test/org/objectstyle/wolips/bindings/wod/TagShortcutTest.java; (4) bindings/preferences/PreferenceConstants.java:79 TAG_SHORTCUTS_KEY; (5) bindings/preferences/PreferenceInitializer.java:60-130 (the 69 TagShortcut defaults — count verified — plus the setDefault call and the TagShortcut import); (6) ApiCache: _tagShortcuts field (line 25), _tagShortcutsStr field, getTagShortcutNamed() (146-153), getTagShortcuts() (156-166) incl. its re-entrant PreferenceInitializer bootstrap hack; (7) WodParserCache.getTagShortcutNamed (428-430, ALREADY ZERO CALLERS today) and getTagShortcuts (432-434, sole caller TemplateAssistProcessor:184). SIMPLIFY (remove else-branch of if(aliasesActive) splits): FuzzyXMLWodElement.java:36-72 (legacy loop, case-mismatch recording, matchingTagShortcut attribute expansion at 65-71); AbstractWodElement.java:454-460 (legacy suggestion candidates); ElementCatalog.java:188 legacy branch; WodAnnotationHover.java:396-415; ShowInElementReferenceHandler.java:72-79; TemplateAssistProcessor.java:184-190; InlineWodTagInfo.java:27/42 (_tagShortcut field — note the constructor calls ApiCache.getTagShortcutNamed UNCONDITIONALLY even for alias-active projects; harmless because getExpandedElementTypeName checks isActiveFor first, but it forces the legacy preference parse on every completion proposal) and its use at line 88. CASCADE CANDIDATES once legacy branch goes: SimpleWodElement.setTagShortcutCaseMismatch/getTagShortcutCaseMismatch/getTagShortcutCorrectCase (170-190) and their sole consumer AbstractWodElement:302-308 (the case-mismatch is only ever SET in FuzzyXMLWodElement's legacy branch, line 54).

**Evidence:**
```java
plugin.xml:719: <page class="org.objectstyle.wolips.preferences.TagShortcutPreferencePage" ... name="Inline Binding Shortcuts"
```

**Fix:** Delete in the order above; the only behavioral question is whether alias-active projects need a case-mismatch warning replacement

#### B21. PaletteView is a 795-line file with 773 commented-out lines wrapping an empty no-op ViewPart, reachable only from dead code

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `tk/eclipse/plugin/htmleditor/views/PaletteView.java`:13

The class body is 'public void createPartControl(Composite parent) {}' + empty setFocus(); the remaining 773 lines are the entire old GEF palette implementation commented out with no explanatory comment. It is NOT registered as a view in plugin.xml (grep finds no org.eclipse.ui.views entry for it), so the only reference — HTMLSourceEditor.OpenPaletteAction.run() calling showView("tk.eclipse.plugin.htmleditor.views.PaletteView") at HTMLSourceEditor.java:858 — would throw PartInitException if it ever ran. It never runs: the only instantiation of OpenPaletteAction is commented out (HTMLSourceEditor.java:98 '//setAction(ACTION_OPEN_PALETTE, new OpenPaletteAction());', and :278 '//addAction(menu, GROUP_HTML, ACTION_OPEN_PALETTE);'). Cascade deletions: OpenPaletteAction inner class (HTMLSourceEditor:847-864), ACTION_OPEN_PALETTE constant (:82), HTMLPlugin.ICON_PALETTE (:94) + its image-registry entry (:267), and the 'HTMLEditor.OpenPaletteAction' resource string.

**Evidence:**
```java
public class PaletteView extends ViewPart {
  @Override
  public void createPartControl(Composite parent) {
  }
 ... //  private PaletteViewer viewer;
```

**Fix:** Delete PaletteView.java and the OpenPaletteAction/ACTION_OPEN_PALETTE/ICON_PALETTE chain in HTMLSourceEditor/HTMLPlugin

#### B22. Twelve whole classes (~641 lines) have zero references anywhere in java/, test/, or plugin.xml

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `ng/componenteditor/NGComponentEditorPlugin.java`:9

Verified by per-class whole-word grep across java/, test/, plugin.xml, MANIFEST.MF, *.properties (fully-qualified names in XML would still match): (1) ng/componenteditor/NGComponentEditorPlugin.java — 15 lines, its PLUGIN_ID constant is read by nobody; (2) editor/actions/AddActionAction.java (30), (3) editor/actions/AddKeyAction.java (30), (4) editor/actions/CleanWOBuilderElementNamesAction.java (26) — old action classes with no plugin.xml actionSets/handlers pointing at them; (5) wodclipse/core/refactoring/DeleteComponentRefactoring.java (48); (6) editor/api/DeletePage.java (138); (7) wodclipse/core/parser/BindingNamespaceRule.java (55); (8) tk/eclipse/plugin/htmleditor/assist/FieldAssistUtils.java (143); (9) locate/result/DefaultLocateResult.java (62); (10) jp/aonir/fuzzyxml/util/ElementFilter.java (29); (11) wodclipse/core/woo/eomodel/EOModelException.java (60); (12) editor/template/ITemplateValidationMarkerCreator.java (5-line interface, no implementors, no callers). All are safe deletes per the project's dead-code policy; verify with the standard tycho build after removal.

**Evidence:**
```java
public class NGComponentEditorPlugin {
	public static final String PLUGIN_ID = "ng.componenteditor"; (zero references to NGComponentEditorPlugin outside its own file)
```

**Fix:** Delete all 12 files, clean imports, run mvn verify, note in CHANGES.md

#### B23. WooeditorPlugin.start()/stop() never run; the workspace listener is registered as a permanent side effect of getDefault() the first time a WOO editor logs or needs colors

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/editor/woo/WooeditorPlugin.java`:202

Companion to the resourceChanged finding: MANIFEST.MF's Bundle-Activator is HTMLPlugin, and nothing calls WooeditorPlugin.start()/stop(), so the start() registration (line 205) and the stop() cleanup (line 222, including formColors.dispose()) are dead. The live path is getDefault() (line 91-97): first call — triggered by WooEditor.getFormToolkit() or any WooEditor error logging — constructs the plugin and does 'plugin.workspace.addResourceChangeListener(plugin)', and that full-delta-walking listener stays registered for the rest of the session even after all WOO editors close. Also note stop()'s ordering bug if it ever were called: 'super.stop(context)' runs in finally BEFORE removeResourceChangeListener, and 'plugin = null' races getDefault()'s re-creation. Cleanest fix given the merged-bundle reality: drop the IResourceChangeListener from the plugin class entirely and scope encoding-sync registration to open WooEditor instances (WooEditor already manages its own listener correctly, adding at line 182 and removing at 189).

**Evidence:**
```java
public static synchronized WooeditorPlugin getDefault() {
    if (plugin == null) {
      plugin = new WooeditorPlugin();
      plugin.workspace.addResourceChangeListener(plugin);
```

**Fix:** Delete dead start()/stop(); move or scope the encoding-sync listener to WooEditor lifetime

#### B24. WodParserCache.getTagShortcutNamed(String) has zero callers

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:428

Pure delegating wrapper around ApiCache.getTagShortcutNamed; grep across java/, test/, plugin.xml finds no call site (the only '.getTagShortcutNamed(' calls go directly to ApiCache from WodAnnotationHover:410, ShowInElementReferenceHandler:72, InlineWodTagInfo:42). Its sibling getTagShortcuts() (line 432) has exactly one caller (TemplateAssistProcessor:184, inside the legacy else-branch). Both disappear with the TagShortcut registry removal, but getTagShortcutNamed is deletable today.

**Evidence:**
```java
public TagShortcut getTagShortcutNamed(String shortcut) {
    return ApiCache.getTagShortcutNamed(shortcut);
  }
```

**Fix:** Delete the method now (and the TagShortcut import once getTagShortcuts goes too)

#### B25. Unused preference constant PARENT_BINDING_VALUE plus commented-out WO54 remnants with no explanation

**cleanup** · confidence: high · area: `deadcode-tests`

**Location:** `org/objectstyle/wolips/wodclipse/core/preferences/PreferenceConstants.java`:66

Swept every 'public static final String' constant in both PreferenceConstants classes against all of java/: PARENT_BINDING_VALUE ("ParentBindingValue") is read by nothing — no initializer sets a default for it and no code reads it. Related unexplained commented-out remnants in the bindings plugin: org/objectstyle/wolips/bindings/preferences/PreferenceConstants.java:83 '// public static final String WO54_KEY = "WO 5.4";' and org/objectstyle/wolips/bindings/Activator.java:64-66 (commented-out isWO54(IProject) method). Also TagShortcutPreferencePage.processChange (lines ~182-196) contains a commented-out workspace-rebuild loop with no explanatory comment (moot once the page is deleted per the registry inventory). All fall under the project's stated dead-code policy.

**Evidence:**
```java
public static final String PARENT_BINDING_VALUE = "ParentBindingValue"; (zero readers outside the declaring file)
```

**Fix:** Delete PARENT_BINDING_VALUE and the commented WO54/rebuild blocks

#### B26. TemplateSourceEditor.dispose() can construct a WodParserCache during disposal

**cleanup** · confidence: high · area: `editor-ui`

**Location:** `org/objectstyle/wolips/editor/template/TemplateSourceEditor.java`:158

dispose() calls getParserCache(), which when _cache is null (input changed via doSetInput, or the editor is disposed before first parse) runs the full WodParserCache.parser(inputFile) locate/creation machinery just to immediately null out the html document on it. Worst case it fabricates a cache entry for a component that was never parsed; best case it is wasted work during teardown. WodEditor.dispose() (WodEditor.java line 264) has the same shape.

**Evidence:**
```java
public void dispose() {
    try {
      WodParserCache cache = getParserCache();
      cache.getHtmlEntry().setDocument(null);
```

**Fix:** Only clear the document when _cache != null (skip lazy creation in dispose)

#### B27. Class javadoc documents a detection step ('project.name without project.base → WO') that getProjectType() does not implement

**cleanup** · confidence: high · area: `explorer-wizards-locate`

**Location:** `org/objectstyle/wolips/variables/ParsleyProject.java`:28

The class-level javadoc lists priority 3 as '{@code project.name} in build.properties (without explicit project.base) → ProjectType#WO', matching legacy WOLips semantics (presence of build.properties marked a WO project). getProjectType() (lines 89-111) implements only project.base checks and the classpath probe — there is no PROJECT_NAME consultation (the Key.PROJECT_NAME constant is otherwise unused for detection). Consequence beyond doc drift: a legacy WO project whose build.properties has project.name but whose classpath probe fails (e.g. WOElement not yet resolvable during import/indexing) is classified UNKNOWN instead of WO, so decorators/editor-association skip it, where the documented behavior would have claimed it.

**Evidence:**
```java
*   <li>{@code project.name} in build.properties (without explicit project.base) → {@link ProjectType#WO}</li>
```

**Fix:** Either implement the project.name fallback in getProjectType() or delete the stale javadoc line (the method-level javadoc already omits it).

#### B28. Mojibake (corrupted Shift-JIS) comments throughout the core impl classes

**cleanup** · confidence: high · area: `fuzzyxml`

**Location:** `jp/aonir/fuzzyxml/internal/FuzzyXMLElementImpl.java`:108

FuzzyXMLElementImpl, AbstractFuzzyXMLNode, FuzzyXMLAttributeImpl and FuzzyXMLDocumentImpl still carry the original Japanese javadoc/comments, now byte-corrupted into mojibake (e.g. line 108 'XML�̒f�Ѓe�L�X�g...'). They document non-trivial offset-maintenance semantics (appendChild/appendOffset/fireModifyEvent contracts) that nobody can read, which directly conflicts with the project convention of thoroughly commenting Eclipse-era subtleties. Worth rewriting in English before the parslips.tooling extraction freezes this API.

**Evidence:**
```java
* XML�̒f�Ѓe�L�X�g����q�m�[�h�Q��ǉ����܂��B
```

**Fix:** Replace mojibake comments with English javadoc describing the offset-update contracts.

#### B29. Task-tag subsystem is unreachable: nature and builder are never registered in plugin.xml

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/HTMLProjectNature.java`:17

plugin.xml contains no org.eclipse.core.resources.natures or .builders extensions (verified by grep), yet HTMLProjectNature.HTML_NATURE_ID/HTML_BUILDER_ID reference 'tk.eclipse.plugin.htmleditor.HTMLProjectNature/Builder'. Consequences: (a) HTMLProjectBuilder.build() can never run — the builder id doesn't exist; (b) HTMLProjectPropertyPage still shows a 'Detect task tags' checkbox (line 127/154/177) whose save path (HTMLProjectParams.save -> addNature, lines 208-212/217-230) writes an undefined nature id into the project's .project file, so the checkbox appears to persist while the feature silently does nothing; (c) HTMLTaskTagPreferencePage is still registered at plugin.xml:708 and its 'rebuild' call (HTMLProjectBuilder.doBuild, line 130) is a no-op/error. The whole tasktag package (AbstractTaskTagDetector, HTMLTaskTagDetector, JavaScriptTaskTagDetector, TaskTag, HTMLTaskTagPreferencePage), HTMLProjectBuilder, HTMLProjectNature, and the detectTaskTag plumbing in HTMLProjectParams/HTMLProjectPropertyPage are a coherent dead subsystem, safe to remove.

**Evidence:**
```java
public static final String HTML_BUILDER_ID = "tk.eclipse.plugin.htmleditor.HTMLProjectBuilder";  // no matching <extension point="org.eclipse.core.resources.builders"> anywhere
```

**Fix:** Delete tasktag/*, HTMLProjectBuilder, HTMLProjectNature, the plugin.xml:708 pref page, and detectTaskTag UI/persistence; document in CHANGES.md.

#### B30. Palette subsystem is dead: PaletteView is not registered as a view and all call sites are commented out

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/views/PaletteView.java`:40

plugin.xml's org.eclipse.ui.views extension (line 473) registers only Bindings Inspector, Parsley Explorer, and Element Reference — no PaletteView. PaletteView's own body is largely commented out (lines 40-42), HTMLSourceEditor's OpenPaletteAction inner class is never instantiated (setAction(ACTION_OPEN_PALETTE,...) is commented at HTMLSourceEditor.java:98; the class remains at line 847), and HTMLPlugin.getPaletteContributer/getPaletteContributerGroups/loadPalleteContributer (HTMLPlugin.java:470-514) plus the 'paletteItem' extension point (plugin.xml:10) have no live consumers. Caveat: IPaletteTarget/HTMLEditor.getPaletteTarget() IS still used by HTMLEditorContributor.java:38 to unwrap the inner source editor, so keep that accessor (possibly renamed) while deleting the rest.

**Evidence:**
```java
//    String[] groups = HTMLPlugin.getDefault().getPaletteContributerGroups();
```

**Fix:** Remove PaletteView/IPaletteItem/DefaultPaletteItem/IPaletteContributer, the paletteItem ext point, HTMLPlugin palette accessors, OpenPaletteAction and ACTION_OPEN_PALETTE/ACTION_CHOOSE_COLOR; retain the getPaletteTarget unwrap used by HTMLEditorContributor.

#### B31. Seven declared extension points with zero extensions and dead accessor methods in HTMLPlugin

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/HTMLPlugin.java`:462

plugin.xml:8-16 declares customTagConverter, customTagAttributeAssist, paletteItem, noValidationNatures, fileAssistProcessor, hyperlinkProvider, tldLocator, customTagValidator, preferenceContributer. Only fileAssistProcessor has a registered extension (plugin.xml:842). Verified no callers exist for HTMLPlugin.getHyperlinkProviders(), getCustomTagContributer(), getCustomTagValidatorContributer(), getNoValidationNatureId(); getCustomTagAttributeAssists() has only a commented-out call (TemplateAssistProcessor.java:325). The real hyperlink provider is wired programmatically: TemplateConfiguration.createHyperlinkDetector() calls hyperlinkDetector.addHyperlinkProvider(new InlineWodElementHyperlinkProvider()) — the extension-point path is vestigial. 'tldLocator' has no code at all; IHTMLPreferenceContributer is only read by HTMLPreferenceInitializer:84 against an empty point.

**Evidence:**
```java
public IHyperlinkProvider[] getHyperlinkProviders(){  // grep: no callers outside HTMLPlugin.java
```

**Fix:** Delete the unused extension points, their accessor methods, and interfaces ICustomTagConverter(Contributer), ICustomTagValidator(Contributer), ICustomTagAttributeAssist, IHTMLPreferenceContributer.

#### B32. Radar check: EmptyWoFolderCleaner does NOT have the WooeditorPlugin-style delta problem (confirmed well-behaved), but posts a UI runnable per .wo change

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/EmptyWoFolderCleaner.java`:71

Explicit confirm/refute of the radar item for this area: unlike WooeditorPlugin.resourceChanged, this listener prunes derived resources (returns false at line 71-73) and stops descending into .wo folders (returns false at line 82). install/uninstall are symmetric with HTMLPlugin.start/stop. The only residual cost: every CHANGED event on any .wo folder (i.e. every save of a file inside a bundle template) posts a Display.asyncExec that calls woFolder.members() — harmless singly, but it is one more per-save UI-thread hop in a plugin that already has ~9 workspace listeners. Could cheaply skip when the delta shows no REMOVED children.

**Evidence:**
```java
if (resource.isDerived()) {
			return false;
		}
```

**Fix:** Optionally only call deleteIfEmpty when delta.getAffectedChildren(IResourceDelta.REMOVED).length > 0.

#### B33. getAutoEditStrategies() bypasses the cached strategy; getAutoEditStrategy()/_autoEditStrategy are dead

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/editors/HTMLConfiguration.java`:162

getAutoEditStrategies() (line 161-163) calls createAutoEditStrategy() directly, creating a new instance per call, while the lazily-cached public getAutoEditStrategy() (lines 106-121) has no callers anywhere (verified by grep). Either the cache should be used in getAutoEditStrategies (JFace calls it per content type — currently one instance per content type) or the getter and _autoEditStrategy field should be deleted. Similarly TemplateConfiguration declares 'private IContentAssistant _assistant;' (TemplateConfiguration.java:35) that is never read or written — dead field shadowing the superclass's private one.

**Evidence:**
```java
return new IAutoEditStrategy[]{ createAutoEditStrategy() };
```

**Fix:** Use getAutoEditStrategy() in getAutoEditStrategies() and delete TemplateConfiguration._assistant.

#### B34. No-op statement in updateFolding empty-tag branch: substring result discarded

**cleanup** · confidence: high · area: `htmleditor-fork`

**Location:** `tk/eclipse/plugin/htmleditor/editors/HTMLSourceEditor.java`:702

In the empty-tag folding branch, 'text.substring(0, text.length() - 1);' discards its result — the intended stripping of the trailing '/' never happens, so for '<br/>' (no space before /) the FoldingInfo type is stored as 'br/' instead of 'br'. Harmless in practice because self-closing tags never need close-tag matching, but it is exactly the kind of dead statement CLAUDE.md asks to remove or fix.

**Evidence:**
```java
text.substring(0, text.length() - 1);
```

**Fix:** Either assign the result (text = text.substring(...)) or delete the line.

#### B35. _elementTypeCache leaks entries for closed projects

**cleanup** · confidence: medium · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodCompletionUtils.java`:39

The static Map<IProject, TypeNameCollector> is only cleared by clearElementTypeCacheForProject, which the invalidator calls on .java file deltas. Project close produces a POST_CHANGE delta without per-file children (and PRE_CLOSE has no delta at all), so a closed project's TypeNameCollector — holding a Set of all element type names plus IType handles and the IJavaProject reference — stays in the map for the life of the session. Same pattern applies to TemplateAssistProcessor's tag-info cache if it is keyed the same way. Bounded by project count so growth is slow, but it pins JDT handles for projects the user closed to free resources.

**Evidence:**
```java
private static Map<IProject, TypeNameCollector> _elementTypeCache = new HashMap<IProject, TypeNameCollector>();
```

**Fix:** Clear the entry on project CLOSED/REMOVED deltas (project-level delta kinds) in WodParserCacheInvalidator.

#### B36. Dead _undoManager field allocates a TextViewerUndoManager per cache instance

**cleanup** · confidence: high · area: `wodclipse-core`

**Location:** `org/objectstyle/wolips/wodclipse/core/completion/WodParserCache.java`:46

_undoManager is assigned new TextViewerUndoManager(25) in init() and never read anywhere in the codebase (only the declaration and assignment exist). It's a JFace object with undo-history bookkeeping allocated for every WodParserCache — pure dead weight from the WOLips extraction, and it drags the org.eclipse.jface.text.TextViewerUndoManager import into an otherwise headless-leaning class (relevant to the parslips.tooling decoupling goal).

**Evidence:**
```java
private TextViewerUndoManager _undoManager;
```

**Fix:** Delete the field, assignment, and import; verify with mvn build per CLAUDE.md dead-code protocol.


### C. Refuted (2) — knocked out by verification

#### C1. Known issue CONFIRMED and sharpened: forProject re-does JAR I/O not just per call but per ELEMENT — a fresh TypeCache/ApiCache is created inside the loop

**perf** · confidence: high · area: `bindings-core`

**Location:** `org/objectstyle/wolips/bindings/api/ElementCatalog.java`:228

definitionKindFor is called once per catalog entry and constructs `new TypeCache().getApiCache(project)` each time, so every cached negative/positive result is thrown away per element: for each of the hundreds of elements on a classpath, findApiextBytes opens the containing jar, findGlobalApiextBytes probes bundle entries, and findApiSnapshot re-opens the jar and re-parses the .api XML. renderCardBody (line 283) repeats the same fresh-TypeCache pattern per selection, re-parsing the .api already parsed during enumeration. The 'redo work per call' design is documented as deliberate, but the per-element throwaway cache is not required by that design — hoisting a single TypeCache local to forProject (and one to renderCardBody) keeps the no-global-cache property while cutting jar opens/XML parses dramatically. Correctness of results is otherwise fine (I checked the precedence order matches the hover's: project .apiext > bundled .apiext > .api > global). Minor duplication: lines 221-222 call findGlobalApiextBytes(simpleName) twice when type == null.

**Evidence:**
```java
if (type != null && ApiUtils.findApiSnapshot(type, new TypeCache().getApiCache(project)) != null) {
```

**Verifier's correction:** The fresh-TypeCache-per-element pattern is pointless code (a hoisted instance would be marginally cleaner) but has no measurable perf effect: caches are per-type-FQN and each type is looked up once per enumeration, so hoisting saves nothing. The real per-element jar I/O in forProject is inherent to cold lookups (findApiextBytes/findApiFile open the jar per element and use no cache) and is already acknowledged and mitigated by running on a background Job. Two valid minor points survive: (1) renderCardBody re-opens/re-parses one .api per selection click that the enumeration already parsed — fixable only with a cache shared across calls, not a local hoist; (2) at lines 221-222 the second findGlobalApiextBytes(simpleName) call is always redundant (not just when type == null), since findGlobalApiextBytes internally falls back to the simple name.

**Why it fell:** The observed pattern is real — definitionKindFor (ElementCatalog.java:228) and renderCardBody (:283) each build `new TypeCache().getApiCache(project)`, and TypeCache._apiCache is per-instance, so each call gets an empty ApiCache. But the claimed perf consequence and fix don't hold: ApiCache is keyed by fully-qualified type name, forProject iterates a Set of type names (TypeNameCollector._typeNames is a Set, no duplicates) calling findApiSnapshot exactly once per type, so a TypeCache hoisted to a forProject local would get zero cache hits — every lookup is a cold miss either way, saving no jar opens or XML parses. The dominant per-element I/O (findApiextBytes' per-element JarFile opens, findGlobalApiextBytes' bundle probes) takes no cache parameter at all, and the one repeatedly-hit path (WebObjectDefinitions.xml for framework-private elements) is already statically cached in ApiUtils._globalApiSnapshots. renderCardBody makes exactly one findApiSnapshot call per invocation, so a local hoist there is equally a no-op; the only real redundancy is one re-parse per user click, fixable only by a cache shared across calls — precisely the persistent caching the documented "redo work per call" design (ElementHelpView.reload javadoc; enumeration already moved to a background Job in commit 8c9d45624) declines to keep.

#### C2. Template validation runs on a request thread — possible unsynchronized shared-DOM access (cross-area, verify in WodBuilder)

**threading** · confidence: low · area: `devserver`

**Location:** `java/org/objectstyle/wolips/devserver/ValidateComponentHandler.java`:94

THREADING (radar cross-check). The radar rule states the global API model (WebObjectDefinitions.xml) is a shared Xerces DOM singleton and ALL access must be synchronized on apiModel because Xerces DOM is not thread-safe even for reads. ValidateComponentHandler.handle calls WodBuilder.validateComponent(...) directly on the dev-server REQUEST thread (line 94), concurrently with the editor's own validation threads. The handler javadoc (lines 44-46) only claims 'workspace operations take their own locks' — it says nothing about the apiModel monitor. Whether this is an actual race depends on whether WodBuilder.validateComponent and the code it reaches synchronize on the shared apiModel; that lives outside the devserver package and I did not verify it here. Flagging because the dev server introduces a NEW concurrent entry point into that validation path that the original editor-only design didn't have. Needs confirmation against WodBuilder / the validation model.

**Evidence:**
```java
WodBuilder.validateComponent(validationResource, false, new NullProgressMonitor());
```

**Why it fell:** The shared-DOM premise is obsolete: the DOM-backed ApiModel was deleted in commit 455a5e8d7 (2026-03-01, "Rewrite API editor to use POJOs, delete 19 DOM classes"), and MutableApiModel's javadoc explicitly states "there is no shared mutable DOM tree to synchronize on"; no `apiModel` monitor sites remain anywhere in the plugin. The global WebObjectDefinitions.xml is parsed once inside static synchronized ApiUtils.ensureGlobalApiSnapshots() (bindings/api/ApiUtils.java:151) into a map of effectively-immutable ApiSnapshot POJOs — the Xerces Document is method-local in ApiParser and discarded, so validation threads never touch a shared DOM. Additionally, WodBuilder already validates from a fixed thread pool of availableProcessors*2 threads (WodBuilder.java:81), so the dev-server request thread is not a new kind of concurrency for this path, and ApiCache uses synchronized maps. The radar rule (and the matching CLAUDE.md note) is stale documentation describing the pre-March-2026 architecture.

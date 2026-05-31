# Wiring Audit: accidentally-disabled, half-wired, and dead code in ng.componenteditor

**Status:** Findings document — investigation only. Not executed.

**Method:** We enumerated every entry point Eclipse can reach — the 31 extension points and ~97 `class=` registrations in `plugin.xml`, the `Bundle-Activator` + lazy activation in `META-INF/MANIFEST.MF`, the custom `ng.componenteditor.fileAssistProcessor` point, and adapter-factory registrations — then ran the inverse query: classes that are *shaped* like live participants (extend `IncrementalProjectBuilder`, implement `IProjectNature` / `IResourceChangeListener`, look like handlers) but are not registered to run. Each candidate was diffed against the pre-extraction WOLips tree at `/Users/hugi/git/wolips-original/wolips/plugins` to distinguish "always dead" from "the wiring was silently dropped during extraction." Findings were produced by multiple agents and then independently re-verified adversarially — every claim below survived an explicit attempt to refute it (checking reflection, adapter factories, extension-point instantiation, and lazy activation before concluding "not reached"). Refuted findings were discarded; several survivors were downgraded in severity and are marked as such.

## Executive summary

The headline: **at least four things that the original WOLips product ran are not running here, and at least one live code path misbehaves.** None of them are component-editing essentials — editing, completion, and on-open validation all work — but they are bugs disguised as code, and the most important is a real *behavior reduction*, not mere dead weight.

Final (post-verification) category counts:

- **Should be running but isn't (Category 1):** 1 live-but-broken code path (`ComponentEditorPart` close-on-project-close indexing bug).
- **Half-wired / silently narrowed (Category 2):** 7 distinct defects — the lost project-wide validation/marker-sweep (the big one), open-editors-only revalidation on Java/API change, the split-and-narrowed `.api` revalidation, the ignored CONTENT delta, the no-op "re-validate on rule change" preference, the empty *Parsley* Navigate submenu, the always-false NSKVC branch, and the property-page limb that writes a phantom nature.
- **Dead / disabled in place (Category 3):** 1 cluster — the entire `HTMLProjectBuilder` + `HTMLProjectNature` + task-tag subsystem (~6 classes plus a registered-but-useless preference page).
- **Uncertain / flagged for verification (Category 4):** 1 — `ContentDescriberWO`'s process-global static flag.

The recurring root cause: **extraction systematically dropped build-time wiring.** The original drove validation and marker maintenance through a registered project builder + nature (`org.objectstyle.wolips.builders` + `org.eclipse.core.resources.builders` + `org.eclipse.core.runtime.natures`). Our `plugin.xml` registers *no* builder and *no* validation nature at all. Everything that depended on "the builder visits the whole project on build" is now either gone or narrowed to "whatever happens to be open in an editor."

---

## Category 1 — Should be running but isn't (rewire candidates)

### 1.1 `ComponentEditorPart.resourceChanged` uses the wrong loop index when closing editors on project close

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/editor/component/ComponentEditorPart.java:493-516`
- **Evidence:** `ComponentEditorPart` (line 85) implements `IResourceChangeListener` and self-registers in its constructor (line 109, `ResourcesPlugin.getWorkspace().addResourceChangeListener(this)`); `ComponentEditor` (registered in `plugin.xml:70`) extends it and does not override `resourceChanged`. So this method *runs* on every `PRE_CLOSE`. On project close the outer loop variable is `i` (over `componentEditorInput.getInput()`) and the inner loop variable is `j` (over the workbench pages), but the body indexes `pages[i]`, `pages[i].findEditor(...)`, and `pages[i].closeEditor(...)` using the **outer** index `i` instead of the inner index `j` (lines 502, 504). The inner `j` loop body never references `j`, so it is effectively dead and only `pages[i]` is ever consulted.
- **Why this is Category 1, not half-wired:** the code is fully wired and runs; it is a latent *logic* bug in a live path, not dropped wiring. (This is the one finding the adversarial pass *re-categorized* — from half-wired up to should-run — precisely because both ends connect and it executes.)
- **Impact (downgraded from the original write-up):** In the dominant single-page case (`pages.length == 1`) `pages[0]` is always valid and the loop breaks on first successful close, so the `ArrayIndexOutOfBounds` is an edge case (page-0 lookup/close must fail first). The real, reliably-reproducible defect is the multi-page miss: component editors open on workbench pages other than `pages[0]` are not closed when their project closes, because the code never iterates to `pages[j]`.
- **Recommendation:** investigate / fix. Change `pages[i]` to `pages[j]` in the inner loop body, and reconsider whether `closeEditor` should target the editor actually found rather than always `ComponentEditorPart.this`.
- **Confidence:** medium (bug confirmed verbatim; severity in the common case is modest).

---

## Category 2 — Half-wired / silently narrowed

These run, but under-deliver versus what the shape of the code (or the original product) implies. The first three are facets of the same lost subsystem and pair directly with `proposal-revalidate.md`.

### 2.1 Project-wide build-time component validation + stale-marker sweep is gone; only open editors validate now

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/wodclipse/core/builder/WodBuilder.java:75-218` (now a static utility); live callers `.../editor/component/JavaChangeRevalidator.java:127` and `.../wodclipse/core/completion/WodParserCache.java:364`.
- **Evidence:** `WodBuilder` is now a plain class with only static `validateComponent` / `_validateComponent` plus a `ValidationProgressJob` — no `extends`, no `build()`, no `handleWoappResources` / `handleSource` / `buildStarted` (their deletion is documented at `CHANGES.md:411`). Our `plugin.xml` declares **no** `org.eclipse.core.resources.builders` and **no** custom `org.objectstyle.wolips.builders` extension, and no validation nature (the only `nature=` in `plugin.xml:614` is an unrelated `org.eclipse.jdt.core.javanature` reference on a navigator/query participant). So nothing drives `WodBuilder` at build time. `validateComponent` is reached only from (a) `WodParserCache` when a template is parsed/edited and (b) `JavaChangeRevalidator.revalidateOpenComponents`, which iterates `page.getEditorReferences()` and validates only currently-open `ComponentEditor` instances. `deleteProblems` still exists (`WodModelUtils.java:82`) and `TEMPLATE_PROBLEM_MARKER` is registered (`plugin.xml:747`), but nothing calls `deleteProblems` on a project-wide sweep — only `WodParserCache`/`AbstractCacheEntry` clear markers for the file currently in the cache.
- **Original comparison:** `wolips-original/.../org.objectstyle.wolips.wodclipse.core/plugin.xml:27-30` registered `WodBuilder` via `org.objectstyle.wolips.builders`; the original class extended `AbstractFullAndIncrementalBuilder` and its `handleWoappResources` (original `WodBuilder.java:225-291`) ran a per-resource `deleteProblems`-then-revalidate sweep over **every** `.wo` / `.html` / `.wod` / `.woo` resource on full/incremental builds, deleting stale markers on FULL/CLEAN builds even when validation was off. The top-level WO nature + `org.eclipse.core.resources.builders` registration (`org.objectstyle.wolips/plugin.xml:9-19`) attached it. None of that survives here.
- **Impact:** "Build-time validation" is, in our plugin, save/editor-time validation. After a branch switch, a dependency change, or a bulk edit, a closed component whose binding error was *fixed* keeps its red marker, and a closed component newly *broken* shows no marker, until it is opened and reparsed. Standalone `.html` templates outside `.wo` folders are likewise validated only via the editor cycle, not by a build. There is no project-wide "validate all components" pass and no build-triggered stale-marker cleanup. Note `CHANGES.md:407` calls the old builder "never invoked" (true for the extraction *snapshot*, since the coordinator plugin was already gone) while `CHANGES.md:1067-1090` still advertises "build-time validation" for standalone templates and references `handleWoappResources()`/`handleSource()` methods that no longer exist — the documentation overstates the surviving wiring.
- **Recommendation:** leave-with-comment. The reduction appears to be an intentional consequence of dropping the WOLips builder framework, not an accidental orphan — but the staleness gap should be documented as a known limitation (and `CHANGES.md` corrected to say "editor/save-time validation"), rather than left as a silent regression. If proactive project-wide validation is wanted back, that is a design effort tracked alongside `proposal-revalidate.md`.
- **Confidence:** high.

### 2.2 `JavaChangeRevalidator` revalidates only OPEN component editors on Java/API change

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/editor/component/JavaChangeRevalidator.java:105-135` (`revalidateOpenComponents`).
- **Evidence:** The listener is live — installed via `HTMLPlugin.start()` (`tk/eclipse/plugin/htmleditor/HTMLPlugin.java:180`, uninstalled at `:281`) as a `POST_CHANGE` `IResourceChangeListener` (lines 41-46). `resourceChanged()` correctly collects all projects with Java changes plus a workspace-wide `apiChanged` flag (lines 64-94), but `revalidateOpenComponents` then iterates only `page.getEditorReferences()`, calls `ref.getEditor(false)` (does not materialize unopened editors), filters on `instanceof ComponentEditor` (lines 114-118), and only then calls `WodBuilder.validateComponent` (line 127). Closed components are never revalidated. Even the class javadoc (lines 25-36) silently scopes the API path to "all **open** component editors."
- **Original comparison:** the original registered builder revalidated *all* affected component resources in the project on any Java/API/template change, independent of editor state. Our `JavaChangeRevalidator` is the stand-in, and it only covers open editors.
- **Impact:** Edit a Java method that fixes or breaks a binding keypath used by a closed component, and that component's markers go stale until it is opened and reparsed. Same for required-binding changes in a shared `.api` consumed by closed components. The Problems view is silently inaccurate for everything not open.
- **Recommendation:** investigate. Restoring closed-component coverage requires computing the dependency graph (the cost the original avoided by being a build visitor). Pairs with `proposal-revalidate.md`.
- **Confidence:** high.

### 2.3 `WodParserCacheInvalidator` `.api` branch clears caches but never revalidates

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/wodclipse/core/completion/WodParserCacheInvalidator.java:77-85`.
- **Evidence:** On an `.api` change the invalidator calls `ApiCache.clearCacheForElementNamed` and `TemplateAssistProcessor.clearTagInfoCacheForProject`, but does no revalidation — no `WodBuilder.validateComponent`, no marker sweep. Revalidation of `.api` changes is delegated entirely to `JavaChangeRevalidator` (finding 2.2, open-editor-only). No other listener picks up the slack: `plugin.xml` has zero `builders` / `natures` extensions, and the other `IResourceChangeListener`s (`ComponentLocateCache`, `EmptyWoFolderCleaner`, `PulledUpFolderRefresher`, the Woo editor listener, editor parts) contain no `.api` or validate handling.
- **Original comparison:** in the original, the `.api` delta fed the incremental builder, which revalidated dependent components workspace-wide (the original even comments "shouldn't we validate all files using the api?"). Here the behavior is *split* (cache-clear lives in `WodParserCacheInvalidator`) and *narrowed* (revalidate lives only in `JavaChangeRevalidator`'s open-editor loop).
- **Impact:** Marking a binding required/optional in a shared `.api` updates caches but leaves stale required-binding markers on any consuming component not currently open.
- **Recommendation:** investigate (same dependency-graph design question as 2.2). Pairs with `proposal-revalidate.md`.
- **Confidence:** medium.

### 2.4 Validation-rule preference change updates the model but never re-validates

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/preferences/BindingValidationRulePreferencePage.java:172-189` (`processChange`).
- **Evidence:** The page is registered and reachable (`plugin.xml:644`). `processChange()` detects a rule change (`BindingValidationRule.hasChange`) and calls `syncModels()` to persist it, but the entire re-validation block (lines 175-187) — which iterated `root.getProjects()` and called `projects[i].build(IncrementalProjectBuilder.CLEAN_BUILD, ...)` — is commented out. Nothing replaces it. There is no `IPropertyChangeListener` on the rules key and no adapter/extension that revalidates on preference change.
- **Original comparison:** the commented-out block relied on the project builder: `CLEAN_BUILD` triggered `WodBuilder` to re-validate every component against the new rules. With the builder/nature dropped, even un-commenting the block would no-op.
- **Impact:** After editing binding-validation rules, existing markers across the workspace are not refreshed. The new rules *are* applied lazily on the next validation run — `ApiCache.getBindingValidationRules()` (`ApiCache.java:170-177`) re-reads and re-parses the live preference value, consumed at `AbstractWodBinding.java:232` — so there is no permanently-stale parsed-rule cache. But nothing proactively re-validates: open editors only self-correct on the next reconcile, and closed components stay stale indefinitely. The preference appears to take effect (it saves) but its visible result is silently deferred.
- **Recommendation:** investigate. Pairs with `proposal-revalidate.md` — the natural fix is the same workspace revalidation pass that 2.1–2.3 want.
- **Confidence:** high.

### 2.5 `WodParserCacheInvalidator` ignores plain CONTENT changes to `.wod`/`.html`/`.woo` files in `.wo` folders (ENCODING only)

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/wodclipse/core/completion/WodParserCacheInvalidator.java:86-114`.
- **Evidence:** For files inside a `.wo` folder the invalidator calls `WodParserCache.invalidateResource` only on ADDED, REMOVED, or CHANGED-with-`IResourceDelta.ENCODING` (line 111). A plain CONTENT change (external edit, git checkout, save from a non-component editor) carries `IResourceDelta.CONTENT`, not `ENCODING`, and is silently ignored — the invalidator never evicts the entry. This `ENCODING`-only filter is byte-identical to `wolips-original` (original lines 113-114).
- **Original comparison:** the narrow filter was deliberate *there* because the registered `WodBuilder` reparsed and revalidated component files on every CONTENT delta, so the invalidator only needed to catch encoding/add/remove. With the builder gone, nothing in *our* plugin acts on CONTENT deltas to these files.
- **Impact (downgraded from the original write-up):** The strong "operates on stale parsed content" claim is **refuted** by `AbstractCacheEntry.shouldParse()` (lines 241-242), which returns true whenever `_file.getModificationStamp() != _lastParseTime`. An external CONTENT change bumps the modification stamp, so the next access (completion, validation, outline, all route through `WodParserCache.parse()` → `shouldParse()`) reparses from disk. The parser cache is self-healing on content change. The genuine residual is the same one as 2.1: validation **markers** for a closed component are not proactively re-run when its `.wo` files change on disk — which is the dropped builder marker-sweep, not a stale-parser-cache bug. (`invalidateResource` only evicts the LRU entry; it never revalidated, even in the original.)
- **Recommendation:** leave-with-comment. The mechanism is real but its impact is materially smaller than the parser-cache framing suggested; the marker residual is already captured by 2.1.
- **Confidence:** medium.

### 2.6 Empty *Parsley* Navigate submenu — switch-to menu actions dropped on extraction

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/plugin.xml:288-293` (menu id `ng.componenteditor.navigateMenuContribution`).
- **Evidence:** The `editorContribution` declares a *Parsley* menu under `navigate/additions` containing only two empty `<separator>` entries (`group1`, `group2`). An exhaustive search for `navigate` references in `plugin.xml` yields exactly two hits — line 290 (this menu's own `path`) and line 489 (`navigate/open.ext2`, the unrelated Open Component action) — so nothing contributes into `navigateMenuContribution`. The four switch-to commands (`tojava`/`tohtml`/`towod`/`toapi`, lines 163-174) have handlers (lines 206-213) and keybindings (lines 220-235) but no menu item. The handler classes (`org/objectstyle/wolips/editor/menu/SwitchToJavaHandler` etc.) have live `execute()` bodies, so the keyboard path (`M1+M3+1/2/3/5`) works.
- **Original comparison:** `wolips-original/.../org.objectstyle.wolips.componenteditor/plugin.xml:201-231` populated the same menu with `<action>` entries (`switchtoapi`/`switchtowod`/`switchtohtml`/`switchtojava`), each with `menubarPath=.../navigateMenuContribution/group1`. Those action contributions were dropped on extraction; the empty shell survived.
- **Impact:** Cosmetic / discoverability. Users see an empty (or suppressed) *Parsley* submenu under Navigate and have no menu route to switch editor tabs — only the keyboard shortcuts.
- **Recommendation:** rewire. Add the four switch-to `<action>` entries back with `menubarPath` into `navigateMenuContribution/group1`, binding to the existing handlers via `definitionId`, so the menu matches the keybindings.
- **Confidence:** high.

### 2.7 `isNSKeyValueCoding()` is hardcoded `return false` — the missing-NSKVC branch fires only for `java.lang.Object`

- **Location:** `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/java/org/objectstyle/wolips/bindings/utils/BindingReflectionUtils.java:299-301`; consumed at `.../bindings/wod/BindingValueKeyPath.java:175`.
- **Evidence:** `BindingReflectionUtils.isNSKeyValueCoding(IType, TypeCache)` is a `@deprecated` stub that unconditionally `return false;` (line 300), with javadoc stating ng-objects does not use NSKeyValueCoding — an intentional, documented change, not dropped wiring. Its single caller, `BindingValueKeyPath.java:175`, is `if (isNSKeyValueCoding(...) || "java.lang.Object".equals(currentType.getFullyQualifiedName()))`. Because of the OR, `_nsKVC` (set at line 176) is still set true for `java.lang.Object`-typed keys, so `BindingValueKeyPath.isNSKeyValueCoding()` and the downstream `missingNSKVCSeverity` wiring in `AbstractWodBinding.java:263, 280-281, 343-351` still fire for those keys.
- **Impact (corrected — half-wired, not dead):** The type-hierarchy NSKVC detection is permanently off (no concrete type will ever be flagged), but the severity branch and the `MISSING_NSKVC_SEVERITY_KEY` preference are **not** unreachable — they trigger for `java.lang.Object`-typed keypaths, a real and common case (untyped / `Object`-returning getters). So this is a narrowing, not a removable dead branch.
- **Recommendation:** leave-with-comment. Removing the `missingNSKVCSeverity` wiring would silently change validation for `java.lang.Object` keypaths. The stub already carries explanatory javadoc; what is misleading is that the branch is still messaged around "implements NSKeyValueCoding" when it now only triggers for `java.lang.Object`. A clarifying comment at `BindingValueKeyPath.java:175` resolves the confusion without behavior change.
- **Confidence:** medium.

### 2.8 `HTMLProjectPropertyPage` ("Zombie: Validation") writes an unregistered nature + builder into projects

- **Location:** Registration `/Users/hugi/git/wolips/wolips/plugins/ng.componenteditor/plugin.xml:716-722` (`HTMLProjectPropertyPage`); wiring `java/tk/eclipse/plugin/htmleditor/HTMLProjectPropertyPage.java:164-186` → `HTMLProjectParams.java:208-229` (`addNature`) → `HTMLProjectNature.java:17-38`.
- **Evidence:** The property page is registered and reachable — `enabledWhen` is `<adapt type="org.eclipse.core.resources.IProject"/>` with **no** nature filter, so it shows on any project. `performOk()` (line 164) calls `setDetectTaskTag(...)` then `params.save(...)`; `save()` (line 208) calls `addNature(project)` when `_detectTaskTag` is set, appending `HTMLProjectNature.HTML_NATURE_ID` to the project's `natureIds` via `setDescription()`. But `plugin.xml` registers no `org.eclipse.core.runtime.natures` for `HTMLProjectNature` and no `org.eclipse.core.resources.builders` for `HTMLProjectBuilder` (grep count 0 for both; 0 references to either class in `plugin.xml`).
- **Original comparison:** the original Amateras `tkhtmleditor` registered both via `org.eclipse.core.resources.natures` + `org.eclipse.core.resources.builders`; extraction kept the page and the add/remove code but dropped the registrations.
- **Impact (downgraded — only the nature limb is dead):** The original framing "the page persists state nothing consumes" overreaches. `HTMLProjectParams`'s validation flags *are* consumed live by editor/validation code (`JavaScriptValidator.java:32`, `JavaScriptHyperlinkDetector.java:120`, `JavaScriptAssistProcessor.java:378`, `JavaScriptEditor.java:220`, `HTMLSourceEditor.java:316/413`), bypassing the builder. Only the "Detect Task Tag" branch is the dead limb: enabling it mutates `.project` to add an unknown nature and an unknown builder command, which Eclipse rejects/logs ("Unknown nature"/"Unknown builder") — dirtying project metadata to no effect.
- **Recommendation:** remove the `addNature`/`removeNature`/`configure` limb and the "Detect Task Tag" toggle (no builder is or can be registered); keep the validation-flag persistence that live code consumes. This is part of the Category 3 cluster below.
- **Confidence:** high (mechanism confirmed; impact scoped down).

---

## Category 3 — Dead / disabled in place (remove candidates)

### 3.1 `HTMLProjectBuilder` + `HTMLProjectNature` + the task-tag subsystem — entire builder/nature cluster dead in place

- **Location:**
  - `tk/eclipse/plugin/htmleditor/HTMLProjectBuilder.java` (`extends IncrementalProjectBuilder`; `build()` at line 43, `doBuild()` at line 130)
  - `tk/eclipse/plugin/htmleditor/HTMLProjectNature.java` (`implements IProjectNature`)
  - `tk/eclipse/plugin/htmleditor/tasktag/*` — `HTMLTaskTagDetector`, `JavaScriptTaskTagDetector`, `TaskTag`, `AbstractTaskTagDetector`, `ITaskTagDetector`, `HTMLTaskTagPreferencePage`
- **Evidence:** `plugin.xml` contains **zero** `org.eclipse.core.resources.builders` and **zero** `org.eclipse.core.runtime.natures` extensions (grep count 0; whole-file scan confirms), and zero references to either class. So Eclipse can never instantiate `HTMLProjectBuilder.build()` (only invoked for a registered builder) nor `HTMLProjectNature.configure()` (only invoked for a registered nature). The cluster is dead behind **two independent always-false gates**:
  1. The one programmatic entry, `HTMLProjectBuilder.doBuild()` (line 130), is called only from `HTMLTaskTagPreferencePage.processChange()` (line 169, page registered at `plugin.xml:695` under the deprecated/"Zombies" category) and is guarded by `project.hasNature(HTML_NATURE_ID)`. No project can carry that nature because it is unregistered (and `setDescription` with an unknown nature id fails validation). The guard is always false.
  2. Even if reached, `doBuild()` calls `project.build(FULL_BUILD, HTML_BUILDER_ID, ...)` (line 136) for an unregistered builder id — Eclipse silently no-ops.
  - `TaskTag.loadFromPreference()` is consumed only inside `HTMLProjectBuilder.build()` (line 45) and in the preference page UI, so the "HTML Task Tags" page lets users edit tags that nothing ever reads. No reflection / `Class.forName`, no adapter factory, and no lazy-activation path reaches any of these classes (the only outside references are javadoc `@see` comments in `BindingValidationRulePreferencePage.java:39` and `TagShortcutPreferencePage.java:38`).
- **Original comparison:** `wolips-original/.../org.objectstyle.wolips.tkhtmleditor/plugin.xml` registered **both** the nature (`org.eclipse.core.resources.natures`, lines 281-291) and the builder (`org.eclipse.core.resources.builders`, lines 296-300, `<run class=.../>`). Extraction dropped both extensions; the classes survive but are unreachable.
- **Impact:** Dead code cluster (~6 classes plus a registered-but-useless preference page). HTML/JS task-tag (TODO/FIXME) markers are never produced. The "HTML Task Tags" preference UI and the "Detect Task Tags" checkbox are non-functional; toggling the latter on dirties `.project` with a phantom nature (see 2.8). No functional loss for component editing.
- **Recommendation:** remove. Delete `HTMLProjectBuilder`, `HTMLProjectNature`, the entire `tasktag/` package, and the task-tag preference-page registration (`plugin.xml:695`). **Caveat:** do not delete `HTMLProjectParams` — it is live (consumed by the JS validators, see 2.8). Relocate the `HTML_NATURE_ID` / `HTML_BUILDER_ID` constants out of `HTMLProjectNature` (or strip their dead consumers) so the still-live `HTMLProjectParams` / `HTMLProjectPropertyPage` continue to compile, and remove only the dead `_detectTaskTag` / `addNature` / `removeNature` / `checkTaskTag` bits from them.
- **Confidence:** high.

> *Note:* the verified-findings set described this cluster from three angles (builder, nature, full-subsystem). They are the same zombie; treat them as one removal.

---

## Category 4 — Uncertain (flagged for verification)

### 4.1 `ContentDescriberWO` uses a process-global static flag toggled by `TemplateEditor`

- **Location:** Registration `plugin.xml:58` (content-type `org.objectstyle.wolips.editors.wohtml`, describer `org.objectstyle.wolips.editors.ContentDescriberWO`, priority `high`, `file-extensions=html`); class `java/org/objectstyle/wolips/editors/ContentDescriberWO.java:64-74`; toggled from `java/org/objectstyle/wolips/editor/template/TemplateEditor.java:60` and `:130`.
- **Evidence:** `describe(InputStream)` returns the static field `ANSWER`, which defaults to `INVALID`; `describe(Reader)` always returns `INVALID`. The only writers are `TemplateEditor` (sets `ANSWER=VALID` on open at line 60, `ANSWER=INVALID` at line 130). So whether a `.html` file matches the high-priority "Html with WO tags" content type depends on a process-wide mutable static reflecting whether a `TemplateEditor` happens to be active — not the file's contents. This is **not** a broken wire: the class is registered and referenced, and the content type drives the `ComponentEditor`/`TemplateEditor` `contentTypeBinding` (`plugin.xml:78`, `:93`).
- **Original comparison:** the static-`ANSWER` pattern is byte-for-byte inherited from `wolips-original` (`.../org.objectstyle.wolips.editors/plugin.xml:103`); it is pre-existing, not introduced by extraction.
- **Impact:** Content-type detection for `.html` is governed by a shared static flag rather than file content, so results can be order/timing-dependent across editor lifecycles. Likely pre-existing behavior.
- **Recommendation:** leave-with-comment. Document the static-flag fragility rather than rip out a working-but-questionable wire; verify behavior before any change.
- **Confidence:** low.

---

## Cross-cutting observations

1. **Extraction systematically dropped build-time wiring.** Every Category 1/2 validation finding (2.1–2.5) traces to one fact: our `plugin.xml` registers no project builder and no validation nature, whereas the original ran a registered full+incremental builder (`org.objectstyle.wolips.builders` + `org.eclipse.core.resources.builders`) attached via a nature. The builder was the workspace-wide engine for "validate every component, sweep stale markers." Its loss is not visible at a grep level because the *class* (`WodBuilder`) survives as a static utility — it looks alive. The behavior it provided was quietly redistributed onto editor-time hooks that only cover what is open.

2. **The builders/natures gap also strands a whole subsystem.** The same missing `org.eclipse.core.resources.builders` + `org.eclipse.core.runtime.natures` extensions leave the `HTMLProjectBuilder` / `HTMLProjectNature` / task-tag cluster (Category 3) unreachable, and make the "Detect Task Tag" toggle (2.8) actively harmful (it writes a phantom nature). The Amateras HTML-validation lineage was carried over as classes without its registrations.

3. **Empty shells outlived their contents.** Twice — the *Parsley* Navigate submenu (2.6) and the task-tag preference page (3.1) — the declarative *shell* survived extraction while the `<action>`/registration entries that gave it meaning were dropped. These read as "feature present" in the UI but do nothing.

4. **Documentation overstates surviving wiring.** `CHANGES.md` simultaneously calls the old builder "never invoked" (line 407) and advertises "build-time validation" for standalone templates (lines 1067-1090), citing methods that no longer exist. The intentional `isNSKeyValueCoding` stub (2.7) is well-documented; the validation-pass reduction is not.

---

## Suggested triage order

Fix order is should-run → half-wired → remove.

1. **First — 1.1 (`ComponentEditorPart` index bug).** A live code path that misbehaves; a one-line `pages[i]` → `pages[j]` fix plus a glance at the `closeEditor` target. Lowest cost, clearest bug.

2. **Second — the validation-staleness cluster (2.1, 2.2, 2.3, 2.4).** These are the substantive behavior reduction and they share a single root cause and a single natural fix: a workspace/dependency-aware revalidation pass to replace the lost builder sweep. **These pair directly with `proposal-revalidate.md`** — that proposal is the right home for the design decision (recompute cost vs. coverage). Until then, document the staleness as a known limitation and correct `CHANGES.md`. Treat 2.5 as folded into 2.1 (its only real residual is the same marker sweep).

3. **Third — the cosmetic/clarity items (2.6, 2.7, 2.8).** 2.6 is a low-risk `plugin.xml` rewire (restore the four switch-to actions). 2.7 is a comment-only clarification. 2.8's dead nature limb is removed together with the cluster below.

4. **Last — remove the dead cluster (3.1, including 2.8's nature limb).** Pure cleanup, no behavior change for component editing, but do it carefully: keep `HTMLProjectParams` (live), relocate the `HTML_*_ID` constants, and verify with `mvn verify -pl wolips/plugins/ng.componenteditor -am -Dtycho.localArtifacts=ignore` after removal.

5. **Verify, don't act — 4.1 (`ContentDescriberWO`).** Confirm the static-flag behavior in a running Eclipse before touching it; it is inherited and load-bearing for content-type detection.

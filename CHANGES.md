# ng.componenteditor — Change Log

This document tracks changes made to the `ng.componenteditor` plugin since its initial creation from the WOLips source.

## Origin

`ng.componenteditor` is a standalone Eclipse plugin extracted from the WOLips plugin suite. It merges source from ~18 WOLips plugins into a single bundle, with unique identifiers (`ng.componenteditor.*`) so it can coexist with a WOLips installation.

The initial import was commit `d2c9da47` ("Initial ng import").

---

## Changes

### Quick fix (Cmd+1): open Add Action dialog for action bindings

- When using Cmd+1 on a missing key that's on an action binding (e.g. `action="$performSubmit"`), the quick fix now opens the "Add Action" dialog instead of the "Add Key" dialog. The Add Action dialog defaults to `WOActionResults` and generates the correct method stub — no extra keystrokes needed.
- The binding name (e.g. `"action"`) is now stored on the problem marker so the quick-fix processor can determine the binding type. Detection uses the existing `ApiUtils.isActionBindingName()` — matches `"action"` and any name ending with `"Action"`.
- The Cmd+1 menu now shows "Create action 'performSubmit'" instead of "Create key 'performSubmit'" for action bindings.

### ElementDescriptor: unified element file resolution model

- New `ElementDescriptor` class — an immutable model object that captures "what files belong to this element?" (HTML, WOD, WOO, API, Java, project, template format). Replaces ad-hoc calls to the locate system scattered throughout the codebase.
- New `TemplateFormat` enum — `BUNDLE`, `STANDALONE`, or `NONE`.
- Factory methods: `ElementDescriptor.forFile(IFile)` resolves any component file to a descriptor via the locate system; `fromLocateResult()` wraps an existing `LocalizedComponentsLocateResult`.
- `WodParserCache.getElementDescriptor()` — bridge method so editor-context consumers can get a descriptor without calling locate directly.
- `ComponentEditorInput.getElementDescriptor()` — populated during input creation for both bundle and standalone templates.
- Migrated all four switch handlers (`SwitchToHtmlHandler`, `SwitchToWodHandler`, `SwitchToJavaHandler`, `SwitchToApiHandler`) to use `ElementDescriptor` instead of calling `LocatePlugin.getLocalizedComponentsLocateResult()` directly.
- Migrated `OpenComponentAction` — now uses `ElementDescriptor` and opens standalone templates correctly (falls back to HTML when no WOD file exists).
- Migrated `WodElementTypeHyperlink` (F3 hyperlink) — same pattern, now handles standalone templates.
- Migrated `RenameComponentAction.findComponentJavaType()` — uses `ElementDescriptor.getJavaType()`.
- 15 unit tests for `ElementDescriptor` format detection, accessors, and convenience methods.
- This is the first step toward the Unified Element Editor described in the roadmap.

### Quick fix (Cmd+1): also fix closing tag for element type errors

- When using the quick fix on an invalid inline element type like `<wo:container>`, the fix now corrects both the opening and closing tag. Previously only the opening tag was fixed, leaving the closing `</wo:container>` unchanged.
- `InlineWodProblem.createProblemMarkers()` now stores the closing tag position as custom marker attributes (`closeTagStart`/`closeTagEnd`) using the FuzzyXMLElement's existing close tag data.
- New `ReplaceTagPairProposal` inner class in `TemplateQuickAssistProcessor` replaces both tag names in a single apply — closing tag first to preserve offsets.
- Self-closing tags and keypath errors are unaffected (single replacement as before).

### Fix Cmd+2,R (Rename Tag) for standalone templates

- `QuickRenameElementAction` required both the HTML editor and WOD editor to be present, silently doing nothing for standalone templates (which have no WOD file). Removed the WOD editor requirement — HTML tag rename only needs the HTML viewer. Same pattern previously fixed for `FormatAction` and `ExtractComponentAction`.
- Added a defensive null guard in `QuickRenameRefactoring.renameHtmlSelection()` for the WOD element reference rename path, preventing an NPE if `wodViewer` is null.

### WOLips coexistence: "Let Parsley handle all elements" preference

- New preference in **Preferences → Parsley → WOLips Coexistence**: "Let Parsley handle all elements by default". When enabled, Parsley activates its editors, decorators, and keyboard shortcuts for all recognized WO/NG projects — even those without `project.base` in `build.properties`.
- Default is off (unchanged behavior): Parsley defers to WOLips for projects without explicit `project.base`.
- Refactoring participants (`RenameBindingKeyParticipant`, `RenameComponentParticipant`) are intentionally excluded from the preference — they still require `project.base` to avoid conflicts with WOLips' own refactoring participants, which skip based on the same property.
- Refactored `ParsleyProject` to separate concerns: `isParsleyProject()` is now a pure project type check ("is this a WO or NG project?"), while the new `shouldHandleProject()` is the policy gate that considers WOLips installation and the preference. A separate `shouldRefactor()` gates refactoring participants conservatively.

### Usages tab in standalone API editor — element-level reverse dependency view

- Added a **Usages** tab to the standalone API editor (used for non-component elements like `WODynamicElement` subclasses and `NGElement` implementations). This provides the same "who uses this element?" reverse dependency view that was previously only available in the multi-tab component editor.
- Extracted the usages UI (toolbar, table, scanning logic) into a reusable `UsagesComposite` shared by both `UsagesTab` (component editor) and `UsagesFormPage` (API editor).
- The API editor now always shows its tab bar (previously hidden when there was only one page), with "Bindings" and "Usages" tabs.

### Fix Cmd+Alt+1 (switch to Java) failing for cross-project components

- When WOLips is installed, pressing Cmd+Alt+1 in a component editor opened via F3 from a different project would silently fail. The handler checked `isParsleyProject()` on the component's project — which requires `project.base` to be set in `build.properties` — and delegated to WOLips when it wasn't. Unlike the HTML, WOD, and API handlers (which detect `ComponentEditor` and switch tabs directly), the Java handler always went through the project check. Fixed by using the already-resolved `LocalizedComponentsLocateResult` from the `ComponentEditorInput` when invoked from a ComponentEditor, bypassing the project check entirely.

### Usages tab — reverse dependency view

- New **Usages** tab in the component editor shows which other components in the workspace reference the current component as an element — a "who uses me?" view.
- Shown for both standalone and bundle templates.
- The tab contains a two-column table: **Component** (the name of the using component) and **Project** (which project it belongs to). Double-clicking a row opens that component's template.
- The scan runs lazily on first tab activation using a background `Job` to avoid blocking the UI. A **Refresh** button triggers a manual re-scan.
- Reuses the regex matching logic from `RenameComponentProcessor` (the same patterns that power the rename refactoring's cross-reference detection). Extracted `htmlContainsElementReference()` and `wodContainsElementReference()` as public static methods for shared use.

### Fix formatter not working in standalone templates

- The "Format Template" action (`Cmd+Shift+F`) did nothing in standalone (non-bundle) templates. The action required both a template editor and a WOD editor to be present, but standalone templates have no WOD tab. The formatter operates purely on the HTML template and has no WOD dependency, so the WOD editor check was unnecessary.

### Extract Wrapper refactoring

- New refactoring action: **Extract Wrapper** (Edit > Refactor > Extract Wrapper..., `Cmd+2, X`). This is the inverse of Extract Component — instead of extracting the *selected* HTML into a new component, it extracts everything *around* the selection (the "wrapper" or "chrome") into a new component that uses `<wo:content />` as a placeholder. The original template is replaced with the selection wrapped in the new component tag.
- Typical use case: extracting a page layout (nav, header, footer, page structure) into a reusable wrapper component, leaving only the page-specific content in the original template.

### Extract Component and Extract Wrapper support standalone templates

- Both Extract Component and Extract Wrapper now correctly handle ng-objects projects: they create standalone `.html` files instead of `.wo` bundles.

### Fix ng project creation issues

- **No more stray .wod/.woo files:** The project wizard was creating `Main.wod` and `Main.woo` in ng-objects projects. These files are only used by bundle templates (WebObjects) — standalone templates (ng-objects) are single `.html` files.
- **Components folder resolution:** `ParsleyProject.findComponentsFolder()` searched all of `src/main/` for a folder named "components", but ng-objects projects have both `src/main/java/.../components/` (Java sources) and `src/main/resources/.../components/` (templates). The recursive search could find the Java source folder first, causing the "New WO Component" wizard to create templates alongside Java files. The fix narrows the search to `src/main/resources/` for ng projects.
- **Superclass defaults to project type:** The component wizard pre-filled the superclass from a saved preference, which persisted across projects. Creating an ng component saved `NGComponent`, then creating a WO component would still default to `NGComponent`. The superclass is now always derived from the target project's framework type — no stale preference.

### Fix crash when navigating into binary types in Bindings Inspector

- Clicking a key whose return type is a binary class (e.g. `java.util.List` from the JDK) caused a `NullPointerException` in `TypeCache.resolveType()`. The method called `declaringType.getCompilationUnit().getImports()` without checking for null — binary types have no compilation unit (they come from class files, not source).
- The fix guards the import-scanning block with a null check on `getCompilationUnit()`. For binary types, the import scan is skipped and resolution falls through to the other strategies (java.lang lookup, package-local lookup, `JavaModelUtil.getResolvedTypeName`).

### Bindings Inspector: hide navigation arrow for empty types

- The key browser no longer shows a navigation arrow on keys whose return type has no visible binding keys after filtering. Previously, the arrow was shown based on `isLeaf()` which only checked whether the return type was non-null and not String/Object. This caused arrows on types like `WOActionResults` — but navigating into them produced an empty column because the only method (`generateResponse`) is filtered as a system binding.
- The fix adds `TypeCache.hasVisibleBindingKeys(IType)` which performs a full `getGroupedBindingValueKeys` introspection (including system binding filtering) and caches the result per fully qualified type name. The cache is invalidated when types are cleared.
- Both the label provider (arrow icon) and the browser (column creation on click) use this check, so clicking a key with no visible children no longer creates an empty column either.

### API tab for standalone templates

- Standalone HTML templates (not inside `.wo` folders) now show the tab bar with "HTML" and "Api" tabs, matching the bundle template editor layout. Previously, standalone templates had no tabs at all — the tab bar was hidden and there was no way to access the API editor.
- Both creation paths are covered: the locate system path (`create(file, locateResult)`) and the fallback path (`createStandaloneHtml(file)`). Both derive the sibling `.api` file automatically.
- The API tab guard in `ComponentEditorPart.createPages()` now checks `getApiEditor() != null` instead of `getStandaloneHtmlEditor() == null`, so it works for both bundle and standalone templates.

### "Create key" quick-fix (Cmd+1)

- When the cursor is on a validation error for a missing binding key (e.g. "There is no key 'nme'"), pressing Cmd+1 now offers a **"Create key"** proposal alongside the existing "Replace with" suggestions. Selecting it opens the Add Key dialog to generate the field/accessor on the component's Java class.
- Only offered for direct keys on the component class — nested keypaths like `session.nme` don't offer key creation since it would target the wrong class.

### "Restore WOLips Keybindings" button on coexistence preference page

- The WOLips Coexistence preference page (Preferences → Parsley → WOLips Coexistence) now has a **"Restore WOLips Keybindings"** button. Click it before uninstalling Parsley to remove all keybinding overrides and restore WOLips' original keyboard shortcuts. The button also unchecks the shadow preference so the overrides are not re-applied on next startup.

### Support for Fluffy Bunny project layout

- **Components folder discovery now supports the Fluffy Bunny layout.** The New Component wizard, Extract Component action, and Switch to API handler previously only searched for a `components` folder under `src/main/` (Maven layout). They now fall back to `Components/` at the project root, which is the convention used by "Fluffy Bunny" WebObjects projects.
- The shared `ParsleyProject.findComponentsFolder()` method centralizes this logic — the three duplicate implementations have been replaced with calls to it.

### Convert between template formats

- **New context menu action: "Convert to Standalone Template"** on `.wo` folders. Converts bundle templates from WOD-reference syntax (`<webobject name="X">`) to inline binding syntax (`<wo:Type binding="value">`), then moves the HTML file out of the `.wo` folder and deletes it.
- Supports multi-selection: select multiple `.wo` folders and convert them all at once.
- **"Convert All to Standalone Templates"** action on regular folders: recursively finds and converts all bundle templates inside.
- Missing WOD entries are handled gracefully — affected tags are left unchanged and a warning dialog lets the user decide whether to proceed with partial conversion.
- Respects the "Spaces around equals" formatting preference — produces `value = "$x"` or `value="$x"` accordingly.
- Core transformation logic (`ConvertBundleToInlineTransformer`) is a pure string-based engine with no Eclipse dependencies, fully covered by 31 unit tests.
- Uses the existing `AbstractWodBinding.writeInlineFormat()` infrastructure for correct serialization of literal, key path, and OGNL binding values with the project's configured inline binding prefix/suffix.
- **New context menu action: "Convert to Bundle Template"** on standalone `.html` template files. Creates a `.wo` folder, moves the HTML file inside, and creates an empty `.wod` file. The reverse of the standalone conversion. Supports multi-selection; files already inside `.wo` folders are excluded.

### Convert WOD to Inline (single tag)

- **New editor action: "Convert WOD to Inline"** (`Cmd+2, I`). Place the cursor on a `<webobject name="X">` tag in the template editor and invoke the action to replace it with inline `<wo:Type binding="value">` syntax. The WOD entry is looked up, the tag is rewritten with the correct element type and bindings, and the WOD declaration is removed from the `.wod` file.
- The reverse of the existing "Convert Inline to WOD" (`Cmd+2, W`).
- Respects the "Spaces around equals" formatting preference and the project's inline binding prefix/suffix.

### WOLips coexistence: keybinding shadow preference

- **New preference page: "WOLips Coexistence"** under Parsley preferences. Provides a single checkbox: "Take over component shortcuts from WOLips".
- When enabled, creates Eclipse `Binding.USER` entries that shadow all WOLips SYSTEM keybindings, eliminating the disambiguation popup that appears when both plugins register the same key sequences (Cmd+Alt+1/2/3/5, Cmd+Shift+X, etc.). Uses null-command unbindings to suppress WOLips' bindings, plus positive USER bindings to re-establish Parsley's shortcuts — the same mechanism Eclipse's own Keys preference page uses.
- Shadows are persisted to the workspace preference store and reapplied on startup.
- **Command handler delegation:** when the shadow is active, Parsley's handlers check `isParsleyProject()` — for Parsley projects they run normally, for non-Parsley projects they delegate to the corresponding WOLips command via `IHandlerService.executeCommand()`. This preserves WOLips behavior on WOLips projects.
- **New classes:** `WOLipsCoexistencePreferencePage`, `WOLipsBindingShadow`, `WOLipsCommandDelegate`.

### Use unique element factory ID

- Changed the `ComponentEditorInputFactory` ID from `org.objectstyle.wolips.components.input.ComponentEditorInputFactory` (same as WOLips) to `ng.componenteditor.input.ComponentEditorInputFactory`. The old shared ID caused Eclipse to pick one factory at random when both plugins were installed — if WOLips' factory won, Parsley editors would fail to restore on Eclipse restart (broken editor state). Also a problem without WOLips if Eclipse's memento contained stale references.

### Share content type IDs and names with WOLips

- Changed content type IDs from `ng.componenteditor.{wod,api,woo,html}` to `org.objectstyle.wolips.editors.{wod,api,woo,wohtml}` — the same IDs used by WOLips. When both plugins are installed, they now share a single content type per file extension instead of registering duplicates. No functional change when Parsley is installed alone.
- Changed content type names to match WOLips: removed the "NG" prefix (e.g. "NG WOComponent wod part" → "WOComponent wod part"). Ensures the UI looks identical to WO users regardless of which plugin is installed.

### Treat HTML void elements as implicitly self-closing

- **HTML void elements** (`<br>`, `<hr>`, `<img>`, `<input>`, `<meta>`, `<link>`, etc.) were pushed onto the unclosed-tag stack, causing close-tag completion to suggest closing the void element instead of its parent. For example, `<div><p><br></p>` would suggest `</br>` instead of `</div>`.
- **Fix:** The scanner now maintains a set of all 14 HTML void elements and never pushes them onto the stack, regardless of whether they use explicit self-closing syntax (`<br />`) or not (`<br>`). The self-closing `/` pop also skips void elements to avoid popping a parent tag.
- **8 new tests** covering `<br>`, `<hr>`, `<img>`, `<input>`, `<meta>`, `<link>`, explicit self-closing void elements, and multiple void elements in sequence.

### Skip script/style content in close-tag completion

- **JavaScript operators like `<` inside `<script>` blocks** were being parsed as HTML tags, corrupting the tag stack. For example, `<body><script>if (a < b) {}</script>` would cause close-tag completion to suggest `</script>` instead of `</body>`, because `< b` was treated as opening a `<b>` tag.
- **Fix:** When the scanner encounters the closing `>` of a `<script>` or `<style>` opening tag, it now skips ahead to the matching `</script>` or `</style>` closing tag, bypassing the raw content entirely. If no closing tag is found (cursor is inside the block), the scanner skips to the end of the text, leaving the script/style tag correctly on the stack.
- Case-insensitive matching handles `<SCRIPT>`, `<Script>`, etc.
- **7 new tests** covering `<` in JavaScript, multiple operators, script tags with attributes, `<style>` tags, case variations, unclosed scripts, and empty script tags.

### Fix slash in body text corrupting close-tag completion

- **A forward slash in body text** (e.g. `<td>Price / amount</td>`) was incorrectly interpreted as a self-closing tag marker, popping the current tag off the stack. This caused close-tag auto-completion (Ctrl+Space) to suggest the wrong tag.
- **Root cause:** The self-closing detection (`temp1.endsWith("/")`) in `TagStackAnalyzer` fired on any token ending with `/`, even in body text where no tag was being parsed.
- **Fix:** Added a `!prevTag.equals("")` guard so the self-closing pop only fires when currently inside a tag context. `prevTag` is cleared when `>` is hit, so it's always empty in body text.
- **4 new tests** covering slashes in text content (Price / amount), URLs in body text, math expressions, and slashes after closed tags.

### Fix double-click attribute name selection

- **Double-clicking an attribute name after the first one in a tag** previously selected the preceding whitespace, the `=` sign, and surrounding spaces along with the name. Root cause: the editor's "select quoted string" handler (`selectComment` in `HTMLDoubleClickStrategy`) ran before the word-selection handler and found the nearest `"` on each side — which were the closing quote of the *previous* attribute's value and the opening quote of the *current* attribute's value, spanning across attribute boundaries.
- **Fix:** After finding the two bounding quotes, verify the selected text doesn't contain `=`. If it does, the selection spans across attributes — reject and let `selectWord()` handle it instead, which correctly stops at whitespace and `=`.

### Validate missing closing '>' on tags

- **The parser now reports an error when a tag is missing its closing `>`**, e.g. `<wo:if condition="$showGraphs"` followed by a newline. The fuzzy regex intentionally allows this (for recovery), but it's almost always a typo that silently corrupts the document structure.
- Detection uses the regex capture group that records the tag's final character — if it's not `>`, the tag is unclosed. Special constructs (comments, CDATA, declarations) are excluded since they have different closing syntax.
- The parser still processes the tag normally (recovery is preserved), but the user now gets an error marker pointing at the unclosed tag.
- **11 new tests** covering WO tags, HTML tags, close tags, self-closing tags, no false positives on well-formed markup, and recovery behavior.

### Validate missing '=' in template attributes

- **The parser now reports an error when a quote character appears inside an attribute name**, detecting the common typo of omitting `=` between an attribute name and its value — e.g. `negate"true"` instead of `negate="true"`. The error message identifies the attribute name and suggests the correct syntax.
- **Error recovery.** Instead of leaving the DOM corrupted (the entire `negate"true"` was previously treated as a single attribute name), the parser now recovers by treating the quote as an implicit `=`. This means the attribute name (`negate`) and value (`true`) are correctly parsed, so downstream tools (validation, autocomplete, hover) continue to work.
- **Deferred error firing.** Parse errors detected in the attribute scanner are stored in `TagInfo` and fired as proper error events once the tag's global offset is known. This keeps the attribute parser clean and avoids changing its method signature.
- **15 new tests** covering error detection (double/single quotes, multiple errors, self-closing tags), no false positives (valid attributes, valueless attributes, empty values), error recovery (attribute name/value extraction), and error offset accuracy.

### Rename binding key in associated template

- **Binding keys in a component's own template now update when the Java method/field is renamed.** When a developer renames e.g. `title()` to `heading()` in `MyComponent.java` via Refactor > Rename, WOD binding values (`value = title;`) and inline HTML bindings (`value="$title"`) in the component's own template files are automatically updated in the refactoring preview.
- **KVC-aware key derivation.** The participant correctly strips getter/setter prefixes (`get`, `set`, `is`, `_get`, `_set`, `_is`, `_`) and the underscore field prefix. Renaming `getTitle()` to `getHeading()` renames key `title` → `heading`. Renaming field `_title` to `_heading` renames key `title` → `heading`.
- **Key path support.** Only the first segment of key paths is replaced: `value = title.length;` becomes `value = heading.length;`. String literals, caret references (`^parent.title`), and non-key-path substrings are left alone.
- **New class: `RenameBindingKeyParticipant`** — LTK `RenameParticipant` for `IMethod` and `IField` renames. Activates only when the declaring type is a WOComponent/NGComponent subclass. Derives old/new binding keys and delegates to `RenameBindingKeyProcessor`.
- **New class: `RenameBindingKeyProcessor`** — regex-based scanner that finds binding key references in a component's own WOD and HTML template files. Follows the same `TextFileChange`/`MultiTextEdit`/`ReplaceEdit` pattern as `RenameComponentProcessor` and `RenameBindingProcessor`.
- **36 new tests** covering WOD key scanning, HTML inline key scanning, edit application with various replacement lengths, and KVC key derivation for all prefix combinations.

### Remove dead WOLips builder infrastructure

The old WOLips builder pipeline (`IBuilder` → `IFullBuilder`/`IIncrementalBuilder` → `AbstractFullAndIncrementalBuilder` → `WodBuilder`) was never invoked — the coordinator that read the `ng.componenteditor.builders` extension point lived in the old `org.objectstyle.wolips.builder` plugin, which no longer exists. Actual validation happens via `WodBuilder.validateComponent()` called directly by `JavaChangeRevalidator` and `WodParserCache`.

- Deleted `IBuilder`, `IFullBuilder`, `IIncrementalBuilder`, `AbstractFullAndIncrementalBuilder`, `AbstractOldBuilder` — entire builder framework.
- Stripped `WodBuilder` to only the live static validation code (was 435 lines, now ~200). Removed all instance fields, constructor, lifecycle methods (`buildStarted`, `handleSource`, `handleWoappResources`, etc.), and the `getBooleanProperty` helper.
- Removed `BuildProperties.Key` entries: `VALIDATE_TEMPLATES`, `VALIDATE_TEMPLATES_ON_BUILD`, `THREADED_VALIDATION` — only used by dead builder lifecycle.
- Removed preference constants `VALIDATE_TEMPLATES_ON_BUILD_KEY` and `THREADED_VALIDATION_KEY`, their defaults in `PreferenceInitializer`, and UI fields in `BindingValidationPreferencePage`.
- Removed `ng.componenteditor.builders` extension point declaration and builder registration from `plugin.xml`.

### Make BuildProperties public API accept Key enum instead of raw strings

- `get(String)`, `get(String, String)`, `getBoolean(String, boolean)` made private — no longer part of the public API.
- Added `get(Key)`, `get(Key, String)`, `getBoolean(Key, boolean)` as the type-safe public API.
- All internal callers in `BuildProperties` updated to use `Key` directly (no more `.key()` indirection).
- External callers updated: `ParsleyProject`, `WodBuilder.getBooleanProperty`, `ProjectDecorator`.

### Tighten access modifiers and remove newly-exposed dead code

- **`BuildProperties`**: 13 methods made private — `isDirty()`, `getProject()`, `getBuildPropertiesEclipseFile()`, `getBuildPropertiesFile()`, `remove()`, `put(String, boolean)`, `put(String, String)`, `save()`, `getName()`, `setName()`, `isFramework()`, `load()`, `ensureDefaultsInitialized()`. None had callers outside the class.
- **`ParsleyProject`**: Instance methods `getElementClass()`, `getComponentClass()`, `getPrivateElementPackage()` made private (only used by static convenience methods). `forProject()` made private. Constants `NG_PRIVATE_ELEMENT_PACKAGE` and `WO_PRIVATE_ELEMENT_PACKAGE` made private. Removed three dead static convenience methods: `getElementClass(IJavaProject)`, `getComponentClass(IJavaProject)`, `getPrivateElementPackage(IProject)`.
- **`WodParserCache`**: `clearLocateResultsCache()`, `clearValidationCache()`, `_validate()` made private. Removed dead `cloneCache()` and `getWooEntry()` (zero callers, exposed as dead by privatization).
- **`WodModelUtils`**: `getProblems(IWodElement, ...)` 4-param overload made private. Removed dead `createWooModel(IDocument)`, `createWooModel(IFile)`, `getProblems(IWodModel, WodParserCache)`, `getProblems(IWodModel, ...)` 4-param overload (zero callers, exposed as dead by privatization).
- **`HTMLUtil`**: `jspComment2space()` made private (only called internally by `comment2space()`).

### Dead code and commented-out code cleanup

- **`HTMLUtil`**: Removed 13 dead methods — `contains()`, `copyFolder()`, `nullConv()`, `getActiveEditor()`, `openClassSelectDialog()`, `trim()`, `getFirstElement()`, `getGetterName()`, `getSetterName()`, `toRGB()` (with buggy `toDecimal()`), `selectXPathNode()`, `selectXPathNodes()`, and commented-out `cloneList`. Class shrunk from 642 to 430 lines.
- Deleted `JavaScriptFormatter` and `CSSStyleSheetFormatter` — entirely commented-out classes (abandoned Rhino/CSSDOM formatter prototypes).
- Removed dead `WodHtmlUtils.getHtmlFileForWodFilePath()` (no callers).
- Removed dead `WodModelUtils.validateWodFile(IFile, ...)` (only called itself, never externally).
- Removed dead `WodParserCache._lastJavaParseTime` field (declared but never read or written).
- Removed empty no-op validity check in `WodParserCache.getComponentsLocateResults()`.
- Cleaned up commented-out code fragments in `FuzzyXMLNode`, `AbstractFuzzyXMLNode`, `FuzzyXMLElementImpl`, `WodModelUtils`, `TemplateSourceEditor`, and `TemplateAssistProcessor`.

### Introduce `ParsleyProject` as the project model

- **New class: `ParsleyProject`** — a proper project model that owns `BuildProperties` and hosts project-level concerns (framework detection, element/component class resolution). Obtained via `project.getAdapter(ParsleyProject.class)`.
- **Extracted from `BuildProperties`:** `isNGProject()`, `getElementClass()`, `getComponentClass()`, `getPrivateElementPackage()`, classpath probing (`resolveFrameworkClass()`, `classpathContains()`), static convenience methods, and framework constants (`NG_ELEMENT_CLASS`, `WO_ELEMENT_CLASS`, etc.) all moved to `ParsleyProject`. `BuildProperties` now only represents the `build.properties` file.
- **New adapter factory: `ParsleyProjectAdapterFactory`** — replaces `BuildPropertiesAdapterFactory`. Produces both `ParsleyProject` and `BuildProperties` adapters for backward compatibility.
- **Deleted dead `ProjectAdapter` hierarchy** — `ProjectAdapter`, `ProjectAdapterFactory`, `AbstractResourceAdapter`, `AbstractResourceAdapterFactory`, `IResourceType`, and `BuildPropertiesAdapterFactory`. The `ProjectAdapterFactory` was dead code (its `createAdapter()` returned `null`).
- Updated all call sites (~25 files) to use `ParsleyProject` for project-level queries and `BuildProperties` only for raw property access.

### F3 "Open Declaration" for wo: tags

- **F3 now opens the Java class declaration** when the cursor is on a `<wo:ComponentName>` tag's type name in the template editor. This is the same navigation that Cmd+click provides, now available via the standard Eclipse "Open Declaration" shortcut.
- Replaced the legacy `OpenDeclarationAction` (WOD-only, opened `.wod` files) with a modern `AbstractHandler` that works in the template editor and opens the Java class.

### Framework-aware tag shortcut resolution

- **Tag shortcuts now resolve to NG class names in ng-objects projects.** Tag shortcuts (e.g. `if` → `WOConditional`) previously always expanded to WO class names, causing expensive failed type lookups in ng-objects projects where those classes don't exist. Now, when the project has `project.base=ng`, WO-prefixed class names are automatically translated to their NG equivalents (e.g. `WOConditional` → `NGConditional`, `WORepetition` → `NGRepetition`). Non-WO-prefixed shortcuts (like `ERXLocalizedString`) pass through unchanged.
- **New method: `TagShortcut.getActual(ParsleyProject)`** — returns the framework-appropriate class name. The translation is a simple `WO` → `NG` prefix swap, matching ng-objects' naming convention. This is a temporary bridge; the long-term plan is per-project tag shortcut registration.
- Updated `FuzzyXMLWodElement` (validation path) and `InlineWodTagInfo` (autocomplete path) to use framework-aware shortcut expansion. `ParsleyProject` is threaded through `TemplateAssistProcessor` → `InlineWodTagInfo` via a new `setParsleyProject()` setter.
- **Global API definitions now apply to NG elements.** `ApiUtils.findApiSnapshot()` now recognizes `ng.appserver.templating.elements` as a framework package (alongside `_private`), and `findGlobalApiSnapshotByClassName()` falls back from NG to WO names (e.g. `NGConditional` → `WOConditional`) when looking up `WebObjectDefinitions.xml`. This gives NG elements the same binding autocomplete and validation as their WO counterparts without requiring separate `.api` files.
- **`InlineWodTagInfo.loadAttributeInfo()` supplements reflection-based bindings with global API bindings.** Previously, when a type was found on the classpath, only Java reflection was used for binding discovery. Now, global API bindings are merged in as well, which is essential for NG dynamic elements whose bindings are declared via private association fields rather than KVC-style getters/setters.

### Bindings Inspector cleanup

- **Removed dead `WOBrowserPageBookView` and `WOBrowserPage`** — standalone view/page wrappers for WOBrowser that were never registered in `plugin.xml`. The WOBrowser is always embedded in `BindingsInspectorPage`.
- **Fixed `PopAnimator.stopAnimation()` null-check bug** — compared a local variable against the field it was just nulled from (`animationRect != _animationRect`, always true after `_animationRect = null`). Changed to `animationRect != null`.
- **Removed `if (true ||` debug leftover in `PopAnimator.paintControl()`** — the shadow is always drawn; removed the dead `lineWidth > minBorderWidth` condition.
- **Cleaned up `BindingsInspector.bindElementType()`** — removed dead `UpdateValueStrategy` and commented-out `_dataBindingContext.bindValue()` call that was abandoned in favor of a manual FocusListener. Added input validation (blank/space rejection) to the FocusListener to preserve the safety the dead code was meant to provide.
- **Removed commented-out code** — old `setLabelProvider()` call and superseded `SWTObservables`/`BeansObservables` API comments in `bindElementName()`.

### Rename binding across files (from .api editor)

- **Cross-file binding rename support.** When a binding is renamed in the `.api` editor and saved with the "Refactor on rename" checkbox enabled, a refactoring preview dialog shows all `.html` and `.wod` files that reference the old binding name on the relevant component, and offers to update them all at once. The scan covers the source project and all projects that transitively depend on it, so components defined in framework/library projects have their references updated in consuming application projects.
- **New "Refactor on rename" checkbox** in `BindingDetailsPage`, next to the binding name field. Opt-in, unchecked by default. When checked, saving after a rename triggers the cross-file scan; when unchecked, saving works exactly as before.
- **New class: `RenameBindingProcessor`** — regex-based cross-project scanner that finds binding references in HTML templates (`<wo:ComponentName oldBinding="...">`) and WOD files (`: ComponentName { oldBinding = ...; }`). Scans the source project and all transitively dependent projects via `IProject.getReferencingProjects()`. Follows the same `IResourceVisitor` + regex pattern approach as `RenameComponentProcessor`.
- **New class: `RenameBindingRefactoring`** — LTK `Refactoring` subclass that wraps `RenameBindingProcessor` and presents changes via `RefactoringWizardOpenOperation` for the standard Eclipse preview-and-confirm dialog.
- **`MutableApiModel` tracks original binding names** using an `IdentityHashMap` (keyed by object identity, since `equals`/`hashCode` change when the name is mutated). `getBindingRenames()` returns old→new name mappings; `resetBindingRenames()` resets the baseline after save.
- **`ApiEditor.doSave()` triggers refactoring** when renames are detected and the checkbox is enabled. Also updates binding references in the snapshot's validation trees before saving.
- **`ApiValidation.withRenamedBinding()`** — returns a new immutable validation tree with the binding name substituted, sharing unaffected subtrees.
- **`ApiSnapshot.renameBindingInValidations()`** — rebuilds any validation nodes that reference the old binding name.
- **Added 20 new tests:** 6 for `ApiValidation.withRenamedBinding()` and 14 for `RenameBindingProcessor` HTML/WOD regex scanning patterns.

### Formatter: preserve blank lines between elements

- **Fixed blank lines between top-level elements being lost** — `FormatRefactoring` iterated document children directly, calling `toXMLString()` without going through `delegate.renderNode()`. This caused hidden text nodes (whitespace between top-level elements) to be skipped entirely when trim was enabled, losing all blank lines between elements like `</style>` and `<div>`. Fixed by calling `delegate.renderNode()` for each child, matching the rendering path used by `FuzzyXMLElementImpl.getValue()`.
- **Updated formatter tests to match actual rendering path** — the test `format()` helper now iterates children of the document element (matching `FormatRefactoring`), rather than rendering the `<document>` wrapper tag. Added 10 new tests covering blank line preservation patterns: between top-level elements, inside nested structures, with indented blank lines, and between scripts.

### Formatter: preserve `&nbsp;` entities in output

- **Fixed `&nbsp;` being lost during formatting** — the earlier fix that stopped converting printable characters to entities (ó, ð, ú) also prevented `&nbsp;` (char 160) from being re-encoded. The parser decodes `&nbsp;` to char 160 on input; without re-encoding, it became a literal non-breaking space indistinguishable from a regular space. `escape()` now uses `Character.getType()` to distinguish printable characters (letters, digits, symbols — left as UTF-8) from non-printable ones (`&nbsp;`, `&shy;`, etc. — re-encoded to their HTML entity names).

### Formatter: respect original line structure (continued)

- **Fixed `<wo:if>` text collapsing inline** — `isNonBreaking()` only checked hidden (whitespace-only) text nodes for newlines, but the parser stores content like `\n\t\ttext\n\t` as a single visible text node. Now also checks visible text for leading/trailing newline characters.
- **Fixed elements separated by flat whitespace splitting to new lines** — `renderNode()` element branch was triggering newlines for any hidden whitespace separator, even spaces without newlines. Now uses `lastHiddenTextHadNewline()` so elements separated by spaces-only stay on the same line.

### Formatter: indentation settings in preferences

- **Added "Indent with tabs" checkbox** to `XMLPreferencePage` — wired to `PreferenceConstants.INDENT_TABS`, which `FormatRefactoring` and `XMLEditor` already read. When checked, the indent size spinner is disabled.
- **Added "Indent size" spinner** (1–8 spaces) — wired to `PreferenceConstants.INDENT_SIZE`. Both preferences existed since WOLips but were never exposed in the UI.
- **Fixed `spacesAroundEquals` save behavior** — previously saved on every checkbox click via `widgetSelected()`; now saves properly in `performOk()` like all other preferences on the page.

### Type resolution performance — three-part optimization

- **Cached resolved `IType` in `InlineWodTagInfo`** — `loadAttributeInfo()` now stores the resolved element type in a `_resolvedElementType` field, and `getElementType()` returns the cached value when attribute info has already been loaded. Previously, `HTMLAssistProcessor` called `getElementType()` on every completion proposal to check `memberIsDeprecated()`, which triggered a full `findElementType()` → `WOHierarchyScope` lookup for each tag in the list. The deprecation check now reuses the already-resolved type at zero cost.
- **Skip `WOHierarchyScope` for exact-match lookups** — `findMatchingElementClassNames()` now uses a project-scoped `IJavaSearchScope` (via `SearchEngine.createJavaSearchScope()`) for `R_EXACT_MATCH` queries instead of building a `WOHierarchyScope`. The hierarchy scope constructs a full type hierarchy and resource vector (200-500ms per invocation), which is only needed for prefix/pattern matching. Exact-match lookups (the common case during validation) now bypass this entirely.
- **Cached `InlineWodTagInfo` instances per project** — `TemplateAssistProcessor` maintains a static `Map<IProject, Map<String, InlineWodTagInfo>>` cache. `getCachedTagInfo()` returns existing instances, and `clearTagInfoCacheForProject()` invalidates the cache when Java or `.api` files change (wired through `WodParserCacheInvalidator`). Repeat autocomplete invocations reuse cached instances instead of creating new ones and re-running `loadAttributeInfo()` each time.

### Fix race conditions in `WOHierarchyScope`

- **Fixed NPE in `encloses(String)`** — concurrent `initialize()` calls from multiple JDT search threads could reset the `elements` array while `encloses()` was iterating with an old `elementCount`, causing null pointer dereferences. Fixed by snapshotting both the array reference and count into local variables, adding a bounds check (`i < elts.length`), and a null guard on individual elements.
- **Synchronized `initialize()`** — concurrent calls to `initialize()` could corrupt the `elements` array (one thread resizing while another was mid-copy), causing `ArrayIndexOutOfBoundsException` in `add()`. Made `initialize()` synchronized to prevent concurrent execution.

### Space before `/>` in self-closing tag autocomplete

- Self-closing tag completions now insert `<wo:str value="" />` instead of `<wo:str value=""/>` — added a space before the closing `/>` for readability.
- Fixed cursor positioning for non-void self-closing tags — the `forceAttributePosition` logic was missing the `else` branch for self-closing wo: tags (as opposed to HTML void elements like `<br>`), so the cursor ended up after the `/>` instead of before the attributes.

### Self-closing tag autocomplete and required binding pre-insertion

- **`InlineWodTagInfo` now checks `wocomponentcontent`** to determine whether autocomplete should insert a self-closing tag (`<wo:str />`) or an opening+closing tag pair (`<wo:form></wo:form>`). Previously, all `wo:` tag completions unconditionally inserted opening+closing tags. The `componentContent` flag is resolved from the element's `.api` file (project-local or global `WebObjectDefinitions.xml`).
- **Required bindings are pre-inserted in tag completions.** When an element has required bindings (per its `.api` file), those binding names are included in the autocomplete insertion with the cursor positioned inside the first required binding's quotes — e.g. completing `wo:str` inserts `<wo:str value="⎸" />` so you can start typing the value immediately.
- **Added `TagInfo.setHasBody()`** and **`AttributeInfo.setRequired()`** setters so these flags can be updated after construction.
- **Overrode `hasBody()` in `InlineWodTagInfo`** to trigger lazy API loading before the flag is read — necessary because `HTMLAssistProcessor` calls `hasBody()` before `getAttributeInfo()`.
- **Changed `emptyTag` to `false`** in `InlineWodTagInfo` constructor — the `emptyTag` flag is for HTML void elements like `<br>`, not `wo:` tags.

### Add SwitchToApiHandler — open .api file via Cmd+Alt+5

- **Created `SwitchToApiHandler`** — new command handler that wires up the existing `Cmd+Alt+5` keybinding (which had a command and keybinding defined in plugin.xml but no handler registered). Three code paths: (1) if already in the ComponentEditor, switches to the API tab via `switchToApi()`; (2) if a component template exists, opens the ComponentEditor with the API tab active (using the `.api` file path, which triggers `displayApiPartOnReveal`); (3) if no template exists but a `.api` file does (common for non-component WOElements), opens the standalone ApiEditor.
- **Registered handler in plugin.xml** for `ng.componenteditor.editors.toapi`.
- **Auto-create `.api` file**: when no `.api` file exists, the handler creates a blank one automatically. For the location, it uses the same `findComponentsFolder()` heuristic as the New Component wizard (first folder named "components" under `src/main/`), falling back to the Java file's directory if no components folder exists. Uses `MutableApiModel.blankContent()` for the blank file content.

### Remove dead extension point machinery and ConsoleLogger

- **Deleted `ConsoleLogger`** — dead class with zero callers, implementing `ILogger` with `System.out.println`. All logging now goes through Eclipse `ILog`.
- **Deleted `IBuildPropertiesInitializer`** — interface with zero implementations. The extension point `ng.componenteditor.buildPropertiesInitializer` was referenced in `BuildPropertiesAdapterFactory` but never registered in `plugin.xml`, so it was inert code.
- **Removed `initializeBuildProperties()` and `initializeBuildPropertiesDefaults()`** from `BuildPropertiesAdapterFactory` — these looked up the dead extension point and always returned early. Also removed the call from `BuildProperties.ensureDefaultsInitialized()`.
- **Cleaned up misleading TODO stubs**: replaced `// TODO Auto-generated method stub` and `// TODO` in intentional no-op interface methods (`BindingsDragHandler.dragLeave()`, `WOBrowser.keyReleased()`) with explanatory comments, and removed stale `// TODO Auto-generated catch block` in `WOComponentCreationPage` where logging was already in place.

### Commented-out debug println cleanup and BuildProperties dead method removal

- **Removed ~60 commented-out `System.out.println` debug remnants** across 17 files (FuzzyXMLParser, WOHTMLRenderDelegate, FuzzyXMLElementImpl, FuzzyXMLTextImpl, BindingReflectionUtils, BindingValueKey, BindingValueKeyPath, WOBrowserColumn, WOBrowser, WOHierarchyScope, TypeNameCollector, TemplateOutlinePage, WodBuilder, AbstractCacheEntry, WodParserCache, TemplateValidator, AbstractBindingsDropHandler). These were leftover debug print statements from the original WOLips code, all commented out but adding noise.
- **Removed ~35 unused WOLips-era methods from `BuildProperties`**: `getWebXML`/`setWebXML`, `isServletDeployment`/`setServletDeployment`, `getWebXML_CustomContent`/`setWebXML_CustomContent`, `getEOGeneratorArgs`/`setEOGeneratorArgs`, `getPrincipalClass`/`setPrincipalClass`, `getCustomInfoPListContent`/`setCustomInfoPListContent`, `getEOAdaptorClassName`/`setEOAdaptorClassName`, `getProjectFrameworkFolder`/`setProjectFrameworkFolder`, `setJavaClient`/`isJavaClient`, `setJavaWebStart`/`isJavaWebStart`, `hasValidProjectType`, `setFramework`, `getBundleType`, `getVersion`/`setVersion`/`getVersionDefault`/`setVersionDefault`, `getWOVersion`/`setWOVersion`/`getWOVersionDefault`/`setWOVersionDefault`, `setInlineBindingPrefix`/`setInlineBindingSuffix`, `setInlineBindingPrefixDefault`/`setInlineBindingSuffixDefault`, `setWellFormedTemplateRequired`/`setWellFormedTemplateRequiredDefault`, `setProperties`/`getProperties`, `getInlineBindingPrefixDefault`/`getInlineBindingSuffixDefault`/`getWellFormedTemplateRequiredDefault`. Also removed unused `_woVersionDefault` and `_versionDefault` fields. Inlined the remaining default lookups directly into the property getters.

### Dead code removal and final System.out.println cleanup

- **Deleted `WodQuickAssistAssistant`** — dead class with zero references anywhere in the codebase. Subclassed `QuickAssistAssistant` but was never instantiated.
- **Removed `WooModel(URL)` constructor** — empty body with a `TODO: Fix me` comment, zero callers. Also removed the now-unused `java.net.URL` import.
- **Removed `AddKeyInfo.getEntityNames()`** — always returned an empty array (entity support was removed with eomodeler). Removed the sole caller in `AddKeyDialog` (which was a no-op: `setItems(new String[0])`). Also removed a commented-out call in `AddActionDialog`, cleaned up eomodeler FIXME comments and unused imports (`Set`, `WodParserCache`), and added class javadoc.
- **Converted last 3 active `System.out.println` calls to Eclipse logging**: `BindingReflectionUtils.getFullClassName()` unknown/ambiguous type warnings → `Activator.getDefault().log()`, `HTMLOutlinePage.RootNode.getChildren()` reentrance guard → `HTMLPlugin.logDebug()`. The only remaining `System.out.println` calls are in `ConsoleLogger` (intentional — it's a console logger by design).

### Replace e.printStackTrace() with Eclipse logging

- **Replaced all ~119 active `e.printStackTrace()` calls with proper Eclipse logging** across ~60 files. Each package uses its appropriate activator: `ComponenteditorPlugin.getDefault().log()` for `componenteditor.*` (22 files), `WodclipsePlugin.getDefault().log()` for `wodclipse.*` (16 files), `HTMLPlugin.logException()` for `templateeditor.*`, `htmleditor.*`, `fuzzyxml.*`, and `xmleditor.*` (19 files), `WooeditorPlugin.getDefault().log()` for `wooeditor.*`, `Activator.getDefault().log()` for `bindings.*`, `baseforplugins.*`, `baseforuiplugins.*`, `templateengine.*`, and `variables.*`, `CorePlugin.getDefault().log()` for `core.resources.*`, `LocatePlugin.getDefault().log()` for `locate.*`, and `WizardsPlugin.getDefault().log()` for `wizards.*`. Also removed the redundant `ex.printStackTrace()` from `HTMLPlugin.logException()` (which already logged via `ILog`), removed several duplicate log-then-print patterns where both `Activator.getDefault().log(e)` and `e.printStackTrace()` were called, and rewrote `WizardsPlugin.log()` to use `Platform.getLog()` with `IStatus` instead of printing to stderr.

### Comprehensive test additions

- **Added 142 new unit tests** in 5 new test files, bringing the total from 178 to 320. Tests cover the pure-functional API model layer and several utility classes:
  - `ApiParserTest` (25 tests) — XML-to-POJO parsing: bindings, validations, componentContent, preview, parseAll multi-definition, action detection
  - `ApiSerializerTest` (19 tests) — round-trip fidelity: parse → serialize → re-parse → compare for bindings, validations, XML escaping, mutations
  - `ApiValidationTest` (34 tests) — `evaluate(Map)` for all leaf predicates (bound/unbound, settable/unsettable, gettable/ungettable), composite operators (and/or/not), COUNT operator (all comparison operators), and `isAffectedByBindingNamed`
  - `TagShortcutTest` (28 tests) — attribute string parsing/serialization, preference string round-trips, equality, clone, hasChange
  - `BindingValidationRuleTest` (19 tests) — preference string serialization, equality, clone, hasChange
  - `WooUtilsTest` (19 tests) — all 16 Objective-C→Java encoding mappings plus edge cases
  - `WodHtmlUtilsTest` (42 tests) — `isInline`, `isWOTag`, `isParserDirective`, `getLineAtOffset`, `toBindingValue`, `WEBOBJECTS_PATTERN` regex matching
  - `ApiUtilsTest` (34 tests) — `isActionBindingName`, `isActionBinding`, `getSelectedDefaults`, `SimpleApiBinding` equality/compareTo/defaults/required/willSet

### Post-refactoring cleanup

- **Fixed TypeCache invalidation bug.** `TypeCacheEntry._resource` was never initialized (the assignment was commented out since the original WOLips import), making `clearCacheForProject()` and `clearCacheForResource()` no-ops — they iterated cache entries looking for matching resources, but every entry had `_resource == null` so nothing ever matched. The `CHANGED` path in `WodParserCacheInvalidator` worked by key (`clearCacheForType(IType)`), but file deletion and full-project invalidation were silently broken. Fixed by uncommenting the initialization.
- **Removed dead classes:** `SimpleWodModel` (never instantiated), `DisplayPage` and `ValidationPage` (empty API editor page subclasses, never instantiated — leftovers from the API editor rewrite).
- **Removed `updateWebObjectsTagNames` no-op chain.** `WodEditor.updateWebObjectsTagNames()` had its body entirely commented out since the original WOLips import (with a "MS: Come back to this" comment). `WodclipsePlugin.updateWebObjectsTagNames(WodEditor)` called it and tracked a `lastWodEditor` field (also pointless). `HtmlWodTab` registered selection listeners and called the chain from 4 sites, all producing no visible effect. Removed the methods, the field, the listeners, and the call sites.
- **Removed `ApiUtils.acceptResources()`** — a recursive method that only called itself and had no external callers.
- **Removed active `System.out.println` debug statements** from `BindingsInspector`, `WOBrowserPageBookView`, `InsertHtmlAndWodAction`, `QuickRenameElementAction`, `TemplateSourceViewerDecorationSupport`, `AbstractEngine`, `WooeditorPlugin`. Also removed dead `WOComponentCreationPage.logPreferences()` method (zero callers, body was all `System.out.println`).
- **Removed commented-out code blocks** in `AbstractWodBinding.fillInBindingProblems()` (three no-op `isWillSet()` validation blocks), `TemplateAssistProcessor.computeCompletionProposals()` (JSP scriptlet completion code), `ComponentEditor.close()`, `HtmlWodTab.close()`, `WodEditor.createActions()`, `WodEditor.webObjectTagSelected()`, and `TemplateSourceViewerDecorationSupport.createAnnotationPainter()`.
- **Removed duplicate `"neq"` entry** in `AbstractWodBinding.VALID_OGNL_VALUES` static initializer.
- **Removed dead `previewPageId` field** from `ComponentEditorPart` (declared but never read or assigned).

### Separate IWodBinding from IApiBinding

- **Removed the incorrect `IWodBinding extends IApiBinding` inheritance.** A WOD binding is a *usage* of a binding (`item = session.cart`), not a *definition* of what bindings a component accepts. The inheritance forced `AbstractWodBinding` to implement stub versions of `getDefaults()`, `isRequired()`, `isWillSet()`, `getValidValues()`, and `getSelectedDefaults()` that returned meaningless values (null, false, empty). It also allowed WOD bindings to be mixed into `IApiBinding[]` arrays in `getApiBindings()`, obscuring the distinction between definitions and usages throughout the inspector UI.
- **New class: `VisibleBinding`** — a presentation-layer wrapper representing a binding visible in the inspector UI, whether defined in the component's `.api` file or used in the WOD/template but not defined in any API. Provides `getName()`, `isAction()`, `isDefinedInApi()`, and `getApiBinding()`. The `isDefinedInApi()` check replaces the old `instanceof IWodBinding` check used for bold-face font in the inspector.
- **New method: `IWodElement.getVisibleBindings(ApiSnapshot)`** — returns `VisibleBinding[]` instead of the old `getApiBindings()` which returned a mixed `IApiBinding[]`. Uses `VisibleBinding.fromApi()` for API-defined bindings and `VisibleBinding.fromWod()` for WOD-only bindings. The old `getApiBindings()` method has been removed.
- **New method: `ApiUtils.isActionBindingName(String)`** — extracted the name-only heuristic (`"action".equals(name) || name.endsWith("Action")`) from `isActionBinding(IApiBinding)` so it can be used without a full `IApiBinding` reference.
- **Switched all inspector UI consumers to `VisibleBinding`:** `BindingsContentProvider`, `BindingsLabelProvider`, `BindingsInspector` (9 cast sites), `BindingsInspectorPage`, `BindingsInspectorDropHandler`, and `BindingsPopUpMenu` now work with `VisibleBinding` instead of `IApiBinding`.
- **Simplified `WodCompletionUtils.openBinding()` and `addKeyOrAction()`** — changed from taking an `IApiBinding` parameter (only used for `isAction()`) to taking a `boolean isAction`. Eliminates the last place where a WOD binding was passed where an `IApiBinding` was expected.
- **Rewrote `WodBindingValueHyperlink`** — stores `boolean _isAction` (computed at construction) and `String _bindingValue` instead of holding `IWodBinding` and `WodParserCache` references.
- **Simplified `WodBindingProblem` hierarchy** — removed the dead `_binding` field (type `IApiBinding`, with `getBinding()` having zero callers) from `WodBindingProblem`. Simplified constructors of `WodBindingNameProblem`, `WodBindingValueProblem`, and `WodBindingDeprecationProblem` to remove the `IApiBinding` parameter. `ApiBindingValidationProblem` retains its own `_binding` field since it's created from real API bindings.
- **Removed 7 IApiBinding stub methods from `AbstractWodBinding`:** `compareTo(IApiBinding)`, `isAction()`, `getDefaults()`, `getSelectedDefaults()`, `getValidValues()`, `isRequired()`, `isWillSet()`.
- **Removed dead `hasValidationProblem(IApiBinding, ...)` overload** from `WodModelUtils` — all callers now use the `String` overload directly.
- New file: `VisibleBinding.java`.

### API editor rewrite: eliminate DOM classes

- **Rewrote the `.api` editor to use the same POJO model as the read path, then deleted all 19 DOM-only classes.** The editor previously used a parallel Xerces DOM-backed class hierarchy (`ApiModel`, `Wo`, `Binding`, and 14 validation wrapper classes) for mutating and saving `.api` files, while the read path (validation, autocomplete, hover) used the newer `ApiSnapshot`/`SimpleApiBinding`/`ApiValidation` POJOs. This created a "two model" burden: 27 files in the `bindings/api/` package, 19 of which existed only for the editor. Now the editor mutates POJOs directly and serializes them to XML on save, eliminating the DOM layer entirely.
- **New files:**
  - `ApiSerializer.java` — serializes an `ApiSnapshot` back to `.api` XML format. Produces well-formed XML matching the existing file format, with bindings, validation trees, and preview content. Handles attribute serialization (`required="YES"`, `settable="YES"`, `defaults`) and recursive validation tree output.
  - `MutableApiModel.java` — file-backed mutable model replacing the DOM-backed `ApiModel`. Uses `ApiParser` to load and `ApiSerializer` to save. Creates blank `.api` files for new components. Refreshes the Eclipse resource after saving so `JavaChangeRevalidator` can trigger revalidation.
- **Extended existing POJOs for mutation:**
  - `IApiBinding` — added `isExplicitlySettable()` default method (mirrors `isExplicitlyRequired()`).
  - `SimpleApiBinding` — added `_explicitlySettable` field/getter/setter; added `setDefaults(int)` for the editor's defaults combo box.
  - `ApiSnapshot` — added mutation methods: `setComponentContent()`, `addBinding()`, `removeBinding()`, `bindingNameChanged()`, `removeImplicitValidation()`. Internal collections are mutable but `getBindings()` and `getValidations()` still return unmodifiable views. Read-path consumers get their own snapshot instances from `ApiParser`, so the editor mutating its own instance is safe.
  - `ApiParser` — now stores the `explicitlySettable` flag when parsing `settable="YES"` attributes (was extracting but not storing).
- **Rewired editor UI:**
  - `ApiEditor` — switched from `ApiModel` to `MutableApiModel`.
  - `BindingsPageBlock` — replaced DOM `Binding` references with `IApiBinding`/`SimpleApiBinding`; removed `BindingChangedListener` (no listener pattern needed with POJOs); add/remove binding buttons and component content checkbox now mutate `ApiSnapshot` directly.
  - `BindingDetailsPage` — replaced DOM `Binding` with `SimpleApiBinding`; simplified required/willSet toggle logic (set boolean flags + remove implicit validations, instead of manipulating DOM validation elements).
  - `CreatePage` — switched from `ApiModel` to `MutableApiModel`.
  - `GenerateAPIAction` — switched from `ApiModel`/`Wo` to `MutableApiModel`/`ApiSnapshot`.
- **Deleted 19 DOM classes:** `ApiModel`, `Wo`, `Binding`, `Wodefinitions`, `AbstractApiModelElement`, `Validation`, `IValidation`, `AbstractNamedValidation`, `AbstractValidationContainer`, `Unbound`, `Unsettable`, `Bound`, `Settable`, `Gettable`, `Ungettable`, `And`, `Or`, `Not`, `Count`. The `bindings/api/` package now contains 10 files (down from 27).

### API changes revalidate open component editors

- `JavaChangeRevalidator` now also listens for `.api` file changes. When an API file is saved (e.g., marking a binding as required), all open component editors are revalidated immediately, so validation markers reflect the updated API without needing to manually re-save or reopen the component. For `.api` changes, all open editors are revalidated regardless of project, since the API file may belong to a dependency project.

### API editor: fix required/will-set checkbox

- Fixed a bug where unchecking "Required" on a binding in the API editor would throw `DOMException: NOT_FOUND_ERR`. Two root causes:
  1. **Double removal in `Unbound.removeFromWoWithBinding()`**: `Validation.removeChild()` already removes the `<validation>` element from its parent when the validation has no remaining children. But `removeFromWoWithBinding()` had a redundant `wo.element.removeChild(validation.element)` call that tried to remove it a second time, causing the NOT_FOUND_ERR. Removed the redundant removal.
  2. **Stale DOM after save**: `ApiModel.saveChanges()` did not update `_lastModified` after writing, so the next `parseIfNecessary()` call (triggered by `doSave()` → `reloadModel()`) would reparse the DOM from disk, orphaning any `Binding`/`Wo` references held by the editor. `saveChanges()` now updates `_lastModified` after writing and refreshing, preventing unnecessary reparses.
- Removed redundant `refreshLocal()` call from `ApiEditor.doSave()` — `ApiModel.saveChanges()` already handles it.

### Immutable API model for the read path

- **Replaced the mutable Xerces DOM-backed `.api` model with immutable POJOs for all read-path consumers.** The old model used `Wo`, `Binding`, and a 14-class validation hierarchy (`Validation`, `And`, `Or`, `Not`, `Count`, `Bound`, `Unbound`, `Settable`, `Unsettable`, `Gettable`, `Ungettable`, etc.) as thin wrappers around a shared Xerces DOM. Thread safety was attempted via ~31 `synchronized(this.apiModel)` blocks, but Xerces DOM isn't thread-safe even for reads, and several callers accessed returned objects outside the lock (TOCTOU bugs).
- **New types:** `ApiSnapshot` (immutable component definition), `ApiValidation` (single class replacing the 14-class validation hierarchy, using a `Kind` enum discriminator), and `ApiParser` (static factory that parses XML into `ApiSnapshot` and discards the DOM).
- **All read-path consumers migrated:** validation (`AbstractWodElement.fillInProblems()`), autocomplete (`WodCompletionUtils`), hover documentation (`WodAnnotationHover`), tag info (`InlineWodTagInfo`), binding inspection (`BindingsContentProvider`, `BindingsInspector`, `BindingsPopUpMenu`), component insertion (`InsertComponentAction`, `InsertHtmlAndWodAction`), and drag-drop (`ComponentDropTargetAdaptor`).
- **The `.api` editor write path is unchanged** — `ApiModel`, `Wo`, `Binding`, and the validation DOM classes remain for the editor UI that needs to mutate and save XML.
- **`ApiCache` updated** with timestamp-based invalidation: every cache read compares the `.api` file's current modification timestamp against the cached timestamp; if the file has changed (even in a dependency project), the snapshot is automatically reparsed. The negative cache ("no `.api` file found") also expires after a 10-second TTL so newly created `.api` files are picked up without restart. The file lookup logic was extracted into `ApiFileInfo`/`findApiFile()` so timestamp checks are cheap (one stat call, no XML parsing).
- **`WodParserCache`** renamed `getWo()` methods to `getApiSnapshot()`, returning `ApiSnapshot` instead of `Wo`.
- New files: `ApiSnapshot.java`, `ApiValidation.java`, `ApiParser.java`.
- Modified interfaces: `IApiBinding` (added `isExplicitlyRequired()`), `SimpleApiBinding` (added `_explicitlyRequired` field), `IWodElement` (changed `getApi()` and `getApiBindings()` signatures).

### Auto-delete empty .wo folders

- Empty `.wo` folders left behind by git (which removes files but not directories) are now automatically deleted. A lightweight resource change listener watches for changes to `.wo` folders and removes them when they become empty, preventing ghost components from blocking the New Component wizard.
- New file: `EmptyWoFolderCleaner.java`.

### Rename element: .api file support for non-component elements

- Renaming a `WOElement`/`NGElement` subclass (that is not a component) via Refactor > Rename now also renames the associated `.api` file, if one exists. Previously `.api` renames only happened for `WOComponent`/`NGComponent` subclasses.

### New component wizard: template type selection

- **The New Component wizard now offers a choice between standalone and bundle template formats.** A "Component Format" radio group lets the user pick between a standalone template (ng-objects style) and a bundle template (WebObjects style).
- The default format is auto-detected from the project type: ng-objects projects default to standalone templates, WO projects default to bundle templates.
- When "Standalone HTML file" is selected, the "Optional Files" group (body tag, doctype, encoding, API) is hidden since those settings are only relevant for bundle templates.
- Standalone creation bypasses the Velocity template engine and generates files directly.

### Extract Component from selection

- **New refactoring action:** Select HTML in the template editor, press `Cmd+2, E` (or Edit > Refactor > Extract Component...), enter a name, and the selected HTML is extracted into a new WO component. The plugin creates the `.wo` folder with `.html`, `.wod`, `.woo`, and `.java` files, replaces the selection with a `<wo:NewComponentName/>` tag, and opens the new component for editing.
- The new component is created alongside the current component (same parent directory) and uses the same Java package and component superclass.
- **Automatic dedenting:** The extracted HTML is dedented — the common leading whitespace is stripped from all lines, so the new component's template starts at column 0 regardless of how deeply the code was nested in the parent. The replacement tag in the parent template is placed at the original indentation level.
- The selected HTML is placed as-is — no automatic binding detection. The user wires up bindings manually afterward.
- Input validation ensures the name is a valid Java identifier and doesn't conflict with an existing component.
- New file: `ExtractComponentAction.java`.

### Component documentation on hover

- **Hover over `<wo:ComponentName>` to see its API bindings.** The template editor now shows component documentation — accepted bindings, required/settable markers, and defaults — when hovering over inline binding tags.
- Works for project components (via `.api` files) and built-in WO components (via `WebObjectDefinitions.xml`).
- Validation errors still take priority: when both an error and documentation are available, the error message appears first with the documentation below.
- Enhanced `WodAnnotationHover` to combine error annotations and component documentation in a single hover provider.

### Rename element type: cross-reference updating

- **Template references are updated automatically:** When renaming any WOElement/NGElement subclass (not just components), all templates in the project that reference the old type name are rewritten. Inline binding tags (`<wo:OldName>` → `<wo:NewName>`, including close tags) and WOD element type declarations (`Foo : OldName { }` → `Foo : NewName { }`) are both handled.
- The participant now activates for all WOElement/NGElement subclasses, not just WOComponent/NGComponent. Custom dynamic elements that appear as tag types in templates are now covered.
- Uses `TextFileChange` with `ReplaceEdit` for precise text-level edits, integrated with LTK's refactoring preview.
- Skips derived resources and the component's own template files (which are being renamed at the resource level).

### Rename Component (bidirectional)

- **Rename Java class → rename template files:** When renaming a WOComponent/NGComponent Java class via Eclipse's Refactor > Rename, the component's template files are now also renamed automatically. The `.wo` folder, all contained files (`.html`, `.wod`, `.woo`), standalone `.html` templates, and `.api` files are renamed to match the new class name. Implemented as an LTK `RenameParticipant` that participates in Eclipse's standard Rename Type refactoring.
- **Rename `.wo` folder → rename Java class:** Right-click a `.wo` folder and select "Rename Component..." to rename the component from the template side. If a Java class exists, the rename is delegated to Eclipse's JDT refactoring (which triggers the participant above), so both directions produce the same atomic result. Template-only components (no Java class) are also supported.
- **New files:** `RenameComponentProcessor` (core rename logic), `RenameComponentParticipant` (LTK integration), `RenameComponentAction` (context menu action).
- **New dependency:** `org.eclipse.ltk.ui.refactoring` added to MANIFEST.MF for programmatic refactoring support.

### Tag shortcut capitalization validation

- **Miscapitalized tag shortcuts are now flagged:** Writing `<wo:Repetition>` when the shortcut is defined as `repetition` now produces a validation error with a "Did you mean 'repetition'?" suggestion. Uses the same error format as element type errors — the user doesn't need to know whether what they typed was a shortcut or a class name. Previously, the case-insensitive shortcut matching silently accepted any capitalization.
- Quick-fix support via Cmd+1 and the Problems view to correct the capitalization.
- Severity follows the "Missing component" preference — error by default.

### Element type quick-fixes ("Did you mean?")

- **"Did you mean?" suggestions for mistyped element type names:** When `<wo:Str>` or `<wo:WOStirng>` fails validation, the editor now suggests corrections — e.g. "Did you mean 'str'?" or "Did you mean 'WOString'?". Particularly useful for capitalization errors, which are the most common mistake.
- Extends the same quick-fix infrastructure as keypath errors: Cmd+1 anywhere on the line, Problems view Quick Fix, and hover help all work for element type errors.
- Multiple errors on the same line (e.g. `<wo:Str value="$application.nme" />`) are handled correctly — Cmd+1 shows proposals for both the element name and the keypath error.

### Keypath quick-fixes ("Did you mean?")

- **"Did you mean?" suggestions for mistyped keys:** When a keypath validation error is found (e.g. "There is no key 'nme' in MyComponent"), the validator now computes close matches using Damerau–Levenshtein string distance against all valid keys on the type where resolution failed. The best suggestion is included in the error message itself ("Did you mean 'name'?"), and up to 3 suggestions are available as quick-fixes.
- **Cmd+1 quick-fix (Quick Assist):** Pressing Cmd+1 anywhere on a line with a keypath error offers "Replace 'nme' with 'name'" proposals. This works line-wide — the cursor doesn't need to be on the exact error position.
- **Problems view quick-fix:** Right-click a keypath error in the Problems view and select Quick Fix for the same replacement proposals.
- **Hover help on errors:** Hovering over squiggly-underlined text in the template editor now shows the error message. Previously, hover only worked on the vertical ruler.
- **Adaptive distance thresholds:** Short key names (1–4 chars) only match at edit distance 1; medium names (5–8 chars) up to 2; long names (9+ chars) up to 3. This prevents noisy suggestions like "list" for "item" while still catching reasonable typos like "vlaue" for "value".
- New files: `StringDistance.java`, `KeypathQuickFixGenerator.java`, `ReplaceKeypathQuickFix.java`, `TemplateQuickAssistProcessor.java`.
- Modified files: `AbstractWodBinding.java`, `BindingValueKeyPath.java`, `WodProblem.java`, `WodModelUtils.java`, `TemplateConfiguration.java`, `plugin.xml`.
- 37 new unit tests (`StringDistanceTest.java`, `KeypathQuickFixTest.java`).
- Dead commented-out code removed from `TemplateConfiguration.java` (~80 lines of old JSP scanner stubs).

### Parser control tags (`p:raw`, `p:comment`)

- **`<p:raw>`:** Content inside this block is treated as literal text — no dynamic tag processing occurs. Useful for embedding example code or content that contains `<wo:` but shouldn't be treated as dynamic.
- **`<p:comment>`:** Content is ignored entirely — a template-level comment that is stripped from output, unlike HTML comments which are sent to the client.
- Both block types are rendered with a distinct background tint and muted text color, visually signalling that the content is treated differently from the rest of the template.
- Validation is skipped for content inside both block types.
- Linked rename (Cmd+2, R) works on both tag types — renaming the open tag updates the close tag and vice versa.
- Block content is isolated at the partitioner level — unclosed quotes or broken HTML inside a `p:` block cannot leak into the rest of the document.
- Block content is also blanked during parser preprocessing (`pBlock2space`), preventing unclosed strings from corrupting the FuzzyXML parse of surrounding elements.
- Tag autocomplete works in the `p:` namespace — typing `<p:` and pressing Ctrl+Space offers `p:raw` and `p:comment` with descriptions.
- Modified files: `FuzzyXMLParser.java`, `FuzzyXMLUtil.java`, `WodHtmlUtils.java`, `TemplateValidator.java`, `HTMLTagScanner.java`, `HTMLPartitionScanner.java`, `HTMLConfiguration.java`, `HTMLFileDocumentProvider.java`, `HTMLTextDocumentProvider.java`, `TemplateAssistProcessor.java`.

### Close-tag completion fix

- **Fixed wrong close tag suggested when attributes contain "/":** Typing `<wo:bork>` after an `<a href="/">` and pressing Ctrl+Space would suggest `</div>` instead of `</wo:bork>`. The tag-stack scanner in `getLastWord()` treated the `/` inside `href="/"` as a self-closing tag marker, popping the wrong element from the stack. A second bug — an operator precedence error (`&&`/`||` without proper grouping) — caused the same corruption for single-quoted values like `href='/'`.
- **Extracted `TagStackAnalyzer`:** The tag-stack logic (`getLastWord` and `isDelimiter`) was extracted from `HTMLAssistProcessor` into a standalone `TagStackAnalyzer` class with no SWT dependencies, making it directly unit-testable.
- Both bugs are covered by 14 new unit tests.

### Unit test infrastructure

- Added a `test/` source folder to `ng.componenteditor` with JUnit 4 via `tycho-surefire-plugin` (`plugin-test` goal, headless — no UI harness).
- Tests run automatically during `mvn verify`.
- Initial test suite: 51 tests covering `FuzzyXMLUtil.pBlock2space()` preprocessing, `FuzzyXMLParser` p: block DOM structure and offset tracking, and `TagStackAnalyzer` close-tag completion logic.

### Plugin infrastructure

- **New activator:** Bundle activator is `tk.eclipse.plugin.htmleditor.HTMLPlugin` (declared in MANIFEST.MF). Old per-plugin activators were converted to plain singletons.
- **Removed `CorePlugin.PLUGIN_ID`:** The constant `"org.objectstyle.wolips.core"` was unused and potentially confusing — removed.
- **Unique extension IDs:** All `plugin.xml` extension IDs use `ng.componenteditor.*` prefix instead of `org.objectstyle.wolips.*`.

### Features / Fixes

- **Single-file template support:** Added support for `.html` templates that are not inside `.wo` bundles.
- **Tag autocomplete and attribute name autocomplete** in the template editor.
- **Component ↔ Java switching** now works correctly.
- **Binding validation:** Template/WOD validation with markers working. Fixed marker type to use the `ng.componenteditor` bundle symbolic name.
- **Auto-revalidation on Java save:** Saving any Java file in the project now triggers automatic re-validation of all open component editors in that project. This updates validation markers immediately — e.g., if you add a method to a Java class that resolves a missing binding keypath, the error marker disappears without needing to reopen or re-save the template. Implemented via `JavaChangeRevalidator`, an `IResourceChangeListener` installed by the bundle activator.
- **CMD-click "Create key/action" dialog:** Fixed — CMD-clicking an unresolved binding keypath now correctly opens the "Add Key" or "Add Action" dialog. The old code required `isValid()` which returned `false` for ng-objects types (they don't implement `NSKeyValueCoding`). The check was relaxed to only require `!exists() && isSingleKey()`.
- **`NGContext` class name fix** for ng-objects compatibility.

### Removed: EOF / EOModeler

All EOModeler/EOF-related code was removed (~130 files). This includes:

- `org.objectstyle.wolips.eomodeler.core` — entire package (model classes, KVC framework, utils, etc.)
- EOModel resource adapters and related factory registrations

ng-objects does not use EOF.

### Removed: Build infrastructure / Patternsets

Patternsets are WO-specific build configuration for include/exclude patterns on resources, classes, and webserver resources. Removed entirely:

- `org.objectstyle.wolips.editors.patterset` — PatternsetEditor, PatternsetPage, PatternsetEditorMessages
- `org.objectstyle.wolips.core.resources.pattern` — PatternsetMatcher, PatternsetReader, PatternsetWriter, IPatternList, PatternList
- `org.objectstyle.wolips.core.resources.internal.types.project.ProjectPatternsets`
- `plugin.xml` — patternset content type and editor registrations

Cascading from patternset removal, the entire WOLips builder infrastructure was also removed (all unreferenced by the component editor):

- `org.objectstyle.wolips.core.resources.internal.build` — Builder, BuilderWrapper, AbstractBuildVisitor, IncrementalBuildDeltaVisitor, FullBuildDeltaVisitor
- `CorePlugin` — removed `BuilderWrapper` field and `loadBuilderExtensionPoint()`/`getBuilderWrapper()` methods

### Removed: Plist editors

Two self-contained plist editor packages were removed (22 files total):

- `org.objectstyle.wolips.wooeditor.plisteditor` — text-based plist source editor with syntax coloring (PlistEditor, PlistConfiguration, PlistDocumentProvider, PlistScanner, etc.)
- `org.objectstyle.wolips.baseforuiplugins.plist` — tree-based plist editor widget (PropertyListEditor, PropertyListEditorPart, content/label providers, editing support classes, StableDataStructureFactory, etc.)

Neither package was referenced by any other code in the plugin.

### Removed: PB.project / legacy project types

- `PBDotProjectAdapter` — legacy PB.project file support
- `DotSubprojAdapter` — subproject folder adapter
- `FileAdapterFactory` — file-level adapter factory
- `AbstractResourceAdapter`, `LocalizedPath`, `ILocalizedPath`
- `IPBDotProjectOwner`, `IPBDotProjectAdapter`, `IDotSubprojAdapter`

### Removed: Other unused code

- `WOLipsNatureUtils` — WOLips nature checking utilities
- `IWOLipsResource`, `ComponentWOLipsResource`, `EOModelWOLipsResource`, `GenericWOLipsResource` — WOLips resource type hierarchy
- `WOLipsResourceAdapterFactory`
- `DotLprojAdapter`, `IDotLprojAdapter` — localized project folder adapters
- `DotEOModeldAdapter`, `IDotEOModeldAdapter` — .eomodeld folder adapters
- `WoprojectAdapter`, `IWoprojectAdapter` — .woproj folder adapters
- `IProjectPatternsets` — interface for project patternsets
- `SubTypeHierarchyCache`, `SuperTypeHierarchyCache` — type hierarchy caches
- `ILaunchInfo` — launch configuration interface
- `Preferences` — stripped down significantly (removed unused launch/build preference code)
- `ProjectAdapter` — stripped down significantly (removed dead methods referencing deleted adapters)
- `FolderAdapterFactory` — deleted entirely (see "Removed: folder adapter cascade" below)
- `ProjectVariables` — deleted entirely. Was a wrapper around `WOVariables`/`WOEnvironment` for reading `wolips.properties`. Not needed by ng-objects.
- `VariablesPlugin` — deleted entirely. Was the activator/factory for `ProjectVariables`. Callers were updated:
  - `BuildProperties.ensureDefaultsInitialized()` — now uses hardcoded defaults directly instead of reading from `wolips.properties`
  - `WodBuilder.getBooleanProperty()` — removed the global properties middle tier; now falls through from build.properties directly to Eclipse preference store
- `ERXValueUtilities` — deleted entirely. Was an ERExtensions utility class (~740 lines). The `er.extensions.foundation` package is gone.

### WOLips coexistence: hardcoded ID cleanup

To allow `ng.componenteditor` to coexist with WOLips in the same Eclipse installation without cross-talk, all hardcoded `"org.objectstyle.wolips.*"` string references that identify *this* plugin's own resources were updated to `"ng.componenteditor.*"`. This includes:

- **Removed all `Export-Package` declarations** from MANIFEST.MF — since no other bundle depends on `ng.componenteditor`, exports are unnecessary. This eliminates OSGi split-package conflicts with WOLips (both bundles use `org.objectstyle.wolips.*` Java packages internally).
- **`imageDescriptorFromPlugin` calls** (5 files: `ApieditorPlugin`, `ComponentsPlugin`, `EditorsPlugin`, `WooeditorPlugin`, `WodclipsePlugin`) — changed plugin ID from various WOLips IDs to `"ng.componenteditor"`.
- **Editor ID constants** in `EditorsPlugin` — `ApiEditorID`, `HTMLEditorID`, `WodEditorID`, `WooEditorID`, `ComponentEditorID`, `WOBuilderID` now use `"ng.componenteditor.*"` matching plugin.xml.
- **Page ID constants** in API editor pages (`BindingsPage`, `DisplayPage`, `ValidationPage`, `DeletePage`, `CreatePage`) and `DisplayGroupPage` — changed from `"org.objectstyle.wolips.wodclipse.*"` to `"ng.componenteditor.*"`.
- **`PLUGIN_ID` constants** — `ComponenteditorPlugin.PLUGIN_ID`, `wodclipse.core.Activator.PLUGIN_ID`, `bindings.Activator.PLUGIN_ID` all now return `"ng.componenteditor"`.
- **Extension point lookup** in `BuildPropertiesAdapterFactory` — changed from `"org.objectstyle.wolips.variables.buildPropertiesInitializer"` to `"ng.componenteditor.buildPropertiesInitializer"`.
- **Preference store qualifier** in `BuildProperties.ensureDefaultsInitialized()` — changed from `"org.objectstyle.wolips.bindings"` to `"ng.componenteditor"`.
- **WOD content type** in `WodFileDocumentProvider` — changed from `"org.objectstyle.wolips.wodclipse.wod"` to `"ng.componenteditor.wod"` (matches plugin.xml).
- **Annotation type** in `TemplateEditor.BINDING_HOVER_ANNOTATION` — changed to `"ng.componenteditor.bindingHover"` (matches plugin.xml).
- **Preference keys** — `HtmlWodTab` sash weights key, `TemplateOutlinePage` compact view key, `Preferences.PREF_WOLIPS_PROPERTIES_FILE` all namespaced to `ng.componenteditor.*`.
- **Error status ID** in `ErrorUtils` — changed to `"ng.componenteditor"`.

**Intentionally kept as `org.objectstyle.wolips.*`:**
- `AbstractEngine` Velocity resource loader class name — this is an actual Java fully-qualified class name, not a plugin ID

### WOLips coexistence: project-aware editor selection

Added `NGEditorAssociationOverride`, an `IEditorAssociationOverride` that ensures the NG Component Editor takes precedence over WOLips (or any other editor) for component files in ng-objects projects.

**How it works:** When Eclipse is about to open an editor for an `.html`, `.wod`, `.woo`, or `.api` file, the override checks whether the file's project has `project.base=ng` in its `build.properties`. If so, it forces the NG Component Editor. Projects without `project.base=ng` are left alone, so WOLips continues to work normally for WebObjects projects.

**Key files:**
- `NGEditorAssociationOverride.java` — the override implementation
- `plugin.xml` — registers the `org.eclipse.ui.ide.editorAssociationOverride` extension
- Uses the existing `BuildProperties` adapter to read `build.properties`

### Removed: WODisplayGroup editor and related dead code

Gutted the WOO editor tab (the "Display Groups" tab in the component editor). The WODisplayGroup/ERXDisplayGroup editing UI, data model, and supporting classes have been removed — ng-objects doesn't use EOF display groups.

**Deleted:**
- `DisplayGroup.java`, `DisplayGroupPage.java`, `DisplayGroupPageBlock.java`, `DisplayGroupDetailsPage.java` — the entire display group editor UI and data model
- `WooFormPage.java` — base class, orphaned after display group removal
- `wooeditor/databinding/observable/` (4 files) and `wooeditor/widgets/RadioGroup.java` — custom SWT databinding classes used exclusively by the display group detail form

**Simplified:**
- `WooEditor.java` — stripped to a minimal `FormEditor` shell with a blank placeholder page. Retains encoding change detection. Ready to host future component-level settings.
- `WooModel.java` — removed all display group fields, constants, create/remove/refactor methods. Retained: .woo file I/O, encoding management, encoding validation.
- Component editor tab label changed from "Display Groups" to "WOO"

**Additional dead code removed (unrelated to display groups):**
- `tobeintregrated/` package (`MethodSearch`, `ASTMethodExplorer`, `NameComparator`) — abandoned, never-integrated code with zero callers
- `IPattern.java` + `Pattern.java` — orphaned patternset glob matcher (the Ant replacement noted below), no callers remained
- `EOGenLocateScope.java` — EOGenerator folder locator, no callers
- `IPropertyChangeSource.java` — interface with zero implementations
- `DuplicateNameException.java` + `ISortableEOModelObject.java` — only referenced each other
- `PropertyListComparator.java` — removed dead `ISortableEOModelObject` code branch

### Removed: WOLips nature checks and launcher infrastructure

ng-objects projects don't use WOLips project natures — they use `project.base=ng` in `build.properties` instead. All nature-checking code has been removed, along with unregistered JavaScript launcher infrastructure inherited from the Amateras HTML editor.

**Deleted:**
- `WOLipsNatureUtils.java` — WOLips nature IDs and add/remove/check utilities
- `Nature.java` — `IProjectNature` implementation, orphaned after `WOLipsNatureUtils` removal
- `jseditor/launch/` (8 files) — `JavaScriptLaunchConfigurationDelegate`, `JavaScriptLaunchConstants`, `JavaScriptLaunchShortcut`, `JavaScriptLaunchUtil`, `JavaScriptLibraryTable`, `JavaScriptMainTab`, `JavaScriptTabGroup`, `executer/JavaScriptExecutor`. None were registered in `plugin.xml` — dead code from the Amateras HTML editor
- `SystemEditorLauncher.java` — not registered in `plugin.xml`
- `JavaScriptPropertyPage.java` — not registered in `plugin.xml`, fully dependent on deleted `JavaScriptLibraryTable`

**Simplified:**
- `ProjectAdapterFactory` — `createAdapter()` now returns `null` directly (ng-objects projects don't have WOLips natures, so the adapter factory never produces results). Kept as a no-op stub because it's registered in `plugin.xml`.
- `FolderAdapterFactory` — same treatment; `createAdapter()` returns `null`, `isSupported()` simplified.
- `BuildPropertiesAdapterFactory` — removed stale `WOLipsNatureUtils` import and commented-out nature check.
- `HTMLPlugin` — removed `JavaScriptLaunchUtil.removeLibraries()` call from `stop()`.
- `JavaScriptAssistProcessor` and `JavaScriptHyperlinkDetector` — removed `JavaScriptLibraryTable` imports, inlined the `"entry:"` prefix constant.

### Wired up: insert component toolbar

Registered all 15 insert actions in `plugin.xml` so the component editor toolbar matches WOLips. Previously only the generic "Insert Component" button was wired up (and its icon was missing). Now the full set appears in the toolbar: Insert Tag, Insert Component, WOForm, WOBrowser, WOPopUpButton, WOText, WOCheckBox, WORadioButton, WOSubmitButton, WOImage, WOTextField, WOHyperlink, WORepetition, WOConditional, WOString. Copied the 15 corresponding toolbar icons from WOLips.

### Removed: folder adapter cascade

The entire WOLips folder adapter type hierarchy has been deleted — 21 files total. These adapter types were produced by `FolderAdapterFactory`, which was already a no-op stub (always returning `null`). No instances of these classes were ever created at runtime.

**Deleted (8 implementations + 8 interfaces + base classes):**
- `DotApplicationAdapter`, `DotFrameworkAdapter`, `DotWoAdapter`, `BuildAdapter`, `ContentsAdapter`, `ResourcesAdapter`, `WebServerResourcesAdapter`, `ProductAdapter`
- `IDotApplicationAdapter`, `IDotFrameworkAdapter`, `IDotWoAdapter`, `IBuildAdapter`, `IContentsAdapter`, `IResourcesAdapter`, `IWebServerResourcesAdapter`, `IProductAdapter`
- `AbstractFolderAdapter`, `IFolderAdapter` — orphaned after concrete adapters removed
- `AbstractFileAdapter`, `IFileAdapter` — no subclasses remained
- `FolderAdapterFactory` — removed from both source and `plugin.xml`

**Simplified:**
- `ProjectAdapter` — removed `getBuildAdapter()` and `getBuildFolder()` (referenced deleted `IBuildAdapter`)
- `ApiUtils` — removed dead "Resources" binding defaults branch that relied on the never-instantiated adapter chain
- `WOComponentCreationPage` — fixed latent NPE: was calling `getAdapter(ProjectAdapter.class)` which always returns `null`, then dereferencing it. Inlined the `getDefaultComponentsFolder()` logic (returns project root) directly.

### Removed: orphaned icons

Deleted 41 orphaned icon files (30 PNGs, 11 GIFs) with zero references in source or `plugin.xml`:

- 17 EOF/EOModeler icons: `eoArgument`, `eoAttribute`, `eoDatabaseConfig`, `eoEntity`, `eoEntityIndex`, `eoFetchSpecification`, `eoModel`, `eoRelationship`, `eoStoredProcedure`, `newArgument`, `newAttribute`, `newDatabaseConfig`, `newEntity`, `newEntityIndex`, `newFetchSpecification`, `newRelationship`, `newStoredProcedure`
- 13 other orphaned PNGs: `allowsNull`, `ascending`, `check`, `classProperty`, `descending`, `flattenRelationship`, `locking`, `migration`, `primaryKey`, `reverseEngineer`, `sql`, `subclassEntity`, `verify`
- 11 orphaned GIFs: `BindingOutline`, `ComponentEditor`, `WODEditor`, `ant_buildfile`, `delete`, `eomodeler`, `icns`, `projectbuilder`, `ruleeditor`, `sample`, `wobuilder`

### Removed: orphaned lib JARs

Deleted 5 JARs from `lib/` and removed them from `Bundle-ClassPath` in MANIFEST.MF. No import statements for any of these libraries exist in the codebase.

- `JavaScriptExecutor.jar` — dead after JavaScript launcher removal
- `velocity-tools-generic-1.4.jar` — Velocity tools (never imported; Velocity itself is still used)
- `oro-2.0.8.jar` — obsolete regex library (replaced by `java.util.regex`)
- `avalon-logkit-2.1.jar` — obsolete logging framework (replaced by SLF4J)
- `commons-lang-2.1.jar` — Apache Commons Lang (no imports found)

### Removed: adapter factory debug logging

Removed the noisy debug log message from `AbstractResourceAdapterFactory.getAdapter()` that logged "This Adapter Factory does not support adaptableObject: ..." for every non-matching adapter query. This is normal Eclipse adapter framework behavior and doesn't warrant a log entry. The unused `CorePlugin` import was also cleaned up.

### Removed: Apache Ant dependency

Removed `org.apache.ant` and `org.eclipse.ant.core` from `Require-Bundle` in MANIFEST.MF. These bundles are not always present in modern Eclipse installations (e.g. Eclipse IDE for Java Developers) and were preventing the OSGi bundle from resolving.

The only usage was `org.apache.tools.ant.types.selectors.SelectorUtils.matchPath()` in `Pattern.java` — replaced with an equivalent pure-Java Ant-style glob matcher. The `Pattern` / `IPattern` classes themselves are currently unused by any other code in the plugin.

### Added: install.sh

Added `install.sh` — a one-command build-and-install script for local development. Usage: `./install.sh /path/to/Eclipse.app`. Builds the plugin via Maven/Tycho, installs it via the p2 director, and patches `bundles.info` so Eclipse actually loads the new version on restart. (The p2 director updates the p2 profile in `~/.p2/` but does not update the SimpleConfigurator's `bundles.info` inside `Eclipse.app`, causing Eclipse to keep loading the old version — the script fixes this.)

### Removed: JSP editor

Deleted the entire `tk.eclipse.plugin.jspeditor` package tree (28 Java files) — a vendored JSP editor from the Amateras HTML editor suite. JSP editing is irrelevant to WebObjects/ng-objects component development, and none of the JSP classes were registered in `plugin.xml` or referenced by active code.

Also deleted JSP-specific resource files: 5 DTDs (`jspxml.dtd`, `web-jsptaglibrary_1_1.dtd`, `web-jsptaglibrary_1_2.dtd`, `web-app_2_2.dtd`, `web-app_2_3.dtd`), 4 XSDs (`jsp_2_0.xsd`, `jspxml.xsd`, `web-jsptaglibrary_2_0.xsd`, `web-app_2_4.xsd`), and the entire `TLD/` directory (15 JSTL tag library descriptors).

Cleaned up 5 residual JSP references in HTML editor code:
- `HTMLPlugin` — removed `IJSPFilter` / `ITLDLocator` fields and methods
- `TLDPreferencePage` — removed `ITLDLocator` usage in `performDefaults()`
- `ICustomTagConverter` — removed `JSPInfo` parameter from `process()` method
- `ICustomTagValidator` — removed `IJSPValidationMarkerCreator` and `JSPInfo` parameters from `validate()` method
- `JarAcceptor` — removed `TLDInfo`-dependent `accept(IProject, ...)` overload

### Removed: dead utilities

- `com.uwyn.rife.tools.ObjectUtils` — vendored RIFE utility class with zero references
- `StringUtilities.java` — merged 2 used methods (`isDigitsOnly`, `isNumericOnly`) into `StringUtils.java`, deleted 4 unused methods. Callers updated: `AbstractWodBinding`, `ElementRename`.
- `StringUtils.java` — trimmed from 16 methods down to 5 (the only ones with callers): `isDigitsOnly`, `isNumericOnly`, `getErrorMessage` (2 overloads), `findUnusedName`.

### Package structure: merged tiny packages

Merged 12 single/two-file packages into their logical neighbors, eliminating unnecessary package fragmentation. All `plugin.xml` class references and Java imports updated accordingly.

| Merged package | → Into | Files moved |
|---|---|---|
| `componenteditor.contributor` | `componenteditor.part` | `ComponentEditorContributor` |
| `componenteditor.listener` | `componenteditor.part` | `JavaChangeRevalidator` |
| `componenteditor.bindings` | `componenteditor.actions` | `AddActionAction`, `AddKeyAction` |
| `componenteditor.outline` | `componenteditor.part` | `ComponentEditorOutline`, `EmptyOutlinePage` |
| `componenteditor.launcher` | `componenteditor.part` | `ComponentEditorMatchingStrategy`, `NGEditorAssociationOverride` |
| `editors.contentdescriber` | `editors` | `ContentDescriberWO` |
| `wodclipse.core.validation` | `wodclipse.core.completion` | `TemplateValidator`, `HtmlProblem`, `InlineWodProblem` |
| `eomodeler.core.utils` | `eomodeler.core.model` | `BooleanUtils` |
| `wooeditor.editor` | `wooeditor` | `WooEditor`, `NonExistingFileEditorInput` |
| `thirdparty.velocity.resourceloader` | `templateengine` | `ResourceLoader` |
| `core.resources.types.project` | `core.resources.types` | `ProjectAdapter` |
| `core.resources.internal.types.project` | `core.resources.internal.types` | `ProjectAdapterFactory` |

### Dual framework support (ng-objects + WebObjects)

The plugin can now be used for both ng-objects and WebObjects projects. Previously, the element type hierarchy was hardcoded to `ng.appserver.templating.NGElement` — which meant autocomplete, validation, and the "New Component" wizard only worked with ng-objects projects.

**Detection priority** (per-project):
1. `project.base=ng` in `build.properties` → uses ng-objects types (`NGElement`, `NGComponent`)
2. `project.base=wo` in `build.properties` → uses WebObjects types (`WOElement`, `WOComponent`)
3. Neither set → probes the project classpath (tries `NGElement` first, falls back to `WOElement`)

This allows users with mixed-framework workspaces (or projects that include both library sets) to explicitly control which framework the plugin targets via `build.properties`.

**Key changes:**
- `BuildProperties` — added framework detection utility: `getElementClass()`, `getComponentClass()`, `getPrivateElementPackage()`, `isNGProject()`, plus static convenience overloads accepting `IProject`/`IJavaProject`. Constants for both ng and WO type names defined centrally.
- `TypeNameCollector` — default constructors now call `BuildProperties.getElementClass(project)` instead of hardcoding `NGElement`
- `WodBuilder.handleSource()` — element type lookup now uses `BuildProperties.getElementClass()` for the project
- `LocalizedComponentsLocateResult` — `superclasses` array now includes both `NGElement` and `WOElement` (this class has no project context at init time)
- `BindingReflectionUtils.isWOComponent()` — now checks for both `NGComponent` and `WOComponent` in the type hierarchy
- `BindingReflectionUtils._systemTypeNames` — now includes both ng and WO system type names (`NGElement`/`WOElement`, `NGComponent`/`WOComponent`, `NGActionResults`/`WOActionResults`)
- `ApiUtils.findApiModelWo()` — private element package check now matches both `ng.appserver.templating._private.` and `com.webobjects.appserver._private.`
- `AbstractWodElement.fillInProblems()` — error message updated from "does not extend NGElement" to framework-agnostic wording
- `WOComponentCreationPage` — "New Component" wizard default superclass now resolved dynamically from the target project
- `NGEditorAssociationOverride.isNGProject()` — now delegates to `BuildProperties.isNGProject()` (includes classpath probing, not just `project.base=ng` check)

### Added: NG Explorer view

Ported the WO Explorer project view from `org.objectstyle.wolips.jdt` into `ng.componenteditor`. The NG Explorer is a Package Explorer variant with component-aware behavior for bundle template folders.

**What's included:**
- `.wo` component folders show the expansion triangle — can be expanded to see contents (html/wod/woo files)
- Double-click or Enter on a `.wo` folder opens the NG Component Editor (finds the HTML template inside)
- Custom `.wo` component icon via a global decorator (`WOComponentDecorator`) — appears in ALL views (NG Explorer, Package Explorer, Project Explorer, etc.), with problem marker overlays
- Custom sorting — `.wo` bundles sorted alphabetically alongside files, after regular folders
- Working Set mode support

**Improvements over WOLips' WO Explorer:**
- `.wo` folders are expandable (WOLips collapsed them into opaque leaf nodes with no way to see contents)
- Source folder pull-up: `src/main/components`, `src/main/woresources`, and `src/main/webserver-resources` are pulled up to the project root level (similar to how Eclipse shows `src/main/java`). Only folders that actually exist in the project are shown. They are removed from their physical location under `src/main/` to avoid duplication. Labels show the full project-relative path (e.g., `src/main/components`). Ordering: `src/main/*` Java source roots first, then pulled-up folders, then `src/test/*` source roots, then classpath containers (JRE, Maven, etc.).

**What's been stripped (vs WOLips' WO Explorer):**
- Tagged Components feature — unused virtual folder grouping (~1,000 lines removed)
- `.eomodeld` bundle handling — ng-objects doesn't use EOF
- `RenameWOComponentAction` — required the separate WOLips refactoring plugin

**Coexistence:** Uses unique identifiers (`ng.componenteditor.explorer.*`, `ng.componenteditor.decorator.*`) so it can coexist with WOLips' WO Explorer in the same Eclipse installation. The view appears under the "NG Component Editor" view category.

**Key files:**
- `ng.componenteditor.explorer.NGPackageExplorerPart` — main view class (double-click + Enter handling, label provider install)
- `ng.componenteditor.explorer.NGPackageExplorerContentProvider` — content provider with source folder pull-up and `isComponentBundle()` helper
- `ng.componenteditor.explorer.NGWorkingSetAwareContentProvider` — working set variant with same pull-up
- `ng.componenteditor.explorer.NGJavaElementComparator` — sorting for source folders, .wo bundles
- `ng.componenteditor.explorer.SourceFolderDecorator` — wraps the tree's inner `IStyledLabelProvider` to show full paths and WO overlay badges for pulled-up source folders
- `ng.componenteditor.explorer.WOComponentDecorator` — global `ILabelDecorator` for `.wo` folder icon + problem markers
- `ng.componenteditor.explorer.NGWorkingSetAwareJavaElementSorter` — working set sorting variant
- `plugin.xml` — view registration, JDT filters, decorator registration

### Syntax highlighting improvements

- **Distinct OGNL syntax colors:** OGNL keypaths (e.g. `session.user.name`) now have their own syntax color (dark teal), distinct from constant strings. Previously, both OGNL expressions and string constants used the same color, making it hard to distinguish dynamic bindings from static values at a glance.
- **Distinct dynamic binding tag color:** `<wo:` and `<webobject>` tags now use a distinct color (dark purple) to visually separate them from regular HTML tags.
- **Subtle background tint for WO tags:** Dynamic binding tags (`<wo:...>`, `<webobject>`) render with a subtle warm-tinted background to make component boundaries easy to spot in dense templates.

### Source folder labels and badges

- **Uniform source folder labels:** Pulled-up source folders in the Parsley Explorer (e.g. `src/main/components`) display their full project-relative path as a uniform-styled label — no grey qualifier prefix that the default JDT label provider would add.
- **WO overlay badge:** Pulled-up source folders display a small "wo" overlay badge at bottom-left, similar to how JDT marks Java source folders. The badge uses `DecorationOverlayIcon` with a cached composite image.

### Source folder decorator: reflection-based wrapping

The `SourceFolderDecorator` was rewritten to preserve Eclipse's platform decorator chain (EGit branch labels, problem markers, team decorations).

**Problem:** The original approach replaced the `DecoratingStyledCellLabelProvider` on the tree viewer with a custom wrapper. This severed the connection to Eclipse's `DecorationScheduler`, which feeds asynchronous decorator updates (EGit git branch/status labels, problem markers, etc.) into the viewer. Result: git decorations were missing from the Parsley Explorer.

**Solution:** Instead of replacing the label provider, `SourceFolderDecorator.install()` now wraps only the *inner* `IStyledLabelProvider` delegate inside the existing `DecoratingStyledCellLabelProvider`, using reflection to re-inject the wrapper. The outer decorator chain stays intact, so all platform decorators continue to work. A `FallbackSourceFolderLabelProvider` is used as a last resort if the reflection approach fails.

### Added: Parsley perspective

Added a dedicated Parsley perspective for WebObjects / ng-objects component development.

**Layout:**
- Left: Parsley Explorer + Type Hierarchy
- Center: Editor area
- Center bottom: Problems, Console, Javadoc, Source views
- Right: Outline

**Includes:** Debug, Java, and element creation action sets. Team/EGit action sets for git toolbar actions. Show View shortcuts for all commonly used views. New wizard shortcuts for Java types, source folders, and files.

**Key files:**
- `ng.componenteditor.NGPerspectiveFactory` — perspective layout definition
- `plugin.xml` — perspective registration with `icons/parsley16.png` icon

### Renamed: perspective and explorer branding

The perspective is now called "Parsley" and the explorer view is called "Parsley Explorer". The perspective factory class is `NGPerspectiveFactory` and the perspective ID is `ng.componenteditor.ParsleyPerspective`.

### Fixed: WOD validation false positives (DOM thread-safety)

Fixed a long-standing intermittent bug where WOD validation would report false errors like "exactly one of 'count' or 'list' must be bound" on `WORepetition` — even though `list` was clearly bound.

**Root cause:** The `WebObjectDefinitions.xml` API model is a shared static singleton (`_globalApiModel` in `ApiUtils`). Its backing DOM `Document` was accessed concurrently by multiple WOD validation threads. Java's DOM implementation (Xerces) is not thread-safe, even for read-only access. When multiple threads simultaneously traversed the DOM tree (calling `getChildNodes()`, `getAttribute()`, etc.), `getAttribute("name")` on `<bound name="list"/>` elements would intermittently return `""` instead of `"list"`, causing the validation count to be 0 instead of 1, which triggered the false error.

**Fix:** `Wo.getFailedValidations()` now holds `synchronized (this.apiModel)` for the entire validation evaluation, ensuring exclusive access to the DOM tree during validation. This is the same lock used by `parse()`, `getWODefinitions()`, and other DOM-accessing methods.

### Full editor support for standalone HTML templates

Standalone templates (`.html` files not inside `.wo` folders) now have full editor support: **autocomplete**, **keypath validation**, and **build-time validation** — all on par with traditional `.wo` folder components. This is new territory beyond what WOLips ever supported, and a key enabler for ng-objects, where single-file templates are the primary component format.

**What works now:**
- Inline `wo:` tag autocomplete with correct element type filtering (only WOElement/NGElement subclasses)
- Keypath completion against the component's Java class (or the base component class for classless templates)
- Validation markers for invalid keypaths, missing bindings, undefined elements
- Build-time validation triggered by both template saves and Java class saves
- Correct framework detection (ng-objects vs WebObjects) in mixed workspaces

**What was fixed (cumulative across multiple commits):**

*Validation for classless components:*
- `WodParserCache.getComponentType()` — when no Java class exists for a component, returns `WOComponent`/`NGComponent` as a fallback so validation still runs. The fallback is intentionally NOT cached in `_componentType` to avoid poisoning the cache if JDT's index isn't ready yet.

*Element type autocomplete filtering:*
- `BuildProperties.WO_ELEMENT_CLASS` — corrected from `com.webobjects.appserver._private.WOElement` to `com.webobjects.appserver.WOElement`. The `_private` package is for built-in element implementations, not the `WOElement` base class itself. This caused `findType()` to return null → no superclass filtering → every Java class appeared in autocomplete.
- `TypeNameCollector.acceptType()` — re-enabled the supertype hierarchy check as a safety net. The check had been commented out, relying solely on `WOHierarchyScope` which can return `true` for everything in edge cases.
- `BuildProperties` static convenience methods — changed fallback defaults from `NG_*` to `WO_*` when the project adapter lookup fails (the common case for projects without explicit `project.base=` configuration).

*Build-time validation for standalone templates:*
- `WodBuilder.handleWoappResources()` — added handling for `.html` files not inside `.wo` folders. Previously only `.wo` folder contents were processed.
- `WodBuilder.handleSource()` — when a Java class is saved, now also re-validates standalone HTML templates (not just `.wod` files inside `.wo` folders).
- `WodParserCache.validate()` — for standalone templates, validates directly on the cache instance instead of delegating to `WodBuilder.validateComponent()` (which can't re-locate a component from a plain parent directory).

*Component file location for standalone templates (the deep one):*
- `WodParserCache` — added `_standaloneFile` field to track the original HTML file. Previously, the cache only stored `_woFolder` (the parent directory for standalone files), and `clearLocateResultsCache()` passed this to `LocatePlugin.getLocalizedComponentsLocateResult()`. The locate system extracted the component name via `fileNameWithoutExtension()` on the folder — getting the *directory name* (e.g. "Components") instead of the *component name* (e.g. "MyPage"). With the fix, standalone files are passed directly to the locate system, so it searches for the correct component name and finds the associated Java class, API file, etc.
- `WodParserCache.getCacheKey()` — standalone templates now use the file path as the cache key (not the parent directory), since multiple standalone templates can live in the same directory.

### Deleted: old WOLips plugin

The original WOLips plugin suite (all `org.objectstyle.wolips.*` plugins, features, and the p2 update site) was deleted from the repository. Only `ng.componenteditor` and the build infrastructure remain.

### New modules: parslips.tooling and parslips.lsp

Two new modules added alongside `ng.componenteditor` to begin separating template intelligence from the Eclipse editor:

**`parslips.tooling`** — IDE-agnostic template editing intelligence library. Packaged as an OSGi bundle with zero dependencies (no Eclipse, no LSP, no framework imports). This is where generic template completion, validation, hover, and navigation logic will live, consumed by both the Eclipse editor and the LSP server. Currently a skeleton with a `package-info.java` documenting the planned architecture.

**`parslips.lsp`** — In-process LSP server for template files. Uses LSP4E and LSP4J to provide language server capabilities within Eclipse. Architecture:
- `ParslipsLanguageServer` — implements LSP4J `LanguageServer`, declares capabilities (text sync, completion)
- `ParslipsTextDocumentService` — handles document lifecycle and editing requests (stub, to be wired to `parslips.tooling`)
- `ParslipsWorkspaceService` — handles workspace-level requests (stub)
- `ParslipsStreamConnectionProvider` — bridges LSP4E to the in-process server via piped streams
- `plugin.xml` — registers the server with LSP4E, maps to HTML template and WOD content types

Both modules are included in the feature (`ng.componenteditor.feature`) and the p2 update site. The parent POM builds them before `ng.componenteditor` (since `ng.componenteditor` may eventually depend on `parslips.tooling`).

### Removed: woenvironment.jar dependency

Eliminated the `woenvironment.jar` vendored dependency. This JAR was gitignored and not tracked by git, which caused CI builds (GitHub Actions) to fail because the JAR wasn't present.

**What was removed:**
- `lib/woenvironment.jar` — deleted
- `woenvironment.jar` entry in `.gitignore` — removed (no longer needed)
- `lib/woenvironment.jar` entry in MANIFEST.MF `Bundle-ClassPath` — removed
- `EOModelParserDataStructureFactory.java` — deleted (only consumer of `ParserDataStructureFactory` interface from the JAR)

**What was replaced in `BuildProperties.java`:**
- `Version` class → replaced with plain `String`. The `Version` class was a version string holder with comparison methods, but the only version comparison (`isBuildFolderRequired()` calling `isAtLeastVersion(5, 6)`) had zero callers. Removed `isBuildFolderRequired()` as dead code.
- `Root` class → `isEmbed(Root)` and `setEmbed(Root)` methods had zero callers. Removed as dead code.
- `ToHellWithProperties` → replaced with an anonymous `Properties` subclass that sorts keys via `TreeSet` (same deterministic output behavior).

**What was replaced in `WooModel.java`:**
- `WOLPropertyListSerialization` → replaced with inline `parseSimplePlist()` and `serializeSimplePlist()` methods. The `.woo` file format is a simple NeXT-style plist dictionary (`{ "key" = "value"; }`) with only flat string key-value pairs, so a minimal parser is sufficient. Handles quoted strings with backslash escapes.
- `PropertyListParserException` → no longer thrown; `IOException` is used instead.
- `EOModelParserDataStructureFactory` → no longer needed (was only passed to `WOLPropertyListSerialization`).

### Added: "New Project" wizard

Added a wizard for creating new ng-objects or WebObjects Maven projects from scratch. Appears in Eclipse's "New Project" dialog under the "NG Objects" category.

**What it does:**
- Single-page wizard with project name, location controls, and a framework selector (ng-objects / WebObjects)
- On Finish → generates a complete, ready-to-run Maven project with a sample Main component
- Opens `Main.html` in the editor after creation

**Architecture:** `WOProjectCreator` is a pure file writer with zero Eclipse dependencies — it only uses `java.nio.file` and `java.io`. This keeps the templates portable and reusable for future Maven archetypes. `WOProjectCreationPage` handles Eclipse integration: it writes files to disk via `WOProjectCreator`, then imports the result as a Maven project using m2e's `IProjectConfigurationManager.importProjects()` (equivalent to "Import > Existing Maven Projects").

**Generated project structure (ng-objects):**
- `pom.xml` — jar packaging, Java 25, `ng-appserver` + `ng-adaptor-jetty` + `slf4j-simple` dependencies
- `build.properties` — `project.base=ng` for framework detection
- `Application.java`, `Session.java`, `DirectAction.java` — standard ng-objects classes
- `components/Main.java` — extends `NGComponent` with `NGContext` constructor
- `src/main/components/Main.html` — standalone hello world template
- `src/main/components/Main.wod`, `Main.woo` — empty WOD and UTF-8 WOO

**Generated project structure (WebObjects):**
- `pom.xml` — `woapplication` packaging, Java 25, Wonder 8.0.0.slim-SNAPSHOT (ERExtensions, ERLoggingReload4j, Ajax) + JavaWebObjects 5.4.3, `vermilingua-maven-plugin` for WO build support
- `build.properties` — `project.base=wo` for framework detection
- `Application.java`, `Session.java`, `DirectAction.java` — standard Wonder/WO classes
- `components/Main.java` — extends `WOComponent(WOContext)`
- `src/main/components/Main.wo/` — bundled component (Main.html, Main.wod, Main.woo)
- `src/main/woresources/Properties` — log4j console configuration

**Key files:**
- `WOProjectCreationWizard.java` — wizard entry point, follows `WOComponentCreationWizard` pattern
- `WOProjectCreationPage.java` — extends `WizardNewProjectCreationPage`, adds framework radio buttons, derives package name from project name. Uses m2e for project import. SWT widget values captured on UI thread before background work.
- `WOProjectCreator.java` — pure file writer (zero Eclipse deps), generates all files using Java text blocks with `String.format()`
- `plugin.xml` — wizard registration with `project="true"`
- `Messages.properties` — 6 new strings for wizard UI labels
- MANIFEST.MF — added `org.eclipse.m2e.core` to `Require-Bundle`

### Added: Maven preference page

Added a "Maven" preference page under Preferences → NG Component Editor → Maven.

**WOCommunity Maven Repository setup:**
- Detects whether the user's `settings.xml` already has the WOCommunity repository configured (searches for `maven.wocommunity.org` in any `<url>` element)
- If not configured, provides an "Add WOCommunity Repository" button that adds a profile with release and snapshot repositories (plus matching plugin repositories)
- Creates a backup (`settings.xml.bak`) before modifying
- Handles missing `settings.xml` (creates a new one) and missing `~/.m2/` directory
- Uses m2e's configured user settings path, falling back to `~/.m2/settings.xml`

**WOCommunity Archetype Catalog:**
- Statically registered via the `org.eclipse.m2e.core.archetypeCatalogs` extension point
- Always available in Eclipse's New Maven Project wizard — no user action needed
- The preference page shows the registration status

**Key files:**
- `MavenPreferencePage.java` — preference page with DOM-based settings.xml manipulation
- `plugin.xml` — preference page registration + archetype catalog extension

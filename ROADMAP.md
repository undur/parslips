# Parsley — Feature Roadmap

Ideas and planned features for the Parsley template editor. Items are roughly ordered by perceived impact, not by implementation order.

## ~~Type resolution performance~~ ✓

Implemented. Three improvements that together eliminated the 4-5 second delay when opening components or triggering tag autocomplete in large projects:

1. **~~Don't check deprecation status eagerly~~** — `InlineWodTagInfo` now caches the resolved `IType` from `loadAttributeInfo()`, so the deprecation check in `HTMLAssistProcessor` reuses the already-resolved type instead of triggering a redundant `findElementType()` call for every completion proposal.

2. **~~Skip `WOHierarchyScope` for exact-match lookups~~** — `findMatchingElementClassNames()` now uses a project-scoped search for `R_EXACT_MATCH` queries instead of building the expensive hierarchy scope. The hierarchy scope (which constructs a full type hierarchy + resource vector) is only used for prefix/pattern matching.

3. **~~Cache `InlineWodTagInfo` instances per project~~** — `TemplateAssistProcessor` maintains a per-project cache of `InlineWodTagInfo` instances, invalidated when Java or `.api` files change (via `WodParserCacheInvalidator`). Repeat autocomplete invocations reuse cached instances instead of re-running `loadAttributeInfo()` each time.

## ~~Inline validation quick-fixes~~ ✓

Implemented. Keypath validation errors ("There is no key 'nme'") now include "Did you mean 'name'?" suggestions computed via Damerau–Levenshtein string distance. Quick-fixes are available via Cmd+1 (anywhere on the line) and the Problems view. Hover over the error to see the suggestion inline.

Binding *name* validation quick-fixes (is "valu" a valid binding on WOString?) are deferred until the element API model is improved — most WO components accept arbitrary bindings beyond their `.api` definitions, making name validation too noisy.

### "Create key" vs. "Create action" should be a user choice

The quick-fix currently infers whether to open the Add Key or Add Action dialog based on the binding name (`action` or `*Action` → action method, everything else → key). This heuristic is dubious: a data binding might legitimately be called `action`, and an action binding might have a non-standard name. The dialog choice should really be presented to the user — either as two separate quick-fix entries ("Create key 'foo'" and "Create action 'foo'") or as a single entry that opens a chooser. The binding-name heuristic can still determine the default/ordering, but the user should always have both options available.

### Add Action dialog improvements

Two issues with the Add Action dialog:

- **Missing "return null" option** — the generated action method has no way to produce a `return null;` body, which is the common case for actions that stay on the same page. The dialog should offer this as a default or a checkbox.
- **Empty component popup in NG projects** — the return type combo's component list is likely populated by searching for subclasses of `WOComponent`, which finds nothing in an ng-objects project. Needs to search for `NGComponent` subclasses instead (same pattern as `ParsleyProject.getComponentClass()`).

## Refactoring across files

### ~~Rename component~~ ✓

Implemented. Renaming a WOComponent/NGComponent Java class now also renames the `.wo` folder, contained template files (`.html`, `.wod`, `.woo`), and `.api` file. Works bidirectionally: renaming from the Java side (Refactor > Rename) triggers the template renames via an LTK `RenameParticipant`, and a "Rename Component..." context menu action on `.wo` folders triggers the Java rename.

### ~~Rename element type: cross-reference updating~~ ✓

Implemented. Renaming any WOElement/NGElement subclass now automatically updates all `<wo:OldName>` tags and `Foo : OldName { }` WOD entries across all templates in the project. The participant activates for all element subclasses, not just components — custom dynamic elements are covered too.

Remaining:

- **Undo support** — the undo entry recorded by Eclipse's refactoring framework fails with "No input element provided" after the rename completes. This appears to be a conflict between JDT's compilation unit undo tracking and the resource-level `RenameResourceChange` objects added by our participant. Needs investigation into LTK's undo/redo machinery.

### ~~Rename binding key in associated template~~ ✓

Implemented. Renaming a method or field that serves as a binding key in a component's Java class (via Refactor > Rename) now automatically updates the corresponding key references in the component's own template files — both WOD binding values (`value = title;` → `value = heading;`) and inline HTML bindings (`value="$title"` → `value="$heading"`). Key paths are handled correctly (only the first segment is renamed). KVC getter/setter prefixes (`get`, `set`, `is`, `_get`, `_set`, `_is`, `_`) and field prefixes (`_`) are stripped when deriving the binding key.

### Deep keypath segment renaming

When a method like `Invoice.customer()` is renamed to `Invoice.buyer()`, template keypaths that traverse through that type should update too — e.g. `$selectedInvoice.customer.name` → `$selectedInvoice.buyer.name`. The current binding key rename only handles the first segment (the component's own keys); this would extend it to any segment in a keypath chain.

Requires type resolution through the keypath — resolving `selectedInvoice` to `Invoice`, then matching `.customer` against that type. The validation engine already does this for error checking, but that machinery is tightly coupled to the Eclipse workspace. Bridging JDT's type-aware rename with template scanning across all templates in the workspace is the main challenge. Scope is also much larger: unlike first-segment renames (local to one component), a type like `Invoice` could appear in keypaths across any template.

### ~~Find references in templates~~ ✓

Implemented. Eclipse's "Find References" (Ctrl+Shift+G) on a method or field in a component class now also finds binding key references in the component's own template files — both inline HTML bindings (`value="$name"`) and WOD binding values (`value = name;`). Results appear in the standard Search view alongside Java references. Implemented via the `org.eclipse.jdt.ui.queryParticipants` extension point.

### Element type references should resolve tag shortcuts

Find References on an element type (e.g. `WOConditional`) currently only finds templates that reference it by its class name — `<wo:WOConditional>` in HTML or `: WOConditional {` in WOD. Templates using a tag shortcut like `<wo:if>` are not found, even though `if` resolves to `WOConditional`. Once the tag shortcut mechanism is cleaned up (it's currently too fragile), Find References should resolve shortcuts to their target types and include those matches.

### Deep keypath segment references

The same challenge as deep keypath renaming, but for search: finding that `Customer.name()` is referenced via `$selectedObject.name` in another component's template requires type-resolving every keypath in every template in the project. This would need either a keypath index (expensive to build and maintain) or a brute-force scan with full type resolution per template. Deferred until the keypath indexing infrastructure exists.

## ~~Component documentation on hover~~ ✓

Implemented. Hovering over a `<wo:ComponentName>` tag in the template editor now shows the component's API documentation — accepted bindings, required/settable markers, and defaults. Works for both project components (via `.api` files) and built-in WO components (via `WebObjectDefinitions.xml`). Validation errors take priority when both are available.

## Unified Element Editor

The current editor architecture has a split personality: `ComponentEditorPart` (multi-page editor for components with templates) and `ApiEditor` (standalone form editor for `.api` files) are two completely separate editors. `NGEditorAssociationOverride` decides which one to use based on file extension, and features like the Usages tab have to be wired into both places separately. The result is redundancy (e.g. Usages tabs at two levels when the API editor is embedded inside the component editor) and gaps (dynamic elements that have no template get a degraded experience).

The fix is a single **Element Editor** that is always the entry point, regardless of which file you click (`.html`, `.wod`, `.api`, `.java`). It resolves the element identity first, then presents tabs based on what files and capabilities exist:

- **Component with bundle template** → HTML, WOD, WOO, API, Usages
- **Component with standalone template** → HTML, API, Usages
- **Dynamic element (no template)** → API, Usages
- **Element with no `.api` file** → just the relevant template tabs + Usages

This eliminates:
- The `ComponentEditorPart` vs `ApiEditor` fork
- The `ComponentEditorInput.create()` vs `createStandaloneHtml()` fork
- Conditional Usages tab logic (always present, once)
- The switch handlers' project-type checks (the editor knows what it has)
- Double-tab UI when features exist at both editor levels

### ~~Model layer: `ElementDescriptor`~~ ✓

Implemented. Immutable model object that captures "what files belong to this element?" — name, HTML, WOD, WOO, API, Java, project, and template format (`BUNDLE`, `STANDALONE`, `NONE`). Wraps `LocalizedComponentsLocateResult` without replacing it. Factory methods: `ElementDescriptor.forFile(IFile)` for on-demand resolution, `fromLocateResult()` for wrapping an existing locate result.

Migrated consumers: all four switch handlers (Cmd+1/2/3/4), F3 hyperlink (`WodElementTypeHyperlink`), Open Component action, Rename Component action. Bridge methods on `WodParserCache` and `ComponentEditorInput` make the descriptor available to editor-context code.

### Next: integrate `ElementDescriptor` into the editor itself

The descriptor is wired in but the editor doesn't depend on it yet. Concrete next steps, roughly in order:

- **`SwitchToJavaHandler` fast-path cleanup** — the ComponentEditor fast-path still reaches into `ComponentEditorInput.getLocalizedComponentsLocateResult()` to get the Java file. Could use `getElementDescriptor().getJavaFile()` instead, removing the last place the editor needs the raw locate result for a simple file query.
- **`ComponentEditorPart` tab decisions** — the editor inspects `getComponentEditors()`, `getApiEditor()`, `getStandaloneHtmlEditor()` to decide which tabs to create. The descriptor's `isBundle()` / `isStandalone()` / `hasTemplate()` could simplify that logic, making the tab setup more declarative.
- **Remaining consumers** — `ApiUtils`, `WodEditor`, `WodModelUtils`, `RenameComponentProcessor`, `RenameBindingKeyProcessor` still call the locate system directly. These are deeper in the validation and refactoring layers, so migration needs more care (thread safety with `WodModelUtils`, the rename processors' name-based lookups).
- **Name-based factory method** — `RenameComponentAction` and the rename processors resolve by `(IProject, String)` rather than by file. A `forComponent(IProject, String)` factory on `ElementDescriptor` would let them use the descriptor cleanly instead of wrapping a locate result.
- **Toward the Unified Element Editor** — once all consumers go through `ElementDescriptor`, the `ComponentEditorInput.create()` / `createStandaloneHtml()` fork could potentially be collapsed into a single path that builds tabs based on what the descriptor reports. This is the big payoff but depends on the tab decision cleanup above.

## Component dependency graph

A navigable view of "this component uses these sub-components" — useful for understanding unfamiliar codebases and spotting circular dependencies.

Prior work exists in a separate project. Natural home would be an Eclipse view contributed by the plugin, possibly with a graphical representation using Zest or a simple tree/table.

## ~~New component wizard: template type selection~~ ✓

Implemented. The wizard now offers a "Component Format" radio group to choose between standalone template (ng-objects style) and bundle template (WebObjects style). The default is detected from the project type, and the choice is persisted between invocations.

## ~~Convert bundle template to standalone template~~ ✓

Implemented. Context menu actions on .wo folders ("Convert to Standalone Template") and regular folders ("Convert All to Standalone Templates") convert WOD-reference syntax to inline bindings, move the HTML file out, and delete the .wo folder. Supports multi-selection and recursive conversion. Missing WOD entries produce a warning dialog for partial conversion.

## ~~Convert standalone template to bundle template~~ ✓

Implemented. Context menu action "Convert to Bundle Template" on standalone `.html` files creates a `.wo` folder, moves the HTML file inside, and creates an empty `.wod` file. Supports multi-selection; files already inside `.wo` folders are excluded.

## Convert between WOD and inline bindings

Convert individual tags or an entire template between WOD-reference syntax (`<webobject name="X">` + `.wod` entry) and inline binding syntax (`<wo:Type binding="value">`). This is purely about the binding style within the files — not about the template format (which is handled by the bundle/standalone conversion actions above).

- **Inline → WOD (single tag)**: already exists as `Cmd+2, W` (`ConvertInlineToWodAction`), which extracts one inline tag's bindings into the `.wod` file and replaces it with a named reference.
- **WOD → inline (single tag)**: implemented as `Cmd+2, I` (`ConvertWodToInlineAction`). Place cursor on a `<webobject name="X">` tag to convert it to inline syntax and remove the WOD entry.
- **Inline → WOD (entire file)**: batch-convert all inline tags in a template to WOD-reference syntax in one step. Essentially `Cmd+2, W` applied to every inline tag.
- **WOD → inline (entire file)**: batch-convert all WOD-reference tags in a template to inline syntax. The core engine for this already exists (`ConvertBundleToInlineTransformer`) but it's only exposed as part of the bundle/standalone format conversion — it needs to be wired up as a standalone editor action.
- **Cross-editor undo**: the single-tag actions modify both the HTML and WOD documents, but Eclipse maintains separate undo stacks per editor. Pressing Ctrl+Z in the template editor only reverts the HTML change, leaving the WOD file modified. Ideally, undo would be atomic across both files. Requires investigation into Eclipse's `IOperationHistory` and shared `IUndoContext` APIs.
- **Tag shortcut support for WOD → inline conversion**: when converting a WOD declaration to an inline tag, the refactoring currently uses the full element type name (e.g. `<wo:WOConditional>`). It should be possible to specify a preferred short tag name for well-known elements — e.g. `<wo:if>` instead of `<wo:WOConditional>`, `<wo:str>` instead of `<wo:WOString>`, `<wo:loop>` instead of `<wo:WORepetition>`. This requires a mapping from element types to their short tag names, which could live in the component API model, in a plugin-level registry, or as a user preference. The same mapping could also feed autocomplete suggestions (offering short names alongside full names).

## Rich component API model

A major evolution of the `.api` file format to describe the full component contract. This applies to templating as a whole — not just the parser or the editor, but the runtime framework, the template language, and the tooling around it. Changes touch the API file format itself (in ng-objects or as an extended schema), the `ng-template-parser` (for validation), Parsley (for editor support and UI), and potentially ng-objects itself (for runtime enforcement). Items roughly from most to least impactful:

- **Binding type checking** — go beyond "this binding doesn't exist" to "this binding expects a `String`, you're passing an `NSArray`." Requires bridging WOD binding values to Java type information via JDT. The validation infrastructure is already in place; this adds a type-resolution step after binding lookup.
- **Binding directionality** — specify whether a binding pulls values (get), pushes values (set), or both. Enables validation ("this binding is push-only, you can't read from it") and better documentation.
- **Deprecated bindings** — mark a binding as deprecated with docs explaining what to use instead. Tooling can show strikethrough, hover warnings, and quick-fix suggestions.
- **Default values** — specify what a binding defaults to when not bound. Useful for documentation on hover and for understanding component behavior without reading source.
- **Binding value constraints (enums)** — allow a binding to declare a set of valid values, probably backed by an Enum registered by the element. Enables autocomplete for binding values, not just binding names.
- **Valid/invalid binding combinations** — express rules like "if you bind `action`, you must not bind `href`" or "`item` requires `list`". Enables cross-binding validation beyond single-binding type checks.
- **Unknown binding policy** — specify whether a component allows unknown bindings at all. Some components are strict (unknown = error), others are permissive.
- **Additional attributes and their behavior** — for elements like `<wo:img>`, additional attributes get pushed to the generated HTML tag (e.g. `style`, `class`). The API should be able to declare this pass-through behavior, so tooling knows these aren't "unknown bindings" but intentional HTML attributes.
- **Embeddability** — specify whether a component is a page-level component or embeddable. Page-level components shouldn't appear in the component selector when editing a template, since they can't be nested.
- **Content model** — can this component have body content (like `<wo:if>` wrapping child elements) or is it self-closing (like `<wo:img />`)? Enables validation like "`<wo:string>` should not have body content" or "`<wo:form>` requires body content."
- **Required vs. optional bindings** — explicitly declare which bindings are required. Enables straightforward validation: "missing required binding `list` on `WORepetition`." May already be partially present in the current `.api` format.
- **Per-binding documentation** — human-readable description for each individual binding, not just the component as a whole. "What does `escapeHTML` actually do?" on hover, without reading the source.
- **Component description / summary** — a short doc string for the component itself, shown in the component selector and on hover. e.g. "A conditional element that renders its content when `condition` evaluates to true."
- **Semantic value types** — beyond Java types, a binding might accept a `String` that is semantically a CSS class name, a URL, a date format pattern, a key path, etc. A semantic type layer on top of Java types powers smarter validation and context-aware autocomplete.
- **Component categories / tags** — grouping components for the selector: "Layout", "Forms", "Navigation", "Data Display". Makes the component catalog browsable instead of a flat alphabetical list.

This feeds into almost every other roadmap item: hover documentation, validation quick-fixes, and the component catalog.

## ~~Extract component from selection~~ ✓

Implemented. Select HTML in the template, press `Cmd+2, E`, enter a name — the selected HTML is extracted into a new component (`.wo` folder + all files), the selection is replaced with a `<wo:NewComponentName/>` tag, and the new component opens for editing. Uses the same package and superclass as the parent component. The extracted HTML is automatically dedented to start at column 0.

## ~~Formatter: indentation settings~~ ✓

Implemented. The `XMLPreferencePage` ("Template Formatting") now exposes:

- **Indent with tabs** — checkbox (defaults to spaces). When checked, the indent size spinner is disabled since it's irrelevant.
- **Indent size (spaces)** — spinner, 1–8, defaults to 2.

Both settings were already supported by the formatter backend (`FormatRefactoring` → `RenderContext`) via `PreferenceConstants.INDENT_TABS` and `INDENT_SIZE` — they just weren't wired to the UI. Also fixed the existing `spacesAroundEquals` checkbox to save via `performOk()` instead of on every click.

## Tag namespace support

The ng-template-parser supports tag namespaces beyond `wo:`. Two namespaces are worth considering:

- **`html:` namespace** — dynamic HTML tags where attributes are processed as binding values/key paths, e.g. `<html:tag attribute="$someValue">`. Convenient shorthand but achievable with existing elements, so the cost/benefit for editor support is questionable.
- **`p:` namespace (parser control)** — `<p:raw>` and `<p:comment>` are now implemented in the editor (parser, validation, syntax highlighting, linked rename). Future `p:` directives can be added following the same pattern. A possible enhancement: shade the entire content block (not just the tags) with a background tint, to visually signal that the content is treated differently from the rest of the template. This could be done via Eclipse annotations or a custom document partitioner.

General arbitrary namespace support is probably not worth the complexity — each namespace needs the full tooling chain (parser, validation, autocomplete, highlighting) to understand it. Better to support specific, well-defined namespaces as needed.

## IDE-independent core for LSP

The long-term goal for Parsley's editor intelligence is to make it available outside Eclipse — in VS Code, IntelliJ, Emacs, or any editor that speaks LSP (Language Server Protocol). This means extracting the core logic (parsing, validation, completion, refactoring) into a standalone, IDE-independent library with no Eclipse dependencies, then wrapping it in an LSP server.

The current codebase is tightly coupled to Eclipse APIs (JDT for type resolution, IResource for file access, LTK for refactoring). Decoupling will require introducing abstraction layers for type lookup, file system access, and project model queries. The recent move to immutable POJOs (ApiSnapshot, VisibleBinding) is a step in this direction — the fewer mutable platform objects the core holds, the easier the extraction.

This is the direction the project is actually heading. Improving the Eclipse editor is valuable in its own right (and a good way to iterate on the feature set), but the real payoff is making template tooling available everywhere.

## Live preview

A Chromium-based preview tab in the component editor that renders the template as you edit. Two tiers, building on each other:

### Tier 1: Static structural preview (no running app)

Transform the template into renderable HTML by replacing `<wo:*>` tags with preview-friendly equivalents. The SWT `Browser` widget (Chromium-backed on all platforms) handles the rendering. Key capabilities:

- **Standard HTML passes through unchanged** — `<div>`, `<h1>`, CSS classes, static text all render natively.
- **`WOString`** → placeholder showing the binding key (e.g. `[name]`), styled to stand out from static text. Static string values render as literal text.
- **`WOConditional`** → renders children with a subtle labeled border (`[if condition]`).
- **`WORepetition`** → renders children once with a labeled border (`[loop items]`).
- **`WOImage`** → resolves literal `filename` bindings against the project's webserver-resources and renders real images.
- **`WOHyperlink`** → renders as a styled link with its children.
- **Form elements** (`WOTextField`, `WOCheckBox`, `WOPopUpButton`, etc.) → render as their HTML equivalents with placeholder values.
- **Unknown components** → render children inside a labeled box showing the component type name.
- **Component nesting** → recursively render sub-components using their actual templates.
- **Project CSS** → serve the project's webserver-resources so the preview reflects real styling.
- **`.api` preview attribute** → components can declare custom preview HTML with binding substitution, giving component authors control over how their component appears in the preview.

The old WOLips codebase (`org.objectstyle.wolips.htmlpreview`) had a working implementation of this tier with ~20 tag delegates. The FuzzyXML `RenderDelegate` pattern is a clean extension mechanism.

### Tier 2: Live connected preview (running app)

When the application is running locally, the preview tab points the Chromium browser directly at `localhost:port/ComponentName`. Combined with DCEVM hot-reload:

- **Edit a binding in the template** → file saves → DCEVM hot-swaps → preview refreshes automatically.
- **Bindings Inspector as a live property editor** — change a binding value in the inspector, see the result in the preview immediately.
- **No context switching** — the entire edit-preview cycle happens inside the IDE.

This tier requires detecting whether the app is running (checking for a known port or launch configuration) and auto-refreshing the browser on file save. The static preview serves as the fallback when the app isn't running.

## One-click deployment

Right-click an application → "Deploy" → it's on the internet. The ng-objects runtime produces a standard fat JAR (embedded Jetty), making it a natural fit for container-based PaaS providers (Fly.io, Railway, Render, etc.).

Envisioned workflow:

1. **First time:** user provides an API token and picks a provider. Stored in project preferences or `build.properties`.
2. **Every deploy:** plugin builds the application, wraps the artifact in a Docker image (or pushes directly if the provider supports bare JARs), and deploys via the provider's CLI/API.

The plugin would essentially be a GUI wrapper around a CLI deploy command. Main challenges:

- **First-run setup** — authenticating with the provider, creating the app, choosing a region.
- **Database provisioning** — if the app needs one, the provider's managed database needs to be wired up.
- **Environment variables / secrets** — configuration that differs between dev and prod.
- **Provider abstraction** — start with one provider, but design the abstraction so others can be added. A Dockerfile-based approach is the most portable.

This is a long-distance item — not specific to Parsley's template editor, but a natural extension of the "make ng-objects development frictionless" mission. May eventually live in its own project or in the ng-objects tooling layer.

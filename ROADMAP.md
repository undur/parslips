# Parsley — Feature Roadmap

Ideas and planned features for the Parsley template editor. Items are roughly ordered by perceived impact, not by implementation order.

## Type resolution performance

Opening a component editor or triggering tag autocomplete for the first time in a large project takes 4-5 seconds. The bottleneck is JDT type resolution — every `<wo:SomeElement>` tag triggers `BindingReflectionUtils.findElementType()`, which on cache miss builds a full `WOHierarchyScope` (type hierarchy + resource vector) just to resolve a single element name.

Analysis identified three concrete improvements:

1. **Don't check deprecation status eagerly** — `HTMLAssistProcessor` calls `getElementType()` on *every* completion proposal to check `memberIsDeprecated()`. This triggers `loadAttributeInfo()` → `findElementType()` for each tag in the list. Deferring or caching this would eliminate redundant type resolution on every autocomplete invocation.

2. **Skip `WOHierarchyScope` for exact-match lookups** — When `findMatchingElementClassNames()` is called with `R_EXACT_MATCH`, a simple `javaProject.findType()` or workspace-scoped search suffices. Building the expensive hierarchy scope is only needed for prefix/pattern matching (the "find all element types" query). This is the single biggest win for validation speed.

3. **Cache `InlineWodTagInfo` instances per project** — Currently, fresh instances are created on every autocomplete invocation. Each one re-runs `loadAttributeInfo()` on first access. Reusing cached instances (invalidated when Java files change, same as `_elementTypeCache`) would make repeat autocomplete instant.

The caching infrastructure is already in place (`TypeCache.ApiCache` for name→FQN, `WodCompletionUtils._elementTypeCache` for the full type list, `SubTypeHierarchyCache` for hierarchies). The issue is that the fast paths aren't used in several hot code paths.

## ~~Inline validation quick-fixes~~ ✓

Implemented. Keypath validation errors ("There is no key 'nme'") now include "Did you mean 'name'?" suggestions computed via Damerau–Levenshtein string distance. Quick-fixes are available via Cmd+1 (anywhere on the line) and the Problems view. Hover over the error to see the suggestion inline.

Binding *name* validation quick-fixes (is "valu" a valid binding on WOString?) are deferred until the element API model is improved — most WO components accept arbitrary bindings beyond their `.api` definitions, making name validation too noisy.

## Refactoring across files

### ~~Rename component~~ ✓

Implemented. Renaming a WOComponent/NGComponent Java class now also renames the `.wo` folder, contained template files (`.html`, `.wod`, `.woo`), and `.api` file. Works bidirectionally: renaming from the Java side (Refactor > Rename) triggers the template renames via an LTK `RenameParticipant`, and a "Rename Component..." context menu action on `.wo` folders triggers the Java rename.

### ~~Rename element type: cross-reference updating~~ ✓

Implemented. Renaming any WOElement/NGElement subclass now automatically updates all `<wo:OldName>` tags and `Foo : OldName { }` WOD entries across all templates in the project. The participant activates for all element subclasses, not just components — custom dynamic elements are covered too.

Remaining:

- **Undo support** — the undo entry recorded by Eclipse's refactoring framework fails with "No input element provided" after the rename completes. This appears to be a conflict between JDT's compilation unit undo tracking and the resource-level `RenameResourceChange` objects added by our participant. Needs investigation into LTK's undo/redo machinery.

### Rename binding across files

Rename a binding in Java and have it update in the WOD and HTML template automatically.

WO component bundles are inherently multi-file (HTML + WOD + WOO + Java + .api), so cross-file refactoring is where tooling can save the most manual effort.

## ~~Component documentation on hover~~ ✓

Implemented. Hovering over a `<wo:ComponentName>` tag in the template editor now shows the component's API documentation — accepted bindings, required/settable markers, and defaults. Works for both project components (via `.api` files) and built-in WO components (via `WebObjectDefinitions.xml`). Validation errors take priority when both are available.

## Binding type checking

Go beyond "this binding doesn't exist" to "this binding expects a `String`, you're passing an `NSArray`."

Requires bridging the WOD binding values to Java type information via JDT. The validation infrastructure is already in place; this adds a type-resolution step after binding lookup.

## Component dependency graph

A navigable view of "this component uses these sub-components" — useful for understanding unfamiliar codebases and spotting circular dependencies.

Prior work exists in a separate project. Natural home would be an Eclipse view contributed by the plugin, possibly with a graphical representation using Zest or a simple tree/table.

## ~~New component wizard: template type selection~~ ✓

Implemented. The wizard now offers a "Component Format" radio group to choose between standalone HTML (ng-objects style) and .wo folder bundle (WebObjects style). The default is detected from the project type, and the choice is persisted between invocations.

## Convert between component formats

Convert a `.wo` folder bundle to a single-file standalone component, and vice versa.

- **`.wo` → single file**: merge the WOD into inline bindings, move the HTML out of the bundle, clean up the `.wo` folder.
- **Single file → `.wo`**: create the bundle folder, optionally extract inline bindings into a separate `.wod` file, generate a `.woo` file.

Could be offered as a context menu action on components in the explorer.

## Convert between WOD and inline bindings

Convert a component's WOD bindings to inline bindings within the HTML template, and vice versa. Useful when switching coding styles or converting between component formats.

- **WOD → inline**: for each WOD definition, find the corresponding tag in the template and replace it with an inline binding tag, then remove the WOD entry.
- **Inline → WOD**: extract inline bindings from the template into named WOD definitions, replacing the inline tags with named references.

Requires the parser to map WOD entries to their corresponding template tags. Could be offered as an editor action, a context menu item, or both.

## Rich component API model

A major evolution of the `.api` file format to describe the full component contract. This applies to templating as a whole — not just the parser or the editor, but the runtime framework, the template language, and the tooling around it. Changes touch the API file format itself (in ng-objects or as an extended schema), the `ng-template-parser` (for validation), Parsley (for editor support and UI), and potentially ng-objects itself (for runtime enforcement). Items roughly from most to least impactful:

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

This feeds into almost every other roadmap item: hover documentation, validation quick-fixes, binding type checking, and the component catalog.

## ~~Extract component from selection~~ ✓

Implemented. Select HTML in the template, press `Cmd+2, E`, enter a name — the selected HTML is extracted into a new component (`.wo` folder + all files), the selection is replaced with a `<wo:NewComponentName/>` tag, and the new component opens for editing. Uses the same package and superclass as the parent component. The extracted HTML is automatically dedented to start at column 0.

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

Side-by-side rendering of the template as you edit. Challenging for WO components since they render server-side with dynamic bindings, but even a static structural preview (showing component nesting and placeholder content) could be valuable.

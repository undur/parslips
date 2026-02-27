# Parsley — Feature Roadmap

Ideas and planned features for the Parsley template editor. Items are roughly ordered by perceived impact, not by implementation order.

## Inline validation quick-fixes

"Did you mean `userName`?" with a click-to-correct action.

String-distance-based suggestions are already implemented in the validation layer, but the quick-fix step requires token location tracking in `ng-template-parser`. This needs work on both the parser side (preserving source locations for binding names and values) and the plugin side (mapping parser locations to Eclipse `IMarker` positions and providing `IMarkerResolution` quick-fixes).

## Refactoring across files

Rename a binding in Java and have it update in the WOD and HTML template automatically. Rename a component and update all references across the project.

WO component bundles are inherently multi-file (HTML + WOD + WOO + Java + .api), so cross-file refactoring is where tooling can save the most manual effort. Eclipse's LTK refactoring framework (`org.eclipse.ltk.core.refactoring`) supports this — the plugin already depends on it.

## Component catalog / documentation on hover

Hover over a component tag in the template or WOD and see its API: accepted bindings, which are required, expected types, and a short description.

The data source already exists — `.api` files describe the component interface. This is about surfacing that information inline via `ITextHover` in the template editor and WOD editor.

## Binding type checking

Go beyond "this binding doesn't exist" to "this binding expects a `String`, you're passing an `NSArray`."

Requires bridging the WOD binding values to Java type information via JDT. The validation infrastructure is already in place; this adds a type-resolution step after binding lookup.

## Component dependency graph

A navigable view of "this component uses these sub-components" — useful for understanding unfamiliar codebases and spotting circular dependencies.

Prior work exists in a separate project. Natural home would be an Eclipse view contributed by the plugin, possibly with a graphical representation using Zest or a simple tree/table.

## Extract component from selection

Select a chunk of template HTML, extract it into a new component — generating the `.wo` bundle, WOD entries, and a stub Java class. The template equivalent of "Extract Method."

This ties into the refactoring infrastructure and the "New WO Component" wizard that already exists.

## Live preview

Side-by-side rendering of the template as you edit. Challenging for WO components since they render server-side with dynamic bindings, but even a static structural preview (showing component nesting and placeholder content) could be valuable.

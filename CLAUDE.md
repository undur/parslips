# CLAUDE.md — Project Guide for ng.componenteditor (Parslips)

## What is this project?

`ng.componenteditor` (branded **Parslips**) is a standalone Eclipse plugin for editing WebObjects / ng-objects component templates. It was extracted from the WOLips plugin suite (~18 plugins merged into one bundle) and can coexist with a WOLips installation.

The plugin provides:
- A multi-tab component editor (HTML template + WOD + WOO)
- Tag/attribute autocomplete, binding validation, syntax highlighting
- An "NG Explorer" (Package Explorer variant with component-aware behavior)
- A "New WO Component" wizard
- Support for both ng-objects (`project.base=ng`) and WebObjects (`project.base=wo`) projects

## Repository structure

```
wolips/
  plugins/
    ng.componenteditor/          # The only active plugin
      java/                      # Java source (org.objectstyle.wolips.* packages)
      META-INF/MANIFEST.MF       # OSGi bundle manifest
      plugin.xml                 # Eclipse extension registrations
      icons/                     # Toolbar and decorator icons
      lib/                       # Vendored JARs (Velocity, Xerces, CSS parser, etc.)
  features/
    ng.componenteditor.feature/  # Eclipse feature wrapper for p2
  wolips.p2/
    src/main/resources/          # Public-facing HTML site for the p2 update site
      changelog.html             # HTML changelog (user-facing, keep in sync with CHANGES.md)
      roadmap.html               # HTML roadmap (user-facing, keep in sync with ROADMAP.md)
      features.html              # Feature overview page
      index.html                 # Landing page
      non-features.html          # Anti-features / design decisions
      guide.html                 # Getting started guide (installation, setup, WOLips coexistence)
      css/                       # Site styles
      img/                       # Site images
  pom.xml                        # Parent POM (Maven/Tycho build)
  install.sh                     # Build-and-install script for local dev
  CHANGES.md                     # Detailed changelog (project history reference)
  ROADMAP.md                     # Feature ideas and planned work
```

## Building

```bash
# Full build (from repo root)
mvn verify

# Plugin only (faster for iteration)
mvn verify -pl wolips/plugins/ng.componenteditor -am -Dtycho.localArtifacts=ignore

# Build and install into Eclipse
./install.sh /path/to/Eclipse.app
```

The build uses **Tycho** (Maven plugin for Eclipse/OSGi builds). Target platform is Eclipse 2024-12+, Java 21+.

## Terminology

- **Standalone template** — a single `.html` file with inline bindings (`<wo:Type binding="value">`). The ng-objects default.
- **Bundle template** — a `.wo` folder containing `.html` + `.wod` + optionally `.woo`. Bindings are expressed via `<webobject name="X">` references into the `.wod` file. The traditional WebObjects format.
- **Inline bindings** — binding syntax where attributes are written directly on the tag: `<wo:WOString value="$name" />`. This describes the *syntax*, not the template format — don't say "inline template" when you mean "standalone template."
- **WOD bindings** — binding syntax where tags reference named entries in a separate `.wod` file: `<webobject name="MyString">` + `MyString : WOString { value = name; }`.

Always use "bundle template" as the full phrase, never just "bundle" — "bundle" is an overloaded term in WebObjects (framework bundles, application bundles, etc.).

## Key architectural concepts

### Eclipse wizard lifecycle (WOComponentCreationPage)

The "New WO Component" wizard has a tricky lifecycle — see the class javadoc for details. Key points:
- `processSelection()` sanitizes the Package Explorer selection before passing to super
- `initialPopulateContainerNameField()` sets the smart default folder — must NOT call super after setting our path, because super would overwrite it
- `getContainerFullPath()` can return null — all callers must null-check
- The Package Explorer returns many different object types (IJavaProject, IPackageFragment, IPackageFragmentRoot, classpath containers, etc.) — all must be handled

### Eclipse editor association (NGEditorAssociationOverride)

Ensures the component editor is used for `.html`, `.wod`, `.woo`, `.api` files. Activates for:
- Any file inside a `.wo` folder (regardless of project type)
- Component file extensions in ng-objects projects (`project.base=ng` or NGElement on classpath)

### Element type detection (BuildProperties)

Per-project framework detection:
1. `project.base=ng` in build.properties → ng-objects types (NGElement, NGComponent)
2. `project.base=wo` in build.properties → WebObjects types (WOElement, WOComponent)
3. Neither → probes classpath (tries NGElement first, falls back to WOElement)

### WOD validation thread safety

The global API model (`WebObjectDefinitions.xml`) is a shared DOM singleton. All validation access must be synchronized on `this.apiModel` — Java's DOM (Xerces) is not thread-safe even for reads.

### Cache invalidation (WodParserCacheInvalidator)

`IResourceChangeListener` that invalidates caches when files change:
- Java file changes → clear element type cache (`WodCompletionUtils._elementTypeCache`)
- Template/WOD changes → clear parser cache

### Standalone HTML templates

Single-file `.html` templates (not inside `.wo` bundles) have full editor support. Key difference: `WodParserCache` uses `_standaloneFile` field to track the original file, and uses the file path (not parent directory) as the cache key.

## Coding conventions

### Comments and documentation

**Comment code thoroughly.** This is a refactored legacy codebase with many non-obvious Eclipse platform quirks. When writing or modifying code:
- Add class-level javadoc explaining the purpose and any lifecycle/ordering subtleties
- Comment fields that aren't self-explanatory, especially if they exist for non-obvious reasons
- Add inline comments for Eclipse platform interactions (widget lifecycle, adapter patterns, etc.)
- Explain *why*, not just *what* — the Eclipse APIs often have surprising behavior
- When a null check exists for defensive reasons, comment what scenario it guards against

### Code style

- **Tabs, not spaces** — use tabs for indentation everywhere: Java source, generated templates (HTML, XML, Java, WOD, WOO), and any other file we produce
- Field names use underscore prefix: `_fieldName`
- Constants use `UPPER_SNAKE_CASE`
- Eclipse platform patterns: adapters, extension points, structured selections
- Favor early returns for guard clauses
- Keep methods focused — extract helpers when logic gets complex

### Dead code

This codebase was extracted from WOLips which had significant dead code. When you encounter:
- Unused imports, fields, methods — remove them
- Commented-out code with no explanatory comment — remove it
- No-op stubs that exist only because deleted code called them — remove them
- Always verify with a build after removing code

### Commits

- Write clear, descriptive commit messages
- First line: imperative mood, concise summary
- Body (if needed): explain the *why* and any non-obvious details
- Do NOT include `Co-Authored-By` lines
- **Never push to remote without asking first.** Commit locally, then let the user review and test before pushing. This keeps the remote history clean.

## Releases: a public changelog card IS a release

The whoacommunity site (and anyone else) reads this project's history from GitHub releases,
so every card added to `wolips.p2/src/main/resources/changelog.html` is published as one. The
release history was backfilled from the changelog in September 2026 (v5.0.0 … v5.6.0), and from
there on the rule is:

1. **Bump the version first**, so GitHub, Eclipse's About dialog and the p2 site agree:
   `mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=X.Y.Z-SNAPSHOT -Dtycho.mode=maven`
   — a **minor** bump for a feature card, a **patch** bump for a fix-only card.
2. Add the changelog card, commit (card + bump together is fine), push.
3. `tools/changelog_release.py release X.Y.Z` — tags HEAD as `vX.Y.Z`, pushes the tag and creates
   the GitHub release: named after the card's headline, with the card rendered as Markdown notes.

Never publish a release without its changelog card, and never add a card without releasing —
the two are the same act. Ask before pushing/releasing, as with any push.

## CHANGES.md

`CHANGES.md` at the repository root is the detailed project changelog. It documents every significant change since the initial WOLips extraction. Consult it for:
- Understanding why code was removed or changed
- Historical context for architectural decisions
- What's been cleaned up vs. what remains

When making significant changes, add an entry to CHANGES.md.

## Common tasks

### Adding support for a new selection type in the wizard

1. Handle it in `processSelection()` (redirect to project if it's not a valid container)
2. Handle it in `initialPopulateContainerNameField()` (extract project for components folder lookup)
3. Null-guard any new `getContainerFullPath()` usage

### Removing dead code

1. Search for references (callers, implementations, plugin.xml registrations)
2. Remove the code
3. Clean up imports
4. Build to verify: `mvn verify -pl wolips/plugins/ng.componenteditor -am -Dtycho.localArtifacts=ignore`
5. Document in CHANGES.md if it's a significant removal

### Fixing editor association issues

Check `NGEditorAssociationOverride.shouldUseComponentEditor()` — it has two paths:
1. `isInsideWoFolder()` — for any file inside a `.wo` folder
2. `isComponentFile() && isNGProject()` — for component files in ng-objects projects

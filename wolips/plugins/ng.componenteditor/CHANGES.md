# ng.componenteditor — Change Log

This document tracks changes made to the `ng.componenteditor` plugin since its initial creation from the WOLips source.

## Origin

`ng.componenteditor` is a standalone Eclipse plugin extracted from the WOLips plugin suite. It merges source from ~18 WOLips plugins into a single bundle, with unique identifiers (`ng.componenteditor.*`) so it can coexist with a WOLips installation.

The initial import was commit `d2c9da47` ("Initial ng import").

---

## Changes

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

**How it works:** When Eclipse is about to open an editor for an `.html`, `.wod`, `.woo`, or `.api` file, the override checks whether the file's project has `base=ng` in its `build.properties`. If so, it forces the NG Component Editor. Projects without `base=ng` are left alone, so WOLips continues to work normally for WebObjects projects.

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

ng-objects projects don't use WOLips project natures — they use `base=ng` in `build.properties` instead. All nature-checking code has been removed, along with unregistered JavaScript launcher infrastructure inherited from the Amateras HTML editor.

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
1. `base=ng` in `build.properties` → uses ng-objects types (`NGElement`, `NGComponent`)
2. `base=wo` in `build.properties` → uses WebObjects types (`WOElement`, `WOComponent`)
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
- `NGEditorAssociationOverride.isNGProject()` — now delegates to `BuildProperties.isNGProject()` (includes classpath probing, not just `base=ng` check)

### Added: NG Explorer view

Ported the WO Explorer project view from `org.objectstyle.wolips.jdt` into `ng.componenteditor`. The NG Explorer is a Package Explorer variant that collapses `.wo` component bundle folders into single tree nodes (with a custom icon) and sorts them alongside regular files.

**What's included:**
- `.wo` folder collapsing — component bundles appear as single leaf nodes (no children shown)
- Custom `.wo` component icon — `WOComponentBundle.png`
- Custom sorting — `.wo` bundles sorted alphabetically alongside files, after regular folders
- Working Set mode support — all the above works when Package Explorer is in "Working Sets" root mode

**What's been stripped (vs WOLips' WO Explorer):**
- Tagged Components feature — unused virtual folder grouping (~1,000 lines removed)
- `.eomodeld` bundle handling — ng-objects doesn't use EOF
- `RenameWOComponentAction` — required the separate WOLips refactoring plugin

**Coexistence:** Uses unique identifiers (`ng.componenteditor.explorer.*`) so it can coexist with WOLips' WO Explorer (`org.objectstyle.wolips.jdt.ui.WOPackageExplorer`) in the same Eclipse installation. The view appears under the "NG Component Editor" view category.

**Key files:**
- `ng.componenteditor.explorer.NGPackageExplorerPart` — main view class
- `ng.componenteditor.explorer.NGPackageExplorerContentProvider` — collapses `.wo` bundles
- `ng.componenteditor.explorer.NGPackageExplorerLabelProvider` — custom `.wo` icon
- `ng.componenteditor.explorer.NGJavaElementComparator` — sorting
- `ng.componenteditor.explorer.NGWorkingSetAwareContentProvider` — working set variant
- `ng.componenteditor.explorer.NGWorkingSetAwareJavaElementSorter` — working set sorting variant
- `plugin.xml` — view registration with standard JDT filters

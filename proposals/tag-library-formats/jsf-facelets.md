# JSF / Facelets *.taglib.xml + Composite Component <cc:interface>/<cc:attribute>

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Runtime **and** tooling (one source)

**Artifact / file shape:** Two cooperating artifacts. (1) A *.taglib.xml file (root <facelet-taglib>, governed by web-facelettaglibrary_4_0.xsd) is a hand-authored registration document, usually in META-INF/, mapping an XML namespace to a set of tags, EL functions, converters, validators, and behaviors — each tag binding a tag-name to a component-type / converter-id / validator-id / behavior-id / handler-class / source file. (2) Composite components are authored as standalone .xhtml VDL files under resources/<library>/, where the descriptor IS the component file: a <cc:interface> block declares the public contract (props via <cc:attribute>, slots via <cc:facet>, events via <cc:clientBehavior>, attach-points via <cc:editableValueHolder>/<cc:valueHolder>/<cc:actionSource>) and a sibling <cc:implementation> holds the realization. The taglib XML can be omitted for composite components, which are auto-discovered by file location under the default jakarta.faces.composite namespace.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `facelet-taglib` | Top-level root element of a *.taglib.xml file. Registers a whole tag library (a namespace's worth of tags, functions, behaviors) so Facelets can resolve XML namespaces to component/handler implementations. | Complex element (facelet-taglibType) carrying id + version attributes and a child structure. | ✔ | both | `<facelet-taglib version="4.0">...</facelet-taglib>` |
| `namespace` | The XML namespace URI a page author binds a prefix to (xmlns:my=...) to use this library's tags. Canonical identity of the library at consumption time. | string (URI), required when not using library-class path | cond. | both | `<namespace>http://example.com/components</namespace>` |
| `short-name` | Advisory short prefix suggestion for tags from this library. Advisory only — the page author picks the actual prefix. Teaching point: the library SUGGESTS a default prefix but does not own it. | string (0..1) | — | both | `<short-name>my</short-name>` |
| `composite-library-name` | Maps this namespace to a directory of composite components under resources/<name>/, letting file-based composite components live under a custom (non-default) namespace. | string (0..1) | — | both | `<composite-library-name>mycomps</composite-library-name>` |
| `library-class` | Alternative whole-library path: a Java class that programmatically defines the library. Mutually exclusive with the namespace+tag-list path. | fully-qualified-classType (string) | cond. | RT | `<library-class>com.example.MyTagLibrary</library-class>` |

### `attribute/prop` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `version` | Schema version of the taglib document; selects which facelet-taglib XSD applies. | facelet-taglib-versionType enum (e.g. 2.0/2.2/2.3/3.0/4.0) | ✔ | RT | `version="4.0"` |
| `id` | Optional XML ID for the root element, for cross-referencing within the document. | xsd:ID | — | RT | `id="myLib"` |
| `component-type` | Registered UIComponent type id the tag instantiates (looked up via the Application factory). | string, required within component | ✔ | RT | `<component-type>jakarta.faces.Input</component-type>` |
| `renderer-type` | Renderer type id used to render the component — separates component identity from rendering, allowing multiple renderers per component. | string (0..1) | — | RT | `<renderer-type>jakarta.faces.Text</renderer-type>` |
| `resource-id` | Within a component definition, points the tag at a composite-component resource file rather than a programmatic component type — the file-based bridge for composite components registered as named tags. | string (0..1) | — | RT | `<resource-id>mylib/widget.xhtml</resource-id>` |
| `converter-id` | Registered Converter id the tag instantiates. | string, required within converter | ✔ | RT | `<converter-id>jakarta.faces.Number</converter-id>` |
| `validator-id` | Registered Validator id the tag instantiates. | string, required within validator | ✔ | RT | `<validator-id>jakarta.faces.Required</validator-id>` |
| `behavior-id` | Registered Behavior id the tag instantiates. | string, required within behavior | ✔ | RT | `<behavior-id>jakarta.faces.behavior.Ajax</behavior-id>` |
| `function-name` | EL-callable name of the function (part after the prefix). | string, required | ✔ | RT | `<function-name>toUpper</function-name>` |
| `function-class` | FQ class hosting the static method implementing the function. | fully-qualified-classType, required | ✔ | RT | `<function-class>com.example.Functions</function-class>` |
| `function-signature` | Java method signature (return type + name + param types) resolving the function to a static method. Strong typing of an EL-exposed function at registration. | string, required | ✔ | both | `<function-signature>java.lang.String toUpper(java.lang.String)</function-signature>` |
| `attribute (taglib tag-level)` | Within a <tag>, declares/documents an attribute accepted by that tag — taglib-XML-level attribute metadata (parallel to composite:attribute but for handler/component tags). Drives tooling and optional runtime validation. | facelet-taglib-tag-attributeType (0..unbounded) | — | both | `<attribute><name>value</name><required>true</required><type>java.lang.String</type></attribute>` |
| `name (taglib attribute)` | The attribute's name as written on the tag in the page. | xsdNMTOKENType, required | ✔ | both | `<name>styleClass</name>` |
| `required (taglib attribute)` | Whether the page author must supply this attribute. | generic-booleanType (0..1), default false | — | both | `<required>true</required>` |
| `type (taglib attribute)` | Expected Java type of the attribute value (ValueExpression target type). Choice-exclusive with method-signature. | fully-qualified-classType | cond. | both | `<type>java.lang.Integer</type>` |
| `method-signature (taglib attribute)` | Declares the attribute is a MethodExpression with this signature. Choice-exclusive with type. | string | cond. | both | `<method-signature>void action()</method-signature>` |
| `name (cc:interface)` | Advisory name of the composite component. The REAL name is taken from the filename, so this is documentation/tooling-override only. Set onto the composite component BeanDescriptor. | ValueExpression -> java.lang.String (optional) | — | both | `name="loginPanel"` |
| `componentType (cc:interface)` | The component-type of the UIComponent serving as the composite component ROOT; its component-family must be jakarta.faces.NamingContainer. Lets you supply a custom Java root component. Pure runtime concern. | ValueExpression -> java.lang.String (optional) | — | RT | `componentType="com.example.LoginPanel"` |
| `displayName (cc:interface)` | Human-friendly name shown in a tool palette / component picker. IDE-facing; set onto the BeanDescriptor. Clean separation of machine name (filename) vs display name. | ValueExpression -> java.lang.String (optional) | — | IDE | `displayName="Login Panel"` |
| `shortDescription (cc:interface)` | Short description of the component's purpose, for tooltips/palette help. IDE-facing; set onto the BeanDescriptor. | ValueExpression -> java.lang.String (optional) | — | IDE | `shortDescription="A reusable login form"` |
| `preferred (cc:interface)` | Marks the component as 'preferred' — IDE hint to surface/promote it among many. Pure tooling hint. Set onto BeanDescriptor. | ValueExpression -> boolean (optional) | — | IDE | `preferred="true"` |
| `expert (cc:interface)` | Marks the component as for expert users only — IDE may hide it behind an 'advanced' filter. Inherited from java.beans.FeatureDescriptor semantics. | ValueExpression -> boolean (optional) | — | IDE | `expert="true"` |
| `hidden (cc:interface)` | Marks a feature as intended only for tool use and NOT exposed to humans in normal palettes. Distinct from expert (advanced-but-visible): hidden = tool-internal. From FeatureDescriptor. | ValueExpression -> boolean (optional) | — | IDE | `hidden="true"` |
| `composite:attribute (cc:attribute)` | Declares ONE attribute (prop) in the component's usage contract. Referenced from the implementation via #{cc.attrs.<name>}. The core prop-declaration element. Multiple allowed. | Container element with attributes below | — | both | `<cc:attribute name="label" type="java.lang.String" required="true"/>` |
| `name (cc:attribute)` | The attribute name as it must appear on the composite component tag in the using page. The prop's public name. | ValueExpression -> java.lang.String, required | ✔ | both | `name="value"` |
| `type (cc:attribute)` | Expected Java type the ValueExpression must evaluate to. Defaults to java.lang.Object if neither type nor method-signature given. Mutually exclusive with method-signature (if both present, method-signature is IGNORED — type wins). | ValueExpression -> java.lang.String (FQ class name) (optional) | — | both | `type="java.lang.Integer"` |
| `method-signature (cc:attribute)` | Declares the attribute must be a MethodExpression with the given signature. Enables strongly-typed callback/action props. Mutually exclusive with type. | ValueExpression -> java.lang.String (optional) | — | both | `method-signature="java.lang.String submit(jakarta.faces.event.ActionEvent)"` |
| `targets (cc:attribute)` | Space-separated client ids of inner implementation components to which a MethodExpression attribute should be RETARGETED (e.g. wiring an exposed action onto an inner button). Method-retargeting: the contract attribute is rewired onto inner components. | ValueExpression -> java.lang.String (optional) | — | RT | `targets="loginButton"` |
| `default (cc:attribute)` | Default value to use when the attribute is optional and the page author supplies none. Declarative defaulting at the contract level. | ValueExpression -> java.lang.String (optional) | — | both | `default="Submit"` |
| `required (cc:attribute)` | Whether the page author MUST supply a value for this attribute. Default false. | ValueExpression -> boolean (optional, default false) | — | both | `required="true"` |
| `targetAttributeName (cc:attribute)` | Lets the exposed attribute name differ from the attribute name on the targeted inner component during method retargeting. Decouples public prop name from internal attribute name. | ValueExpression -> java.lang.String (optional) | — | RT | `targetAttributeName="actionListener"` |
| `displayName (cc:attribute)` | Tool-palette display name for this attribute. IDE-facing metadata for the prop. | ValueExpression -> java.lang.String (optional) | — | IDE | `displayName="Button Label"` |
| `shortDescription (cc:attribute)` | Short description of the attribute's purpose, for IDE tooltips/help. | ValueExpression -> java.lang.String (optional) | — | IDE | `shortDescription="Text shown on the button"` |
| `preferred (cc:attribute)` | Marks this attribute as preferred — IDE hint to surface it prominently among the component's props. | ValueExpression -> boolean (optional) | — | IDE | `preferred="true"` |
| `expert (cc:attribute)` | Marks the attribute as for expert users only — IDE may tuck it under advanced settings. | ValueExpression -> boolean (optional) | — | IDE | `expert="true"` |
| `hidden (cc:attribute)` | Marks the attribute as tool-only, not to be exposed to humans in normal editing. | ValueExpression -> boolean (optional) | — | IDE | `hidden="true"` |

### `element/tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `tag` | Registers a single tag, binding a tag-name to an implementation (component/converter/validator/behavior/handler-class/source). | facelet-taglib-tagType (0..unbounded) | — | both | `<tag><tag-name>panel</tag-name><component>...</component></tag>` |
| `tag-name` | The local element name the page author writes (after the prefix) to invoke the tag. | facelet-taglib-canonical-nameType (NMTOKEN-like string), required | ✔ | both | `<tag-name>commandButton</tag-name>` |
| `handler-class` | Binds the tag directly to a Facelets TagHandler subclass — most general path, for custom non-component tags. | fully-qualified-classType (string) | cond. | RT | `<handler-class>com.example.MyHandler</handler-class>` |
| `source` | Implements the tag by pointing at another Facelet markup file (relative path) — composition with no Java. | string (relative path) | cond. | both | `<source>panel.xhtml</source>` |
| `component` | Implements the tag as a UIComponent. Container for component-type / renderer-type / handler-class / resource-id. | facelet-taglib-tag-componentType | cond. | RT | `<component><component-type>jakarta.faces.HtmlPanelGroup</component-type></component>` |
| `converter` | Implements the tag as a Converter (attached object). Container for converter-id + optional handler-class. | facelet-taglib-tag-converterType | cond. | RT | `<converter><converter-id>jakarta.faces.DateTime</converter-id></converter>` |
| `validator` | Implements the tag as a Validator. Container for validator-id + optional handler-class. | facelet-taglib-tag-validatorType | cond. | RT | `<validator><validator-id>jakarta.faces.Length</validator-id></validator>` |
| `behavior` | Implements the tag as a client/ajax Behavior (attached object). Container for behavior-id + optional handler-class. | facelet-taglib-tag-behaviorType | cond. | RT | `<behavior><behavior-id>jakarta.faces.behavior.Ajax</behavior-id></behavior>` |
| `function` | Registers an EL function usable in expressions (#{prefix:fn(...)}) — the library can export pure functions, not just tags. Teaching point: a tag library is also a function library. | facelet-taglib-functionType (0..unbounded) | — | RT | `<function>...</function>` |
| `composite:interface (cc:interface)` | Declares the public usage CONTRACT of a composite component (authored as an .xhtml file). Separates the public surface from <cc:implementation>. Zero-or-one per file; if present requires a matching <cc:implementation>. If omitted, the contract is INFERRED from usage — a notable implicit-contract design choice. | Container element; its own attributes are optional ValueExpressions | — | both | `<cc:interface displayName="Login Box"><cc:attribute name="user"/></cc:interface>` |

### `slot` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `composite:facet (cc:facet)` | Declares a named FACET (named insertion slot) the page author may fill with <f:facet name="x">. Contract-level declaration of a named slot — directly analogous to named slots in web components. | Container element with attributes below | — | both | `<cc:facet name="header" required="true"/>` |
| `name (cc:facet)` | The facet/slot name the page author references via <f:facet name=...>. | ValueExpression -> java.lang.String, required | ✔ | both | `name="header"` |
| `required (cc:facet)` | Whether the page author must supply this facet/slot. | ValueExpression -> boolean (optional) | — | both | `required="true"` |
| `preferred/expert/shortDescription/displayName/hidden (cc:facet)` | The same FeatureDescriptor IDE-metadata family as on cc:interface/cc:attribute, describing the slot: palette name, advanced/tool-only flags, description. Demonstrates that slots, like props, carry full tooling metadata. | ValueExpression -> boolean (preferred/expert/hidden) or String (shortDescription/displayName), all optional | — | IDE | `displayName="Header Slot" expert="true"` |
| `composite:renderFacet (cc:renderFacet)` | In the IMPLEMENTATION section: renders a named facet supplied by the using page at this position. The slot-outlet counterpart to cc:facet's slot declaration. | Container with name + required | — | RT | `<cc:renderFacet name="header" required="true"/>` |
| `name (cc:renderFacet)` | Name matching the <f:facet name=...> in the using page to render here. | ValueExpression -> java.lang.String, required | ✔ | RT | `name="header"` |
| `required (cc:renderFacet)` | When true, throws a TagException if the named facet is absent on the top-level component. Runtime enforcement of a required slot at the render site. | ValueExpression -> boolean (optional) | — | RT | `required="true"` |
| `composite:insertChildren (cc:insertChildren)` | In the IMPLEMENTATION section: marks the single point where the using page's child content is re-parented (the default/unnamed slot — analogous to <slot> with no name or {children}). Takes NO attributes. Should appear once; multiple uses cause duplicate-id/undefined behavior. | Empty element, no attributes | — | RT | `<cc:insertChildren/>` |

### `event` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `composite:clientBehavior (cc:clientBehavior)` | Declares the composite implements ClientBehaviorHolder — i.e. exposes a client-side EVENT (ajax/JS event) that page authors can attach behaviors to via <f:ajax event=...>. Contract-level declaration of an exposed event. | Container with attributes below | — | both | `<cc:clientBehavior name="click" event="click" targets="button" default="true"/>` |
| `name (cc:clientBehavior)` | The event name the page author references via the 'for' attribute on an attached object (e.g. <f:ajax for="click">). The public event name. | ValueExpression -> java.lang.String, required | ✔ | both | `name="valueChange"` |
| `event (cc:clientBehavior)` | The actual client event passed as the first argument to addClientBehavior() on the inner ClientBehaviorHolder. Decouples the public event name from the underlying DOM/component event. | ValueExpression -> java.lang.String (optional) | — | RT | `event="change"` |
| `targets (cc:clientBehavior)` | Space-separated client ids (relative to the top-level component) of inner implementation components the behavior attaches to. Lets one exposed event fan out onto multiple inner components. | ValueExpression -> java.lang.String (optional) | — | RT | `targets="field1 field2"` |
| `default (cc:clientBehavior)` | When true, the page author may omit the event attribute on the attached object (this is the default event). Assumes only one clientBehavior is declared — analogous to 'action' being default for commandLink. | ValueExpression -> boolean (optional) | — | RT | `default="true"` |

### `other` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `composite:editableValueHolder (cc:editableValueHolder)` | Declares the composite exposes an inner EditableValueHolder (an editable form field) as a retargeting point, so page authors can attach validators/converters/valueChangeListeners targeting the inner field. A capability-typed attach-point. | Container with name + targets | — | both | `<cc:editableValueHolder name="username" targets="usernameInput"/>` |
| `name (cc:editableValueHolder)` | Maps back to the 'for' attribute on an attached object nested in the using page; if targets is omitted, also identifies the inner target component id. | ValueExpression -> java.lang.String, required | ✔ | both | `name="username"` |
| `targets (cc:editableValueHolder)` | Space-separated list of client ids (relative to top-level component) of inner components the attached object retargets to. (Space, not tab — for XML Schema IDREFS/NMTOKENS compatibility.) | ValueExpression -> java.lang.String (optional) | — | RT | `targets="input1 input2"` |
| `composite:valueHolder (cc:valueHolder)` | Like editableValueHolder but exposes a read-only ValueHolder inner component (converters only, no validators). name + targets + hidden. Demonstrates a typology of attach-points by capability. | Container with name + targets + hidden | — | both | `<cc:valueHolder name="output" targets="out"/>` |
| `composite:actionSource (cc:actionSource)` | Declares the composite exposes an inner ActionSource2 (e.g. a button/command) as a retargeting point for attached action listeners. name + targets + hidden. | Container with name + targets + hidden | — | both | `<cc:actionSource name="submit" targets="submitButton"/>` |
| `name/targets/hidden (cc:valueHolder, cc:actionSource)` | Shared attribute family across the attach-point tags: name = the 'for' mapping (and default target id); targets = space-separated inner client ids; hidden = tool-only flag. Consistent shape across all attach-point declarations. | name: ValueExpression->String (required); targets: ValueExpression->String (optional); hidden: ValueExpression->boolean (optional) | cond. | both | `name="submit" targets="btn"` |
| `composite:implementation (cc:implementation)` | The required companion to cc:interface holding the actual component markup/template. Strict separation of contract (interface) from realization (implementation) within one file. References props via #{cc.attrs.x}. | Container element (markup body), no documented attributes | cond. | RT | `<cc:implementation><h:inputText value="#{cc.attrs.value}"/></cc:implementation>` |

## Multi-library / composition

Each library is identified by a single XML <namespace> URI; page authors bind a prefix to it (xmlns:my="http://..."). Multiple taglib files coexist via distinct namespaces; the author-chosen prefix means no global tag-name collisions across libraries (collisions only happen within a single namespace, where tag-name must be unique). A library may optionally suggest a <short-name> (advisory prefix only — the author still owns the actual prefix). Composite-component libraries are namespaced two ways: implicitly by directory under resources/ (mapped to the default jakarta.faces.composite namespace, subdir = library) or explicitly via <composite-library-name> binding a custom namespace URI to a resources directory. No documented precedence/override mechanism exists for the SAME tag-name across libraries — separation is purely by namespace URI, and resolution is unambiguous because the prefix selects the namespace. library-class registration is an alternative whole-library path mutually exclusive with the namespace+tag-list path.

## What a greenfield ng/WO format should take from this

Several genuinely strong ideas worth adopting. (1) Clean interface/implementation split inside ONE component file — the descriptor is co-located with and authored in the same language as the component, yet the public contract is a distinct, machine-readable block. This beats both a fully separate descriptor file (drifts from code) and no descriptor at all. (2) A unified FeatureDescriptor metadata family — displayName, shortDescription, preferred, expert, hidden — applied CONSISTENTLY to the component, every prop, every slot, and every facet. The expert-vs-hidden distinction (advanced-but-visible vs. tool-internal) is a subtle, well-considered idea most formats lack; a modern format should steal this whole vocabulary. (3) First-class, TYPED categories beyond plain props: slots (cc:facet / insertChildren / renderFacet, with required enforcement and a default unnamed slot), events (cc:clientBehavior with a 'default event' notion), and capability-typed attach-points (valueHolder vs editableValueHolder vs actionSource). Declaring events and slots as distinct kinds — not stringly-typed props — is exactly what a good component descriptor should do. (4) method-signature for callback/action props gives strongly-typed handler attributes, and the type-vs-method-signature mutual exclusion models 'value prop vs. callback prop' cleanly. (5) targets / targetAttributeName retargeting decouples the public prop name from internal wiring — a public-API-stability feature worth having. (6) required + default declared at the contract level enables validation and declarative defaulting without code. Weaknesses to avoid: every attribute value is a ValueExpression-as-String, so even booleans like required/expert are stringly typed and resolved only at runtime — a modern format should use real typed literals in the schema. The 'inferred contract when cc:interface is omitted' convenience undermines reliable tooling. And targets as a space-separated id list inside a string attribute (chosen for XML IDREFS compatibility) is a stringly-typed wiring hack a cleaner format should replace with structured lists.

## Documented pitfall / regret

Documented friction points specific to this format: (1) The contract is OPTIONAL — if <cc:interface> is omitted the contract is inferred from usage, so tooling cannot rely on an explicit declaration; the spec must define inference rules as a fallback. (2) type and method-signature are mutually exclusive and, when both are mistakenly present, method-signature is silently IGNORED rather than erroring — a silent-precedence footgun. (3) targets must be SPACE-separated (not tab) specifically for XML Schema IDREFS/NMTOKENS compatibility — an XML-era constraint leaking into the authoring surface. (4) cc:insertChildren must appear at most once; multiple occurrences cause duplicate-id errors with explicitly 'undefined results' per the docs. (5) Everything is a ValueExpression even for static tooling metadata (displayName, expert, etc.), so IDE-consumed metadata is technically a runtime expression, blurring the tooling/runtime boundary.

## Primary sources

- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/interface>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/attribute>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/facet>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/clientBehavior>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/editableValueHolder>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/valueHolder>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/actionSource>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/insertChildren>
- <https://jakarta.ee/specifications/faces/4.0/vdldoc/cc/renderFacet>
- <https://docs.oracle.com/javaee/7/javaserver-faces-2-2/vdldocs-facelets/cc/interface.html>
- <https://jakarta.ee/xml/ns/jakartaee/web-facelettaglibrary_4_0.xsd>

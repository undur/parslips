# JSP Tag Library Descriptor (.tld)

> Part of the [tag-library format prior-art deep-dive](overview.md). Field-by-field reference extracted from primary sources. **Framing:** observed on its own terms as industry practice — not mapped onto current WO/ng conventions.

**Role:** Runtime **and** tooling (one source)

**Artifact / file shape:** A standalone XML file with a .tld extension, hand-authored (or, for unpackaged tag files, auto-generated as an implicit TLD by the container). It lives under /WEB-INF/ in a WAR or /META-INF/ inside a library JAR — deliberately outside the public document root so it is never served over HTTP. The root element is <taglib> (DTD-validated in JSP 1.1/1.2, XML-Schema-validated from JSP 2.0 onward via the web-jsptaglibrary_*.xsd namespace, with the spec version carried in the version attribute). Structure is a flat list of library-level metadata followed by repeated <tag>, <tag-file>, and <function> declarations, each with nested <attribute>/<variable> children.

## Fields

Every meaningful construct the format declares, at every level. *Use* = consumed by the IDE/tooling, the runtime, or both.

### `library` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `tlib-version` | The version number of THIS tag library (author-assigned, e.g. 1.2). Distinct from the JSP spec version. Lets a container/tool distinguish releases of the same library. | Dewey-decimal version string (e.g. "1.0", "1.2.3") | ✔ | both | `<tlib-version>1.2</tlib-version>` |
| `jsp-version / version (attribute)` | The minimum JSP/Jakarta Pages spec version the library requires. Legacy DTD (1.1/1.2) used the <jsp-version> element; XSD-based TLDs (2.0+) moved it to the version attribute on <taglib>, paired with the schema namespace. Gates which features (deferred expressions, etc.) the container honors. | Version string. Element default 2.0; attribute form e.g. version="2.1" | ✔ | both | `<taglib version="2.1" xmlns="http://java.sun.com/xml/ns/javaee">` |
| `short-name` | A mnemonic / default namespace-prefix suggestion for page-authoring tools. Purely advisory: the actual prefix is chosen by the page author in the taglib directive. For implicit TLDs of unpackaged tag files it is derived from the directory path (slashes to hyphens). | NMTOKEN-like string, no whitespace/colons | ✔ | IDE | `<short-name>fmt</short-name>` |
| `uri` | A globally unique identifier for the library. The canonical key by which a JSP page imports the library (taglib directive uri=...) and by which the container resolves which TLD to load. Often an opaque URL that need not resolve. | Unique URI string | — | both | `<uri>http://java.sun.com/jsp/jstl/core</uri>` |
| `description` | Free-text human documentation of the library's purpose. Shown in tools. | Arbitrary text | — | IDE | `<description>JSTL 1.1 core library</description>` |
| `display-name` | Short human-readable name intended for display by GUI tools (palettes, outlines). Distinct from short-name (prefix hint) and description (long text). | Short display string | — | IDE | `<display-name>JSTL core</display-name>` |
| `icon` | Container for tool-palette icons representing the library. Holds <small-icon> (16x16) and <large-icon> (32x32), each a relative path to a .gif/.jpg. Tooling-only metadata. | Element containing small-icon/large-icon paths | — | IDE | `<icon><small-icon>/img/c16.gif</small-icon></icon>` |
| `validator` | Declares a translation-time validator for the WHOLE library: a class that inspects every JSP page using the library and can reject it. Descriptor pointer to out-of-band code; actual rules live in the Java class, not the TLD (the code-not-descriptor split). Contains <validator-class> + optional <init-param>. | Element: validator-class (FQ class) + init-param* | — | both | `<validator><validator-class>org.x.JstlValidator</validator-class></validator>` |
| `listener` | Registers a servlet-context/session event listener that ships with the library; the container instantiates it when the library is loaded. Contains a single <listener-class>. Registration order across tag-library listeners is undefined (documented limitation vs. web.xml listeners). | Element containing listener-class (FQ class name) | — | RT | `<listener><listener-class>org.x.SetupListener</listener-class></listener>` |
| `tag` | Declares one custom tag (action) backed by a Java tag-handler class. Repeatable. The core element of the format. Sub-elements: name, tag-class, tei-class, body-content, variable*, attribute*, dynamic-attributes, plus doc and tag-extension. | Element (see tag-level fields) | — | both | `<tag><name>out</name><tag-class>org.x.OutTag</tag-class>...</tag>` |
| `tag-file` | Declares a tag implemented as a .tag SOURCE FILE (markup-authored tag) rather than a Java handler class. Repeatable. Maps a tag <name> to a <path> on disk; a library can thus mix code-backed and template-backed tags. Unpackaged tag files under /WEB-INF/tags/ get an implicit TLD generated automatically (zero-descriptor authoring). | Element: name + path (+ doc, tag-extension) | — | both | `<tag-file><name>panel</name><path>/WEB-INF/tags/panel.tag</path></tag-file>` |
| `function` | Declares an EL function: binds an EL-callable name to a public static Java method. Repeatable. Lets templates call helper logic (fn:length, etc.) without a tag. Names must be unique within the library. Sub-elements: name, function-class, function-signature. | Element: name + function-class + function-signature | — | both | `<function><name>length</name><function-class>org.x.Fn</function-class><function-signature>int length(java.lang.Object)</function-signature></function>` |
| `tag-extension / taglib-extension` | Generic open-ended extension hook carrying tool-/vendor-specific metadata the spec deliberately leaves undefined. Appears at library level (taglib-extension) and per-tag/tag-file (tag-extension). Containers ignore it; tooling may read it. The format's escape valve for forward-compatible metadata. | Element with namespaced <extension-element> children | — | IDE | `<taglib-extension><extension-element>...</extension-element></taglib-extension>` |

### `validator` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `validator-class` | Fully qualified name of the class implementing javax.servlet.jsp.tagext.TagLibraryValidator. Runs at translation time, sees the page's XML view, returns validation messages. The TLD only NAMES it; all logic is in Java — validation rules cannot be expressed declaratively. | Fully qualified class name | ✔ | both | `<validator-class>org.apache.taglibs.standard.tlv.JstlCoreTLV</validator-class>` |
| `init-param / param-name / param-value` | Name/value initialization parameters passed to the TagLibraryValidator instance. Lets one validator class be parameterized per library without code changes. param-name and param-value are both required within each init-param. | init-param element containing param-name (string) + param-value (string) | — | RT | `<init-param><param-name>expressionAttributes</param-name><param-value>out:value</param-value></init-param>` |

### `listener` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `listener-class` | Fully qualified name of the event-listener class (ServletContextListener, HttpSessionListener, etc.) bundled with the library. The only sub-element of <listener>. | Fully qualified class name | ✔ | RT | `<listener-class>org.x.MyContextListener</listener-class>` |

### `tag` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The tag's local name as used in markup after the prefix (prefix:name). Unique within the library. The public element name; the handler class is separate, so markup name and Java name are decoupled. | NMTOKEN-like string | ✔ | both | `<name>forEach</name>` |
| `tag-class` | Fully qualified name of the Java tag-handler class (implements Tag/SimpleTag/etc.). The runtime behavior of the tag. A code-backed tag uses tag-class; a template-backed tag uses <tag-file> instead. | Fully qualified class name | ✔ | both | `<tag-class>org.apache.taglibs.standard.tag.rt.core.ForEachTag</tag-class>` |
| `tei-class` | Fully qualified subclass of javax.servlet.jsp.tagext.TagExtraInfo. A code-based ALTERNATIVE to declarative <variable> elements: computes exposed scripting variables (name/type/scope) DYNAMICALLY at translation time from attribute values, and can do extra attribute validation. The classic declarative-vs-imperative escape hatch. | Fully qualified class name | — | both | `<tei-class>org.x.ForEachTEI</tei-class>` |
| `body-content` | Declares how the container treats the tag's body. A 4-valued enum that tells BOTH parser and IDE how to lex/validate the body. 'empty'=no body; 'JSP'=normal JSP, scripting allowed (classic handlers only); 'scriptless'=text/EL/other tags but NO scripting; 'tagdependent'=opaque raw text interpreted by the handler itself (e.g. SQL, JS). | Enum: empty \| JSP \| scriptless \| tagdependent | — | both | `<body-content>scriptless</body-content>` |
| `dynamic-attributes` | Boolean: whether the tag accepts arbitrary undeclared attributes at runtime (collected and passed to a handler implementing DynamicAttributes). Enables open attribute sets (e.g. pass-through HTML attributes) without enumerating each. Default false. | Boolean (true\|false), default false | — | both | `<dynamic-attributes>true</dynamic-attributes>` |
| `variable` | Declaratively exposes a scripting/EL variable the tag publishes into the page. Repeatable. Notable design point: variables are first-class, separately-typed, scoped members of a tag — not just attributes. Sub-elements: name-given\|name-from-attribute, variable-class, declare, scope, description. | Element (see variable-level fields) | — | both | `<variable><name-given>item</name-given><variable-class>java.lang.Object</variable-class><scope>NESTED</scope></variable>` |
| `attribute` | Declares one attribute the tag accepts. Repeatable. The richest sub-structure in the format. Each attribute carries its own type, requiredness, and expression-language capability flags. | Element (see attribute-level fields) | — | both | `<attribute><name>value</name><required>true</required><rtexprvalue>true</rtexprvalue></attribute>` |
| `example` | Informal free-text example of how to use the tag. Tooling-only documentation surfaced in palettes/help. | Arbitrary text | — | IDE | `<example><c:out value="${x}"/></example>` |
| `description / display-name / icon` | Per-tag documentation metadata mirroring the library-level trio: long text, short tool-display name, palette icons. Consumed only by tooling. | text / string / icon element | — | IDE | `<description>Writes its body or value to the page</description>` |

### `attribute` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The attribute's name as written in markup. Unique within the tag (duplicate is a translation error). When dynamic-attributes is true, attributes beyond the declared ones are still allowed. | NMTOKEN-like string | ✔ | both | `<name>value</name>` |
| `required` | Whether the page author MUST supply this attribute. Translation-time error if missing. Default false. A simple but essential validation primitive expressed in the descriptor (not code). | Boolean (true\|false), default false | — | both | `<required>true</required>` |
| `rtexprvalue` | Whether the attribute value may be a runtime expression (scriptlet <%=...%> or EL ${...}) vs. a static compile-time literal only. The key dynamic-vs-static gate. Default false (literal only). Combined with deferred-value/deferred-method it controls whether both immediate and deferred expressions are accepted. | Boolean (true\|false), default false | — | both | `<rtexprvalue>true</rtexprvalue>` |
| `type` | The Java type of the attribute's value. For rtexprvalue attributes it declares the expected runtime/return type of the expression (enables IDE type-checking and container coercion). Default java.lang.String. Forced when fragment=true. | Fully qualified Java type name; default java.lang.String | — | both | `<type>java.util.Collection</type>` |
| `fragment` | Whether this attribute is a JSP FRAGMENT: a deferred, separately-evaluable chunk of template the handler can invoke zero or more times. If true, container forces rtexprvalue=true and type=javax.servlet.jsp.tagext.JspFragment. Powerful: an attribute whose value is itself renderable template content (a named slot / render-prop passed as an attribute). | Boolean (true\|false), default false | — | both | `<fragment>true</fragment>` |
| `deferred-value` | Marks the attribute as accepting a DEFERRED VALUE expression (#{...}) — an EL expression captured as a ValueExpression object and evaluated later (by the component, not at tag execution). Contains an optional <type> child (default java.lang.Object). Mutually exclusive with deferred-method. Separates 'when written' from 'when evaluated'. | Element with optional <type> (FQ type, default java.lang.Object) | — | both | `<deferred-value><type>java.lang.Boolean</type></deferred-value>` |
| `deferred-method` | Marks the attribute as accepting a DEFERRED METHOD expression (#{bean.action}) captured as a MethodExpression, invoked later. Contains an optional <method-signature> child (default 'void methodName()'). Mutually exclusive with deferred-value. Lets an attribute bind to a behavior/callback rather than a value. | Element with optional <method-signature> string | — | both | `<deferred-method><method-signature>java.lang.String submit(java.lang.String)</method-signature></deferred-method>` |
| `description` | Per-attribute documentation text. Tooling-only; surfaced in attribute autocomplete/help. | Arbitrary text | — | IDE | `<description>The value to write</description>` |

### `attribute (deferred-value child)` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `type (in deferred-value)` | Expected Java type of the deferred ValueExpression's evaluated result. Enables coercion and IDE checking of #{...} bindings. Default java.lang.Object. | Fully qualified Java type; default java.lang.Object | — | both | `<type>java.lang.Boolean</type>` |

### `attribute (deferred-method child)` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `method-signature` | Expected signature of the bound MethodExpression: return type, method name, parameter type list. Lets container/tooling validate the referenced method matches. Default 'void methodName()'. | Signature string: returnType name(paramType,...) | — | both | `<method-signature>void actionListener(javax.faces.event.ActionEvent)</method-signature>` |

### `variable` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name-given` | The literal, statically-known name of the exposed variable. One of name-given / name-from-attribute is required. Use when the variable name is fixed by the tag. | NMTOKEN-like string | cond. | both | `<name-given>now</name-given>` |
| `name-from-attribute` | Names an ATTRIBUTE of the tag whose VALUE (at translation time) supplies the exposed variable's name. Indirection letting the page author choose the variable name (e.g. var="row"). Alternative to name-given; exactly one required. | Name of a declared attribute | cond. | both | `<name-from-attribute>var</name-from-attribute>` |
| `variable-class` | The Java type of the exposed variable. Drives the generated scripting-variable declaration and IDE type info. Default java.lang.String. | Fully qualified Java type; default java.lang.String | — | both | `<variable-class>java.lang.Object</variable-class>` |
| `declare` | Whether the container should actually declare (instantiate a new scripting variable for) this name vs. assume it exists. Default true. Translation error if both declare and fragment are specified. | Boolean (true\|false), default true | — | RT | `<declare>true</declare>` |
| `scope` | Lexical visibility window of the exposed variable relative to the tag: NESTED (only between start/end tags), AT_BEGIN (from start tag to end of enclosing scope), AT_END (from end tag onward). The descriptor expresses WHERE a tag-published binding is in scope. Default NESTED. | Enum: NESTED \| AT_BEGIN \| AT_END, default NESTED | — | both | `<scope>AT_BEGIN</scope>` |
| `description` | Per-variable documentation text. Tooling-only. | Arbitrary text | — | IDE | `<description>Current loop item</description>` |

### `tag-file` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The markup name of a template-implemented (.tag) tag. Unique within the library. | NMTOKEN-like string | ✔ | both | `<name>panel</name>` |
| `path` | Filesystem location of the .tag source file, relative to app root. Must begin with /WEB-INF/tags/ (WAR) or /META-INF/tags/ (JAR). Binds a markup name to a template file on disk — the descriptor's link between element and its template implementation. | Context-relative path string | ✔ | both | `<path>/WEB-INF/tags/panel.tag</path>` |

### `function` level

| Field | Declares | Accepts | Req. | Use | Example |
|---|---|---|---|---|---|
| `name` | The EL-callable name of the function (used as prefix:name(...) in ${}). Unique within the library. | NMTOKEN-like string | ✔ | both | `<name>length</name>` |
| `function-class` | Fully qualified name of the class containing the backing public static method. The function maps to a static Java method, not an instance — a lightweight, stateless callable. | Fully qualified class name | ✔ | both | `<function-class>org.apache.taglibs.standard.functions.Functions</function-class>` |
| `function-signature` | The full Java signature of the static method: return type, method name, fully-qualified parameter types. Used to resolve overloads and type-check EL calls in tooling. Format: 'returnType methodName(paramType, ...)'. | Signature string | ✔ | both | `<function-signature>int length(java.lang.Object)</function-signature>` |

## Multi-library / composition

Each TLD describes exactly ONE library, identified by its <uri>. A page composes libraries by issuing one taglib directive per library (<%@ taglib uri="..." prefix="x" %>), and the page author — not the descriptor — chooses the prefix. The namespace prefix is therefore a per-page binding, fully decoupled from the library: the same library can appear under different prefixes on different pages, and <short-name> is only a non-binding suggestion. The container resolves a uri to a TLD via an explicit <taglib> map in web.xml, via the <uri> declared inside TLDs auto-discovered in JARs, or by treating the uri as a direct path. Conflict handling is minimal: names must be unique WITHIN a library (duplicate tag/attribute/function name = translation error), but cross-library collisions are simply impossible because every tag is always prefix-qualified. There is no notion of one library extending, importing, or overriding another; composition happens only at the page level through prefixes.

## What a greenfield ng/WO format should take from this

Several ideas here are worth stealing for a modern descriptor. (1) body-content as a small enum (empty | scriptless | tagdependent | JSP) is excellent: it tells the parser AND the editor how to lex a tag's children, and a 'tagdependent'/opaque-body mode (raw text the element interprets itself) is exactly what you want for elements wrapping SQL, JS, or other embedded languages. (2) First-class <variable> declarations with an explicit <scope> (NESTED/AT_BEGIN/AT_END) are a genuinely good model: a tag declares what bindings it PUBLISHES into the surrounding template and over what lexical window — far better than leaving exposed loop/item variables undocumented. (3) name-from-attribute is a clean way to say 'the author picks the published variable's name via this attribute' (var="..."). (4) Per-attribute type + a capability flag for static-vs-dynamic values (rtexprvalue) + the fragment concept (an attribute whose value is itself renderable template content — i.e. a named slot / render-prop) are all strong primitives. (5) tag-file shows a descriptor can uniformly mix code-backed and TEMPLATE-backed elements, and implicit-TLD generation for convention-placed files is a great zero-config default. What to AVOID: the format leans on out-of-band Java (tei-class TagExtraInfo, validator-class TagLibraryValidator) the moment anything is dynamic — variable computation and cross-tag validation escape into compiled code the descriptor only names, so a tool reading just the TLD cannot know the real variables or rules. A modern format should push that expressiveness (conditional variables, attribute interdependencies, validation) into the DECLARATIVE descriptor instead of a code escape hatch. Also avoid the dual DTD/XSD heritage and the verbose one-element-per-field XML; a flatter, attribute-based or non-XML schema would be far more authorable.

## Documented pitfall / regret

Documented friction points: (1) the descriptor-vs-code split — TagExtraInfo (tei-class) and TagLibraryValidator (validator-class) move the interesting logic out of the TLD into Java, so the descriptor is incomplete on its own and tooling cannot fully reason about a library statically; widely cited as why TLDs are awkward for IDEs. (2) Tag-library <listener> registration order is explicitly undefined (unlike web.xml listeners), a noted inconsistency. (3) The format carries dead weight from its DTD era: <jsp-version> as an element migrated to a version attribute, and deferred-value/deferred-method were bolted on for JSF, giving attributes three overlapping expression modes (rtexprvalue immediate ${}, deferred #{}, and their combination) that are subtle and easy to misconfigure. (4) The verbose one-XML-element-per-property style makes hand-authoring tedious — part of why convention-based implicit TLDs and .tag files were introduced as escapes from writing TLDs at all.

## Primary sources

- <https://docs.oracle.com/cd/E17904_01/web.1111/e13722/tld.htm>
- <https://docs.oracle.com/javaee/5/tutorial/doc/bnamu.html>
- <https://docs.oracle.com/javaee/5/tutorial/doc/bnahq.html>
- <https://jakarta.ee/specifications/pages/>

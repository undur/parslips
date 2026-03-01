package org.objectstyle.wolips.bindings.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * Parses {@code .api} XML into immutable {@link ApiSnapshot} objects.
 *
 * <p>Each parse method is a pure function: it reads XML from some source,
 * produces an {@link ApiSnapshot}, and does not retain the DOM. The result
 * is inherently thread-safe.
 *
 * <p>For files containing multiple {@code <wo>} elements (such as the
 * global {@code WebObjectDefinitions.xml}), use {@link #parseAll(Document)}
 * or the file/URL variants to get a map of class name to snapshot.
 *
 * <p>The parsing logic handles legacy encoding issues (old {@code .api} files
 * that declare {@code encoding="macintosh"}) by replacing the encoding with
 * UTF-8 before parsing.
 */
public final class ApiParser {

	/** Not instantiable — all methods are static. */
	private ApiParser() {
	}

	// ---- Public entry points ------------------------------------------------

	/**
	 * Parses a DOM document containing one or more {@code <wo>} elements and
	 * returns a map of class name to snapshot. Used for multi-definition files
	 * like {@code WebObjectDefinitions.xml}.
	 *
	 * @param document a parsed Xerces Document
	 * @return map of fully-qualified class name to {@link ApiSnapshot}
	 */
	public static Map<String, ApiSnapshot> parseAll(Document document) {
		Map<String, ApiSnapshot> snapshots = new HashMap<>();
		Element root = document.getDocumentElement();
		List<Element> woElements = directChildrenByTagName(root, "wo");
		for (Element woElement : woElements) {
			ApiSnapshot snapshot = parseWoElement(woElement);
			if (snapshot.getClassName() != null && !snapshot.getClassName().isEmpty()) {
				snapshots.put(snapshot.getClassName(), snapshot);
			}
		}
		return snapshots;
	}

	/**
	 * Parses a DOM document and returns the first {@code <wo>} element as a
	 * snapshot. Used for single-definition {@code .api} files.
	 *
	 * @param document a parsed Xerces Document
	 * @return the snapshot, or null if no {@code <wo>} element is found
	 */
	public static ApiSnapshot parse(Document document) {
		Element root = document.getDocumentElement();
		List<Element> woElements = directChildrenByTagName(root, "wo");
		if (woElements.isEmpty()) {
			return null;
		}
		return parseWoElement(woElements.get(0));
	}

	/**
	 * Parses an {@code .api} file, handling the legacy "macintosh" encoding
	 * issue, and returns all snapshots by class name.
	 */
	public static Map<String, ApiSnapshot> parseAllFromFile(File file) throws ApiModelException {
		Document document = parseFileToDocument(file);
		return parseAll(document);
	}

	/**
	 * Parses an {@code .api} file and returns the first {@code <wo>} element
	 * as a snapshot.
	 */
	public static ApiSnapshot parseFile(File file) throws ApiModelException {
		Document document = parseFileToDocument(file);
		return parse(document);
	}

	/**
	 * Parses a URL (e.g. a bundle entry) and returns all snapshots by class name.
	 */
	public static Map<String, ApiSnapshot> parseAllFromUrl(URL url) throws ApiModelException {
		Document document = parseUrlToDocument(url);
		return parseAll(document);
	}

	/**
	 * Parses a URL and returns the first {@code <wo>} element as a snapshot.
	 */
	public static ApiSnapshot parseUrl(URL url) throws ApiModelException {
		Document document = parseUrlToDocument(url);
		return parse(document);
	}

	/**
	 * Parses from a Reader and returns the first {@code <wo>} element as a snapshot.
	 */
	public static ApiSnapshot parseReader(Reader reader) throws ApiModelException {
		Document document = parseReaderToDocument(reader);
		return parse(document);
	}

	// ---- DOM parsing --------------------------------------------------------

	/**
	 * Reads a file into a DOM Document, handling the legacy "macintosh"
	 * encoding that old WOBuilder-produced {@code .api} files may declare.
	 */
	private static Document parseFileToDocument(File file) throws ApiModelException {
		try {
			// Preprocess the file to replace "macintosh" encoding with UTF-8.
			// Old WOBuilder (or something else) produced .api files that claim
			// to be encoding "macintosh", which Java's XML parser doesn't recognize.
			StringBuilder buffer = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line = reader.readLine();
				if (line != null && line.startsWith("<?")) {
					buffer.append(line.replaceAll("encoding=\"macintosh\"", "encoding=\"UTF-8\""));
					buffer.append("\n");
				}
				while ((line = reader.readLine()) != null) {
					buffer.append(line);
					buffer.append("\n");
				}
			}
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(new InputSource(new StringReader(buffer.toString())));
			document.normalize();
			return document;
		} catch (Exception e) {
			throw new ApiModelException("Failed to parse API file '" + file.getAbsolutePath() + "'.", e);
		}
	}

	private static Document parseUrlToDocument(URL url) throws ApiModelException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(url.toExternalForm());
			document.normalize();
			return document;
		} catch (Exception e) {
			throw new ApiModelException("Failed to parse API URL '" + url + "'.", e);
		}
	}

	private static Document parseReaderToDocument(Reader reader) throws ApiModelException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(new InputSource(reader));
			document.normalize();
			return document;
		} catch (Exception e) {
			throw new ApiModelException("Failed to parse API from reader.", e);
		}
	}

	// ---- Element → POJO conversion ------------------------------------------

	/**
	 * Converts a {@code <wo>} DOM element into an immutable {@link ApiSnapshot}.
	 *
	 * <p>This is the core conversion method. It extracts all data from the DOM
	 * and builds the POJO tree, including pre-computing derived binding
	 * properties (isRequired, isWillSet) from both explicit attributes and
	 * the validation tree.
	 */
	private static ApiSnapshot parseWoElement(Element woElement) {
		String className = woElement.getAttribute("class");
		boolean componentContent = parseComponentContent(woElement);
		String preview = serializePreview(woElement);

		// Parse validations first — we need them to compute binding properties
		List<ApiValidation> validations = parseValidations(woElement);

		// Parse bindings, pre-computing isRequired and isWillSet from the
		// validation tree so consumers don't have to cross-reference at read time
		List<IApiBinding> bindings = parseBindings(woElement, validations);

		return new ApiSnapshot(className, componentContent, bindings, validations, preview);
	}

	/**
	 * Parses the {@code wocomponentcontent} attribute. Accepts "true" or "yes"
	 * (case-insensitive), matching the behavior of {@link Wo#isComponentContent()}.
	 */
	private static boolean parseComponentContent(Element woElement) {
		String value = woElement.getAttribute("wocomponentcontent");
		if (value == null) {
			return false;
		}
		return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
	}

	/**
	 * Parses all {@code <binding>} children of a {@code <wo>} element into
	 * {@link SimpleApiBinding} instances, pre-computing the derived properties
	 * {@code isRequired} and {@code isWillSet} from both explicit attributes
	 * and the validation tree.
	 *
	 * <p>The "explicitly required" flag is set when the binding has
	 * {@code required="YES"}. The "required" flag is set when either explicitly
	 * required OR the validation tree contains a single {@code <unbound>}
	 * validation referencing this binding (the implicit required pattern).
	 * The same logic applies for "willSet" via {@code <unsettable>} validations.
	 */
	private static List<IApiBinding> parseBindings(Element woElement, List<ApiValidation> validations) {
		List<Element> bindingElements = directChildrenByTagName(woElement, "binding");
		List<IApiBinding> bindings = new ArrayList<>(bindingElements.size());

		for (Element bindingElement : bindingElements) {
			String name = bindingElement.getAttribute("name");
			String defaults = bindingElement.getAttribute("defaults");

			SimpleApiBinding binding = new SimpleApiBinding(name);

			if (defaults != null && !defaults.isEmpty()) {
				binding.setDefaults(defaults);
			}

			// Explicit required: required="YES" attribute on the binding element
			boolean explicitlyRequired = "YES".equalsIgnoreCase(bindingElement.getAttribute("required"));
			binding.setExplicitlyRequired(explicitlyRequired);

			// Effective required: explicit OR single-unbound validation pattern
			boolean required = explicitlyRequired || hasImplicitUnboundValidation(name, validations);
			binding.setRequired(required);

			// Effective willSet: explicit settable="YES" OR single-unsettable validation pattern
			boolean explicitlySettable = "YES".equalsIgnoreCase(bindingElement.getAttribute("settable"));
			boolean willSet = explicitlySettable || hasImplicitUnsettableValidation(name, validations);
			binding.setWillSet(willSet);

			bindings.add(binding);
		}

		return bindings;
	}

	/**
	 * Checks whether the validation tree contains a single {@code <unbound>}
	 * validation referencing the given binding name. This is the implicit
	 * "required" pattern used in {@code .api} files.
	 *
	 * <p>Matches the logic in {@link Binding#isRequired()}: a validation with
	 * exactly one UNBOUND child whose binding name matches is treated as
	 * an implicit required marker.
	 */
	private static boolean hasImplicitUnboundValidation(String bindingName, List<ApiValidation> validations) {
		for (ApiValidation validation : validations) {
			List<ApiValidation> children = validation.getChildren();
			if (children.size() == 1) {
				ApiValidation child = children.get(0);
				if (child.getKind() == ApiValidation.Kind.UNBOUND && bindingName.equals(child.getBindingName())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks whether the validation tree contains a single {@code <unsettable>}
	 * validation referencing the given binding name. This is the implicit
	 * "will set" pattern.
	 *
	 * <p>Matches the logic in {@link Binding#isWillSet()}.
	 */
	private static boolean hasImplicitUnsettableValidation(String bindingName, List<ApiValidation> validations) {
		for (ApiValidation validation : validations) {
			List<ApiValidation> children = validation.getChildren();
			if (children.size() == 1) {
				ApiValidation child = children.get(0);
				if (child.getKind() == ApiValidation.Kind.UNSETTABLE && bindingName.equals(child.getBindingName())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Parses all {@code <validation>} children of a {@code <wo>} element
	 * into immutable {@link ApiValidation} trees.
	 */
	private static List<ApiValidation> parseValidations(Element woElement) {
		List<Element> validationElements = directChildrenByTagName(woElement, "validation");
		List<ApiValidation> validations = new ArrayList<>(validationElements.size());
		for (Element validationElement : validationElements) {
			String message = validationElement.getAttribute("message");
			List<ApiValidation> children = parseValidationChildren(validationElement);
			validations.add(new ApiValidation(ApiValidation.Kind.VALIDATION, message, null, children));
		}
		return validations;
	}

	/**
	 * Recursively parses the children of a validation container element
	 * ({@code <validation>}, {@code <and>}, {@code <or>}, {@code <not>},
	 * {@code <count>}) into {@link ApiValidation} nodes.
	 */
	private static List<ApiValidation> parseValidationChildren(Element parentElement) {
		List<ApiValidation> children = new ArrayList<>();
		NodeList childNodes = parentElement.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element child = (Element) node;
			ApiValidation validation = parseValidationNode(child);
			if (validation != null) {
				children.add(validation);
			}
		}
		return children;
	}

	/**
	 * Parses a single validation DOM element into an {@link ApiValidation} node.
	 * Leaf predicates (bound, unbound, etc.) extract the binding name;
	 * composite operators (and, or, not, count) recurse into their children.
	 *
	 * @return the parsed validation node, or null if the element tag is not recognized
	 */
	private static ApiValidation parseValidationNode(Element element) {
		String tagName = element.getTagName();
		switch (tagName) {
			// Leaf predicates — each references a binding by name
			case "unbound":
				return new ApiValidation(ApiValidation.Kind.UNBOUND, element.getAttribute("name"));
			case "bound":
				return new ApiValidation(ApiValidation.Kind.BOUND, element.getAttribute("name"));
			case "unsettable":
				return new ApiValidation(ApiValidation.Kind.UNSETTABLE, element.getAttribute("name"));
			case "settable":
				return new ApiValidation(ApiValidation.Kind.SETTABLE, element.getAttribute("name"));
			case "ungettable":
				return new ApiValidation(ApiValidation.Kind.UNGETTABLE, element.getAttribute("name"));
			case "gettable":
				return new ApiValidation(ApiValidation.Kind.GETTABLE, element.getAttribute("name"));

			// Composite operators — recurse into children
			case "and":
				return new ApiValidation(ApiValidation.Kind.AND, null, null, parseValidationChildren(element));
			case "or":
				return new ApiValidation(ApiValidation.Kind.OR, null, null, parseValidationChildren(element));
			case "not":
				return new ApiValidation(ApiValidation.Kind.NOT, null, null, parseValidationChildren(element));
			case "count":
				return new ApiValidation(ApiValidation.Kind.COUNT, null, element.getAttribute("test"), parseValidationChildren(element));

			default:
				// Unknown element — skip silently (forward compatibility)
				return null;
		}
	}

	// ---- Preview serialization ----------------------------------------------

	/**
	 * Serializes the content of the {@code <preview>} element to a string.
	 * Replicates the logic from {@link Wo#getPreview()}: child elements are
	 * serialized via an XML Transformer, text nodes are appended directly.
	 *
	 * @return the preview string, or null if no {@code <preview>} element exists
	 */
	private static String serializePreview(Element woElement) {
		NodeList previewNodes = woElement.getElementsByTagName("preview");
		if (previewNodes.getLength() == 0) {
			return null;
		}

		try {
			StringWriter sw = new StringWriter();
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", Integer.valueOf(4));
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

			for (int nodeNum = 0; nodeNum < previewNodes.getLength(); nodeNum++) {
				Element previewElement = (Element) previewNodes.item(nodeNum);
				NodeList previewChildren = previewElement.getChildNodes();
				for (int childNum = 0; childNum < previewChildren.getLength(); childNum++) {
					Node childNode = previewChildren.item(childNum);
					if (childNode instanceof Element) {
						transformer.transform(new DOMSource(childNode), new StreamResult(sw));
					} else if (childNode instanceof Text) {
						sw.append(((Text) childNode).getTextContent());
					}
				}
			}
			return sw.toString();
		} catch (Exception e) {
			// Preview serialization failure is non-fatal — return null
			return null;
		}
	}

	// ---- DOM utility --------------------------------------------------------

	/**
	 * Returns direct child elements with the given tag name.
	 * Unlike {@code getElementsByTagName()}, this only looks at immediate
	 * children, not descendants.
	 */
	private static List<Element> directChildrenByTagName(Element parent, String tagName) {
		List<Element> children = new ArrayList<>();
		NodeList childNodes = parent.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
				children.add((Element) node);
			}
		}
		return children;
	}
}

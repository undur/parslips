package parslips.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handles text document lifecycle events and editing requests for the Parslips LSP server.
 *
 * <p>This is where the actual template intelligence lives (or rather, where it will
 * be wired in from parslips.tooling). Each LSP request method translates from
 * LSP protocol types to parslips.tooling calls, then translates the results back.
 */
public class ParslipsTextDocumentService implements TextDocumentService {

	/**
	 * Known WO inline element names and their descriptions.
	 * Eventually this will come from parslips.tooling's element registry,
	 * populated from the project's classpath. For now, hardcoded basics.
	 */
	private static final List<ElementInfo> KNOWN_ELEMENTS = List.of(
		new ElementInfo("conditional", "Conditionally renders its content based on a boolean condition"),
		new ElementInfo("repetition", "Iterates over a list, rendering its content for each item"),
		new ElementInfo("string", "Displays a formatted string value"),
		new ElementInfo("form", "HTML form that handles WebObjects action processing"),
		new ElementInfo("hyperlink", "A link that can trigger a WebObjects action or navigate to a URL"),
		new ElementInfo("image", "Displays an image from a resource or URL"),
		new ElementInfo("text", "Multi-line text input field (textarea)"),
		new ElementInfo("textfield", "Single-line text input field"),
		new ElementInfo("checkbox", "Boolean checkbox input"),
		new ElementInfo("popup", "Drop-down select menu (popup button)"),
		new ElementInfo("browser", "Multi-select list box"),
		new ElementInfo("radiobutton", "Single radio button input"),
		new ElementInfo("radiobuttonlist", "Group of radio buttons generated from a list"),
		new ElementInfo("checkboxlist", "Group of checkboxes generated from a list"),
		new ElementInfo("submitbutton", "Form submit button"),
		new ElementInfo("resetbutton", "Form reset button"),
		new ElementInfo("hiddenfield", "Hidden form input field"),
		new ElementInfo("passwordfield", "Password input field (masked)"),
		new ElementInfo("fileupload", "File upload input"),
		new ElementInfo("body", "HTML body tag with WebObjects resource support"),
		new ElementInfo("genericcontainer", "Generic HTML container element with dynamic tag name"),
		new ElementInfo("genericelement", "Generic HTML void element with dynamic tag name"),
		new ElementInfo("componentcontent", "Renders the content passed into this component by its parent"),
		new ElementInfo("switchcomponent", "Dynamically switches which component to render"),
		new ElementInfo("iframe", "Inline frame element"),
		new ElementInfo("javascript", "Inline JavaScript block"),
		new ElementInfo("resourceurl", "Generates a URL for a web server resource"),
		new ElementInfo("actionurl", "Generates a URL that triggers a WebObjects action"),
		new ElementInfo("imagebutton", "Image-based submit button"),
		new ElementInfo("activeimage", "Clickable image that triggers an action"),
		new ElementInfo("redirect", "Redirects to another URL"),
		new ElementInfo("param", "Passes a parameter to an embedded object or applet"),
		new ElementInfo("nestedlist", "Renders a hierarchical (nested) list structure"),
		new ElementInfo("xmlnode", "Generic XML node element")
	);

	/**
	 * The LSP client proxy for pushing diagnostics and notifications.
	 */
	private LanguageClient _client;

	/**
	 * In-memory cache of open document contents, keyed by document URI.
	 * Updated on didOpen/didChange, removed on didClose. Needed because
	 * completion requests only provide a position, not the document text.
	 */
	private final Map<String, String> _documentContents = new HashMap<>();

	/**
	 * Called by the language server after the client connection is established.
	 */
	void connect(LanguageClient client) {
		_client = client;
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		final String uri = params.getTextDocument().getUri();
		final String text = params.getTextDocument().getText();
		_documentContents.put(uri, text);
		publishDiagnostics(uri, text);
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		// With Full sync, the first content change contains the entire document text
		final String uri = params.getTextDocument().getUri();
		final String text = params.getContentChanges().get(0).getText();
		_documentContents.put(uri, text);
		publishDiagnostics(uri, text);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		final String uri = params.getTextDocument().getUri();
		_documentContents.remove(uri);
		// Clear diagnostics when the document is closed
		_client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		// TODO: Optional — trigger full validation on save
	}

	/**
	 * Publishes diagnostics for the given document. Currently a no-op — real
	 * validation from parslips.tooling will be wired in here.
	 */
	private void publishDiagnostics(String uri, String text) {
		final List<Diagnostic> diagnostics = new ArrayList<>();
		// TODO: wire in real validation from parslips.tooling
		_client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		final String uri = params.getTextDocument().getUri();
		final String text = _documentContents.get(uri);

		if (text == null) {
			return CompletableFuture.completedFuture(Either.forLeft(List.of()));
		}

		final Position pos = params.getPosition();
		final String[] lines = text.split("\n", -1);

		if (pos.getLine() >= lines.length) {
			return CompletableFuture.completedFuture(Either.forLeft(List.of()));
		}

		// Get the text on the current line up to the cursor position
		final String line = lines[pos.getLine()];
		final int col = Math.min(pos.getCharacter(), line.length());
		final String linePrefix = line.substring(0, col);

		final List<CompletionItem> items = new ArrayList<>();

		if (linePrefix.endsWith("<wo:")) {
			// User just typed "<wo:" — offer all element names
			items.addAll(buildElementCompletions(""));
		}
		else {
			// Check if we're in the middle of typing a wo: tag name, e.g. "<wo:rep"
			final int lastTagStart = linePrefix.lastIndexOf("<wo:");

			if (lastTagStart != -1) {
				final String partial = linePrefix.substring(lastTagStart + 4);

				// Only offer completions if the partial looks like an incomplete tag name
				// (no spaces, >, or / yet — those mean we're past the tag name)
				if (!partial.isEmpty() && partial.chars().allMatch(Character::isLetterOrDigit)) {
					items.addAll(buildElementCompletions(partial));
				}
			}
		}

		return CompletableFuture.completedFuture(Either.forLeft(items));
	}

	/**
	 * Builds completion items for wo: element names, filtered by the partial
	 * tag name the user has typed so far. Returns snippet completions that
	 * insert the element name with a closing tag and cursor placement.
	 *
	 * @param partial the text typed after {@code <wo:}, may be empty
	 */
	private static List<CompletionItem> buildElementCompletions(String partial) {
		final List<CompletionItem> items = new ArrayList<>();
		final String lowerPartial = partial.toLowerCase();

		for (final ElementInfo element : KNOWN_ELEMENTS) {
			if (element.name().startsWith(lowerPartial)) {
				final CompletionItem item = new CompletionItem("wo:" + element.name());
				item.setKind(CompletionItemKind.Class);
				item.setDetail(element.description());

				// Snippet: completes the tag name and adds closing tag with cursor inside.
				// $1 is where the cursor lands (for adding bindings), $0 is the final
				// cursor position (inside the element content).
				item.setInsertTextFormat(InsertTextFormat.Snippet);
				item.setInsertText(element.name() + " $1>$0</wo:" + element.name() + ">");

				items.add(item);
			}
		}

		return items;
	}

	/**
	 * Simple record holding an element name and its human-readable description.
	 * Will be replaced by parslips.tooling's element registry.
	 */
	private record ElementInfo(String name, String description) {}
}

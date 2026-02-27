package parslips.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handles text document lifecycle events and editing requests for the Parslips LSP server.
 *
 * <p>This is where the actual template intelligence lives (or rather, where it will
 * be wired in from parslips.tooling). Each LSP request method translates from
 * LSP protocol types to parslips.tooling calls, then translates the results back.
 *
 * <p>Currently a minimal stub that accepts documents and returns empty completions.
 * As parslips.tooling grows, the corresponding methods here will delegate to it.
 */
public class ParslipsTextDocumentService implements TextDocumentService {

	/**
	 * The LSP client proxy for pushing diagnostics and notifications.
	 */
	private LanguageClient _client;

	/**
	 * Called by the language server after the client connection is established.
	 */
	void connect(LanguageClient client) {
		_client = client;
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		publishDiagnostics(params.getTextDocument().getUri(), params.getTextDocument().getText());
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		// With Full sync, the first content change contains the entire document text
		final String text = params.getContentChanges().get(0).getText();
		publishDiagnostics(params.getTextDocument().getUri(), text);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		// Clear diagnostics when the document is closed
		_client.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), List.of()));
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		// TODO: Optional — trigger full validation on save
	}

	/**
	 * Scans the document for {@code <wo:} tags and publishes an informational
	 * diagnostic for each one. This is a proof-of-life placeholder — it will be
	 * replaced with real validation from parslips.tooling.
	 */
	private void publishDiagnostics(String uri, String text) {
		final List<Diagnostic> diagnostics = new ArrayList<>();
		final String[] lines = text.split("\n", -1);

		for (int lineNum = 0; lineNum < lines.length; lineNum++) {
			final String line = lines[lineNum];
			int searchFrom = 0;

			while (true) {
				final int tagStart = line.indexOf("<wo:", searchFrom);

				if (tagStart == -1) {
					break;
				}

				// Find the end of the tag name (space, >, or /)
				int tagNameEnd = tagStart + 4; // skip past "<wo:"
				while (tagNameEnd < line.length() && line.charAt(tagNameEnd) != ' ' && line.charAt(tagNameEnd) != '>' && line.charAt(tagNameEnd) != '/') {
					tagNameEnd++;
				}

				final String tagName = line.substring(tagStart + 1, tagNameEnd); // e.g. "wo:repetition"
				final Range range = new Range(new Position(lineNum, tagStart), new Position(lineNum, tagNameEnd));
				final Diagnostic diagnostic = new Diagnostic(range, "Parslips sees: <" + tagName + ">", DiagnosticSeverity.Information, "parslips");
				diagnostics.add(diagnostic);

				searchFrom = tagNameEnd;
			}
		}

		_client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		// TODO: Delegate to parslips.tooling completion engine.
		// For now, return an empty list so the server is functional but inert.
		final List<CompletionItem> items = new ArrayList<>();
		return CompletableFuture.completedFuture(Either.forLeft(items));
	}
}

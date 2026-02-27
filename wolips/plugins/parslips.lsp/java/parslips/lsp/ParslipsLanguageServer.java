package parslips.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * LSP server for ng-objects / WebObjects template files.
 *
 * <p>This is an in-process language server that runs inside Eclipse via LSP4E.
 * It delegates all template intelligence to the parslips.tooling library,
 * translating between LSP protocol types and the tooling's domain model.
 *
 * <p>The server currently supports:
 * <ul>
 *   <li>Text document synchronization (full sync)</li>
 *   <li>Completion (stub — to be wired to parslips.tooling)</li>
 * </ul>
 *
 * <p>As parslips.tooling gains capabilities (validation, hover, navigation),
 * this server will expose them by declaring the corresponding LSP capabilities
 * and implementing the relevant handler methods.
 */
public class ParslipsLanguageServer implements LanguageServer, LanguageClientAware {

	/**
	 * The LSP client proxy, provided by LSP4E. Used to push diagnostics,
	 * show messages, and other server-to-client communication.
	 * Will be used once we wire in validation/diagnostics from parslips.tooling.
	 */
	@SuppressWarnings("unused")
	private LanguageClient _client;

	/**
	 * Handles all text document requests (completion, hover, diagnostics, etc.).
	 */
	private final ParslipsTextDocumentService _textDocumentService = new ParslipsTextDocumentService();

	/**
	 * Handles workspace-level requests (configuration changes, watched files, etc.).
	 */
	private final ParslipsWorkspaceService _workspaceService = new ParslipsWorkspaceService();

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		final ServerCapabilities capabilities = new ServerCapabilities();

		// Full document sync: the client sends the entire document content on every change.
		// This is simpler than incremental sync and fine for our template files which are
		// typically small. We can switch to Incremental later if performance demands it.
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

		// Enable completion support. The trigger characters will cause the client to
		// request completions automatically when the user types them.
		final CompletionOptions completionOptions = new CompletionOptions();
		completionOptions.setResolveProvider(false);
		capabilities.setCompletionProvider(completionOptions);

		return CompletableFuture.completedFuture(new InitializeResult(capabilities));
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void exit() {
		// In-process server — nothing to shut down. The JVM lifecycle is managed by Eclipse.
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return _textDocumentService;
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return _workspaceService;
	}

	@Override
	public void connect(LanguageClient client) {
		_client = client;
		_textDocumentService.connect(client);
	}
}

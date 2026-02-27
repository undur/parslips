package parslips.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Future;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Connects LSP4E (the Eclipse LSP client framework) to our in-process
 * {@link ParslipsLanguageServer}.
 *
 * <p>LSP4E communicates with language servers over streams (stdin/stdout for
 * external processes). For an in-process server like ours, we use piped streams
 * to connect the two sides within the same JVM:
 *
 * <pre>
 *   LSP4E (client)                         ParslipsLanguageServer
 *       writes to clientOutputStream  -->  serverInputStream (server reads)
 *       reads from clientInputStream  <--  serverOutputStream (server writes)
 * </pre>
 *
 * <p>This approach means the server communicates via standard LSP JSON-RPC,
 * even though it's in-process. This is important because:
 * <ul>
 *   <li>The same server code could later be extracted into a standalone process
 *       for use with VS Code, IntelliJ, or other LSP-capable editors.</li>
 *   <li>LSP4E handles all the protocol plumbing (message framing, JSON
 *       serialization) — we just implement the LanguageServer interface.</li>
 * </ul>
 *
 * <p>Registered via plugin.xml as the connection provider for our language server.
 */
public class ParslipsStreamConnectionProvider implements StreamConnectionProvider {

	/**
	 * Stream that LSP4E reads server responses from.
	 * Connected to the server's output stream via a pipe.
	 */
	private PipedInputStream _clientInputStream;

	/**
	 * Stream that LSP4E writes client requests to.
	 * Connected to the server's input stream via a pipe.
	 */
	private PipedOutputStream _clientOutputStream;

	/**
	 * Future for the server's message-listening loop. Cancelled on stop().
	 */
	private Future<?> _launcherFuture;

	@Override
	public void start() throws IOException {
		// Create the piped stream pairs that connect client ↔ server.
		// Buffer size of 8192 is generous for JSON-RPC messages which are typically small.
		final PipedInputStream serverInputStream = new PipedInputStream(8192);
		final PipedOutputStream serverOutputStream = new PipedOutputStream();

		// The client reads what the server writes, and vice versa
		_clientInputStream = new PipedInputStream(serverOutputStream, 8192);
		_clientOutputStream = new PipedOutputStream(serverInputStream);

		// Create and start the language server
		final ParslipsLanguageServer server = new ParslipsLanguageServer();
		final Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, serverInputStream, serverOutputStream);

		// Connect the server to the client proxy so it can push diagnostics etc.
		server.connect(launcher.getRemoteProxy());

		// Start listening for messages on a background thread
		_launcherFuture = launcher.startListening();
	}

	@Override
	public InputStream getInputStream() {
		return _clientInputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return _clientOutputStream;
	}

	@Override
	public InputStream getErrorStream() {
		// In-process server — no separate error stream. Server errors are
		// communicated via LSP protocol (window/logMessage, window/showMessage)
		// rather than stderr.
		return null;
	}

	@Override
	public void stop() {
		if (_launcherFuture != null) {
			_launcherFuture.cancel(true);
		}

		safeClose(_clientInputStream);
		safeClose(_clientOutputStream);
	}

	/**
	 * Quietly closes a stream, ignoring any IOException.
	 * During shutdown, streams may already be broken/closed.
	 */
	private static void safeClose(AutoCloseable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			}
			catch (Exception e) {
				// Shutdown — nothing useful to do with this
			}
		}
	}
}

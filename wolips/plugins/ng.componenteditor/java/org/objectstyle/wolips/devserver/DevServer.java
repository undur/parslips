package org.objectstyle.wolips.devserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny loopback-only HTTP server that lets a running WebObjects / ng-objects
 * application drive Eclipse — most usefully, letting an exception page in the
 * browser link directly to the offending source line, which opens in Eclipse
 * when clicked.
 *
 * <p>This is a modern reimplementation of the old WOLips "womodeler" server
 * (originally a hand-rolled {@code ServerSocket} loop). It is wire-compatible
 * with existing runtime clients — Wonder's {@code ERXExceptionPage} and the
 * {@code WOLips} framework's {@code WOLipsUtilities} — which generate URLs of
 * the form:
 *
 * <pre>
 *   http://localhost:9485/openJavaFile?pw=PASS&amp;app=APP&amp;className=FQCN&amp;lineNumber=N
 *   http://localhost:9485/openComponent?pw=PASS&amp;app=APP&amp;component=NAME
 *   http://localhost:9485/refresh?pw=PASS&amp;path=PATH
 * </pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li><b>Loopback-only.</b> The server binds to {@code 127.0.0.1}, never to
 *       all interfaces. The original WOLips server bound to every interface,
 *       which exposed an "open arbitrary files in my IDE" endpoint to the
 *       local network. We never do that.</li>
 *   <li><b>Password.</b> When a password is configured, every request must
 *       carry a matching {@code pw} query parameter or it is rejected with
 *       401. The original only enforced this on GET (POST slipped through);
 *       here it applies uniformly. The runtime clients always send {@code pw},
 *       and Wonder's exception page refuses to even render links unless a
 *       password is set, so requiring it costs nothing in practice.</li>
 * </ul>
 *
 * <p>Note that query strings from the runtime may use either {@code &amp;} or
 * the HTML-escaped {@code &amp;amp;} as the parameter separator (the two
 * runtime code paths differ), so {@link #parseQuery} tolerates both.
 */
public class DevServer {

	/** Default port — matches the {@code wolips.port} default used by runtime clients. */
	public static final int DEFAULT_PORT = 9485;

	private final int _port;
	private final String _password;
	private HttpServer _httpServer;

	/**
	 * @param port     the TCP port to listen on (loopback only)
	 * @param password the required password, or {@code null}/empty to disable
	 *                 the password check (not recommended)
	 */
	public DevServer(int port, String password) {
		_port = port;
		_password = (password == null || password.isEmpty()) ? null : password;
	}

	public int getPort() {
		return _port;
	}

	/**
	 * Starts the server on the loopback interface. Idempotent-ish: if already
	 * started, this throws from {@link HttpServer#bind} — callers should stop
	 * before restarting.
	 */
	public synchronized void start() throws IOException {
		InetAddress loopback = InetAddress.getLoopbackAddress();
		_httpServer = HttpServer.create(new InetSocketAddress(loopback, _port), 0);

		_httpServer.createContext("/openJavaFile", new GuardedHandler(new OpenJavaFileHandler()));
		_httpServer.createContext("/openComponent", new GuardedHandler(new OpenComponentHandler()));
		_httpServer.createContext("/refresh", new GuardedHandler(new RefreshHandler()));

		// Use a small daemon thread pool. Requests are short-lived (open an
		// editor, refresh a resource) and arrive one at a time in practice.
		_httpServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
			Thread t = new Thread(runnable, "Parsley Dev Server Request");
			t.setDaemon(true);
			return t;
		}));

		_httpServer.start();
	}

	public synchronized void stop() {
		if (_httpServer != null) {
			// Delay 0 = stop immediately, don't wait for in-flight exchanges.
			_httpServer.stop(0);
			_httpServer = null;
		}
	}

	public synchronized boolean isRunning() {
		return _httpServer != null;
	}

	/**
	 * Wraps a {@link DevServerHandler} with password enforcement and uniform
	 * error/response handling, then adapts it to the JDK {@link HttpHandler}.
	 */
	private final class GuardedHandler implements HttpHandler {
		private final DevServerHandler _delegate;

		GuardedHandler(DevServerHandler delegate) {
			_delegate = delegate;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			try {
				Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

				if (_password != null && !_password.equals(params.get("pw"))) {
					respond(exchange, 401, "Unauthorized");
					return;
				}

				_delegate.handle(params);
				respond(exchange, 200, "ok");
			}
			catch (Exception e) {
				ComponenteditorPlugin.getDefault().log(e);
				try {
					respond(exchange, 500, "error: " + e.getMessage());
				}
				catch (IOException ignored) {
					// Connection already gone — nothing useful to do.
				}
			}
			finally {
				exchange.close();
			}
		}
	}

	private static void respond(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	/**
	 * Parses a raw query string into decoded key/value pairs. Tolerates both
	 * {@code &} and the HTML-escaped {@code &amp;} as separators, because the
	 * two runtime code paths that build these URLs differ on which they use.
	 *
	 * @param rawQuery the raw (still-encoded) query string, or {@code null}
	 * @return a map of decoded parameter names to decoded values (never null)
	 */
	static Map<String, String> parseQuery(String rawQuery) {
		Map<String, String> result = new HashMap<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return result;
		}
		// Normalize the escaped separator to a plain one before splitting.
		String normalized = rawQuery.replace("&amp;", "&");
		for (String pair : normalized.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			if (eq == -1) {
				result.put(decode(pair), "");
			}
			else {
				result.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
			}
		}
		return result;
	}

	private static String decode(String s) {
		return URLDecoder.decode(s, StandardCharsets.UTF_8);
	}
}

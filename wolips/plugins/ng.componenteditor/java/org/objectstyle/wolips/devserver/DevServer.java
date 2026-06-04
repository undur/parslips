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
 *   http://localhost:9485/openJavaFile?app=APP&amp;className=FQCN&amp;lineNumber=N
 *   http://localhost:9485/openComponent?app=APP&amp;component=NAME
 *   http://localhost:9485/refresh?path=PATH
 *   http://localhost:9485/refreshProject?project=NAME[&amp;build=false]
 * </pre>
 *
 * <h2>Security</h2>
 * <b>Loopback-only.</b> The server binds to {@code 127.0.0.1}, never to all
 * interfaces. The original WOLips server bound to every interface, which
 * exposed an "open arbitrary files in my IDE" endpoint to the local network.
 * We never do that — and loopback binding <em>is</em> the security boundary.
 * Anything that can reach a server on the loopback interface is already
 * running code on the machine, at which point an IDE endpoint is the least of
 * one's worries.
 *
 * <p>For that reason there is no password. The original server (and Wonder's
 * exception page) used a {@code pw} query parameter, but on a loopback-only
 * server it was pure friction with no security benefit: both the IDE and the
 * runtime had to agree on a shared secret, and getting it wrong meant links
 * silently 401'd. We simply ignore any {@code pw} parameter that legacy
 * clients still send.
 *
 * <p>Note that query strings from the runtime may use either {@code &amp;} or
 * the HTML-escaped {@code &amp;amp;} as the parameter separator (the two
 * runtime code paths differ), so {@link #parseQuery} tolerates both.
 */
public class DevServer {

	/** Default port — matches the {@code wolips.port} default used by runtime clients. */
	public static final int DEFAULT_PORT = 9485;

	private final int _port;
	private HttpServer _httpServer;

	/**
	 * @param port the TCP port to listen on (loopback only)
	 */
	public DevServer(int port) {
		_port = port;
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

		_httpServer.createContext("/openJavaFile", new RequestHandler(new OpenJavaFileHandler()));
		_httpServer.createContext("/openComponent", new RequestHandler(new OpenComponentHandler()));
		_httpServer.createContext("/refresh", new RequestHandler(new RefreshHandler()));
		_httpServer.createContext("/refreshProject", new RequestHandler(new RefreshProjectHandler()));
		_httpServer.createContext("/validate", new RequestHandler(new ValidateComponentHandler()));
		_httpServer.createContext("/registerApp", new RequestHandler(new RegisterAppHandler()));
		_httpServer.createContext("/apps", new RequestHandler(new AppsHandler()));

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
	 * Adapts a {@link DevServerHandler} to the JDK {@link HttpHandler}, parsing
	 * the query string and applying uniform response/error handling. Any
	 * {@code pw} parameter that legacy clients send is simply ignored (see the
	 * class-level note on why there is no password).
	 */
	private final class RequestHandler implements HttpHandler {
		private final DevServerHandler _delegate;

		RequestHandler(DevServerHandler delegate) {
			_delegate = delegate;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			try {
				Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
				// A handler may return a response body (e.g. validation JSON); a null
				// return is the fire-and-forget case, answered with a plain "ok".
				String body = _delegate.handle(params);
				respond(exchange, 200, body != null ? body : "ok");
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
		// Advertise JSON when the body is one, so clients (and curl | jq) treat it
		// correctly; everything else is plain text.
		String contentType = looksLikeJson(body) ? "application/json; charset=utf-8" : "text/plain; charset=utf-8";
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private static boolean looksLikeJson(String body) {
		if (body == null || body.isEmpty()) {
			return false;
		}
		char first = body.charAt(0);
		return first == '{' || first == '[';
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

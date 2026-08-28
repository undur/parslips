package org.objectstyle.wolips.devserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code /watch} — the live spectator page: a single self-contained HTML page that polls
 * {@code /activity} every second and renders the request feed as narrated, human-readable
 * activity ("Validated Main — clean", "Launched MyApp — ready in 4.2s"). Point a browser
 * (or a projector) at it and watch an agent work the workspace.
 *
 * <p>The page lives as a resource next to this class ({@code watch.html}) rather than a
 * Java text block — it's a full page of markup and script, and a resource file keeps it
 * editable as HTML. Non-Java files in the source folder are copied into the bundle by
 * both PDE and Tycho (same mechanism as the {@code *.properties} resources), so
 * {@link Class#getResourceAsStream} finds it in every runtime.
 */
class WatchHandler implements DevServerHandler {

	@Override
	public String handle(Map<String, String> params) throws IOException {
		try (InputStream in = WatchHandler.class.getResourceAsStream("watch.html")) {
			if (in == null) {
				// Defensive: only reachable if the bundle was built without resource
				// copying — better a readable answer than an NPE-shaped 500.
				throw new IOException("watch.html missing from bundle");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}

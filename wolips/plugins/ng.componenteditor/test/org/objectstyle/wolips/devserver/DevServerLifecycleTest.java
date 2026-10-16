package org.objectstyle.wolips.devserver;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

import org.junit.Test;

/**
 * The dev server either answers on its port or doesn't hold it: a port that's bound with
 * nothing accepting on it makes every client hang, and blocks a restart with "port already in use".
 */
public class DevServerLifecycleTest {

	@Test
	public void startedServerAnswersAndStopReleasesThePort() throws Exception {
		final int port = freePort();
		final DevServer server = new DevServer(port);
		try {
			server.start();
		}
		catch (Throwable t) {
			// Starting needs the platform (DebugPlugin); where it's missing, the failure must not
			// leave the port bound.
			assertFalse(server.isRunning());
			assertTrue("a failed start must release port " + port, isFree(port));
			return;
		}
		try {
			final HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/").toURL().openConnection();
			connection.setConnectTimeout(2000);
			connection.setReadTimeout(5000);
			assertEquals(200, connection.getResponseCode());
		}
		finally {
			server.stop();
		}
		assertFalse(server.isRunning());
		assertTrue("stop must release port " + port, isFree(port));
	}

	@Test
	public void startOnAHeldPortFailsWithoutRunning() throws Exception {
		try (ServerSocket held = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			final DevServer server = new DevServer(held.getLocalPort());
			assertThrows(IOException.class, server::start);
			assertFalse(server.isRunning());
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
	}

	private static boolean isFree(int port) {
		try (ServerSocket socket = new ServerSocket()) {
			socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}
}

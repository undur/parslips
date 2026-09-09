package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The pure parts of {@link LaunchPorts}: reading a config's port from its arguments, and appending arguments. */
public class LaunchPortsTest {

	@Test
	public void programArgumentWins() {
		assertEquals(Integer.valueOf(1201), LaunchPorts.portFromArguments("-WOPort 1201 -WOHost localhost", "-DWOPort=1300"));
		assertEquals(Integer.valueOf(1202), LaunchPorts.portFromArguments("-mainClass Foo -WOPort 1202", ""));
	}

	@Test
	public void vmArgumentIsTheFallback() {
		assertEquals(Integer.valueOf(1300), LaunchPorts.portFromArguments("-mainClass Foo", "-Xmx1g -DWOPort=1300"));
	}

	@Test
	public void noPortMeansNull() {
		assertNull(LaunchPorts.portFromArguments("-mainClass Foo", "-Xmx1g"));
		assertNull(LaunchPorts.portFromArguments(null, null));
		// -WOPortX is not -WOPort
		assertNull(LaunchPorts.portFromArguments("-WOPortX 1", ""));
	}

	@Test
	public void appendArgumentsSpacesSanely() {
		assertEquals("-WOPort 1201", LaunchPorts.appendArguments("", "-WOPort 1201"));
		assertEquals("-WOPort 1201", LaunchPorts.appendArguments(null, " -WOPort 1201 "));
		assertEquals("-mainClass Foo -WOPort 1201", LaunchPorts.appendArguments("-mainClass Foo ", "-WOPort 1201"));
		assertEquals("-mainClass Foo", LaunchPorts.appendArguments("-mainClass Foo", ""));
	}

	@Test
	public void applicationLaunchIsThePrincipalClassOrAnExplicitPort() {
		// the app itself
		assertEquals(true, LaunchPorts.isApplicationLaunch("", "", "myapp.Application", "myapp.Application"));
		// a utility main in the same project: not the app, so no port decisions
		assertEquals(false, LaunchPorts.isApplicationLaunch("", "", "myapp.tools.Importer", "myapp.Application"));
		// an explicit port makes any launch an application launch
		assertEquals(true, LaunchPorts.isApplicationLaunch("-WOPort 1300", "", "myapp.tools.Server", "myapp.Application"));
		// no principalClass declared and no port: unknown, treated as not-the-app
		assertEquals(false, LaunchPorts.isApplicationLaunch("", "", "myapp.Application", null));
	}
}

package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Guards the workspace-free parts of {@link LaunchHandler}: the recovery hint a refused
 * launch carries (an agent acts on this text, so its shape is a contract) and the
 * preference key the prompt-free launch relies on.
 */
public class LaunchHandlerTest {

	@Test
	public void hintNamesACleanRebuildPerBrokenProject() {
		final String hint = LaunchHandler.brokenProjectsHint(List.of("my-framework", "my-model"));
		assertTrue(hint.contains("/refreshProject?project=my-framework&clean=true"));
		assertTrue(hint.contains("/refreshProject?project=my-model&clean=true"));
		assertTrue("the override must be named", hint.contains("ignoreErrors=true"));
	}

	@Test
	public void hintForASingleProjectHasNoSeparator() {
		final String hint = LaunchHandler.brokenProjectsHint(List.of("app"));
		assertTrue(hint.contains("/refreshProject?project=app&clean=true"));
		assertTrue(!hint.contains(" ; "));
	}

	@Test
	public void statusHandlerSwitchIsTheDebugFrameworksKey() {
		// The key is the debug framework's own (org.eclipse.debug.core's
		// PREF_ENABLE_STATUS_HANDLERS); a typo here would silently re-enable the dialogs.
		assertEquals("org.eclipse.debug.core", LaunchHandler.DEBUG_CORE_PREFS);
		assertEquals(LaunchHandler.DEBUG_CORE_PREFS + ".PREF_ENABLE_STATUS_HANDLERS", LaunchHandler.ENABLE_STATUS_HANDLERS);
	}
}

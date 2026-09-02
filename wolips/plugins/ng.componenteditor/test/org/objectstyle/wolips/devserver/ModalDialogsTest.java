package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Guards the pure parts of {@link ModalDialogs}: button-label matching (what
 * {@code /dialogs?press=...} resolves against) and the dialog JSON. The SWT-side listing
 * and pressing need a live Display and are exercised in Eclipse, not here.
 */
public class ModalDialogsTest {

	@Test
	public void stripRemovesMnemonicAmpersands() {
		assertEquals("Proceed", ModalDialogs.strip("&Proceed"));
		assertEquals("Skip All Breakpoints", ModalDialogs.strip("Skip &All Breakpoints"));
		assertEquals("Cancel", ModalDialogs.strip("  Cancel "));
		assertEquals("", ModalDialogs.strip(null));
	}

	@Test
	public void stripKeepsEscapedLiteralAmpersands() {
		// SWT writes a literal ampersand as "&&" — it must survive as one "&", and the
		// label's spaces must survive untouched.
		assertEquals("Save & Launch", ModalDialogs.strip("Save && Launch"));
	}

	@Test
	public void matchingIgnoresCaseMnemonicsAndEllipsis() {
		assertTrue(ModalDialogs.matches("&Proceed", "proceed"));
		assertTrue(ModalDialogs.matches("Browse...", "browse"));
		assertTrue(ModalDialogs.matches("Browse…", "Browse"));
		assertTrue(ModalDialogs.matches("OK", "ok"));
		assertFalse(ModalDialogs.matches("Cancel", "ok"));
		assertFalse(ModalDialogs.matches("Proceed", null));
	}

	@Test
	public void dialogJsonIsWellFormedWithAwkwardText() {
		final ModalDialogs.Dialog dialog = new ModalDialogs.Dialog(
				"Errors in Workspace",
				"Errors exist in required project(s):\n\"my-framework\"\nProceed with launch?",
				List.of("Proceed", "Cancel", "[ ] Always launch without asking"));
		final String json = dialog.toJson();
		IndexHandlerTest.assertWellFormed(json);
		assertTrue(json.contains("\"title\":\"Errors in Workspace\""));
		assertTrue("newlines must be escaped", json.contains("\\n"));
		assertTrue("quotes must be escaped", json.contains("\\\"my-framework\\\""));
		assertTrue(json.contains("\"buttons\":[\"Proceed\",\"Cancel\",\"[ ] Always launch without asking\"]"));
	}

	@Test
	public void snapshotJsonListsDialogs() {
		final ModalDialogs.Snapshot empty = new ModalDialogs.Snapshot(true, List.of());
		assertEquals("[]", empty.dialogsJson());

		final ModalDialogs.Snapshot two = new ModalDialogs.Snapshot(true, List.of(
				new ModalDialogs.Dialog("A", "", List.of("OK")),
				new ModalDialogs.Dialog("B", "", List.of("Yes", "No"))));
		IndexHandlerTest.assertWellFormed(two.dialogsJson());
		assertTrue(two.dialogsJson().startsWith("[{\"title\":\"A\""));
	}
}

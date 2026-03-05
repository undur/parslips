package org.objectstyle.wolips.componenteditor.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.keys.KeyBinding;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.keys.IBindingService;

import tk.eclipse.plugin.htmleditor.HTMLPlugin;

/**
 * Manages USER-level keybinding overrides that shadow WOLips' SYSTEM
 * keybindings, resolving conflicts with Parsley's keybindings.
 *
 * <p>When both plugins are installed, they register SYSTEM bindings for the
 * same key sequences (e.g. Cmd+Alt+1 for "switch to Java"). Eclipse shows
 * a disambiguation popup because two different commands claim the same key.
 *
 * <p>The fix uses the same mechanism as Eclipse's own Keys preference page:
 * <ol>
 *   <li>For each WOLips SYSTEM binding, create a USER binding with a null
 *       command (an "unbinding"). This tells Eclipse to suppress all SYSTEM
 *       bindings for that key+context combination.</li>
 *   <li>For shared shortcuts where Parsley has an equivalent command, also
 *       create a positive USER binding pointing to Parsley's command. This
 *       re-establishes the shortcut after the null unbinding killed it.</li>
 * </ol>
 *
 * <p>USER bindings are persisted to the workspace preference store by
 * {@code IBindingService.savePreferences()}, so they survive Eclipse
 * restarts without needing to re-apply on every startup.
 *
 * <p>WORKAROUND: WOLips coexistence.
 *
 * @see IBindingService
 */
public class WOLipsBindingShadow {

	/** Prefix for all WOLips command IDs. */
	private static final String WOLIPS_COMMAND_PREFIX = "org.objectstyle.wolips.";

	/** Prefix for all Parsley command IDs. */
	private static final String PARSLEY_COMMAND_PREFIX = "ng.componenteditor.";

	/** Default key scheme ID. */
	private static final String DEFAULT_SCHEME_ID =
		"org.eclipse.ui.defaultAcceleratorConfiguration";

	/**
	 * Map of WOLips command ID suffixes to their Parsley equivalents.
	 * When a WOLips binding uses a command whose suffix appears here,
	 * a positive USER binding is created for the Parsley command.
	 *
	 * <p>WOLips commands not in this map (e.g. topreview, towobuilder)
	 * are simply unbound with no replacement.
	 */
	private static final String[][] COMMAND_REPLACEMENTS = {
		// WOLips suffix → Parsley command ID
		{ "componenteditor.editors.tojava", "ng.componenteditor.editors.tojava" },
		{ "componenteditor.editors.tohtml", "ng.componenteditor.editors.tohtml" },
		{ "componenteditor.editors.towod", "ng.componenteditor.editors.towod" },
		{ "componenteditor.editors.toapi", "ng.componenteditor.editors.toapi" },
		{ "componenteditor.openComponent", "ng.componenteditor.openComponent" },
	};

	/**
	 * Creates USER-level overrides that shadow all WOLips SYSTEM keybindings,
	 * letting Parsley's bindings take over for shared shortcuts.
	 *
	 * <p>For each WOLips SYSTEM binding found:
	 * <ul>
	 *   <li>A null-command USER unbinding is created to suppress the SYSTEM
	 *       binding (and any other SYSTEM binding on the same key+context).</li>
	 *   <li>If a Parsley equivalent command exists, a positive USER binding
	 *       is created to re-establish the shortcut.</li>
	 * </ul>
	 *
	 * <p>The result is persisted, so it survives Eclipse restarts.
	 */
	public static void applyShadows() {
		try {
			IBindingService bindingService =
				PlatformUI.getWorkbench().getService(IBindingService.class);
			if (bindingService == null) {
				return;
			}

			ICommandService commandService =
				PlatformUI.getWorkbench().getService(ICommandService.class);
			if (commandService == null) {
				return;
			}

			Binding[] currentBindings = bindingService.getBindings();

			// First pass: collect WOLips SYSTEM bindings so we can
			// identify our shadow entries from previous runs.
			List<Binding> wolipsBindings = new ArrayList<>();
			for (Binding binding : currentBindings) {
				if (isWOLipsSystemBinding(binding)) {
					wolipsBindings.add(binding);
				}
			}

			// Second pass: build the new binding array.
			List<Binding> result = new ArrayList<>(currentBindings.length);
			int shadowCount = 0;

			for (Binding binding : currentBindings) {
				if (isWOLipsSystemBinding(binding)) {
					KeyBinding wolipsBinding = (KeyBinding) binding;
					KeySequence keySequence = wolipsBinding.getKeySequence();
					String contextId = wolipsBinding.getContextId();
					String schemeId = wolipsBinding.getSchemeId();
					if (schemeId == null) {
						schemeId = DEFAULT_SCHEME_ID;
					}

					// 1. Create a null-command unbinding to suppress the
					//    WOLips SYSTEM binding (and any other SYSTEM binding
					//    on this key+context).
					KeyBinding unbinding = new KeyBinding(
						keySequence,
						null,       // null command = unbinding/deletion marker
						schemeId,
						contextId,
						null, null, null,
						Binding.USER
					);
					result.add(unbinding);

					// 2. If Parsley has an equivalent command for this key
					//    sequence, create a positive USER binding so the
					//    shortcut still works.
					String parsleyCommandId = findParsleyReplacement(wolipsBinding);
					if (parsleyCommandId != null) {
						ParameterizedCommand parsleyCommand =
							commandService.deserialize(parsleyCommandId);
						KeyBinding replacement = new KeyBinding(
							keySequence,
							parsleyCommand,
							schemeId,
							contextId,
							null, null, null,
							Binding.USER
						);
						result.add(replacement);
					}

					shadowCount++;
				}
				else if (isOurShadowBinding(binding, wolipsBindings)) {
					// Skip any leftover shadow entries from previous runs
					// — we're regenerating them fresh.
				}
				else {
					result.add(binding);
				}
			}

			if (shadowCount == 0) {
				// No WOLips bindings found — WOLips may not be installed.
				return;
			}

			bindingService.savePreferences(
				bindingService.getActiveScheme(),
				result.toArray(new Binding[0])
			);
		}
		catch (Exception e) {
			HTMLPlugin.logException(e);
		}
	}

	/**
	 * Removes all shadow USER bindings created by {@link #applyShadows()},
	 * restoring WOLips' SYSTEM keybindings.
	 *
	 * <p>The null-command unbindings and positive Parsley USER bindings are
	 * removed from the binding set. On the next
	 * {@code IBindingService.savePreferences()} call, these entries are
	 * cleared from the workspace preference store. WOLips' SYSTEM bindings
	 * (from its plugin.xml) are re-read from the extension registry on
	 * restart, so they reappear automatically.
	 *
	 * <p>Note: the SYSTEM bindings are also immediately re-added to the
	 * in-memory binding set by including them in the array passed to
	 * {@code savePreferences()}.
	 */
	public static void removeShadows() {
		try {
			IBindingService bindingService =
				PlatformUI.getWorkbench().getService(IBindingService.class);
			if (bindingService == null) {
				return;
			}

			Binding[] currentBindings = bindingService.getBindings();

			// Collect WOLips SYSTEM bindings so we can identify our
			// null-command unbindings by matching key sequence + context.
			List<Binding> wolipsBindings = new ArrayList<>();
			for (Binding binding : currentBindings) {
				if (isWOLipsSystemBinding(binding)) {
					wolipsBindings.add(binding);
				}
			}

			List<Binding> cleaned = new ArrayList<>(currentBindings.length);
			int removedCount = 0;

			for (Binding binding : currentBindings) {
				if (isOurShadowBinding(binding, wolipsBindings)) {
					removedCount++;
				}
				else {
					cleaned.add(binding);
				}
			}

			if (removedCount == 0) {
				return;
			}

			bindingService.savePreferences(
				bindingService.getActiveScheme(),
				cleaned.toArray(new Binding[0])
			);
		}
		catch (Exception e) {
			HTMLPlugin.logException(e);
		}
	}

	/**
	 * Returns {@code true} if this is a WOLips SYSTEM keybinding
	 * (i.e. a binding from WOLips' plugin.xml extension point).
	 */
	private static boolean isWOLipsSystemBinding(Binding binding) {
		if (binding.getType() != Binding.SYSTEM) {
			return false;
		}
		if (!(binding instanceof KeyBinding)) {
			return false;
		}
		if (binding.getParameterizedCommand() == null) {
			return false;
		}
		String commandId = binding.getParameterizedCommand().getId();
		return commandId != null && commandId.startsWith(WOLIPS_COMMAND_PREFIX);
	}

	/**
	 * Returns {@code true} if this is a USER binding that we created as part
	 * of shadowing. This includes both null-command unbindings and positive
	 * Parsley replacement bindings.
	 *
	 * <p>We identify our bindings by checking:
	 * <ul>
	 *   <li>USER type (SYSTEM bindings are never ours)</li>
	 *   <li>Either a Parsley replacement command, or a null-command
	 *       unbinding that corresponds to a WOLips SYSTEM binding
	 *       in the current binding set</li>
	 * </ul>
	 *
	 * @param binding           the binding to check
	 * @param wolipsBindings    list of WOLips SYSTEM bindings currently in
	 *                          the binding set (used to match null-command
	 *                          unbindings). May be {@code null} if only
	 *                          checking positive bindings.
	 */
	private static boolean isOurShadowBinding(Binding binding,
			List<Binding> wolipsBindings) {
		if (binding.getType() != Binding.USER) {
			return false;
		}
		if (!(binding instanceof KeyBinding)) {
			return false;
		}

		ParameterizedCommand command = binding.getParameterizedCommand();

		if (command == null) {
			// Null-command USER binding — could be our unbinding.
			// Check if it matches the key sequence + context of a
			// WOLips SYSTEM binding. This is how we identify our
			// unbindings without a dedicated marker.
			if (wolipsBindings == null) {
				return false;
			}
			KeyBinding userBinding = (KeyBinding) binding;
			for (Binding wolips : wolipsBindings) {
				KeyBinding wolipsKey = (KeyBinding) wolips;
				if (userBinding.getKeySequence().equals(wolipsKey.getKeySequence())
						&& eq(userBinding.getContextId(), wolipsKey.getContextId())) {
					return true;
				}
			}
			return false;
		}

		// Positive USER binding pointing to a Parsley command
		String commandId = command.getId();
		if (commandId == null || !commandId.startsWith(PARSLEY_COMMAND_PREFIX)) {
			return false;
		}

		// Check if this Parsley command is one we use as a replacement
		for (String[] mapping : COMMAND_REPLACEMENTS) {
			if (commandId.equals(mapping[1])) {
				return true;
			}
		}

		return false;
	}

	/** Null-safe equality check. */
	private static boolean eq(Object a, Object b) {
		return (a == null) ? (b == null) : a.equals(b);
	}

	/**
	 * Finds the Parsley command ID that should replace a WOLips binding.
	 * Returns {@code null} if there is no Parsley equivalent (e.g. for
	 * WOLips-only commands like "switch to preview" or "switch to WOBuilder").
	 */
	private static String findParsleyReplacement(KeyBinding wolipsBinding) {
		String wolipsCommandId = wolipsBinding.getParameterizedCommand().getId();
		if (wolipsCommandId == null) {
			return null;
		}

		// Strip the "org.objectstyle.wolips." prefix and match against
		// known command suffixes
		String suffix = wolipsCommandId.substring(WOLIPS_COMMAND_PREFIX.length());
		for (String[] mapping : COMMAND_REPLACEMENTS) {
			if (suffix.equals(mapping[0])) {
				return mapping[1];
			}
		}

		return null;
	}
}

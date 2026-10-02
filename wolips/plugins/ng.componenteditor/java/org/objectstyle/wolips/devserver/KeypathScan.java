package org.objectstyle.wolips.devserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.locate.result.ElementDescriptor;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * Where templates reach a key of a CLASS through keypaths — {@code $team.playerCount} reaches
 * {@code Team.playerCount()} — for {@code /find?class=} and {@code /rename?class=}.
 *
 * <p>A component's own keys are found by the editor's refactoring code (first keypath segment,
 * in the component's own templates). A model class's keys are reached deeper, from any
 * component, so each keypath in each template is resolved against its component the way the
 * validator resolves it, and a segment counts when its member is one of the members
 * implementing the key on the class ({@code name()}, {@code getName()}, a field). Resolution,
 * not text, decides: {@code $team.name} matches {@code Team.name()}, and {@code $player.name}
 * doesn't.
 */
final class KeypathScan {

	private KeypathScan() {
	}

	/** {@code $a.b.c} in an HTML template (the prefix is the project's inline binding prefix). */
	private static Pattern inlineKeypath(String prefix) {
		return Pattern.compile(Pattern.quote(prefix) + "([A-Za-z_][\\w]*(?:\\.[A-Za-z_@][\\w]*)*)");
	}

	/** {@code binding = a.b.c;} in a .wod file. */
	private static final Pattern WOD_KEYPATH = Pattern.compile("=\\s*([A-Za-z_][\\w]*(?:\\.[A-Za-z_@][\\w]*)*)\\s*;");

	/** One template use: the file, its content, the offset of the matching segment, the component. */
	record Use(IFile file, String content, int offset, String component) {
	}

	/**
	 * The members implementing {@code key} on {@code type}: what the binding machinery resolves
	 * the key to (accessors and mutators, methods and fields), workspace sources only.
	 */
	static Set<IMember> members(IType type, String key) throws Exception {
		final Set<IMember> members = new LinkedHashSet<>();
		final List<BindingValueKey> keys = new ArrayList<>();
		keys.addAll(WodParserCache.getTypeCache().getBindingValueAccessorKeys(type.getJavaProject(), type, key));
		keys.addAll(WodParserCache.getTypeCache().getBindingValueMutatorKeys(type.getJavaProject(), type, key));
		for (final BindingValueKey bindingKey : keys) {
			final IMember member = bindingKey.getBindingMember();
			if (member != null && member.getElementType() != IJavaElement.TYPE) {
				members.add(member);
			}
		}
		return members;
	}

	/** Every template segment, in the projects, that resolves to one of the members. */
	static List<Use> uses(Set<IProject> projects, Set<IMember> members) throws Exception {
		return uses(projects, members, null);
	}

	/**
	 * As {@link #uses(Set, Set)}; with a {@code trace}, one entry per template scanned: the
	 * class its keypaths were resolved against and how many keypaths it held — for diagnosing
	 * a use the scan missed.
	 */
	static List<Use> uses(Set<IProject> projects, Set<IMember> members, List<JsonObject> trace) throws Exception {
		final List<Use> uses = new ArrayList<>();
		final Map<IFile, IType> componentTypes = new HashMap<>();
		TemplateScan.templates(projects, (file, content, isWod) -> {
			final IType component = componentTypes.computeIfAbsent(file, KeypathScan::componentTypeOf);
			final Pattern pattern = isWod ? WOD_KEYPATH : inlineKeypath(inlinePrefix(file.getProject()));
			final Matcher matcher = pattern.matcher(content);
			int keypaths = 0;
			final int before = uses.size();
			while (matcher.find()) {
				keypaths++;
				if (component != null) {
					try {
						addMatches(uses, file, content, matcher.group(1), matcher.start(1), component, members);
					}
					catch (final Exception e) {
						// One keypath that can't be resolved mustn't drop the file's others (it did:
						// $false failed, and TemplateScan skipped the whole template silently).
						org.objectstyle.wolips.componenteditor.ComponenteditorPlugin.getDefault().log(e);
					}
				}
			}
			if (trace != null && keypaths > 0) {
				trace.add(new JsonObject().put("file", file.getFullPath().toString())
						.put("class", component == null ? null : component.getFullyQualifiedName('.'))
						.put("keypaths", keypaths).put("matches", uses.size() - before));
			}
		});
		return uses;
	}

	private static void addMatches(List<Use> uses, IFile file, String content, String keypath, int keypathOffset, IType component, Set<IMember> members) throws Exception {
		final BindingValueKeyPath path = new BindingValueKeyPath(keypath, component, component.getJavaProject(), WodParserCache.getTypeCache());
		final BindingValueKey[] keys = path.getBindingKeys();
		int segmentOffset = keypathOffset;
		final String[] segments = keypath.split("\\.");
		for (int i = 0; i < segments.length && i < keys.length; i++) {
			if (members.contains(keys[i].getBindingMember())) {
				uses.add(new Use(file, content, segmentOffset, TemplateScan.owner(file)));
			}
			segmentOffset += segments[i].length() + 1;
		}
	}

	private static IType componentTypeOf(IFile file) {
		final ElementDescriptor descriptor = ElementDescriptor.forFile(file);
		return descriptor == null ? null : descriptor.getJavaType();
	}

	private static String inlinePrefix(IProject project) {
		final BuildProperties buildProperties = (BuildProperties) project.getAdapter(BuildProperties.class);
		return buildProperties != null ? buildProperties.getInlineBindingPrefix() : "$";
	}
}

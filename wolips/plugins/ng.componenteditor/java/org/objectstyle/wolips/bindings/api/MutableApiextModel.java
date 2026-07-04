package org.objectstyle.wolips.bindings.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.objectstyle.wolips.bindings.api.ApiextModel.Alternative;
import org.objectstyle.wolips.bindings.api.ApiextModel.Binding;
import org.objectstyle.wolips.bindings.api.ApiextModel.Choose;
import org.objectstyle.wolips.bindings.api.ApiextModel.Constraint;
import org.objectstyle.wolips.bindings.api.ApiextModel.Content;
import org.objectstyle.wolips.bindings.api.ApiextModel.Obligation;
import org.objectstyle.wolips.bindings.api.ApiextModel.Requires;
import org.objectstyle.wolips.bindings.api.ApiextModel.TypeRef;
import org.objectstyle.wolips.bindings.api.ApiextModel.UnknownAttributes;

/**
 * File-backed <em>mutable</em> model for editing an {@code .apiext} file — the editing counterpart to
 * the immutable {@link ApiextModel}, and the model the {@code .apiext} form editor holds and saves
 * through. Mirrors {@link MutableApiModel}: plain mutable POJOs (no listeners), a single
 * {@code _dirty} flag, load via the existing parser, save via {@link ApiextSerializer}.
 * <p>
 * The editor mutates the {@link MutableBinding}/{@link MutableConstraint} POJOs directly and calls
 * {@link #markAsDirty()} after each change. {@link #toImmutable()} produces an {@link ApiextModel}
 * (for the serializer, the validator, and message generation) so the mutable and immutable worlds
 * always agree.
 */
public class MutableApiextModel {

	// ---- mutable sub-structures (edited directly by the form) ----

	/** An editable binding: identity + typed pull/push, doc, required, default, deprecation. */
	public static final class MutableBinding {
		public String name = "";
		public final List<MutableType> pull = new ArrayList<>();
		public final List<MutableType> push = new ArrayList<>();
		public boolean required;
		public String defaultValue; // null = none
		public String doc; // null = none
		/** null = not deprecated; non-null (incl. "") = deprecated with this migration note. */
		public String deprecationNote;
		/** The transitional 'defaults' autocomplete-preset hint, or null. Preserved round-trip. */
		public String defaults;
	}

	/** An editable accepted type: the FQN/value-set name and an optional interpretation. */
	public static final class MutableType {
		public String name = "";
		public String interpretation; // null = none, e.g. "truthy"

		public MutableType() {
			// mutable
		}

		public MutableType(String name, String interpretation) {
			this.name = name;
			this.interpretation = interpretation;
		}
	}

	/** Marker base for the two editable constraint kinds. */
	public abstract static class MutableConstraint {
		public String message; // author override, null = generate
	}

	/** An editable {@code <choose>}: min/max + alternatives (each a set of binding names). */
	public static final class MutableChoose extends MutableConstraint {
		public Integer min;
		public Integer max;
		/** Each alternative is a list of binding names; size 1 = single binding, >1 = an any-of group. */
		public final List<List<String>> alternatives = new ArrayList<>();
	}

	/** An editable {@code <requires>}: consequent + obligation + antecedent (names, empty = unconditional). */
	public static final class MutableRequires extends MutableConstraint {
		public String binding = "";
		public Obligation must = Obligation.BOUND;
		/** Antecedent binding names (OR-combined); empty = unconditional. */
		public final List<String> when = new ArrayList<>();
	}

	// ---- model state ----

	private final File _file;
	private final IFile _eclipseFile;

	public String className = "";
	public Content content; // null = no declared policy (#22)
	public UnknownAttributes unknownAttributes; // null = no declared policy (#1)
	public String doc; // null = none
	public String deprecationNote; // element-level; null = not deprecated (#5)
	public final List<MutableBinding> bindings = new ArrayList<>();
	public final List<MutableConstraint> constraints = new ArrayList<>();

	private boolean _dirty;

	/** Loads a mutable model from an Eclipse {@code .apiext} file. */
	public MutableApiextModel(IFile file) throws ApiModelException {
		_eclipseFile = file;
		_file = file.getLocation().toFile();
		load();
	}

	/** Loads a mutable model from a plain {@code .apiext} file (for tests / non-workspace use). */
	public MutableApiextModel(File file) throws ApiModelException {
		_eclipseFile = null;
		_file = file;
		load();
	}

	private void load() throws ApiModelException {
		final byte[] bytes;
		try {
			bytes = _file.exists() ? Files.readAllBytes(_file.toPath()) : new byte[0];
		}
		catch (IOException e) {
			throw new ApiModelException("Failed to read .apiext file.", e);
		}
		final ApiextModel parsed = bytes.length == 0 ? null : ApiextModel.parse(bytes);
		if (parsed == null) {
			// Blank/unparseable — start an empty model named from the file (the editor's create path).
			className = fileBaseName();
			return;
		}
		copyFrom(parsed);
	}

	/** Fills this mutable model from a parsed immutable one. */
	private void copyFrom(ApiextModel m) {
		className = m.getClassName() != null ? m.getClassName() : "";
		content = m.getContent();
		unknownAttributes = m.getUnknownAttributes();
		doc = m.getDoc();
		deprecationNote = m.isDeprecated() ? (m.getDeprecationNote() == null ? "" : m.getDeprecationNote()) : null;

		for (final Binding b : m.getBindings()) {
			final MutableBinding mb = new MutableBinding();
			mb.name = b.getName() != null ? b.getName() : "";
			mb.required = b.isRequired();
			mb.defaultValue = b.getDefaultValue();
			mb.doc = b.getDoc();
			mb.deprecationNote = b.isDeprecated() ? (b.getDeprecationNote() == null ? "" : b.getDeprecationNote()) : null;
			mb.defaults = b.getDefaults();
			for (final TypeRef t : b.getPullTypes()) {
				mb.pull.add(new MutableType(t.getName(), t.getInterpretation()));
			}
			for (final TypeRef t : b.getPushTypes()) {
				mb.push.add(new MutableType(t.getName(), t.getInterpretation()));
			}
			bindings.add(mb);
		}

		for (final Constraint c : m.getConstraints()) {
			if (c instanceof Choose) {
				final Choose ch = (Choose) c;
				final MutableChoose mc = new MutableChoose();
				mc.min = ch.getMin();
				mc.max = ch.getMax();
				mc.message = ch.getMessage();
				for (final Alternative alt : ch.getAlternatives()) {
					mc.alternatives.add(new ArrayList<>(alt.getBindingNames()));
				}
				constraints.add(mc);
			}
			else if (c instanceof Requires) {
				final Requires r = (Requires) c;
				final MutableRequires mr = new MutableRequires();
				mr.binding = r.getBinding() != null ? r.getBinding() : "";
				mr.must = r.getMust();
				mr.message = r.getMessage();
				if (r.getAntecedent() != null) {
					mr.when.addAll(r.getAntecedent().getBindingNames());
				}
				constraints.add(mr);
			}
		}
	}

	/**
	 * Builds an immutable {@link ApiextModel} snapshot of the current editing state — for the
	 * serializer, the constraint validator, and generated messages. Empty names/types are carried as
	 * written; the validator reports any resulting problems.
	 */
	public ApiextModel toImmutable() {
		final List<Binding> bs = new ArrayList<>();
		for (final MutableBinding mb : bindings) {
			bs.add(new Binding(mb.name, toTypeRefs(mb.pull), toTypeRefs(mb.push), emptyToNull(mb.doc),
					mb.required, emptyToNull(mb.defaultValue), mb.deprecationNote, emptyToNull(mb.defaults)));
		}
		final List<Constraint> cs = new ArrayList<>();
		for (final MutableConstraint mc : constraints) {
			if (mc instanceof MutableChoose) {
				final MutableChoose ch = (MutableChoose) mc;
				final List<Alternative> alts = new ArrayList<>();
				for (final List<String> names : ch.alternatives) {
					alts.add(new Alternative(new ArrayList<>(names)));
				}
				cs.add(new Choose(ch.min, ch.max, alts, emptyToNull(ch.message)));
			}
			else if (mc instanceof MutableRequires) {
				final MutableRequires r = (MutableRequires) mc;
				final Alternative antecedent = r.when.isEmpty() ? null : new Alternative(new ArrayList<>(r.when));
				cs.add(new Requires(r.binding, r.must, antecedent, emptyToNull(r.message)));
			}
		}
		return ApiextModel.build(className, content, unknownAttributes, deprecationNote, doc, bs, cs);
	}

	private static List<TypeRef> toTypeRefs(List<MutableType> types) {
		final List<TypeRef> out = new ArrayList<>();
		for (final MutableType t : types) {
			if (t.name != null && !t.name.isEmpty()) {
				out.add(new TypeRef(t.name, emptyToNull(t.interpretation)));
			}
		}
		return out;
	}

	// ---- dirty + save ----

	public boolean isDirty() {
		return _dirty;
	}

	/** Call after any mutation so the editor enables Save. */
	public void markAsDirty() {
		_dirty = true;
	}

	/** The serialized XML for the current state — also what the read-only Source page shows. */
	public String toXml() {
		return ApiextSerializer.serialize(toImmutable());
	}

	/** Serializes the current state to the backing file and refreshes the workspace resource. */
	public void saveChanges() throws ApiModelException {
		if (_file == null) {
			throw new ApiModelException("Cannot save: no backing file.");
		}
		try {
			try (Writer writer = new FileWriter(_file)) {
				ApiextSerializer.serialize(toImmutable(), writer);
			}
			if (_eclipseFile != null) {
				try {
					_eclipseFile.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				}
				catch (CoreException e) {
					// non-fatal — the file was already written
				}
			}
			_dirty = false;
		}
		catch (IOException e) {
			throw new ApiModelException("Failed to save .apiext file.", e);
		}
	}

	private String fileBaseName() {
		final String n = _file.getName();
		final int dot = n.lastIndexOf('.');
		return dot > 0 ? n.substring(0, dot) : n;
	}

	private static String emptyToNull(String s) {
		return (s == null || s.isEmpty()) ? null : s;
	}
}

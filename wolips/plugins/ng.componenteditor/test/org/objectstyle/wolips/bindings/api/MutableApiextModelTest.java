package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableChoose;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableRequires;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableType;

/**
 * Tests for {@link MutableApiextModel} — the editor's mutable editing model: load from a file,
 * mutate, and produce an immutable {@link ApiextModel} / XML that round-trips, plus dirty tracking.
 */
public class MutableApiextModelTest {

	private static final String CHECKBOX =
			"<?xml version=\"1.0\"?><wodefinitions>"
			+ "<wo class=\"WOCheckBox\" wrapsContent=\"false\" unknownAttributes=\"passthrough\">"
			+ "<doc>A checkbox.</doc>"
			+ "<binding name=\"checked\"><pull><type interpretation=\"truthy\">java.lang.Object</type></pull>"
			+ "<push><type>java.lang.Boolean</type></push></binding>"
			+ "<binding name=\"value\"><pull><type>java.lang.Object</type></pull></binding>"
			+ "<choose min=\"1\" max=\"1\"><binding name=\"checked\"/><binding name=\"value\"/></choose>"
			+ "</wo></wodefinitions>";

	private static File writeTemp(String xml) throws IOException {
		final File f = File.createTempFile("test", ".apiext");
		f.deleteOnExit();
		try (FileWriter w = new FileWriter(f)) {
			w.write(xml);
		}
		return f;
	}

	// ---- load ---------------------------------------------------------------

	@Test
	public void loadsFixtureIntoMutableStructures() throws Exception {
		MutableApiextModel m = new MutableApiextModel(writeTemp(CHECKBOX));
		assertEquals("WOCheckBox", m.className);
		assertFalse(m.wrapsContent);
		assertEquals(ApiextModel.UnknownAttributes.PASSTHROUGH, m.unknownAttributes);
		assertEquals("A checkbox.", m.doc);
		assertEquals(2, m.bindings.size());
		assertEquals("checked", m.bindings.get(0).name);
		assertEquals("truthy", m.bindings.get(0).pull.get(0).interpretation);
		assertEquals(1, m.bindings.get(0).push.size());
		assertEquals(1, m.constraints.size());
		assertTrue(m.constraints.get(0) instanceof MutableChoose);
	}

	@Test
	public void blankFile_startsEmptyNamedFromFile() throws Exception {
		MutableApiextModel m = new MutableApiextModel(writeTemp(""));
		// name derived from the temp file's base name; no bindings/constraints.
		assertTrue(m.bindings.isEmpty());
		assertTrue(m.constraints.isEmpty());
		assertNull(m.unknownAttributes);
	}

	// ---- toImmutable / toXml round-trip -------------------------------------

	@Test
	public void toImmutable_matchesLoadedShape() throws Exception {
		MutableApiextModel m = new MutableApiextModel(writeTemp(CHECKBOX));
		ApiextModel im = m.toImmutable();
		assertEquals("WOCheckBox", im.getClassName());
		assertEquals(ApiextModel.UnknownAttributes.PASSTHROUGH, im.getUnknownAttributes());
		assertEquals(2, im.getBindings().size());
		assertEquals(1, im.getConstraints().size());
	}

	@Test
	public void editThenSerialize_reflectsEdits() throws Exception {
		MutableApiextModel m = new MutableApiextModel(writeTemp(CHECKBOX));

		// Rename a binding, mark another required, add a default, add a deprecation.
		m.bindings.get(1).name = "theValue";
		m.bindings.get(1).required = true;
		m.bindings.get(1).defaultValue = "42";
		m.bindings.get(0).deprecationNote = "use theValue";

		// Add a new binding with a pull type.
		MutableBinding nb = new MutableBinding();
		nb.name = "extra";
		nb.pull.add(new MutableType("java.lang.String", null));
		m.bindings.add(nb);

		// Add an unconditional requires.
		MutableRequires r = new MutableRequires();
		r.binding = "extra";
		r.must = ApiextModel.Obligation.GETTABLE;
		m.constraints.add(r);

		// Round-trip through XML.
		ApiextModel back = ApiextModel.parse(m.toXml().getBytes(StandardCharsets.UTF_8));
		assertEquals(3, back.getBindings().size());
		ApiextModel.Binding value = back.getBindings().stream().filter(b -> b.getName().equals("theValue")).findFirst().orElseThrow();
		assertTrue(value.isRequired());
		assertEquals("42", value.getDefaultValue());
		ApiextModel.Binding checked = back.getBindings().stream().filter(b -> b.getName().equals("checked")).findFirst().orElseThrow();
		assertTrue(checked.isDeprecated());
		assertEquals(2, back.getConstraints().size());
	}

	// ---- dirty + save -------------------------------------------------------

	@Test
	public void dirtyTracking() throws Exception {
		MutableApiextModel m = new MutableApiextModel(writeTemp(CHECKBOX));
		assertFalse(m.isDirty());
		m.markAsDirty();
		assertTrue(m.isDirty());
	}

	@Test
	public void saveChanges_writesAndClearsDirty() throws Exception {
		File f = writeTemp(CHECKBOX);
		MutableApiextModel m = new MutableApiextModel(f);
		m.className = "Renamed";
		m.markAsDirty();
		m.saveChanges();
		assertFalse(m.isDirty());
		// The file now contains the renamed class; re-loading yields it.
		String written = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		assertTrue(written.contains("class=\"Renamed\""));
		assertEquals("Renamed", new MutableApiextModel(f).className);
	}
}

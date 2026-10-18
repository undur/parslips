package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectstyle.wolips.bindings.api.ApiextGenerator.FoundBinding;
import org.objectstyle.wolips.bindings.api.MutableApiextModel.MutableBinding;

/**
 * {@link ApiextGenerator}: "Generate API" adds the bindings an {@code .apiext} lacks and never
 * touches the ones it has.
 */
public class ApiextGeneratorTest {

	@Rule
	public TemporaryFolder _temp = new TemporaryFolder();

	private MutableApiextModel model(String woBody) throws Exception {
		final File file = _temp.newFile("X.apiext");
		Files.writeString(file.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<wodefinitions><wo class=\"X\">" + woBody + "</wo></wodefinitions>");
		return new MutableApiextModel(file);
	}

	private static MutableBinding binding(MutableApiextModel model, String name) {
		return model.bindings.stream().filter(b -> b.name.equals(name)).findFirst().orElse(null);
	}

	@Test
	public void addsTypedPullBindingsToAnEmptyModel() throws Exception {
		final MutableApiextModel model = model("");
		final List<String> added = ApiextGenerator.addMissing(model, List.of(new FoundBinding("title", "java.lang.String", false), new FoundBinding("count", "int", false)));
		assertEquals(List.of("title", "count"), added);
		assertEquals("java.lang.String", binding(model, "title").pull.get(0).name);
		assertTrue(binding(model, "title").push.isEmpty());
		assertEquals("int", binding(model, "count").pull.get(0).name);
		assertTrue(model.isDirty());
	}

	@Test
	public void pushedBindingGetsThePushDirectionToo() throws Exception {
		final MutableApiextModel model = model("");
		ApiextGenerator.addMissing(model, List.of(new FoundBinding("selection", "java.lang.Object", true)));
		assertEquals("java.lang.Object", binding(model, "selection").pull.get(0).name);
		assertEquals("java.lang.Object", binding(model, "selection").push.get(0).name);
	}

	@Test
	public void existingBindingsAreLeftAsTheyAre() throws Exception {
		final MutableApiextModel model = model("<binding name=\"title\" required=\"true\"><pull><type>java.lang.CharSequence</type></pull><doc>Shown on top.</doc></binding>");
		final List<String> added = ApiextGenerator.addMissing(model, List.of(new FoundBinding("title", "java.lang.String", true), new FoundBinding("subtitle", "java.lang.String", false)));
		assertEquals(List.of("subtitle"), added);
		final MutableBinding title = binding(model, "title");
		assertTrue(title.required);
		assertEquals("java.lang.CharSequence", title.pull.get(0).name);
		assertTrue(title.push.isEmpty());
		assertEquals("Shown on top.", title.doc);
	}

	@Test
	public void nothingToAddLeavesTheModelClean() throws Exception {
		final MutableApiextModel model = model("<binding name=\"title\"><pull><type>java.lang.String</type></pull></binding>");
		assertTrue(ApiextGenerator.addMissing(model, List.of(new FoundBinding("title", "java.lang.String", false))).isEmpty());
		assertFalse(model.isDirty());
	}

	@Test
	public void unknownTypeAddsAnUntypedBinding() throws Exception {
		final MutableApiextModel model = model("");
		ApiextGenerator.addMissing(model, List.of(new FoundBinding("mystery", null, true)));
		assertTrue(binding(model, "mystery").pull.isEmpty());
		assertTrue(binding(model, "mystery").push.isEmpty());
	}
}

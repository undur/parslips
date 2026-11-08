package org.objectstyle.wolips.bindings.api;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ElementApiextCreator}: an element without a template gets its first {@code .apiext} from
 * its {@code .api}, or none at all when the {@code .api} has a rule that can't carry over.
 */
public class ElementApiextCreatorTest {

	private IProject _project;

	@Before
	public void createProject() throws Exception {
		_project = ResourcesPlugin.getWorkspace().getRoot().getProject("ElementApiextCreatorTest");
		if (!_project.exists()) {
			_project.create(new NullProgressMonitor());
		}
		_project.open(new NullProgressMonitor());
	}

	@After
	public void deleteProject() throws Exception {
		_project.delete(true, true, new NullProgressMonitor());
	}

	private IFile write(String name, String content) throws Exception {
		final IFile file = _project.getFile(name);
		file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, new NullProgressMonitor());
		return file;
	}

	@Test
	public void writesTheApiextFromTheApi() throws Exception {
		final IFile api = write("Number.api", "<?xml version=\"1.0\"?><wodefinitions><wo wocomponentcontent=\"false\" class=\"Number\">"
				+ "<binding name=\"value\"/><binding name=\"format\"/>"
				+ "<validation message=\"'value' is a required binding\"><unbound name=\"value\"/></validation></wo></wodefinitions>");
		final IFile apiext = _project.getFile("Number.apiext");

		assertEquals(List.of(), ElementApiextCreator.create(apiext, api));
		assertTrue(apiext.exists());
		final ApiextModel model = ApiextModel.parse(apiext.getContents().readAllBytes());
		assertEquals(List.of("value", "format"), model.getBindings().stream().map(ApiextModel.Binding::getName).toList());
		assertTrue(model.getBindings().get(0).isRequired());
	}

	@Test
	public void writesNothingWhenARuleCantCarryOver() throws Exception {
		final IFile api = write("Tree.api", "<?xml version=\"1.0\"?><wodefinitions><wo wocomponentcontent=\"false\" class=\"Tree\">"
				+ "<binding name=\"a\"/><binding name=\"b\"/><binding name=\"c\"/>"
				+ "<validation message=\"'a' and 'b', or 'c' must be bound\"><and><or><unbound name=\"a\"/><unbound name=\"b\"/></or><unbound name=\"c\"/></and></validation></wo></wodefinitions>");
		final IFile apiext = _project.getFile("Tree.apiext");

		final List<String> untranslatable = ElementApiextCreator.create(apiext, api);
		assertEquals(List.of("'a' and 'b', or 'c' must be bound"), untranslatable);
		assertFalse(apiext.exists());
		assertTrue(ElementApiextCreator.refusal(apiext, api, untranslatable).contains("Tree.api has rules with no .apiext form"));
	}

	@Test
	public void withoutAnApiTheApiextStartsEmpty() throws Exception {
		final IFile apiext = _project.getFile("Fresh.apiext");
		assertEquals(List.of(), ElementApiextCreator.create(apiext, null));
		assertTrue(apiext.exists());
	}
}

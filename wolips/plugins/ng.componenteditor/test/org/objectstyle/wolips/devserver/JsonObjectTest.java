package org.objectstyle.wolips.devserver;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * {@link JsonObject}: the builder the #6 endpoints answer through. Escaping and separators are
 * the point of it, so those are what's tested.
 */
public class JsonObjectTest {

	@Test
	public void keepsInsertionOrderAndTypes() {
		final String json = new JsonObject().put("name", "Main").put("count", 3).put("found", true).toString();
		assertEquals("{\"name\":\"Main\",\"count\":3,\"found\":true}", json);
		IndexHandlerTest.assertWellFormed(json);
	}

	@Test
	public void escapesStringsAndRendersNullAsNull() {
		final String json = new JsonObject().put("text", "say \"hi\"\n").put("missing", (String) null).toString();
		assertEquals("{\"text\":\"say \\\"hi\\\"\\n\",\"missing\":null}", json);
		IndexHandlerTest.assertWellFormed(json);
	}

	@Test
	public void nestsObjectsAndArrays() {
		final String json = new JsonObject()
				.put("files", new JsonObject().put("html", "/P/Main.html"))
				.put("tags", List.of("str", "string"))
				.put("items", List.of(new JsonObject().put("line", 4)))
				.put("numbers", Arrays.asList(1, 2))
				.toString();
		assertEquals("{\"files\":{\"html\":\"/P/Main.html\"},\"tags\":[\"str\",\"string\"],\"items\":[{\"line\":4}],\"numbers\":[1,2]}", json);
		IndexHandlerTest.assertWellFormed(json);
	}

	@Test
	public void putIfPresentSkipsNull() {
		assertEquals("{\"a\":\"x\"}", new JsonObject().put("a", "x").putIfPresent("b", null).toString());
	}

	@Test
	public void emptyObjectAndArray() {
		assertEquals("{}", new JsonObject().toString());
		assertEquals("{\"list\":[]}", new JsonObject().put("list", List.of()).toString());
	}

	@Test
	public void errorAndMissing() {
		assertEquals("{\"error\":\"missing required parameter 'component'\"}", JsonObject.missing("component"));
	}
}

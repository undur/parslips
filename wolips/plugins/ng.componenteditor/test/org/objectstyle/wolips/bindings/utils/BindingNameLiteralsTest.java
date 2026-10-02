package org.objectstyle.wolips.bindings.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * {@link BindingNameLiterals}: the bindings a non-synchronizing component reads by name are its
 * API (completion, /componentApi) and what a rename must change.
 */
public class BindingNameLiteralsTest {

	private static final String BADGE = """
			public class ClubBadge extends ERXComponent {
				public Team team() {
					return (Team)valueForBinding( "team" );
				}
				public boolean showName() {
					return booleanValueForBinding( "showName", true );
				}
				public boolean limited() {
					return hasBinding("limit") && intValueForBinding("limit", 0) > 0;
				}
				public void choose( Team t ) {
					setValueForBinding( t, "selection" );
					setValueForBinding( pick( t, 2 ), "picked" );
				}
				public String other() {
					return description( "notABinding" ) + valueForBinding( nameAtRuntime() );
				}
			}
			""";

	@Test
	public void namesInOrderOfFirstUse() {
		assertEquals(List.of("team", "showName", "limit", "selection", "picked"), List.copyOf(BindingNameLiterals.names(BADGE)));
	}

	@Test
	public void everyOccurrenceWithTheOffsetOfTheName() {
		final List<BindingNameLiterals.Occurrence> limits = BindingNameLiterals.find(BADGE).stream().filter(o -> o.name().equals("limit")).toList();
		assertEquals(2, limits.size());
		for (final BindingNameLiterals.Occurrence occurrence : limits) {
			assertEquals("limit", BADGE.substring(occurrence.offset(), occurrence.offset() + 5));
			assertEquals('"', BADGE.charAt(occurrence.offset() - 1));
		}
	}

	@Test
	public void otherStringLiteralsAndComputedNamesAreNotBindings() {
		final Set<String> names = BindingNameLiterals.names(BADGE);
		assertEquals(false, names.contains("notABinding"));
	}

	@Test
	public void noSourceNoNames() {
		assertEquals(Set.of(), BindingNameLiterals.names((String)null));
	}
}

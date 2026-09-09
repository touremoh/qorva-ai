package ai.qorva.core.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CVQueryParamsMergeTest {

	private static CVQueryParams params(List<String> skills, String location, Integer limit, String clarification) {
		return new CVQueryParams(
			skills, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, location, null, List.of(), limit,
			List.of(), List.of(), clarification, List.of(), null
		);
	}

	@Test
	void slotsLeftEmptyByTheFollowUpInheritThePreviousTurn() {
		// "show me the top 10 profiles" extracted a limit and nothing else, then asked for specifics
		var previous = params(List.of(), null, 10, "Which technology?");
		// "java development" extracts the skill but has no idea a top-10 was asked for
		var followUp = params(List.of("Java"), null, null, null);

		var merged = followUp.mergeOnto(previous);

		assertThat(merged.skills()).containsExactly("Java");
		assertThat(merged.limit()).isEqualTo(10);
	}

	@Test
	void valuesSetByTheFollowUpOverrideThePreviousTurn() {
		var previous = params(List.of("Java"), "Paris", 10, null);
		var followUp = params(List.of("Kotlin"), "Belgium", 20, null);

		var merged = followUp.mergeOnto(previous);

		assertThat(merged.skills()).containsExactly("Kotlin");
		assertThat(merged.location()).isEqualTo("Belgium");
		assertThat(merged.limit()).isEqualTo(20);
	}

	@Test
	void clarificationIsNeverInherited() {
		var previous = params(List.of(), null, 10, "Which technology?");
		var followUp = params(List.of("Java"), null, null, null);

		assertThat(followUp.mergeOnto(previous).needsClarification()).isFalse();
	}

	@Test
	void mergingOntoNothingIsANoOp() {
		var followUp = params(List.of("Java"), "Belgium", 20, null);

		assertThat(followUp.mergeOnto(null)).isEqualTo(followUp);
	}

	@Test
	void limitAloneIsNotASearchFilter() {
		// Otherwise a bare "show me the top 10 profiles" would look answerable and query the whole pool
		assertThat(params(List.of(), null, 10, null).hasAnyFilter()).isFalse();
		assertThat(params(List.of("Java"), null, null, null).hasAnyFilter()).isTrue();
		assertThat(params(List.of(), "Belgium", null, null).hasAnyFilter()).isTrue();
	}

	@Test
	void withoutClarificationClearsOnlyTheClarification() {
		var cleared = params(List.of("Java"), "Belgium", 10, "Which technology?").withoutClarification();

		assertThat(cleared.needsClarification()).isFalse();
		assertThat(cleared.skills()).containsExactly("Java");
		assertThat(cleared.location()).isEqualTo("Belgium");
		assertThat(cleared.limit()).isEqualTo(10);
	}
}

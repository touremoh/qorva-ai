package ai.qorva.core.dto.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtsRefMergeTest {

	private static AtsRef ref(String provider, String externalId, Instant importedAt) {
		return AtsRef.builder()
			.provider(provider).externalId(externalId).lastImportedAt(importedAt)
			.resumeContentHash(provider + "-hash").build();
	}

	@Test
	void linksToOtherAtsSurviveTheMerge() {
		var greenhouse = ref("greenhouse", "17681532", Instant.parse("2026-09-01T10:00:00Z"));
		var lever = ref("lever", "opp-9", Instant.parse("2026-08-20T10:00:00Z"));

		var merged = AtsRef.merge(List.of(greenhouse), List.of(lever));

		assertThat(merged).extracting(AtsRef::getProvider).containsExactly("greenhouse", "lever");
	}

	@Test
	void sameLinkOnBothSidesKeepsTheMostRecentlyImported() {
		var stale = ref("greenhouse", "17681532", Instant.parse("2026-08-01T10:00:00Z"));
		var fresh = ref("greenhouse", "17681532", Instant.parse("2026-09-01T10:00:00Z"));
		fresh.setResumeContentHash("fresh-hash");

		assertThat(AtsRef.merge(List.of(stale), List.of(fresh)))
			.singleElement()
			.extracting(AtsRef::getResumeContentHash).isEqualTo("fresh-hash");
		// Argument order must not change the winner.
		assertThat(AtsRef.merge(List.of(fresh), List.of(stale)))
			.singleElement()
			.extracting(AtsRef::getResumeContentHash).isEqualTo("fresh-hash");
	}

	@Test
	void sameProviderWithDifferentExternalIdsAreDistinctLinks() {
		var merged = AtsRef.merge(
			List.of(ref("greenhouse", "1", Instant.now())),
			List.of(ref("greenhouse", "2", Instant.now())));

		assertThat(merged).hasSize(2);
	}

	@Test
	void nullAndIncompleteRefsAreDropped() {
		assertThat(AtsRef.merge(null, null)).isNull();
		assertThat(AtsRef.merge(List.of(), null)).isNull();
		var incomplete = AtsRef.builder().provider("greenhouse").build();
		assertThat(AtsRef.merge(List.of(incomplete), null)).isNull();
	}

	@Test
	void aRefWithoutAnImportTimestampLosesToOneThatHasIt() {
		var never = ref("greenhouse", "1", null);
		var imported = ref("greenhouse", "1", Instant.parse("2026-09-01T10:00:00Z"));

		assertThat(AtsRef.merge(List.of(never), List.of(imported)))
			.singleElement()
			.extracting(AtsRef::getLastImportedAt).isNotNull();
	}
}

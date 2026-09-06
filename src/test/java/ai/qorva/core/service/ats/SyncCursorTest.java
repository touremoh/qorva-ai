package ai.qorva.core.service.ats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncCursorTest {

	@Test
	void nullOrBlankMeansFromTheBeginning() {
		assertThat(SyncCursor.parse(null).updatedAfter()).isNull();
		assertThat(SyncCursor.parse(null).page()).isEqualTo(1);
		assertThat(SyncCursor.parse("  ").page()).isEqualTo(1);
	}

	@Test
	void isoTimestampIsDurableCursorAtPageOne() {
		var parsed = SyncCursor.parse("2026-09-01T10:00:00Z");
		assertThat(parsed.updatedAfter()).isEqualTo("2026-09-01T10:00:00Z");
		assertThat(parsed.page()).isEqualTo(1);
	}

	@Test
	void pageTokenRoundTrips() {
		var token = SyncCursor.pageToken("2026-09-01T10:00:00Z", 7);
		var parsed = SyncCursor.parse(token);
		assertThat(parsed.updatedAfter()).isEqualTo("2026-09-01T10:00:00Z");
		assertThat(parsed.page()).isEqualTo(7);
	}

	@Test
	void pageTokenWithoutTimestampRoundTrips() {
		var parsed = SyncCursor.parse(SyncCursor.pageToken(null, 3));
		assertThat(parsed.updatedAfter()).isNull();
		assertThat(parsed.page()).isEqualTo(3);
	}
}

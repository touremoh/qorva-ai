package ai.qorva.core.service;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedServiceTest {

	private static final YearMonth NOW = YearMonth.of(2026, 7);

	@Test
	void resolveDateTokens_monthsAgo_yieldsYearMonth() {
		assertThat(DemoSeedService.resolveDateTokens("{\"from\":\"@M-3@\",\"to\":\"Present\"}", NOW))
			.isEqualTo("{\"from\":\"2026-04\",\"to\":\"Present\"}");
	}

	@Test
	void resolveDateTokens_monthsAgo_crossesYearBoundary() {
		assertThat(DemoSeedService.resolveDateTokens("\"@M-10@\"", NOW))
			.isEqualTo("\"2025-09\"");
	}

	@Test
	void resolveDateTokens_yearsAgo_yieldsBareYear() {
		assertThat(DemoSeedService.resolveDateTokens("{\"year\":\"@Y-4@\"}", NOW))
			.isEqualTo("{\"year\":\"2022\"}");
	}

	@Test
	void resolveDateTokens_multipleTokensAndPlainText_untouched() {
		var raw = "{\"a\":\"@M-0@\",\"b\":\"@Y-0@\",\"c\":\"2019-05\",\"d\":\"someone@M-example.com\"}";
		assertThat(DemoSeedService.resolveDateTokens(raw, NOW))
			.isEqualTo("{\"a\":\"2026-07\",\"b\":\"2026\",\"c\":\"2019-05\",\"d\":\"someone@M-example.com\"}");
	}
}

package ai.qorva.core.service;

import java.time.YearMonth;

/** Test access to the demo-seed date tokens, pinned to a fixed month so fixture dates never drift. */
public final class DemoFixtureTokens {

	public static final YearMonth FIXED_MONTH = YearMonth.of(2026, 9);

	private DemoFixtureTokens() {
	}

	public static String resolve(String rawJson) {
		return DemoSeedService.resolveDateTokens(rawJson, FIXED_MONTH);
	}
}

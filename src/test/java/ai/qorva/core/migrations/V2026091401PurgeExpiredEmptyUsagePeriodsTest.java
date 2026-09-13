package ai.qorva.core.migrations;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V2026091401PurgeExpiredEmptyUsagePeriodsTest {

	@Test
	void filterBuildsWithNullMatchesForAbsentCounters() {
		Document f = V2026091401PurgeExpiredEmptyUsagePeriods.filter(new Date());

		assertThat(f.get("currentPeriodEnd", Document.class)).containsKey("$lt");
		@SuppressWarnings("unchecked")
		List<Document> and = (List<Document>) f.get("$and");
		assertThat(and).hasSize(6);
		@SuppressWarnings("unchecked")
		List<Object> in = (List<Object>) and.get(0).get("features.screeningActions.consumed", Document.class).get("$in");
		assertThat(in).containsExactly(0, null);
		assertThat(f.toJson()).contains("\"$in\": [0, null]"); // serialises, i.e. the driver accepts it
	}
}

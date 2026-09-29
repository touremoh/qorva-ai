package ai.qorva.core.migrations;

import ai.qorva.core.it.AbstractIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The raw contact indexes are gone after start, the drop is idempotent, and the rollback restores them. */
class DropRawContactIndexesIntegrationTest extends AbstractIntegrationTest {

	@Autowired private MongoTemplate mongoTemplate;

	@Test
	void rawContactIndexes_areDropped_idempotently_andRollbackRestoresThem() {
		var db = mongoTemplate.getDb();
		var migration = new V2026092901DropRawContactIndexes();
		List<String> raw = List.of(V2026092901DropRawContactIndexes.EMAIL_INDEX, V2026092901DropRawContactIndexes.PHONE_INDEX);

		assertThat(indexNames()).as("Mongock ran at boot").doesNotContainAnyElementsOf(raw)
			.contains(V2026092801NormaliseContactKeys.EMAIL_KEY_INDEX, V2026092801NormaliseContactKeys.PHONE_KEY_INDEX);

		migration.execute(db); // already absent: must not throw
		assertThat(indexNames()).doesNotContainAnyElementsOf(raw);

		migration.rollback(db);
		assertThat(indexNames()).containsAll(raw);

		migration.execute(db);
		assertThat(indexNames()).doesNotContainAnyElementsOf(raw);
	}

	private List<String> indexNames() {
		var names = new ArrayList<String>();
		mongoTemplate.getDb().getCollection("cvs").listIndexes().forEach((Document index) -> names.add(index.getString("name")));
		return names;
	}
}

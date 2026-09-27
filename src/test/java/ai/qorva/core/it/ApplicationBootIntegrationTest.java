package ai.qorva.core.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole application boots against an empty database and every changeunit applies. */
class ApplicationBootIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void contextBoots_andMongockCreatesTheCollections() {
		var collections = mongoTemplate.getCollectionNames();

		assertThat(collections).contains(
			"users", "tenants", "cvs", "job_posts", "matching_reports", "notes",
			"candidate_outreach", "mailbox_connections", "mfa_challenges");
	}
}

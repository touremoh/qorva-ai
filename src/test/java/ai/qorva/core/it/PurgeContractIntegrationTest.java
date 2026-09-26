package ai.qorva.core.it;

import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.CandidateUpdateService;
import ai.qorva.core.service.DemoDataPurgeService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Tenant-wide deletes: clearing the resume library (API) and the demo purge run when a demo account
 * converts. Pins what each removes and keeps — per collection, for the tenant and for the other one —
 * and that no dependent document is left pointing at something that no longer exists.
 */
class PurgeContractIntegrationTest extends AbstractIntegrationTest {

	@Autowired private TwoTenantFixture fixture;
	@Autowired private MongoTemplate mongo;
	@Autowired private DemoDataPurgeService demoDataPurgeService;
	@Autowired private CandidateUpdateService candidateUpdateService;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
	}

	@Test
	void clearLibrary_removesTheLibraryAndWhatDerivesFromIt_only() throws Exception {
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		var bBefore = fixture.fingerprint(b.tenantId());

		var response = mvc.perform(post("/cvs/clear-library").header("Authorization", owner)).andReturn().getResponse();
		ContractSnapshots.assertMatches("purge.clearLibrary.response", response.getStatus(), response.getContentAsString());
		var preflight = mvc.perform(get("/cvs/clear-library/preflight").header("Authorization", owner)).andReturn().getResponse();
		ContractSnapshots.assertMatches("purge.clearLibrary.preflightAfter", preflight.getStatus(), preflight.getContentAsString());
		ContractSnapshots.assertMatches("purge.clearLibrary.countsAfter", 200, countsOf(a.tenantId()));

		assertThat(fixture.fingerprint(b.tenantId())).isEqualTo(bBefore);
		assertNoOrphans();
	}

	@Test
	void demoPurge_removesRecruitmentData_only() {
		var bBefore = fixture.fingerprint(b.tenantId());

		demoDataPurgeService.purgeAll(a.tenantId());

		ContractSnapshots.assertMatches("purge.demo.countsAfter", 200, countsOf(a.tenantId()));
		assertThat(fixture.fingerprint(b.tenantId())).isEqualTo(bBefore);
		assertNoOrphans();
	}

	/** D7: a CV's pending self-service update requests go with it (they used to be left behind). */
	@Test
	void cvDelete_alsoRemovesItsUpdateRequests() throws Exception {
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		TenantScope.runAs(a.tenantId(), () -> candidateUpdateService.createRequest(a.tenantId(), a.cvId(), "c@example.com", "en"));
		assertThat(mongo.getCollection("candidate_update_requests").countDocuments()).isEqualTo(1);

		mvc.perform(delete("/cvs/" + a.cvId()).header("Authorization", owner)).andReturn();

		assertThat(mongo.getCollection("candidate_update_requests").countDocuments()).isZero();
		assertNoOrphans();
	}

	/** Documents per collection for the tenant, as JSON. */
	private String countsOf(String tenantId) {
		var counts = new TreeMap<String, Long>();
		var filter = new Document("$or", List.of(new Document("tenantId", new ObjectId(tenantId)), new Document("tenantId", tenantId)));
		for (var name : mongo.getCollectionNames()) {
			if (name.startsWith("mongock") || name.startsWith("system.")) continue;
			counts.put(name, mongo.getCollection(name).countDocuments(filter));
		}
		return new Document(counts).toJson();
	}

	/** Every chat message, note, outreach row and update request points at something that exists. */
	private void assertNoOrphans() {
		assertThat(OrphanCheck.find(mongo)).as("dangling references").isEmpty();
	}
}

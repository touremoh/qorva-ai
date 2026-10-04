package ai.qorva.core.it;

import ai.qorva.core.dao.entity.BackgroundJob;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The matching run API up to the point where the model would be called: the plan's Top N choices, every
 * refusal of a run (no job, a Top N off the steps or above the plan, a closed job, a job already running),
 * and the recruiter's handling of outdated reports — filtering them and deleting them with their cascade.
 */
class MatchingRunApiIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;

	private TwoTenantFixture.SeededTenant a;
	private String owner;
	private String viewer;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
	}

	private String run(String jobIds, String topN) {
		return "{\"jobIds\":" + jobIds + (topN != null ? ",\"topN\":" + topN : "") + "}";
	}

	@Test
	void theDialogIsOfferedStepsOfFiveWithTenAsTheDefault() throws Exception {
		mvc.perform(get("/ai/matching-runs/options").header("Authorization", owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultTopN").value(10))
			.andExpect(jsonPath("$.allowedTopN", hasItems(5, 10)));
	}

	@Test
	void aRunIsRefusedWithoutJobsWithATopNOffTheStepsOrAboveThePlanAndForAClosedJob() throws Exception {
		var job = "[\"" + a.jobId() + "\"]";
		mvc.perform(post("/ai/matching-runs").header("Authorization", owner).contentType(JSON).content(run("[]", null)))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/ai/matching-runs").header("Authorization", owner).contentType(JSON).content(run(job, "12")))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/ai/matching-runs").header("Authorization", owner).contentType(JSON).content(run(job, "100")))
			.andExpect(status().isForbidden());

		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.jobId()))), Update.update("status", "closed"), "job_posts");
		mvc.perform(post("/ai/matching-runs").header("Authorization", owner).contentType(JSON).content(run(job, "10")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aJobAlreadyInAnActiveRunCannotBeQueuedAgain() throws Exception {
		mongo.insert(BackgroundJob.builder()
			.tenantId(a.tenantId())
			.type(BackgroundJob.TYPE_MATCHING)
			.jobIds(List.of(a.jobId()))
			.topN(10)
			.status(BackgroundJob.STATUS_RUNNING)
			// A live lease, so the worker of this test context leaves it alone.
			.leaseExpiresAt(Instant.now().plus(Duration.ofHours(1)))
			.createdAt(Instant.now())
			.build());

		mvc.perform(post("/ai/matching-runs").header("Authorization", owner).contentType(JSON)
				.content(run("[\"" + a.jobId() + "\"]", "10")))
			.andExpect(status().isConflict());
		mvc.perform(get("/ai/matching-runs").param("active", "true").header("Authorization", viewer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs.length()").value(1));
	}

	@Test
	void startingARunNeedsGenerateReport() throws Exception {
		mvc.perform(post("/ai/matching-runs").header("Authorization", viewer).contentType(JSON)
				.content(run("[\"" + a.jobId() + "\"]", "10")))
			.andExpect(status().isForbidden());
	}

	@Test
	void outdatedReportsCanBeListedApartAndDeletedWithTheirNotesAndChats() throws Exception {
		var outdated = a.reportIds().getFirst();
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(outdated))),
			new Update().set("outdated", true).set("outdatedReason", "RANKED_OUT").set("outdatedAt", new java.util.Date()),
			"matching_reports");

		mvc.perform(get("/matching-reports").header("Authorization", owner)
				.param("jobPostId", a.jobId()).param("outdated", "true").param("pageNumber", "0").param("pageSize", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].outdatedReason").value("RANKED_OUT"));

		mvc.perform(delete("/matching-reports/outdated").param("jobPostId", a.jobId()).header("Authorization", viewer))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/matching-reports/outdated").param("jobPostId", a.jobId()).header("Authorization", owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted").value(1));

		assertThat(mongo.exists(Query.query(Criteria.where("_id").is(new ObjectId(outdated))), "matching_reports")).isFalse();
		assertThat(mongo.count(Query.query(Criteria.where("jobPostId").is(new ObjectId(a.jobId()))), "matching_reports"))
			.isEqualTo(a.reportIds().size() - 1);
		assertThat(OrphanCheck.find(mongo)).as("dangling references after deleting outdated reports").isEmpty();
	}
}

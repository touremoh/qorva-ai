package ai.qorva.core.it;

import ai.qorva.core.service.MatchingStalenessService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smart flagging. A CV update queues the CV for the matching staleness check only when it changes what the
 * matching reports are computed from — never for a tag-only edit — and flags no job by itself. The sweep then
 * flags only the jobs whose results the CV actually changes: the jobs where it would enter the top N, and the
 * jobs where its existing report is now out of date. A CV whose embedding never arrives flags every open job.
 */
class CvRescreenFlagIntegrationTest extends AbstractIntegrationTest {

	private static final String FLAG = "matchingReportsNeeded";
	private static final double[] DIRECTION = {1, 0, 0, 0};
	private static final double[] OPPOSITE = {-1, 0, 0, 0};

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private MatchingStalenessService staleness;

	private TwoTenantFixture.SeededTenant a;
	private String owner;

	@BeforeEach
	void seedWithEveryJobAlreadyScreened() {
		a = fixture.reset().a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		// Jobs are created flagged and CVs queued; start from "matching is up to date, nothing pending".
		mongo.updateMulti(new Query(), new Update().set(FLAG, false).unset("matchingStaleReason"), "job_posts");
		mongo.updateMulti(new Query(), new Update().unset("matchCheckPending").unset("matchCheckPendingSince"), "cvs");
	}

	private long flaggedJobs() {
		return mongo.count(Query.query(Criteria.where(FLAG).is(true)), "job_posts");
	}

	private Document job(String id) {
		return mongo.findById(new ObjectId(id), Document.class, "job_posts");
	}

	private boolean pending(String cvId) {
		var cv = mongo.findById(new ObjectId(cvId), Document.class, "cvs");
		return Boolean.TRUE.equals(cv.getBoolean("matchCheckPending"));
	}

	private void patchCv(String body) throws Exception {
		mvc.perform(patch("/cvs/" + a.cvId()).header("Authorization", owner)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
	}

	private void embed(String collection, String id, double[] vector) {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))),
			Update.update("embedding", Arrays.stream(vector).boxed().toList()), collection);
	}

	/** A job matched before, every candidate eligible, whose top N any candidate at least {@code cutoff} similar enters. */
	private void matched(String jobId, double[] vector, double cutoff) {
		embed("job_posts", jobId, vector);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(jobId))),
			new Update().set("lastMatchedAt", new Date()).set("matchingCutoffScore", cutoff)
				.set("scoringRules.filterOpenToWork", false).unset("scoringRules.availabilityStatuses"), "job_posts");
	}

	private void sweepAfterTheGrace() {
		staleness.sweep(Instant.now().plusSeconds(120));
	}

	@Test
	void tagOnlyEditQueuesNothing() throws Exception {
		patchCv("{\"tags\":[\"shortlist\",\"java\"]}");

		assertThat(pending(a.cvId())).isFalse();
		assertThat(flaggedJobs()).isZero();
	}

	@Test
	void contentEditQueuesTheCvButFlagsNoJobByItself() throws Exception {
		patchCv("{\"tags\":[\"shortlist\"],\"candidateProfileSummary\":\"Updated summary.\"}");

		assertThat(pending(a.cvId())).isTrue();
		assertThat(flaggedJobs()).isZero();
	}

	@Test
	void anEditedCandidateFlagsTheJobWhereTheirReportIsNowStale() throws Exception {
		// Every job matched and far from the candidate, so only the report on the first job can be affected.
		a.jobIds().forEach(id -> matched(id, OPPOSITE, 0.99));
		embed("cvs", a.cvId(), DIRECTION);

		patchCv("{\"careerStartYear\":2009}");
		sweepAfterTheGrace();

		assertThat(job(a.jobId()).getString("matchingStaleReason")).isEqualTo("CANDIDATE_CHANGED");
		assertThat(flaggedJobs()).isEqualTo(1);
		assertThat(pending(a.cvId())).isFalse();
	}

	@Test
	void aNewCandidateFlagsOnlyTheJobsWhoseTopNItWouldEnter() {
		var close = a.jobIds().get(1);
		var far = a.jobIds().get(2);
		matched(close, DIRECTION, 0.9);
		matched(far, OPPOSITE, 0.6);
		embed("cvs", a.cvId(), DIRECTION);
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.cvId()))),
			new Update().set("matchCheckPending", true).set("matchCheckPendingSince", new Date()), "cvs");

		sweepAfterTheGrace();

		var flagged = job(close);
		assertThat(flagged.getBoolean(FLAG)).isTrue();
		assertThat(flagged.getString("matchingStaleReason")).isEqualTo("NEW_CANDIDATES");
		assertThat(flagged.getList("newCandidateIds", String.class)).containsExactly(a.cvId());
		assertThat(job(far).getBoolean(FLAG)).isFalse();
	}

	@Test
	void aCvStillWithinTheGraceIsNotComparedYet() throws Exception {
		a.jobIds().forEach(id -> matched(id, DIRECTION, 0.5));
		embed("cvs", a.cvId(), DIRECTION);

		patchCv("{\"careerStartYear\":2009}");
		staleness.sweep(Instant.now());

		assertThat(pending(a.cvId())).isTrue();
		assertThat(flaggedJobs()).isZero();
	}

	@Test
	void aCvWhoseEmbeddingNeverArrivesFlagsEveryOpenJob() {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.cvId()))),
			new Update().unset("embedding").set("matchCheckPending", true)
				.set("matchCheckPendingSince", Date.from(Instant.now().minusSeconds(3600))), "cvs");
		long openJobs = mongo.count(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))
			.and("status").is("open")), "job_posts");
		assertThat(openJobs).isPositive();

		staleness.sweep(Instant.now());

		assertThat(mongo.count(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))
			.and(FLAG).is(true)), "job_posts")).isEqualTo(openJobs);
		assertThat(pending(a.cvId())).isFalse();
	}
}

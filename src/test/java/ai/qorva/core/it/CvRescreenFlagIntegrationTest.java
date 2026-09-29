package ai.qorva.core.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A CV update re-flags the tenant's open jobs for re-screening only when it changes what the
 * matching reports are computed from — never for a tag-only edit.
 */
class CvRescreenFlagIntegrationTest extends AbstractIntegrationTest {

	private static final String FLAG = "matchingReportsNeeded";

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;

	private TwoTenantFixture.SeededTenant a;
	private String owner;

	@BeforeEach
	void seedWithEveryJobAlreadyScreened() {
		a = fixture.reset().a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		// Jobs are created flagged; start from "matching is up to date".
		mongo.updateMulti(new Query(), Update.update(FLAG, false), "job_posts");
	}

	private long flaggedJobs() {
		return mongo.count(Query.query(Criteria.where(FLAG).is(true)), "job_posts");
	}

	private void patchCv(String body) throws Exception {
		mvc.perform(patch("/cvs/" + a.cvId()).header("Authorization", owner)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
	}

	@Test
	void tagOnlyEditLeavesJobsAlone() throws Exception {
		patchCv("{\"tags\":[\"shortlist\",\"java\"]}");

		assertThat(flaggedJobs()).isZero();
	}

	@Test
	void contentEditFlagsOpenJobs() throws Exception {
		patchCv("{\"careerStartYear\":2009}");

		assertThat(flaggedJobs()).isPositive();
	}

	@Test
	void contentEditWithTagsStillFlagsOpenJobs() throws Exception {
		patchCv("{\"tags\":[\"shortlist\"],\"candidateProfileSummary\":\"Updated summary.\"}");

		assertThat(flaggedJobs()).isPositive();
	}
}

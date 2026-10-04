package ai.qorva.core.it;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.MatchingReportService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The candidate pipeline end to end: moving a candidate along it (permission, tenant, validation), the capped
 * history, what keeps the status (re-scoring, outdated marking), the list filter, the export column, the
 * Dashboard's recruiter metrics, and the "last synced" line recruiters see.
 */
class CandidatePipelineIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private MatchingReportService matchingReportService;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String owner;
	private String viewer;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
	}

	private org.springframework.test.web.servlet.ResultActions setStatus(String token, String reportId, String status) throws Exception {
		return mvc.perform(patch("/matching-reports/" + reportId + "/status").header("Authorization", token)
			.contentType(JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private MatchingReport report(String id) {
		return mongo.findById(new ObjectId(id), MatchingReport.class);
	}

	@Test
	void aRecruiterMovesACandidateAndTheMoveIsRecordedWithWhoAndWhen() throws Exception {
		setStatus(owner, a.reportId(), "shortlisted")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHORTLISTED"))
			.andExpect(jsonPath("$.statusHistory.length()").value(1))
			.andExpect(jsonPath("$.statusHistory[0].from").value("NEW"))
			.andExpect(jsonPath("$.statusHistory[0].by").value(a.ownerId()))
			.andExpect(jsonPath("$.statusHistory[0].via").value("APP"))
			.andExpect(jsonPath("$.statusChangedBy").value(a.ownerId()));

		// Setting the current status again is not a move.
		setStatus(owner, a.reportId(), "SHORTLISTED").andExpect(jsonPath("$.statusHistory.length()").value(1));
	}

	@Test
	void onlyAKnownStatusOnAReportOfTheTenantBySomeoneAllowedToModifyReports() throws Exception {
		setStatus(owner, a.reportId(), "MAYBE").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("error.report.status_invalid"));
		setStatus(viewer, a.reportId(), "SHORTLISTED").andExpect(status().isForbidden());
		setStatus(owner, b.reportId(), "SHORTLISTED").andExpect(status().isNotFound());
		setStatus(owner, "not-an-id", "SHORTLISTED").andExpect(status().isNotFound());
		assertThat(report(b.reportId()).getStatus()).isEqualTo("NEW");

		// The generic update route still doesn't exist for reports.
		mvc.perform(patch("/matching-reports/" + a.reportId()).header("Authorization", owner).contentType(JSON)
			.content("{\"status\":\"HIRED\"}"));
		assertThat(report(a.reportId()).getStatus()).isEqualTo("NEW");
	}

	@Test
	void theHistoryKeepsTheLastTwentyMoves() throws Exception {
		for (int i = 0; i < 22; i++) {
			setStatus(owner, a.reportId(), i % 2 == 0 ? "CONTACTED" : "SHORTLISTED").andExpect(status().isOk());
		}
		var history = report(a.reportId()).getStatusHistory();
		assertThat(history).hasSize(MatchingReport.STATUS_HISTORY_SIZE);
		assertThat(history.getLast().getStatus()).isEqualTo("SHORTLISTED");
	}

	@Test
	void reScoringAndOutdatedMarkingKeepTheStatus() throws Exception {
		setStatus(owner, a.reportId(), "INTERVIEWING").andExpect(status().isOk());

		TenantContextHolder.setTenantId(a.tenantId());
		try {
			var rescore = new MatchingReportDTO();
			rescore.setTenantId(a.tenantId());
			rescore.setMatchingReportDetails(new MatchingReportDetails());
			matchingReportService.updateOne(a.reportId(), rescore);
			matchingReportService.markOutdated(a.tenantId(), a.jobId(), Set.of(), ids -> ids.stream()
				.collect(java.util.stream.Collectors.toMap(id -> id, id -> "RANKED_OUT")));
		} finally {
			TenantContextHolder.clear();
		}

		var after = report(a.reportId());
		assertThat(after.getOutdated()).isTrue();
		assertThat(after.getStatus()).isEqualTo("INTERVIEWING");
		assertThat(after.getStatusHistory()).hasSize(1);
	}

	@Test
	void theListFiltersByStatusAndTheExportEndsWithTheStatus() throws Exception {
		setStatus(owner, a.reportIds().get(1), "REJECTED").andExpect(status().isOk());

		mvc.perform(get("/matching-reports").header("Authorization", viewer)
				.param("jobPostId", a.jobId()).param("status", "REJECTED").param("pageNumber", "0").param("pageSize", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].id").value(a.reportIds().get(1)));

		for (var format : new String[]{"global", "eu"}) {
			var csv = new String(mvc.perform(get("/matching-reports/export/csv").header("Authorization", owner)
					.param("jobPostId", a.jobId()).param("format", format))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
			var header = csv.lines().findFirst().orElseThrow();
			assertThat(header).as(format).endsWith(",qorva_status");
			assertThat(csv).as(format).contains(",REJECTED").contains(",NEW");
		}
	}

	@Test
	void theDashboardCountsEachRecruitersMovesAndTimeToShortlist() throws Exception {
		setStatus(owner, a.reportIds().get(0), "SHORTLISTED").andExpect(status().isOk());
		setStatus(owner, a.reportIds().get(1), "CONTACTED").andExpect(status().isOk());
		setStatus(owner, a.reportIds().get(1), "SHORTLISTED").andExpect(status().isOk());

		mvc.perform(get("/dashboard/pipeline").header("Authorization", viewer).param("jobPostId", a.jobId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentByStatus.SHORTLISTED").value(2))
			.andExpect(jsonPath("$.currentByStatus.HIRED").value(0))
			.andExpect(jsonPath("$.recruiters.length()").value(1))
			.andExpect(jsonPath("$.recruiters[0].by").value(a.ownerId()))
			.andExpect(jsonPath("$.recruiters[0].movesByStatus.SHORTLISTED").value(2))
			.andExpect(jsonPath("$.recruiters[0].totalMoves").value(3))
			.andExpect(jsonPath("$.recruiters[0].medianHoursToShortlist").isNumber());

		// Another tenant sees none of it.
		mvc.perform(get("/dashboard/pipeline").header("Authorization", fixture.bearer(b.ownerEmail(), b.tenantId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recruiters.length()").value(0))
			.andExpect(jsonPath("$.currentByStatus.SHORTLISTED").value(0));

		mvc.perform(get("/dashboard/pipeline").header("Authorization", owner)
				.param("from", "2026-01-01T00:00:00Z").param("to", "2025-01-01T00:00:00Z"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void recruitersSeeWhenTheAtsLastSyncedAndNothingWithoutAnAts() throws Exception {
		mvc.perform(get("/ats/sync-status").header("Authorization", viewer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connections.length()").value(0));

		var syncedAt = Instant.parse("2026-10-04T08:00:00Z");
		mongo.insert(AtsConnection.builder()
			.tenantId(a.tenantId()).provider("greenhouse").displayName("Greenhouse").status(AtsConnection.STATUS_CONNECTED)
			.encryptedCredentials("x").webhookSecret("y")
			.syncState(AtsConnection.SyncState.builder().lastSyncAt(syncedAt).lastSyncError(null).build())
			.createdAt(syncedAt).lastUpdatedAt(syncedAt).createdBy("test").lastUpdatedBy("test")
			.build());

		mvc.perform(get("/ats/sync-status").header("Authorization", viewer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connections.length()").value(1))
			.andExpect(jsonPath("$.connections[0].provider").value("greenhouse"))
			.andExpect(jsonPath("$.connections[0].lastSyncAt").exists())
			.andExpect(jsonPath("$.connections[0].encryptedCredentials").doesNotExist());
		mvc.perform(get("/ats/sync-status").header("Authorization", fixture.bearer(b.ownerEmail(), b.tenantId())))
			.andExpect(jsonPath("$.connections.length()").value(0));
		// The full connection list stays admin-only.
		mvc.perform(get("/ats/connections").header("Authorization", viewer)).andExpect(status().isForbidden());
	}
}

package ai.qorva.core.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pipeline board: a column per status with exact counts, New by best score and the others by latest move,
 * keyset pages that never repeat a card, the filters, moves refused when someone else moved the candidate first,
 * and the tenant boundary.
 */
class PipelineBoardIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private ObjectMapper objectMapper;

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

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private ResultActions move(String token, String reportId, String to, String expected) throws Exception {
		var body = expected == null ? "{\"status\":\"%s\"}".formatted(to) : "{\"status\":\"%s\",\"expectedStatus\":\"%s\"}".formatted(to, expected);
		return mvc.perform(patch("/matching-reports/" + reportId + "/status").header("Authorization", token).contentType(JSON).content(body));
	}

	private JsonNode column(JsonNode board, String status) {
		for (var column : board.path("columns")) {
			if (status.equals(column.path("status").asText())) return column;
		}
		throw new AssertionError("no column " + status);
	}

	private List<String> ids(JsonNode column) {
		var ids = new ArrayList<String>();
		column.path("items").forEach(card -> ids.add(card.path("id").asText()));
		return ids;
	}

	@Test
	void theBoardHasEveryStatusWithExactCountsAndNewByBestScore() throws Exception {
		var board = json(mvc.perform(get("/matching-reports/pipeline").header("Authorization", viewer)));

		assertThat(board.path("columns")).hasSize(8);
		var fresh = column(board, "NEW");
		assertThat(fresh.path("count").asLong()).isEqualTo(a.reportIds().size());
		// Seeded scores 82, 64, 41 in that order.
		assertThat(ids(fresh)).containsExactlyElementsOf(a.reportIds());
		assertThat(fresh.path("items").get(0).path("candidateName").asText()).isNotBlank();
		assertThat(fresh.path("items").get(0).path("score").asDouble()).isEqualTo(82.0);
		assertThat(fresh.path("nextCursor").isNull()).isTrue();
		assertThat(column(board, "HIRED").path("count").asLong()).isZero();
	}

	@Test
	void movedColumnsShowTheLatestMoveFirstWithWhoMovedIt() throws Exception {
		move(owner, a.reportIds().get(0), "SHORTLISTED", "NEW").andExpect(status().isOk());
		Thread.sleep(5);
		move(owner, a.reportIds().get(2), "SHORTLISTED", "NEW").andExpect(status().isOk());

		var board = json(mvc.perform(get("/matching-reports/pipeline").header("Authorization", owner)));
		var shortlisted = column(board, "SHORTLISTED");
		assertThat(shortlisted.path("count").asLong()).isEqualTo(2);
		assertThat(ids(shortlisted)).containsExactly(a.reportIds().get(2), a.reportIds().get(0));
		assertThat(shortlisted.path("items").get(0).path("lastMove").path("from").asText()).isEqualTo("NEW");
		assertThat(column(board, "NEW").path("count").asLong()).isEqualTo(1);
	}

	@Test
	void aColumnPagesWithACursorAndNeverRepeatsACardEvenWhenOneMovesAway() throws Exception {
		var first = json(mvc.perform(get("/matching-reports/pipeline/NEW").param("size", "1").header("Authorization", owner)));
		assertThat(ids(first)).containsExactly(a.reportIds().get(0));
		assertThat(first.path("count").asLong()).isEqualTo(3);

		// The card already shown moves away: the next page still starts after it.
		move(owner, a.reportIds().get(0), "CONTACTED", null).andExpect(status().isOk());
		var second = json(mvc.perform(get("/matching-reports/pipeline/NEW").param("size", "1")
			.param("cursor", first.path("nextCursor").asText()).header("Authorization", owner)));
		assertThat(ids(second)).containsExactly(a.reportIds().get(1));
		var third = json(mvc.perform(get("/matching-reports/pipeline/NEW").param("size", "1")
			.param("cursor", second.path("nextCursor").asText()).header("Authorization", owner)));
		assertThat(ids(third)).containsExactly(a.reportIds().get(2));
		assertThat(third.path("nextCursor").isNull()).isTrue();

		mvc.perform(get("/matching-reports/pipeline/NEW").param("cursor", "garbage").header("Authorization", owner))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/matching-reports/pipeline/MAYBE").header("Authorization", owner))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theBoardFiltersByJobNameAndOutdated() throws Exception {
		var name = mongo.findById(new ObjectId(a.reportIds().get(1)), org.bson.Document.class, "matching_reports")
			.get("candidateInfo", org.bson.Document.class).getString("candidateName");
		var byName = json(mvc.perform(get("/matching-reports/pipeline").param("q", name.substring(0, 4).toLowerCase())
			.header("Authorization", owner)));
		assertThat(ids(column(byName, "NEW"))).contains(a.reportIds().get(1));

		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.reportIds().get(2)))), Update.update("outdated", true), "matching_reports");
		var current = json(mvc.perform(get("/matching-reports/pipeline").param("hideOutdated", "true").header("Authorization", owner)));
		assertThat(column(current, "NEW").path("count").asLong()).isEqualTo(2);

		var otherJob = json(mvc.perform(get("/matching-reports/pipeline").param("jobPostId", new ObjectId().toHexString())
			.header("Authorization", owner)));
		assertThat(column(otherJob, "NEW").path("count").asLong()).isZero();
		mvc.perform(get("/matching-reports/pipeline").param("jobPostId", "nope").header("Authorization", owner))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aMoveIsRefusedWhenSomeoneElseMovedTheCandidateFirst() throws Exception {
		move(owner, a.reportId(), "INTERVIEWING", "NEW").andExpect(status().isOk());

		// A second recruiter still sees the card in New and drags it to Rejected.
		move(owner, a.reportId(), "REJECTED", "NEW")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("error.report.status_conflict"));
		assertThat(mongo.findById(new ObjectId(a.reportId()), org.bson.Document.class, "matching_reports").getString("status"))
			.isEqualTo("INTERVIEWING");
	}

	@Test
	void eachTenantSeesOnlyItsOwnBoardAndViewersCannotMoveCards() throws Exception {
		var other = json(mvc.perform(get("/matching-reports/pipeline").header("Authorization", fixture.bearer(b.ownerEmail(), b.tenantId()))));
		assertThat(ids(column(other, "NEW"))).doesNotContainAnyElementsOf(a.reportIds());

		move(viewer, a.reportId(), "SHORTLISTED", "NEW").andExpect(status().isForbidden());

		// The side panel reads one full report — only in its own tenant.
		mvc.perform(get("/matching-reports/" + a.reportId()).header("Authorization", viewer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.matchingReportDetails").exists());
		mvc.perform(get("/matching-reports/" + b.reportId()).header("Authorization", owner))
			.andExpect(status().isNotFound());
	}
}

package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.dto.common.SearchIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContextSerializerTest {

	private final ChatContextSerializer serializer = new ChatContextSerializer();

	@Test
	void dropsNoiseFieldsNullsAndIdentifiersFromTheCv() {
		var cv = CVDTO.builder()
			.id("cv-1").tenantId("t-1").createdBy("u").createdAt(Instant.now())
			.candidateProfileSummary("Senior Java engineer")
			.tags(List.of("java"))
			.searchIndex(new SearchIndex(null, List.of("java"), null, null))
			.interestsAndHobbies(List.of())
			.build();

		String json = serializer.serialize(cv);

		assertThat(json).contains("\"candidateProfileSummary\":\"Senior Java engineer\"").contains("\"tags\":[\"java\"]");
		assertThat(json).doesNotContain("cv-1", "t-1", "searchIndex", "createdAt", "createdBy", "interestsAndHobbies", "null");
	}

	@Test
	void outputIsDeterministicAndKeepsTheScoringRulesOnTheJob() {
		var rules = new ScoringRules();
		rules.setFilterOpenToWork(true);
		var job = JobPostDTO.builder().id("j").title("Backend dev").description("Build APIs").status("OPEN").scoringRules(rules).build();

		String first = serializer.serialize(job);
		String second = serializer.serialize(job);

		assertThat(first).isEqualTo(second).contains("Backend dev").contains("\"scoringRules\":{\"filterOpenToWork\":true}")
			.doesNotContain("\"id\"", "OPEN");
	}
}

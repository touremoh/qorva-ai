package ai.qorva.core.service;

import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.Strength;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateOutreachDraftServiceTest {

	@Test
	void topSkillsFlattensDedupesAndCaps() {
		var skills = List.of(
			new KeySkill("Backend", List.of("Java", "Spring", "Java", "")),
			new KeySkill("Data", List.of("SQL", "Kafka", "Spark", "Airflow", "dbt", "Python", "Scala")));

		var joined = CandidateOutreachDraftService.topSkills(skills);

		assertThat(joined.split(", ")).hasSize(8).doesNotContain("").containsOnlyOnce("Java");
		assertThat(joined).startsWith("Java, Spring, SQL");
	}

	@Test
	void strengthTitlesOnly() {
		var strengths = List.of(new Strength("Leads teams", "evidence", "high"), new Strength("", "e", "low"),
			new Strength("Ships", "e", "high"));

		assertThat(CandidateOutreachDraftService.strengthTitles(strengths)).isEqualTo("Leads teams; Ships");
		assertThat(CandidateOutreachDraftService.strengthTitles(null)).isEmpty();
	}

	@Test
	void temperatureFollowsModelFamily() {
		assertThat(CandidateOutreachDraftService.temperatureFor("gpt-5.6-terra")).isEqualTo(1.0);
		assertThat(CandidateOutreachDraftService.temperatureFor("gpt-4.1-mini")).isEqualTo(0.7);
	}
}

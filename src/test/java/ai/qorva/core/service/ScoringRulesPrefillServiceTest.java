package ai.qorva.core.service;

import ai.qorva.core.dto.common.ExperienceRequirements;
import ai.qorva.core.dto.common.IndustryPreferences;
import ai.qorva.core.dto.common.LocationPreferences;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.dto.common.ScoringWeight;
import ai.qorva.core.dto.common.SkillRequirement;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@ExtendWith(MockitoExtension.class)
class ScoringRulesPrefillServiceTest {

	@Mock
	private ChatClient chatClient;

	@Mock
	private UsageMonitoringService usageMonitoringService;

	private ScoringRulesPrefillService service;

	@BeforeEach
	void setUp() throws QorvaException {
		service = new ScoringRulesPrefillService(chatClient, usageMonitoringService);
	}

	@Test
	void sanitize_mapsLlmSynonymsToSchemaEnums() {
		// "low"/"high" and "lead" are common LLM outputs but NOT in the job_posts validator enums
		var rules = new ScoringRules();
		rules.setLocationPreferences(new LocationPreferences(List.of("UK"), true, "high"));
		rules.setIndustryPreferences(new IndustryPreferences(List.of("fintech"), "low"));
		rules.setExperienceRequirements(new ExperienceRequirements(6, 6, "lead"));

		var sanitized = service.sanitize(rules);

		assertThat(sanitized.getLocationPreferences().strictness()).isEqualTo("strict");
		assertThat(sanitized.getIndustryPreferences().strictness()).isEqualTo("relaxed");
		assertThat(sanitized.getExperienceRequirements().seniorityLevel()).isEqualTo("senior");
	}

	@Test
	void sanitize_unknownEnumValuesFallBackToDefaults() {
		var rules = new ScoringRules();
		rules.setLocationPreferences(new LocationPreferences(null, null, "whatever"));
		rules.setIndustryPreferences(new IndustryPreferences(null, null));
		rules.setExperienceRequirements(new ExperienceRequirements(null, null, "galactic"));

		var sanitized = service.sanitize(rules);

		assertThat(sanitized.getLocationPreferences().strictness()).isEqualTo("medium");
		assertThat(sanitized.getIndustryPreferences().strictness()).isEqualTo("medium");
		assertThat(sanitized.getExperienceRequirements().seniorityLevel()).isEqualTo("mid");
	}

	@Test
	void sanitize_renormalizesWeightsToOne() {
		var rules = new ScoringRules();
		rules.setSkills(List.of(
			new SkillRequirement("Java", "MANDATORY", 0.6, 5, true),
			new SkillRequirement("Kafka", "invalid_importance", 0.6, 2, null)));
		rules.setScoringWeight(new ScoringWeight(0.8, 0.8, 0.2, 0.2));

		var sanitized = service.sanitize(rules);

		double skillSum = sanitized.getSkills().stream().mapToDouble(SkillRequirement::weight).sum();
		assertThat(skillSum).isCloseTo(1.0, offset(0.02));
		assertThat(sanitized.getSkills().get(0).importance()).isEqualTo("mandatory");
		assertThat(sanitized.getSkills().get(1).importance()).isEqualTo("important");
		var w = sanitized.getScoringWeight();
		assertThat(w.skills() + w.experience() + w.location() + w.industry()).isCloseTo(1.0, offset(0.02));
	}
}

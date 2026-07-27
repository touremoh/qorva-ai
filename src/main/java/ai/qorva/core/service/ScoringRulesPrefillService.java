package ai.qorva.core.service;

import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.dto.common.ScoringWeight;
import ai.qorva.core.dto.common.SkillRequirement;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drafts a job post's ScoringRules from its title/description so the recruiter only
 * reviews and adjusts. Create-mode only (enforced by the caller): an update means a
 * human already invested in these rules. LLM output is sanitized — invalid enums are
 * dropped and weight distributions renormalized to 1.0.
 */
@Slf4j
@Service
public class ScoringRulesPrefillService {

	private static final Set<String> VALID_IMPORTANCE = Set.of("mandatory", "important", "nice_to_have");
	private static final Set<String> VALID_STRICTNESS = Set.of("low", "medium", "high");
	private static final Set<String> VALID_SENIORITY = Set.of("junior", "mid", "senior", "lead");
	private static final Set<String> VALID_AVAILABILITY = Set.of(
		"activelyLooking", "openButNotSearching", "notAvailable", "freelanceOnly");

	private final ChatClient chatClient;
	private final UsageMonitoringService usageMonitoringService;
	private final String promptTemplate;

	@Autowired
	public ScoringRulesPrefillService(ChatClient chatClient, UsageMonitoringService usageMonitoringService) throws QorvaException {
		this.chatClient = chatClient;
		this.usageMonitoringService = usageMonitoringService;
		this.promptTemplate = readPrompt();
	}

	public ScoringRules suggest(String tenantId, String title, String description) throws QorvaException {
		var rules = doSuggest(title, description);
		try {
			usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1);
		} catch (Exception e) {
			log.warn("Failed to meter scoring-rules suggestion for tenant {}", tenantId, e);
		}
		return rules;
	}

	private ScoringRules doSuggest(String title, String description) throws QorvaException {
		if (!StringUtils.hasText(description)) {
			throw new QorvaException("Job description is required",
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		var converter = new BeanOutputConverter<>(ScoringRules.class);
		var prompt = promptTemplate
			.replace("{job_title}", title != null ? title : "")
			.replace("{job_description}", description)
			.replace("{format}", converter.getFormat());

		var content = chatClient.prompt().user(prompt).call().content();
		if (!StringUtils.hasText(content)) {
			throw new QorvaException("Scoring rules suggestion failed",
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return sanitize(converter.convert(content));
	}

	/** LLM output is a draft, never trusted: drop invalid enums, renormalize weights. */
	ScoringRules sanitize(ScoringRules rules) {
		if (rules == null) return null;

		List<SkillRequirement> skills = rules.getSkills() == null ? List.of()
			: rules.getSkills().stream()
				.filter(s -> StringUtils.hasText(s.name()))
				.map(s -> new SkillRequirement(
					s.name(),
					s.importance() != null && VALID_IMPORTANCE.contains(s.importance().toLowerCase())
						? s.importance().toLowerCase() : "important",
					s.weight() != null && s.weight() > 0 ? s.weight() : 0.1,
					s.minYearsOfExperience() != null && s.minYearsOfExperience() >= 0 ? s.minYearsOfExperience() : 1,
					s.exactSkillOnly() != null ? s.exactSkillOnly() : false))
				.collect(Collectors.toList());
		double skillSum = skills.stream().mapToDouble(SkillRequirement::weight).sum();
		if (skillSum > 0) {
			skills = skills.stream()
				.map(s -> new SkillRequirement(s.name(), s.importance(),
					round2(s.weight() / skillSum), s.minYearsOfExperience(), s.exactSkillOnly()))
				.collect(Collectors.toList());
		}
		rules.setSkills(skills);

		if (rules.getExperienceRequirements() != null) {
			var exp = rules.getExperienceRequirements();
			var seniority = exp.seniorityLevel() != null && VALID_SENIORITY.contains(exp.seniorityLevel().toLowerCase())
				? exp.seniorityLevel().toLowerCase() : "mid";
			rules.setExperienceRequirements(new ai.qorva.core.dto.common.ExperienceRequirements(
				exp.minYearsOfExperience() != null && exp.minYearsOfExperience() >= 0 ? exp.minYearsOfExperience() : 0,
				exp.minRelevantYears() != null && exp.minRelevantYears() >= 0 ? exp.minRelevantYears() : 0,
				seniority));
		}
		if (rules.getLocationPreferences() != null) {
			var loc = rules.getLocationPreferences();
			rules.setLocationPreferences(new ai.qorva.core.dto.common.LocationPreferences(
				loc.allowedLocations() != null ? loc.allowedLocations() : List.of(),
				loc.remoteAllowed() != null ? loc.remoteAllowed() : false,
				normalizeStrictness(loc.strictness())));
		}
		if (rules.getIndustryPreferences() != null) {
			var ind = rules.getIndustryPreferences();
			rules.setIndustryPreferences(new ai.qorva.core.dto.common.IndustryPreferences(
				ind.preferredIndustries() != null ? ind.preferredIndustries() : List.of(),
				normalizeStrictness(ind.strictness())));
		}

		var weight = rules.getScoringWeight();
		double s = weight != null && weight.skills() != null ? Math.max(0, weight.skills()) : 0.5;
		double e = weight != null && weight.experience() != null ? Math.max(0, weight.experience()) : 0.3;
		double l = weight != null && weight.location() != null ? Math.max(0, weight.location()) : 0.1;
		double i = weight != null && weight.industry() != null ? Math.max(0, weight.industry()) : 0.1;
		double sum = s + e + l + i;
		if (sum <= 0) { s = 0.5; e = 0.3; l = 0.1; i = 0.1; sum = 1.0; }
		rules.setScoringWeight(new ScoringWeight(round2(s / sum), round2(e / sum), round2(l / sum), round2(i / sum)));

		if (rules.getAvailabilityStatuses() != null) {
			rules.setAvailabilityStatuses(rules.getAvailabilityStatuses().stream()
				.filter(VALID_AVAILABILITY::contains)
				.collect(Collectors.toList()));
		}
		return rules;
	}

	private String normalizeStrictness(String value) {
		return value != null && VALID_STRICTNESS.contains(value.toLowerCase()) ? value.toLowerCase() : "medium";
	}

	private double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private String readPrompt() throws QorvaException {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("prompts/Scoring_rules_prefill_prompt.md").getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new QorvaException("Cannot read scoring rules prefill prompt", e);
		}
	}
}

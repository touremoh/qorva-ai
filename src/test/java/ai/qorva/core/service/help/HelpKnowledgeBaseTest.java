package ai.qorva.core.service.help;

import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.enums.AtsProviderEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class HelpKnowledgeBaseTest {

	/** The help text is put in front of the model whole and may be shown to anyone: nothing internal belongs in it. */
	private static final List<Pattern> FORBIDDEN = List.of(
		Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]*(KEY|SECRET|TOKEN|URL|URI|MODEL|ENABLED|PASSWORD|ID)\\b"),
		Pattern.compile("(?i)\\bgpt-[0-9]"),
		Pattern.compile("(?i)https?://|\\bwww\\."),
		Pattern.compile("(?i)amazonaws|mongodb\\+srv|mongodb://"),
		Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
		Pattern.compile("(?i)\\bqorva\\.(ai|help|products)\\.[a-z]"),
		Pattern.compile("(?i)\\b(agent_runs|help_conversations|support_tickets|matching_reports|tenantId)\\b"),
		Pattern.compile("(?i)/api/|\\bGET /|\\bPOST /|\\bPATCH /"));

	private static QorvaProductProperties products() {
		var limits = FeatureLimits.builder().screeningActions(2000).aiResumeChats(500).talentIntelligenceQueries(1000)
			.agentRuns(100).emailTemplates(3).bulkUploadFiles(100).atsConnections(1).matchingTopNMax(10).build();
		var plan = new QorvaProductProperties.ProductPlanConfig("Starter", ProductFeatures.builder().seats(2).limits(limits).build());
		return new QorvaProductProperties(plan, plan, plan);
	}

	@Test
	void everyArticleParsesWithATitleAndKnownRoutesAndFlags() throws Exception {
		var articles = HelpKnowledgeBase.load();
		assertThat(articles).hasSizeGreaterThanOrEqualTo(10);
		for (var article : articles) {
			assertThat(article.body()).as(article.file()).startsWith("# ");
			assertThat(article.routes()).as(article.file()).allMatch(HelpLinks::isAllowed);
			assertThat(article.requires()).as(article.file()).allMatch("agent.rules"::equals);
		}
	}

	@Test
	void theHelpTextHoldsNothingInternal() throws Exception {
		for (var article : HelpKnowledgeBase.load()) {
			for (var pattern : FORBIDDEN) {
				assertThat(pattern.matcher(article.body()).find())
					.as("%s must not match %s", article.file(), pattern).isFalse();
			}
		}
	}

	@Test
	void theWholePromptStaysWellUnderItsBudget() {
		var kb = new HelpKnowledgeBase(loadQuietly(), products(), Set.of("agent.rules"));
		// ~4 characters per token: under 30k tokens leaves room for instructions, history and the answer.
		assertThat(kb.text().length()).isLessThan(120_000);
	}

	@Test
	void rulesAreLeftOutWhenTheFeatureIsOff() {
		var on = new HelpKnowledgeBase(loadQuietly(), products(), Set.of("agent.rules"));
		var off = new HelpKnowledgeBase(loadQuietly(), products(), Set.of());
		assertThat(on.articles()).anyMatch(a -> a.requires().contains("agent.rules"));
		assertThat(off.articles()).noneMatch(a -> a.requires().contains("agent.rules"));
		assertThat(on.version()).isNotEqualTo(off.version());
	}

	@Test
	void planLimitsAndTheAtsListAreGeneratedFromCode() {
		var text = new HelpKnowledgeBase(List.of(), products(), Set.of()).text();
		assertThat(text).contains("# Plan limits", "| Matching actions (screening actions) | 2,000 | 2,000 | 2,000 |", "| ATS connections | 1 | 1 | 1 |");
		for (var provider : AtsProviderEnum.values()) {
			assertThat(HelpKnowledgeBase.ATS_NAMES).containsKey(provider);
			assertThat(text).contains(HelpKnowledgeBase.ATS_NAMES.get(provider));
		}
	}

	@Test
	void frontMatterIsParsed() {
		var article = HelpKnowledgeBase.parse("x.md", "---\nroutes: [usage, settings.billing]\nrequires: [agent.rules]\n---\n# Title\n\nBody");
		assertThat(article.routes()).containsExactly("usage", "settings.billing");
		assertThat(article.requires()).containsExactly("agent.rules");
		assertThat(article.title()).isEqualTo("Title");
		assertThat(article.body()).isEqualTo("# Title\n\nBody");
	}

	private static List<HelpKnowledgeBase.Article> loadQuietly() {
		try {
			return HelpKnowledgeBase.load();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}

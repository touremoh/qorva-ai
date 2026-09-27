package ai.qorva.core.service;

import ai.qorva.core.dto.InsightTexts;
import ai.qorva.core.dto.LibraryQualityInsight;
import ai.qorva.core.dto.LibraryQualityInsight.Recommendation;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.dto.LibraryQualityReport.QualityIssue;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.InsightTranslationAgent;
import ai.qorva.core.service.orchestrators.LibraryQualityInsightAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The AI summary shown on the Library Quality page.
 * <p>
 * One English summary per tenant and report state (the "fingerprint"), translated on demand, so
 * users of the same tenant reading in different languages get the same advice. Nothing is
 * regenerated while the report's numbers are unchanged, and nothing runs until someone opens the
 * page — the report cache and the sidebar badge never trigger a model call.
 */
@Slf4j
@Service
public class LibraryQualityInsightService {

	static final int MAX_RECOMMENDATIONS = 4;

	private final LibraryQualityService libraryQualityService;
	private final LibraryQualityInsightAgent agent;
	private final InsightTranslationAgent translationAgent;

	/** tenantId → the summary of that tenant's current report. */
	private final LocalizedInsightCache<LibraryQualityInsight> cache =
		new LocalizedInsightCache<>(Duration.ofHours(24), 10_000);

	public LibraryQualityInsightService(LibraryQualityService libraryQualityService, LibraryQualityInsightAgent agent,
	                                    InsightTranslationAgent translationAgent) {
		this.libraryQualityService = libraryQualityService;
		this.agent = agent;
		this.translationAgent = translationAgent;
	}

	/** Empty when the library has no resumes — there is nothing to explain. */
	public Optional<LibraryQualityInsight> getInsight(String tenantId, String acceptLanguage) throws QorvaException {
		// Through the Spring proxy: served from the per-tenant report cache.
		var report = libraryQualityService.getReport(tenantId);
		if (report.totalCVs() == 0) {
			return Optional.empty();
		}
		return Optional.of(cache.get(tenantId, fingerprint(report), LocalizedInsightCache.normalizeLanguage(acceptLanguage),
			() -> generate(report), this::translate));
	}

	private LibraryQualityInsight generate(LibraryQualityReport report) throws QorvaException {
		long start = System.currentTimeMillis();
		var insight = sanitize(agent.generate(report), report);
		log.info("Library quality insight generated ({} recommendations, {} ms)",
			insight.recommendations().size(), System.currentTimeMillis() - start);
		return insight;
	}

	private LibraryQualityInsight translate(LibraryQualityInsight canonical, String language) throws QorvaException {
		var texts = new InsightTexts(canonical.headline(), canonical.explanation(),
			canonical.recommendations().stream().map(Recommendation::text).toList());
		var translated = translationAgent.translate(texts, language, QorvaErrorCodes.QUALITY_INSIGHT_UNAVAILABLE);
		var recommendations = new ArrayList<Recommendation>();
		for (int i = 0; i < canonical.recommendations().size(); i++) {
			recommendations.add(new Recommendation(translated.recommendations().get(i),
				canonical.recommendations().get(i).issueKey()));
		}
		return new LibraryQualityInsight(translated.headline(), translated.explanation(),
			List.copyOf(recommendations), language, canonical.generatedAt());
	}

	/** Keeps the model's output inside what the UI can act on: known open issues, bounded length. */
	static LibraryQualityInsight sanitize(LibraryQualityInsight.Draft draft, LibraryQualityReport report) {
		var openIssueKeys = report.issues().stream()
			.filter(issue -> !issue.dismissed())
			.map(QualityIssue::issueKey)
			.collect(Collectors.toSet());
		var recommendations = Optional.ofNullable(draft.recommendations()).orElse(List.of()).stream()
			.filter(r -> r != null && r.text() != null && !r.text().isBlank())
			.limit(MAX_RECOMMENDATIONS)
			.map(r -> new Recommendation(r.text().strip(),
				openIssueKeys.contains(r.issueKey()) ? r.issueKey() : null))
			.toList();
		return new LibraryQualityInsight(
			draft.headline().strip(),
			draft.explanation() == null ? "" : draft.explanation().strip(),
			recommendations,
			LocalizedInsightCache.DEFAULT_LANGUAGE,
			Instant.now());
	}

	/** Everything the summary talks about; the same fingerprint means the same summary is still true. */
	static String fingerprint(LibraryQualityReport report) {
		var issues = report.issues().stream()
			.sorted(Comparator.comparing(QualityIssue::issueKey))
			.map(i -> i.issueKey() + ":" + i.count() + ":" + i.dismissed())
			.collect(Collectors.joining(","));
		return String.join("|",
			String.valueOf(report.totalCVs()),
			String.valueOf(report.overallScore()),
			String.valueOf(report.completeness().score()),
			String.valueOf(report.freshness().score()),
			String.valueOf(report.uniqueness().score()),
			String.valueOf(report.parseConfidence().score()),
			issues);
	}
}

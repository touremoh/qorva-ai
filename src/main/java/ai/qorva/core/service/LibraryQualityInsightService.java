package ai.qorva.core.service;

import ai.qorva.core.dto.LibraryQualityInsight;
import ai.qorva.core.dto.LibraryQualityInsight.Recommendation;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.dto.LibraryQualityReport.QualityIssue;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.LibraryQualityInsightAgent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

	static final String DEFAULT_LANGUAGE = "en";
	/** The app's locales ({@code qorva-ai-app/src/i18n.js}); anything else is answered in English. */
	static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "fr", "de", "es", "pt", "it", "nl");
	static final int MAX_RECOMMENDATIONS = 4;

	private final LibraryQualityService libraryQualityService;
	private final LibraryQualityInsightAgent agent;

	/** tenantId → the summary of that tenant's current report. Per instance; a miss only costs one call. */
	private final Cache<String, Entry> cache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofHours(24))
		.maximumSize(10_000)
		.build();

	private record Entry(String fingerprint, LibraryQualityInsight canonical, Map<String, LibraryQualityInsight> translations) {}

	/** Carries a checked failure out of a cache loader. */
	private static final class LoadFailure extends RuntimeException {
		private final QorvaException cause;

		LoadFailure(QorvaException cause) {
			super(cause);
			this.cause = cause;
		}
	}

	public LibraryQualityInsightService(LibraryQualityService libraryQualityService, LibraryQualityInsightAgent agent) {
		this.libraryQualityService = libraryQualityService;
		this.agent = agent;
	}

	/** Empty when the library has no resumes — there is nothing to explain. */
	public Optional<LibraryQualityInsight> getInsight(String tenantId, String acceptLanguage) throws QorvaException {
		// Through the Spring proxy: served from the per-tenant report cache.
		var report = libraryQualityService.getReport(tenantId);
		if (report.totalCVs() == 0) {
			return Optional.empty();
		}
		var language = normalizeLanguage(acceptLanguage);
		var entry = currentEntry(tenantId, report);
		if (DEFAULT_LANGUAGE.equals(language)) {
			return Optional.of(entry.canonical());
		}
		try {
			return Optional.of(entry.translations().computeIfAbsent(language, lang -> translate(entry.canonical(), lang)));
		} catch (LoadFailure e) {
			// The English summary is still better than none; the next view retries the translation.
			return Optional.of(entry.canonical());
		}
	}

	/** The tenant's summary for this report state; concurrent callers on a stale entry wait for one generation. */
	private Entry currentEntry(String tenantId, LibraryQualityReport report) throws QorvaException {
		var fingerprint = fingerprint(report);
		try {
			return cache.asMap().compute(tenantId, (key, existing) ->
				existing != null && existing.fingerprint().equals(fingerprint) ? existing : generate(report, fingerprint));
		} catch (LoadFailure e) {
			throw e.cause;
		}
	}

	private Entry generate(LibraryQualityReport report, String fingerprint) {
		try {
			long start = System.currentTimeMillis();
			var draft = agent.generate(report);
			var insight = sanitize(draft, report);
			log.info("Library quality insight generated ({} recommendations, {} ms)",
				insight.recommendations().size(), System.currentTimeMillis() - start);
			return new Entry(fingerprint, insight, new ConcurrentHashMap<>());
		} catch (QorvaException e) {
			throw new LoadFailure(e);
		}
	}

	private LibraryQualityInsight translate(LibraryQualityInsight canonical, String language) {
		try {
			var texts = new LibraryQualityInsight.Texts(canonical.headline(), canonical.explanation(),
				canonical.recommendations().stream().map(Recommendation::text).toList());
			var translated = agent.translate(texts, language);
			var recommendations = new ArrayList<Recommendation>();
			for (int i = 0; i < canonical.recommendations().size(); i++) {
				recommendations.add(new Recommendation(translated.recommendations().get(i),
					canonical.recommendations().get(i).issueKey()));
			}
			return new LibraryQualityInsight(translated.headline(), translated.explanation(),
				List.copyOf(recommendations), language, canonical.generatedAt());
		} catch (QorvaException e) {
			throw new LoadFailure(e);
		}
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
			DEFAULT_LANGUAGE,
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

	/** {@code fr-FR,fr;q=0.9} → {@code fr}; unsupported or missing → {@code en}. */
	static String normalizeLanguage(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return DEFAULT_LANGUAGE;
		}
		var first = acceptLanguage.split(",")[0].split(";")[0].trim();
		var code = Locale.forLanguageTag(first).getLanguage().toLowerCase(Locale.ROOT);
		return SUPPORTED_LANGUAGES.contains(code) ? code : DEFAULT_LANGUAGE;
	}
}

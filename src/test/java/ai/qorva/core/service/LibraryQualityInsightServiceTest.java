package ai.qorva.core.service;

import ai.qorva.core.dto.LibraryQualityInsight;
import ai.qorva.core.dto.LibraryQualityInsight.DraftRecommendation;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.dto.LibraryQualityReport.DimensionScore;
import ai.qorva.core.dto.LibraryQualityReport.QualityIssue;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.LibraryQualityInsightAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryQualityInsightServiceTest {

	private static final String TENANT = "tenant-1";

	private LibraryQualityService reports;
	private LibraryQualityInsightAgent agent;
	private LibraryQualityInsightService service;

	@BeforeEach
	void setUp() throws QorvaException {
		reports = mock(LibraryQualityService.class);
		agent = mock(LibraryQualityInsightAgent.class);
		service = new LibraryQualityInsightService(reports, agent);
		when(agent.generate(any())).thenReturn(draft());
		when(agent.translate(any(), eq("fr"))).thenReturn(new LibraryQualityInsight.Texts(
			"Titre", "Explication", List.of("Relancer l'analyse", "Archiver")));
	}

	@Test
	void emptyLibrary_hasNoInsightAndCallsNoModel() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(0, 3));

		assertThat(service.getInsight(TENANT, "en")).isEmpty();
		verify(agent, never()).generate(any());
	}

	@Test
	void sameReport_isGeneratedOnce() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));

		service.getInsight(TENANT, "en");
		service.getInsight(TENANT, "en-US,en;q=0.9");

		verify(agent, times(1)).generate(any());
	}

	@Test
	void changedReport_isRegenerated_andTranslationsDropped() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));
		service.getInsight(TENANT, "fr");

		when(reports.getReport(TENANT)).thenReturn(report(100, 4));
		service.getInsight(TENANT, "fr");

		verify(agent, times(2)).generate(any());
		verify(agent, times(2)).translate(any(), eq("fr"));
	}

	@Test
	void otherLanguage_translatesTheSameAdvice_withoutRegenerating() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));

		var english = service.getInsight(TENANT, "en").orElseThrow();
		var french = service.getInsight(TENANT, "fr-FR").orElseThrow();
		service.getInsight(TENANT, "fr");

		verify(agent, times(1)).generate(any());
		verify(agent, times(1)).translate(any(), eq("fr"));
		assertThat(french.language()).isEqualTo("fr");
		assertThat(french.headline()).isEqualTo("Titre");
		assertThat(french.recommendations()).extracting(LibraryQualityInsight.Recommendation::issueKey)
			.containsExactlyElementsOf(english.recommendations().stream().map(LibraryQualityInsight.Recommendation::issueKey).toList());
	}

	@Test
	void failedTranslation_fallsBackToEnglish() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));
		when(agent.translate(any(), eq("de"))).thenThrow(unavailable());

		var insight = service.getInsight(TENANT, "de").orElseThrow();

		assertThat(insight.language()).isEqualTo("en");
	}

	@Test
	void failedGeneration_isReported() throws QorvaException {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));
		when(agent.generate(any())).thenThrow(unavailable());

		assertThatThrownBy(() -> service.getInsight(TENANT, "en"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.QUALITY_INSIGHT_UNAVAILABLE);
	}

	@Test
	void concurrentColdRequests_generateOnce() throws Exception {
		when(reports.getReport(TENANT)).thenReturn(report(100, 3));
		var release = new CountDownLatch(1);
		when(agent.generate(any())).thenAnswer(invocation -> {
			release.await(2, TimeUnit.SECONDS);
			return draft();
		});
		var pool = Executors.newFixedThreadPool(4);
		try {
			var futures = new ArrayList<Future<?>>();
			for (int i = 0; i < 4; i++) {
				futures.add(pool.submit(() -> service.getInsight(TENANT, "en")));
			}
			release.countDown();
			for (var future : futures) {
				future.get(5, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		verify(agent, times(1)).generate(any());
	}

	@Test
	void sanitize_dropsUnknownAndDismissedIssueKeys_andCapsTheList() {
		var report = new LibraryQualityReport(100, 70, dim(70), dim(70), dim(70), dim(70), List.of(
			new QualityIssue("MISSING_EMAIL", "HIGH", 12, false),
			new QualityIssue("OUTDATED", "MEDIUM", 30, true)));
		var draft = new LibraryQualityInsight.Draft(" Headline ", "Why", List.of(
			new DraftRecommendation("Re-analyze", "MISSING_EMAIL"),
			new DraftRecommendation("Archive", "OUTDATED"),
			new DraftRecommendation("Invented", "NOT_AN_ISSUE"),
			new DraftRecommendation(" ", "MISSING_EMAIL"),
			new DraftRecommendation("Four", null),
			new DraftRecommendation("Five", null)));

		var insight = LibraryQualityInsightService.sanitize(draft, report);

		assertThat(insight.headline()).isEqualTo("Headline");
		assertThat(insight.recommendations()).hasSize(4);
		assertThat(insight.recommendations()).extracting(LibraryQualityInsight.Recommendation::issueKey)
			.containsExactly("MISSING_EMAIL", null, null, null);
	}

	@Test
	void normalizeLanguage_keepsSupportedPrimaryTags() {
		assertThat(LibraryQualityInsightService.normalizeLanguage("fr-FR,fr;q=0.9")).isEqualTo("fr");
		assertThat(LibraryQualityInsightService.normalizeLanguage("NL")).isEqualTo("nl");
		assertThat(LibraryQualityInsightService.normalizeLanguage("ja")).isEqualTo("en");
		assertThat(LibraryQualityInsightService.normalizeLanguage(null)).isEqualTo("en");
	}

	private static QorvaException unavailable() {
		return QorvaErrors.of(QorvaErrorCodes.QUALITY_INSIGHT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
	}

	private static LibraryQualityInsight.Draft draft() {
		return new LibraryQualityInsight.Draft("Good library", "Mostly complete.", List.of(
			new DraftRecommendation("Re-analyze resumes without email", "MISSING_EMAIL"),
			new DraftRecommendation("Archive outdated resumes", null)));
	}

	private static LibraryQualityReport report(long totalCVs, long missingEmails) {
		return new LibraryQualityReport(totalCVs, totalCVs == 0 ? null : 80, dim(80), dim(75), dim(90), dim(85),
			totalCVs == 0 ? List.of() : List.of(new QualityIssue("MISSING_EMAIL", "HIGH", missingEmails, false)));
	}

	private static DimensionScore dim(int score) {
		return new DimensionScore(score, List.of());
	}
}

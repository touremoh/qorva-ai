package ai.qorva.core.service;

import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LibraryQualityServiceTest {

	private static final String TENANT_ID = new ObjectId().toHexString();

	@Mock
	private CVRepository cvRepository;

	private ExecutorService executor;
	private LibraryQualityService service;

	@BeforeEach
	void setUp() {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		service = new LibraryQualityService(cvRepository, executor);
	}

	@AfterEach
	void tearDown() {
		executor.close();
	}

	@Test
	void getReport_emptyLibrary_returnsNullOverallScore() {
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(null);
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of());
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(null);
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of());
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		assertThat(report.totalCVs()).isZero();
		assertThat(report.overallScore()).isNull();
		assertThat(report.issues()).isEmpty();
	}

	@Test
	void getReport_perfectLibrary_scores100() {
		long total = 10;
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(presence(total, total, 0));
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of(
			new LibraryQualityReport.Metric("UP_TO_DATE", total, 0.0)));
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(
			new LibraryQualityReport.ConfidenceCounts(total, 0, 0, 0.9));
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of());
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		assertThat(report.overallScore()).isEqualTo(100);
		assertThat(report.completeness().score()).isEqualTo(100);
		assertThat(report.freshness().score()).isEqualTo(100);
		assertThat(report.uniqueness().score()).isEqualTo(100);
		assertThat(report.parseConfidence().score()).isEqualTo(100);
		assertThat(report.issues()).isEmpty();
	}

	@Test
	void getReport_unknownFreshness_excludedFromScoreButSurfacedAsIssue() {
		long total = 10;
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(presence(total, total, 0));
		// 5 up to date, 5 unknown — score should ignore the unknowns entirely
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of(
			new LibraryQualityReport.Metric("UP_TO_DATE", 5, 0.0),
			new LibraryQualityReport.Metric("UNKNOWN", 5, 0.0)));
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(
			new LibraryQualityReport.ConfidenceCounts(total, 0, 0, 0.9));
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of());
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		assertThat(report.freshness().score()).isEqualTo(100);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("UNKNOWN_FRESHNESS") && i.count() == 5);
	}

	@Test
	void getReport_bulkImportOfOutdatedCVs_isNotFresh() {
		long total = 10;
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(presence(total, total, 0));
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of(
			new LibraryQualityReport.Metric("UP_TO_DATE", 2, 0.0),
			new LibraryQualityReport.Metric("OUTDATED", 8, 0.0)));
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(
			new LibraryQualityReport.ConfidenceCounts(total, 0, 0, 0.9));
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of());
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		assertThat(report.freshness().score()).isEqualTo(20);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("OUTDATED") && i.count() == 8);
	}

	@Test
	void getReport_duplicates_loweringUniquenessAndListedAsIssue() {
		long total = 10;
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(presence(total, total, 0));
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of(
			new LibraryQualityReport.Metric("UP_TO_DATE", total, 0.0)));
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(
			new LibraryQualityReport.ConfidenceCounts(total, 0, 0, 0.9));
		// one email group of 3 copies -> 2 excess CVs
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of(
			new CVDuplicatesData.DuplicateAggResult("a@b.c", 3, List.of())));
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		assertThat(report.uniqueness().score()).isEqualTo(80);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("DUPLICATES") && i.count() == 1);
	}

	@Test
	void getReport_missingContactFields_lowerCompleteness() {
		long total = 10;
		// nobody has email/phone — critical fields missing
		when(cvRepository.getFieldPresenceByTenantId(any())).thenReturn(new LibraryQualityReport.FieldPresenceCounts(
			total, total, total, 0, 0, total, total, total, total, total, total, total, total, total, total));
		when(cvRepository.getFreshnessBucketsByTenantId(any())).thenReturn(List.of(
			new LibraryQualityReport.Metric("UP_TO_DATE", total, 0.0)));
		when(cvRepository.getParseConfidenceByTenantId(any())).thenReturn(
			new LibraryQualityReport.ConfidenceCounts(total, 0, 0, 0.9));
		when(cvRepository.findEmailDuplicates(any())).thenReturn(List.of());
		when(cvRepository.findPhoneDuplicates(any())).thenReturn(List.of());

		var report = service.getReport(TENANT_ID);

		// 2 critical fields (weight 3 each) at 0% out of total weight 25 → (25-6)/25 of 100
		assertThat(report.completeness().score()).isEqualTo(76);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("MISSING_CONTACT") && i.severity().equals("CRITICAL") && i.count() == 10)
			.anyMatch(i -> i.issueKey().equals("MISSING_EMAIL") && i.count() == 10)
			.anyMatch(i -> i.issueKey().equals("MISSING_PHONE") && i.count() == 10);
	}

	@Test
	void getIssueCVs_duplicatesKey_rejected() {
		assertThatThrownBy(() -> service.getIssueCVs(TENANT_ID, QualityIssueKeyEnum.DUPLICATES, 0, 20))
			.isInstanceOf(QorvaException.class);
	}

	/** All fields present except missingContact/lowConfidence knobs. */
	private LibraryQualityReport.FieldPresenceCounts presence(long total, long has, long missingContact) {
		return new LibraryQualityReport.FieldPresenceCounts(
			total, has, has, has, has, missingContact, has, has, has, has, has, has, has, has, has);
	}
}

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
import java.util.Map;
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

	@Mock
	private ai.qorva.core.dao.repository.QualityIssueStateRepository issueStateRepository;

	@Mock
	private LibraryQualityCacheEvictor cacheEvictor;

	private ExecutorService executor;
	private LibraryQualityService service;

	@BeforeEach
	void setUp() {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		when(issueStateRepository.findByTenantId(any())).thenReturn(List.of());
		service = new LibraryQualityService(cvRepository, issueStateRepository, cacheEvictor, executor);
	}

	@AfterEach
	void tearDown() {
		executor.close();
	}

	private void stubTenant(long total, List<LibraryQualityReport.Metric> flagCounts,
		Map<String, Long> buckets, CVDuplicatesData.DuplicateStats duplicates) {
		when(cvRepository.countActiveByTenantId(any())).thenReturn(total);
		when(cvRepository.countQualityFlagsByTenantId(any())).thenReturn(flagCounts);
		when(cvRepository.countFreshnessBuckets(any())).thenReturn(buckets);
		when(cvRepository.duplicateStats(any())).thenReturn(duplicates);
	}

	private static LibraryQualityReport.Metric flag(String name, long count) {
		return new LibraryQualityReport.Metric(name, count, 0.0);
	}

	@Test
	void getReport_emptyLibrary_returnsNullOverallScore() {
		stubTenant(0, List.of(), Map.of(), new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		assertThat(report.totalCVs()).isZero();
		assertThat(report.overallScore()).isNull();
		assertThat(report.issues()).isEmpty();
	}

	@Test
	void getReport_perfectLibrary_scores100() {
		long total = 10;
		stubTenant(total, List.of(), Map.of("UP_TO_DATE", total),
			new CVDuplicatesData.DuplicateStats(0, 0));

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
		stubTenant(total, List.of(), Map.of("UP_TO_DATE", 5L, "UNKNOWN", 5L),
			new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		assertThat(report.freshness().score()).isEqualTo(100);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("UNKNOWN_FRESHNESS") && i.count() == 5);
	}

	@Test
	void getReport_bulkImportOfOutdatedCVs_isNotFresh() {
		long total = 10;
		stubTenant(total, List.of(), Map.of("UP_TO_DATE", 2L, "OUTDATED", 8L),
			new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		assertThat(report.freshness().score()).isEqualTo(20);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("OUTDATED") && i.count() == 8);
	}

	@Test
	void getReport_duplicates_lowerUniquenessAndListedAsIssue() {
		long total = 10;
		// one group of 3 copies -> 2 excess CVs
		stubTenant(total, List.of(), Map.of("UP_TO_DATE", total),
			new CVDuplicatesData.DuplicateStats(1, 2));

		var report = service.getReport(TENANT_ID);

		assertThat(report.uniqueness().score()).isEqualTo(80);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("DUPLICATES") && i.count() == 1);
	}

	@Test
	void getReport_missingContactFlags_lowerCompleteness() {
		long total = 10;
		stubTenant(total, List.of(
				flag("MISSING_EMAIL", 10), flag("MISSING_PHONE", 10), flag("MISSING_CONTACT", 10)),
			Map.of("UP_TO_DATE", total),
			new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		// 2 critical fields (weight 3 each) at 0% out of total weight 25 → (25-6)/25 of 100
		assertThat(report.completeness().score()).isEqualTo(76);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("MISSING_CONTACT") && i.severity().equals("CRITICAL") && i.count() == 10)
			.anyMatch(i -> i.issueKey().equals("MISSING_EMAIL") && i.count() == 10)
			.anyMatch(i -> i.issueKey().equals("MISSING_PHONE") && i.count() == 10);
	}

	@Test
	void getReport_lowConfidenceCombinesBothFlags() {
		long total = 10;
		stubTenant(total, List.of(flag("NO_AI_ANALYSIS", 1), flag("LOW_AI_CONFIDENCE", 2)),
			Map.of("UP_TO_DATE", total),
			new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		assertThat(report.parseConfidence().score()).isEqualTo(70);
		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("LOW_PARSE_CONFIDENCE") && i.count() == 3);
	}

	@Test
	void getReport_missingBucketCounts_defaultToUnknownNotFresh() {
		long total = 10;
		// bucket query timed out -> empty map; everything must fall into UNKNOWN, not look fresh
		stubTenant(total, List.of(), Map.of(), new CVDuplicatesData.DuplicateStats(0, 0));

		var report = service.getReport(TENANT_ID);

		assertThat(report.freshness().metrics())
			.anyMatch(m -> m.name().equals("UNKNOWN") && m.count() == total);
	}

	@Test
	void getIssueCVs_duplicatesKey_rejected() {
		assertThatThrownBy(() -> service.getIssueCVs(TENANT_ID, QualityIssueKeyEnum.DUPLICATES, 0, 20))
			.isInstanceOf(QorvaException.class);
	}

	@Test
	void performAction_confirmCurrent_capEnforced() {
		var tooMany = java.util.stream.IntStream.range(0, 51)
			.mapToObj(i -> new ObjectId().toHexString()).toList();

		assertThatThrownBy(() -> service.performAction(TENANT_ID,
			new LibraryQualityReport.ActionRequest("CONFIRM_CURRENT", null, tooMany)))
			.isInstanceOf(QorvaException.class);
	}

	@Test
	void performAction_archiveByIssueKey_delegatesAndEvicts() throws QorvaException {
		when(cvRepository.bulkSetArchived(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(35L);

		var result = service.performAction(TENANT_ID,
			new LibraryQualityReport.ActionRequest("ARCHIVE", "OUTDATED", null));

		assertThat(result.modifiedCount()).isEqualTo(35);
		org.mockito.Mockito.verify(cacheEvictor).evict(TENANT_ID);
	}

	@Test
	void performAction_archiveDuplicatesCriteria_rejected() {
		assertThatThrownBy(() -> service.performAction(TENANT_ID,
			new LibraryQualityReport.ActionRequest("ARCHIVE", "DUPLICATES", null)))
			.isInstanceOf(QorvaException.class);
	}

	@Test
	void getReport_dismissedIssueFlagged() {
		long total = 10;
		stubTenant(total, List.of(), Map.of("UP_TO_DATE", 2L, "OUTDATED", 8L),
			new CVDuplicatesData.DuplicateStats(0, 0));
		when(issueStateRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(
			ai.qorva.core.dao.entity.QualityIssueState.builder()
				.tenantId(TENANT_ID).issueKey("OUTDATED").build()));

		var report = service.getReport(TENANT_ID);

		assertThat(report.issues())
			.anyMatch(i -> i.issueKey().equals("OUTDATED") && i.dismissed());
	}
}

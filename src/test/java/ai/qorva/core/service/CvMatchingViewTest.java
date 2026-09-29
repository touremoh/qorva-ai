package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.PersonalInformation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvMatchingViewTest {

	private static CVDTO cv() {
		var cv = new CVDTO();
		cv.setId("cv-1");
		cv.setTenantId("tenant-1");
		cv.setCandidateProfileSummary("Backend engineer, 8 years of Java.");
		cv.setCareerStartYear(2017);
		cv.setPersonalInformation(new PersonalInformation("Ana Ruiz", null, "Backend Engineer", null, null));
		cv.setKeySkills(new ArrayList<>(List.of(new KeySkill("Languages", new ArrayList<>(List.of("Java", "Kotlin"))))));
		cv.setArchived(false);
		cv.setTags(new ArrayList<>(List.of("java")));
		cv.setContentDate(Instant.parse("2025-06-01T00:00:00Z"));
		cv.setQualityFlags(List.of("MISSING_PHONE"));
		cv.setLastUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		return cv;
	}

	@Test
	void annotationsAndDerivedFieldsDoNotChangeTheFingerprint() {
		var before = cv();
		var after = cv();
		after.setTags(List.of("java", "shortlist", "rejected"));
		after.setAtsRefs(List.of(AtsRef.builder().provider("GREENHOUSE").externalId("42").build()));
		after.setContentDate(Instant.parse("2026-09-29T00:00:00Z"));
		after.setContentDateSource("WORK_EXPERIENCE");
		after.setQualityFlags(List.of());
		after.setLastUpdatedAt(Instant.parse("2026-09-29T10:00:00Z"));
		after.setLastUpdatedBy("owner@a.qorva.test");

		assertThat(CvMatchingView.fingerprint(after)).isEqualTo(CvMatchingView.fingerprint(before));
	}

	@Test
	void contentChangesChangeTheFingerprint() {
		var base = CvMatchingView.fingerprint(cv());

		var skills = cv();
		skills.getKeySkills().getFirst().getSkills().add("Go");
		var summary = cv();
		summary.setCandidateProfileSummary("Backend engineer, 9 years of Java.");
		var role = cv();
		role.getPersonalInformation().setRole("Staff Engineer");
		var experience = cv();
		experience.setCareerStartYear(2015);

		assertThat(CvMatchingView.fingerprint(skills)).isNotEqualTo(base);
		assertThat(CvMatchingView.fingerprint(summary)).isNotEqualTo(base);
		assertThat(CvMatchingView.fingerprint(role)).isNotEqualTo(base);
		assertThat(CvMatchingView.fingerprint(experience)).isNotEqualTo(base);
	}

	@Test
	void archivingChangesTheCandidateSetSoItCounts() {
		var archived = cv();
		archived.setArchived(true);

		assertThat(CvMatchingView.fingerprint(archived)).isNotEqualTo(CvMatchingView.fingerprint(cv()));
	}

	@Test
	void theReportInputCarriesNoTagsAndLeavesTheCvUntouched() {
		var cv = cv();

		var view = CvMatchingView.of(cv);

		assertThat(view.getTags()).isNull();
		assertThat(view.getCandidateProfileSummary()).isEqualTo(cv.getCandidateProfileSummary());
		assertThat(view.getKeySkills()).isEqualTo(cv.getKeySkills());
		assertThat(cv.getTags()).containsExactly("java");
		assertThat(CvMatchingView.fingerprint(cv)).doesNotContain("\"tags\":[").doesNotContain("\"java\"]");
	}
}

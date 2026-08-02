package ai.qorva.core.utils;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.Certification;
import ai.qorva.core.dto.common.Education;
import ai.qorva.core.dto.common.WorkExperience;
import ai.qorva.core.enums.ContentDateSourceEnum;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CVContentDateResolverTest {

	@Test
	void parseLatest_monthNameAndYear_resolvesToEndOfMonth() {
		assertThat(CVContentDateResolver.parseLatest("March 2023"))
			.isEqualTo(Instant.parse("2023-03-31T00:00:00Z"));
	}

	@Test
	void parseLatest_slashFormat_resolvesToEndOfMonth() {
		assertThat(CVContentDateResolver.parseLatest("03/2023"))
			.isEqualTo(Instant.parse("2023-03-31T00:00:00Z"));
	}

	@Test
	void parseLatest_bareYear_resolvesToEndOfYear() {
		assertThat(CVContentDateResolver.parseLatest("2022"))
			.isEqualTo(Instant.parse("2022-12-31T00:00:00Z"));
	}

	@Test
	void parseLatest_yearRange_takesLatestYear() {
		assertThat(CVContentDateResolver.parseLatest("2018 - 2022"))
			.isEqualTo(Instant.parse("2022-12-31T00:00:00Z"));
	}

	@Test
	void parseLatest_presentMarker_yieldsNoEvidence() {
		assertThat(CVContentDateResolver.parseLatest("Present")).isNull();
		assertThat(CVContentDateResolver.parseLatest("current")).isNull();
		assertThat(CVContentDateResolver.parseLatest("")).isNull();
		assertThat(CVContentDateResolver.parseLatest(null)).isNull();
	}

	@Test
	void parseLatest_implausibleYear_ignored() {
		assertThat(CVContentDateResolver.parseLatest("1802")).isNull();
	}

	@Test
	void parseLatest_futureDate_cappedAtNow() {
		var parsed = CVContentDateResolver.parseLatest("12/2099");
		assertThat(parsed).isNull();
	}

	@Test
	void resolve_workHistoryBeatsOlderDocMetadata() {
		var dto = cvWithWork("01/2020", "06/2024");
		dto.setContentDate(Instant.parse("2023-01-01T00:00:00Z"));
		dto.setContentDateSource(ContentDateSourceEnum.DOC_METADATA.name());

		CVContentDateResolver.resolve(dto);

		assertThat(dto.getContentDate()).isEqualTo(Instant.parse("2024-06-30T00:00:00Z"));
		assertThat(dto.getContentDateSource()).isEqualTo(ContentDateSourceEnum.WORK_HISTORY.name());
	}

	@Test
	void resolve_docMetadataKeptWhenNewerThanWorkHistory() {
		var dto = cvWithWork("01/2018", "06/2020");
		dto.setContentDate(Instant.parse("2026-01-15T00:00:00Z"));
		dto.setContentDateSource(ContentDateSourceEnum.DOC_METADATA.name());

		CVContentDateResolver.resolve(dto);

		assertThat(dto.getContentDate()).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
		assertThat(dto.getContentDateSource()).isEqualTo(ContentDateSourceEnum.DOC_METADATA.name());
	}

	@Test
	void resolve_noEvidence_marksUnknown() {
		var dto = new CVDTO();

		CVContentDateResolver.resolve(dto);

		assertThat(dto.getContentDate()).isNull();
		assertThat(dto.getContentDateSource()).isEqualTo(ContentDateSourceEnum.UNKNOWN.name());
	}

	@Test
	void resolve_verifiedNeverDowngraded() {
		var dto = cvWithWork("01/2015", "06/2016");
		var verifiedAt = Instant.parse("2026-07-01T00:00:00Z");
		dto.setContentDate(verifiedAt);
		dto.setContentDateSource(ContentDateSourceEnum.VERIFIED.name());

		CVContentDateResolver.resolve(dto);

		assertThat(dto.getContentDate()).isEqualTo(verifiedAt);
		assertThat(dto.getContentDateSource()).isEqualTo(ContentDateSourceEnum.VERIFIED.name());
	}

	@Test
	void resolve_usesEducationAndCertificationYears() {
		var dto = new CVDTO();
		dto.setEducation(List.of(new Education("2019", "MIT", "BSc", "CS", null)));
		dto.setCertifications(List.of(new Certification("AWS SAA", "AWS", "2024", null)));

		CVContentDateResolver.resolve(dto);

		assertThat(dto.getContentDate()).isEqualTo(Instant.parse("2024-12-31T00:00:00Z"));
		assertThat(dto.getContentDateSource()).isEqualTo(ContentDateSourceEnum.WORK_HISTORY.name());
	}

	private CVDTO cvWithWork(String from, String to) {
		var dto = new CVDTO();
		var we = new WorkExperience();
		we.setFrom(from);
		we.setTo(to);
		dto.setWorkExperience(List.of(we));
		return dto;
	}
}

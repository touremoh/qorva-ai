package ai.qorva.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class CVServiceListSortTest {

	@Test
	void defaultsToLastUpdatedDescending() {
		assertThat(CVService.listSort(null)).isEqualTo(Sort.by(Sort.Direction.DESC, "lastUpdatedAt"));
		assertThat(CVService.listSort("bogus,asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "lastUpdatedAt"));
	}

	@Test
	void nameSortsOnPersonalInformationName() {
		assertThat(CVService.listSort("name,asc")).isEqualTo(Sort.by(Sort.Direction.ASC, "personalInformation.name"));
	}

	@Test
	void experienceDescendingMeansEarliestCareerStartFirst() {
		assertThat(CVService.listSort("experience,desc")).isEqualTo(Sort.by(Sort.Direction.ASC, "careerStartYear"));
		assertThat(CVService.listSort("experience,asc")).isEqualTo(Sort.by(Sort.Direction.DESC, "careerStartYear"));
	}
}

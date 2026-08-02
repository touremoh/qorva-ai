package ai.qorva.core.utils;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.CandidateClustering;
import ai.qorva.core.dto.common.Contact;
import ai.qorva.core.dto.common.Education;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.dto.common.SalaryExpectation;
import ai.qorva.core.dto.common.WorkExperience;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.qorva.core.enums.QualityFlagEnum.*;
import static org.assertj.core.api.Assertions.assertThat;

class CVQualityFlagResolverTest {

	@Test
	void resolve_emptyCv_raisesAllFlags() {
		var dto = new CVDTO();

		CVQualityFlagResolver.resolve(dto);

		assertThat(dto.getQualityFlags()).containsExactlyInAnyOrder(
			MISSING_EMAIL.name(), MISSING_PHONE.name(), MISSING_CONTACT.name(),
			MISSING_NAME.name(), MISSING_ROLE.name(),
			NO_WORK_EXPERIENCE.name(), NO_SKILLS.name(),
			MISSING_CAREER_START_YEAR.name(), MISSING_EDUCATION.name(),
			MISSING_LANGUAGES.name(), MISSING_CERTIFICATIONS.name(),
			MISSING_SALARY.name(), MISSING_LINKEDIN.name(), MISSING_SUMMARY.name(),
			NO_AI_ANALYSIS.name());
	}

	@Test
	void resolve_emailOnlyMissingPhone_noMissingContact() {
		var dto = new CVDTO();
		var pi = new PersonalInformation();
		var contact = new Contact();
		contact.setEmail("jane@example.com");
		pi.setContact(contact);
		dto.setPersonalInformation(pi);

		CVQualityFlagResolver.resolve(dto);

		assertThat(dto.getQualityFlags())
			.contains(MISSING_PHONE.name())
			.doesNotContain(MISSING_EMAIL.name(), MISSING_CONTACT.name());
	}

	@Test
	void resolve_blankStringsCountAsMissing() {
		var dto = new CVDTO();
		var pi = new PersonalInformation();
		pi.setName("  ");
		var contact = new Contact();
		contact.setEmail("");
		pi.setContact(contact);
		dto.setPersonalInformation(pi);
		dto.setCandidateProfileSummary("");

		CVQualityFlagResolver.resolve(dto);

		assertThat(dto.getQualityFlags())
			.contains(MISSING_NAME.name(), MISSING_EMAIL.name(), MISSING_SUMMARY.name());
	}

	@Test
	void resolve_confidenceBoundaries() {
		var dto = new CVDTO();
		var clustering = new CandidateClustering();
		clustering.setClusterConfidenceScore(0.49);
		dto.setCandidateClustering(clustering);

		CVQualityFlagResolver.resolve(dto);
		assertThat(dto.getQualityFlags()).contains(LOW_AI_CONFIDENCE.name()).doesNotContain(NO_AI_ANALYSIS.name());

		clustering.setClusterConfidenceScore(0.5);
		CVQualityFlagResolver.resolve(dto);
		assertThat(dto.getQualityFlags()).doesNotContain(LOW_AI_CONFIDENCE.name(), NO_AI_ANALYSIS.name());

		clustering.setClusterConfidenceScore(null);
		CVQualityFlagResolver.resolve(dto);
		assertThat(dto.getQualityFlags()).contains(NO_AI_ANALYSIS.name()).doesNotContain(LOW_AI_CONFIDENCE.name());
	}

	@Test
	void resolve_completeCv_hasNoFlags() {
		var dto = new CVDTO();
		var pi = new PersonalInformation();
		pi.setName("Jane Doe");
		pi.setRole("Engineer");
		var contact = new Contact();
		contact.setEmail("jane@example.com");
		contact.setPhone("+3312345678");
		var socialLinks = new ai.qorva.core.dto.common.SocialLinks();
		socialLinks.setLinkedin("https://linkedin.com/in/jane");
		contact.setSocialLinks(socialLinks);
		pi.setContact(contact);
		dto.setPersonalInformation(pi);
		dto.setCandidateProfileSummary("Senior engineer with 10 years of experience.");
		dto.setCareerStartYear(2014);
		dto.setWorkExperience(List.of(new WorkExperience()));
		dto.setKeySkills(List.of(new KeySkill()));
		dto.setEducation(List.of(new Education()));
		dto.setCertifications(List.of(new ai.qorva.core.dto.common.Certification()));
		dto.setSalaryExpectation(new SalaryExpectation("EUR", 50000, 60000));
		var skills = new ai.qorva.core.dto.common.SkillsAndQualifications();
		skills.setLanguages(List.of(new ai.qorva.core.dto.common.Language()));
		dto.setSkillsAndQualifications(skills);
		var clustering = new CandidateClustering();
		clustering.setClusterConfidenceScore(0.9);
		dto.setCandidateClustering(clustering);

		CVQualityFlagResolver.resolve(dto);

		assertThat(dto.getQualityFlags()).isEmpty();
	}
}

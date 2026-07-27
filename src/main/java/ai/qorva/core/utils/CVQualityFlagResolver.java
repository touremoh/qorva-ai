package ai.qorva.core.utils;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.Contact;
import ai.qorva.core.enums.QualityFlagEnum;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static ai.qorva.core.enums.QualityFlagEnum.*;

/**
 * Single source of truth for a CV's quality flags. Called from every write path
 * (create and update pre-processing) so the stored flags can never drift from the data.
 */
public final class CVQualityFlagResolver {

	private CVQualityFlagResolver() {}

	public static final double LOW_CONFIDENCE_THRESHOLD = 0.5;

	/** Computes and sets {@code qualityFlags} on the DTO. */
	public static void resolve(CVDTO dto) {
		if (dto == null) {
			return;
		}
		var flags = new ArrayList<String>();

		var personalInformation = dto.getPersonalInformation();
		Contact contact = personalInformation != null ? personalInformation.getContact() : null;
		var email = contact != null ? contact.getEmail() : null;
		var phone = contact != null ? contact.getPhone() : null;
		var linkedin = contact != null && contact.getSocialLinks() != null
			? contact.getSocialLinks().getLinkedin() : null;

		addIf(flags, MISSING_EMAIL, !StringUtils.hasText(email));
		addIf(flags, MISSING_PHONE, !StringUtils.hasText(phone));
		addIf(flags, MISSING_CONTACT, !StringUtils.hasText(email) && !StringUtils.hasText(phone));
		addIf(flags, MISSING_NAME, personalInformation == null || !StringUtils.hasText(personalInformation.getName()));
		addIf(flags, MISSING_ROLE, personalInformation == null || !StringUtils.hasText(personalInformation.getRole()));

		addIf(flags, NO_WORK_EXPERIENCE, CollectionUtils.isEmpty(dto.getWorkExperience()));
		addIf(flags, NO_SKILLS, CollectionUtils.isEmpty(dto.getKeySkills()));
		addIf(flags, MISSING_CAREER_START_YEAR, dto.getCareerStartYear() == null);
		addIf(flags, MISSING_EDUCATION, CollectionUtils.isEmpty(dto.getEducation()));
		addIf(flags, MISSING_LANGUAGES, dto.getSkillsAndQualifications() == null
			|| CollectionUtils.isEmpty(dto.getSkillsAndQualifications().getLanguages()));
		addIf(flags, MISSING_CERTIFICATIONS, CollectionUtils.isEmpty(dto.getCertifications()));
		addIf(flags, MISSING_SALARY, dto.getSalaryExpectation() == null);
		addIf(flags, MISSING_LINKEDIN, !StringUtils.hasText(linkedin));
		addIf(flags, MISSING_SUMMARY, !StringUtils.hasText(dto.getCandidateProfileSummary()));

		var confidence = dto.getCandidateClustering() != null
			? dto.getCandidateClustering().getClusterConfidenceScore() : null;
		addIf(flags, NO_AI_ANALYSIS, confidence == null);
		addIf(flags, LOW_AI_CONFIDENCE, confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD);

		dto.setQualityFlags(flags);
	}

	private static void addIf(List<String> flags, QualityFlagEnum flag, boolean condition) {
		if (condition) {
			flags.add(flag.name());
		}
	}
}

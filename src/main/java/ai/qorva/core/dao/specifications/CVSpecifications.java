package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.CV;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class CVSpecifications {
	private CVSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<CV> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<CV> hasSkills(Collection<String> skills) {
		if (skills == null || skills.isEmpty()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("skills").all(skills);
	}

	public static MongoSpecification<CV> hasAnySkill(Collection<String> skills) {
		if (skills == null || skills.isEmpty()) {
			return MongoSpecifications.empty();
		}
		List<Pattern> patterns = skills.stream()
			.filter(s -> s != null && !s.isBlank())
			.map(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE))
			.toList();
		if (patterns.isEmpty()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("skillsAndQualifications.technicalSkills").in(patterns);
	}

	public static MongoSpecification<CV> nameContains(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("personalInformation.name").regex(keyword, "i");
	}

	public static MongoSpecification<CV> hasRoleOrPosition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("personalInformation.role").regex(keyword, "i");
	}

	public static MongoSpecification<CV> hasMinYearOfExperience(Integer minYears) {
		if (minYears == null || minYears < 0) {
			return MongoSpecifications.empty();
		}
		int latestAllowedStartYear = Year.now().getValue() - minYears;
		return () -> Criteria.where("careerStartYear").lte(latestAllowedStartYear);
	}

	public static MongoSpecification<CV> applicantNumberEquals(String applicantNumber) {
		if (applicantNumber == null || applicantNumber.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("applicantNumber").is(applicantNumber);
	}
}

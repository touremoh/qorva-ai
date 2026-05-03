package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.specifications.CVSpecifications;
import ai.qorva.core.dao.specifications.MongoSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CVQueryBuilder implements QorvaQueryBuilder<CV> {

	@Override
	public MongoSpecification<CV> buildQuery(Map<String, String> params) {
		log.debug("CVQueryBuilder.buildQuery params: {}", params);

		String skills = params.get("skills");
		List<String> skillList = (skills != null && !skills.isBlank())
			? Arrays.asList(skills.split(","))
			: null;

		String minYears = params.get("minYearsOfExperience");
		Integer minYearsOfExperience = (minYears != null && !minYears.isBlank())
			? Integer.parseInt(minYears)
			: null;

		return MongoSpecification
			.where(CVSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(CVSpecifications.hasRoleOrPosition(params.get("role")))
			.and(CVSpecifications.nameContains(params.get("name")))
			.and(CVSpecifications.hasAnySkill(skillList))
			.and(CVSpecifications.hasMinYearOfExperience(minYearsOfExperience));
	}
}

package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.ResumeMatch;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.ResumeMatchSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResumeMatchQueryBuilder implements QorvaQueryBuilder<ResumeMatch> {

	@Override
	public MongoSpecification<ResumeMatch> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(ResumeMatchSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(ResumeMatchSpecifications.jobPostIdEquals(params.get("jobPostId")))
			.and(ResumeMatchSpecifications.statusEquals(params.get("status")))
			.and(ResumeMatchSpecifications.candidateNameContains(params.get("candidateName")));
	}
}

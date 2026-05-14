package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MatchingReportSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MatchingReportQueryBuilder implements QorvaQueryBuilder<MatchingReport> {

	@Override
	public MongoSpecification<MatchingReport> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(MatchingReportSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(MatchingReportSpecifications.jobPostIdEquals(params.get("jobPostId")))
			.and(MatchingReportSpecifications.statusEquals(params.get("status")))
			.and(MatchingReportSpecifications.candidateNameContains(params.get("candidateName")))
			.and(MatchingReportSpecifications.recommendationEquals(params.get("recommendation")))
			.and(MatchingReportSpecifications.confidenceLevelEquals(params.get("confidenceLevel")));
	}
}

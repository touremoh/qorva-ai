package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.MatchingReport;
import org.springframework.data.mongodb.core.query.Criteria;

public final class MatchingReportSpecifications {
	private MatchingReportSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<MatchingReport> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<MatchingReport> jobPostIdEquals(String jobPostId) {
		if (jobPostId == null || jobPostId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("jobPostId").is(jobPostId);
	}

	public static MongoSpecification<MatchingReport> statusEquals(String status) {
		if (status == null || status.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("status").is(status);
	}

	public static MongoSpecification<MatchingReport> candidateNameContains(String name) {
		if (name == null || name.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("candidateInfo.candidateName").regex(name, "i");
	}

	public static MongoSpecification<MatchingReport> recommendationEquals(String recommendation) {
		if (recommendation == null || recommendation.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("matchingReportDetails.decisionSummary.recommendation").is(recommendation);
	}

	public static MongoSpecification<MatchingReport> confidenceLevelEquals(String confidenceLevel) {
		if (confidenceLevel == null || confidenceLevel.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("matchingReportDetails.decisionSummary.confidenceLevel").is(confidenceLevel);
	}
}

package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.ResumeMatch;
import org.springframework.data.mongodb.core.query.Criteria;

public final class ResumeMatchSpecifications {
	private ResumeMatchSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<ResumeMatch> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<ResumeMatch> jobPostIdEquals(String jobPostId) {
		if (jobPostId == null || jobPostId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("jobPostId").is(jobPostId);
	}

	public static MongoSpecification<ResumeMatch> statusEquals(String status) {
		if (status == null || status.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("status").is(status);
	}

	public static MongoSpecification<ResumeMatch> candidateNameContains(String name) {
		if (name == null || name.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("candidateInfo.candidateName").regex(name, "i");
	}
}

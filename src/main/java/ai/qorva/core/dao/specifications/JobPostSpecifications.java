package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.JobPost;
import org.springframework.data.mongodb.core.query.Criteria;

public final class JobPostSpecifications {
	private JobPostSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<JobPost> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<JobPost> titleContains(String title) {
		if (title == null || title.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("title").regex(title, "i");
	}

	public static MongoSpecification<JobPost> statusEquals(String status) {
		if (status == null || status.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("status").is(status);
	}

	public static MongoSpecification<JobPost> createdByEquals(String createdBy) {
		if (createdBy == null || createdBy.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("createdBy").is(createdBy);
	}
}

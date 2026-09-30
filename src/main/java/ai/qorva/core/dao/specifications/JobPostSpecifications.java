package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.JobPost;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.regex.Pattern;

public final class JobPostSpecifications {
	private JobPostSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<JobPost> titleContains(String title) {
		if (title == null || title.isBlank()) return MongoSpecifications.empty();
		// Literal text, as in CVSpecifications: "Senior Engineer (Java/Spring)" must match its own parentheses.
		return () -> Criteria.where("title").regex(Pattern.quote(title.trim()), "i");
	}
	public static MongoSpecification<JobPost> descriptionContains(String description) {
		if (description == null || description.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("description").regex(Pattern.quote(description.trim()), "i");
	}

	public static MongoSpecification<JobPost> statusEquals(String status) {
		if (status == null || status.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("status").is(status);
	}

	public static MongoSpecification<JobPost> createdByEquals(String createdBy) {
		if (createdBy == null || createdBy.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("createdBy").is(createdBy);
	}

	public static MongoSpecification<JobPost> jobReferenceEquals(String jobReference) {
		if (jobReference == null || jobReference.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("jobReference").is(jobReference);
	}
}

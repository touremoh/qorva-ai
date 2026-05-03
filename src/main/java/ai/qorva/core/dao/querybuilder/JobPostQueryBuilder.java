package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.specifications.JobPostSpecifications;
import ai.qorva.core.dao.specifications.MongoSpecification;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JobPostQueryBuilder implements QorvaQueryBuilder<JobPost> {

	@Override
	public MongoSpecification<JobPost> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(JobPostSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(JobPostSpecifications.titleContains(params.get("title")))
			.and(JobPostSpecifications.statusEquals(params.get("status")))
			.and(JobPostSpecifications.createdByEquals(params.get("createdBy")));
	}
}

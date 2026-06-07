package ai.qorva.core.dao.repository;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.ClusterBucket;
import ai.qorva.core.dto.SkillFrequencyResult;
import org.bson.types.ObjectId;

import java.util.List;

public interface CVInsightRepository {

	long countCandidatesByFilters(ObjectId tenantId, CVQueryParams params);

	List<SkillFrequencyResult> getSkillFrequencyReport(ObjectId tenantId, CVQueryParams params, int limit);

	List<SkillFrequencyResult> getRareSkillsReport(ObjectId tenantId, CVQueryParams params, int maxCount, int limit);

	List<ClusterBucket> getClusterDistributionReport(ObjectId tenantId, CVQueryParams params, String clusterDimension);
}

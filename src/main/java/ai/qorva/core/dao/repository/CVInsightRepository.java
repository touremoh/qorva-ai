package ai.qorva.core.dao.repository;

import ai.qorva.core.dto.ClusterBucket;
import ai.qorva.core.dto.ExtractedFilters;
import ai.qorva.core.dto.SkillFrequencyResult;
import org.bson.types.ObjectId;

import java.util.List;

public interface CVInsightRepository {

	long countCandidatesByFilters(ObjectId tenantId, ExtractedFilters filters);

	List<SkillFrequencyResult> getSkillFrequencyReport(ObjectId tenantId, ExtractedFilters filters, int limit);

	List<ClusterBucket> getClusterDistributionReport(ObjectId tenantId, String clusterDimension);
}

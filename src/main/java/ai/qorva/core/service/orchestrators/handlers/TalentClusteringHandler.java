package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.repository.CVInsightRepository;
import ai.qorva.core.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TalentClusteringHandler implements InsightHandler {

	private static final List<String> CLUSTER_DIMENSIONS = List.of(
		"seniorityLevel", "skillDepth", "leadershipAndInfluence", "learningVelocity"
	);

	private final CVInsightRepository cvInsightRepository;

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		List<ChartDataDTO> charts = new ArrayList<>();
		List<InsightMetricDTO> metrics = new ArrayList<>();
		Map<String, Object> rawData = new HashMap<>();

		for (String dimension : CLUSTER_DIMENSIONS) {
			List<ClusterBucket> buckets = cvInsightRepository.getClusterDistributionReport(tenantId, dimension);
			if (buckets.isEmpty()) {
				continue;
			}

			List<String> labels = buckets.stream().map(ClusterBucket::name).collect(Collectors.toList());
			List<Number> values = buckets.stream().map(ClusterBucket::count).collect(Collectors.toList());
			charts.add(new ChartDataDTO("pie", dimension + " Distribution", labels, values));

			ClusterBucket topBucket = buckets.get(0);
			metrics.add(new InsightMetricDTO(
				"Top " + dimension,
				topBucket.name() + " (" + topBucket.count() + ")",
				"candidates"
			));

			rawData.put(dimension, buckets);
		}

		return new InsightHandlerResult(List.of(), 0, metrics, charts, rawData);
	}
}

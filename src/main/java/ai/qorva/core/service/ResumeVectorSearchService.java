package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.querybuilder.InsightCVQueryBuilder;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVQueryParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeVectorSearchService {

	private final CVRepository cvRepository;
	private final EmbeddingModel embeddingModel;
	private final InsightCVQueryBuilder queryBuilder;

	private static final int DEFAULT_LIMIT = 10;

	public List<CV> search(CVQueryParams params, ObjectId tenantId) {
		try {
			int limit = (params != null && params.limit() != null) ? params.limit() : DEFAULT_LIMIT;
			String queryString = buildQueryString(params);
			float[] embedding = embeddingModel.embed(queryString);
			Criteria postFilter = (params != null) ? queryBuilder.build(tenantId, params) : null;
			return cvRepository.similaritySearch(embedding, tenantId, null, List.of(), limit, postFilter);
		} catch (Exception e) {
			log.error("Error performing vector search: {}", e.getMessage());
			return List.of();
		}
	}

	private String buildQueryString(CVQueryParams params) {
		if (params == null) {
			return "recruiting professional";
		}

		List<String> parts = new ArrayList<>();
		Stream.of(
			params.roles(),
			params.skills(),
			params.requiredSkills(),
			params.industries(),
			params.requiredIndustries(),
			params.languages(),
			params.seniority() != null ? List.of(params.seniority()) : List.<String>of(),
			params.skillDepth() != null ? List.of(params.skillDepth()) : List.<String>of(),
			params.leadershipLevel() != null ? List.of(params.leadershipLevel()) : List.<String>of(),
			params.location() != null ? List.of(params.location()) : List.<String>of()
		).filter(l -> l != null && !l.isEmpty()).flatMap(Collection::stream).forEach(parts::add);

		String query = String.join(" ", parts);
		return query.isBlank() ? "recruiting professional" : query;
	}
}

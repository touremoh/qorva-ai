package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.ExtractedFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.embedding.EmbeddingModel;
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

	private static final int DEFAULT_LIMIT = 10;
	private static final int POOL_ANALYSIS_LIMIT = 500;

	public List<CV> search(ExtractedFilters filters, ObjectId tenantId) {
		try {
			int limit = (filters != null && filters.limit() != null) ? filters.limit() : DEFAULT_LIMIT;
			String queryString = buildQueryString(filters);
			float[] embedding = embeddingModel.embed(queryString);
			return cvRepository.similaritySearch(embedding, tenantId, null, List.of(), limit);
		} catch (Exception e) {
			log.error("Error performing vector search: {}", e.getMessage());
			return List.of();
		}
	}

	public List<CV> searchForPoolAnalysis(ExtractedFilters filters, ObjectId tenantId) {
		try {
			String queryString = buildQueryString(filters);
			float[] embedding = embeddingModel.embed(queryString);
			return cvRepository.similaritySearch(embedding, tenantId, null, List.of(), POOL_ANALYSIS_LIMIT);
		} catch (Exception e) {
			log.error("Error performing pool analysis vector search: {}", e.getMessage());
			return List.of();
		}
	}

	private String buildQueryString(ExtractedFilters filters) {
		if (filters == null) {
			return "recruiting professional";
		}

		List<String> parts = new ArrayList<>();
		Stream.of(
			filters.roles(),
			filters.skills(),
			filters.seniority() != null ? List.of(filters.seniority()) : List.<String>of(),
			filters.skillDepth() != null ? List.of(filters.skillDepth()) : List.<String>of(),
			filters.leadershipLevel() != null ? List.of(filters.leadershipLevel()) : List.<String>of(),
			filters.location() != null ? List.of(filters.location()) : List.<String>of(),
			filters.industries()
		).filter(l -> l != null && !l.isEmpty()).flatMap(Collection::stream).forEach(parts::add);

		String query = String.join(" ", parts);
		return query.isBlank() ? "recruiting professional" : query;
	}
}

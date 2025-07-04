package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CVRepository extends QorvaRepository<CV> {

	@Query(value = "{ '$text': { $search: ?0 } }",
		   sort  = "{ 'score': { $meta: 'textScore' } }")
	List<CV> searchAll(String searchTerms);

	@Aggregation(pipeline = {
		"{ $vectorSearch: { " +
			"index: 'CVsSearchIndex', " +
			"queryVector: ?0, " +
			"path: 'embedding', " +
			"numCandidates: 500, " +
			"limit: 25, " +
			"filter: { tenantId: { $eq: ?1 } } " +
			"} }",
		"{ $addFields: { score: { $meta: 'vectorSearchScore' } } }",
		"{ $match: { score: { $gte: 0.75 } } }"
	})
	List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId);
}

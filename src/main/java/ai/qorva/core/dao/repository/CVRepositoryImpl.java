package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecificationExecutorImpl;
import ai.qorva.core.dao.specifications.QorvaRepositorySpecification;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CVRepositoryImpl implements QorvaRepositorySpecification<CV>, SimilaritySearchRepository {

	private final MongoTemplate mongoTemplate;
	private final MongoSpecificationExecutorImpl<CV> delegate;

	@Autowired
	public CVRepositoryImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
		this.delegate = new MongoSpecificationExecutorImpl<>(mongoTemplate, CV.class);
	}

	@Override
	public List<CV> findAll(MongoSpecification<CV> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<CV> findAll(MongoSpecification<CV> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<CV> findAll(MongoSpecification<CV> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<CV> findOne(MongoSpecification<CV> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<CV> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<CV> specification) {
		return this.delegate.count(specification);
	}

	@Override
	public List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses) {
		List<Double> vector = new ArrayList<>(queryEmbedding.length);
		for (float f : queryEmbedding) {
			vector.add((double) f);
		}

		Document vectorSearchDoc = new Document()
			.append("index", "cvs_search_index")
			.append("queryVector", vector)
			.append("path", "embedding")
			.append("numCandidates", 500)
			.append("limit", 50)
			.append("filter", new Document("tenantId", new Document("$eq", tenantId)));

		AggregationOperation vectorSearch = ctx -> new Document("$vectorSearch", vectorSearchDoc);
		AggregationOperation addScore = ctx -> new Document("$addFields",
			new Document("score", new Document("$meta", "vectorSearchScore")));

		Criteria matchCriteria = Criteria.where("score").gte(0.4);

		// Exclude documents where openToWork is explicitly false; missing field is included
		if (Boolean.TRUE.equals(filterOpenToWork)) {
			matchCriteria.and("personalInformation.availability.openToWork").ne(false);
		}

		// Exclude documents whose status is in the exclusion list; missing field is included
		if (includedStatuses != null && !includedStatuses.isEmpty()) {
			matchCriteria.and("personalInformation.availability.status").in(includedStatuses);
		}

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(CV.class, vectorSearch, addScore, Aggregation.match(matchCriteria)),
			CV.class
		).getMappedResults();
	}
}

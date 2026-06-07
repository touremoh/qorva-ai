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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CVRepositoryImpl implements QorvaRepositorySpecification<CV>, SimilaritySearchRepository, TextSearchRepository {

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
	public List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses, int limit, Criteria postFilter) {
		List<Double> vector = new ArrayList<>(queryEmbedding.length);
		for (float f : queryEmbedding) {
			vector.add((double) f);
		}

		// Fetch more candidates than needed to absorb post-filter loss, capped at numCandidates
		final int NUM_CANDIDATES = 1000;
		int vectorLimit = Math.min(Math.max(limit * 3, 20), NUM_CANDIDATES);

		Document vectorSearchDoc = new Document()
			.append("index", "cvs_search_index")
			.append("queryVector", vector)
			.append("path", "embedding")
			.append("numCandidates", NUM_CANDIDATES)
			.append("limit", vectorLimit)
			.append("filter", new Document("tenantId", new Document("$eq", tenantId)));

		AggregationOperation vectorSearch = ctx -> new Document("$vectorSearch", vectorSearchDoc);
		AggregationOperation addScore = ctx -> new Document("$addFields",
			new Document("score", new Document("$meta", "vectorSearchScore")));

		List<Criteria> matchConditions = new ArrayList<>();
		matchConditions.add(Criteria.where("score").gte(0.5));

		if (Boolean.TRUE.equals(filterOpenToWork)) {
			matchConditions.add(Criteria.where("personalInformation.availability.openToWork").ne(false));
		}
		if (includedStatuses != null && !includedStatuses.isEmpty()) {
			matchConditions.add(Criteria.where("personalInformation.availability.status").in(includedStatuses));
		}
		if (postFilter != null) {
			matchConditions.add(postFilter);
		}

		Criteria matchCriteria = new Criteria().andOperator(matchConditions.toArray(new Criteria[0]));

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(CV.class, vectorSearch, addScore, Aggregation.match(matchCriteria), Aggregation.limit(limit)),
			CV.class
		).getMappedResults();
	}

	@Override
	public List<CV> textSearch(List<String> textTerms, List<String> industryTerms, ObjectId tenantId, int limit) {
		Query query = buildTextQuery(textTerms, industryTerms, tenantId);
		if (query == null) return List.of();
		query.limit(limit);
		return mongoTemplate.find(query, CV.class);
	}

	@Override
	public long textSearchCount(List<String> textTerms, List<String> industryTerms, ObjectId tenantId) {
		Query query = buildTextQuery(textTerms, industryTerms, tenantId);
		if (query == null) return 0L;
		return mongoTemplate.count(query, CV.class);
	}

	private Query buildTextQuery(List<String> textTerms, List<String> industryTerms, ObjectId tenantId) {
		if (textTerms == null || textTerms.isEmpty()) {
			return null;
		}
		String searchPhrase = String.join(" ", textTerms);
		Query query = new Query(Criteria.where("tenantId").is(tenantId));
		query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(searchPhrase));

		// AND filter: at least one industry term must appear in industryDomains
		if (industryTerms != null && !industryTerms.isEmpty()) {
			List<Criteria> industryCriteria = industryTerms.stream()
				.map(term -> Criteria.where("candidateClustering.industryDomains").regex(term, "i"))
				.collect(Collectors.toList());
			query.addCriteria(new Criteria().orOperator(industryCriteria.toArray(new Criteria[0])));
		}

		return query;
	}
}

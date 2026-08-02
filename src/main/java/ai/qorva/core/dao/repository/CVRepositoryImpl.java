package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecificationExecutorImpl;
import ai.qorva.core.dao.specifications.QorvaRepositorySpecification;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.enums.ContentDateSourceEnum;
import ai.qorva.core.enums.QualityFlagEnum;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CVRepositoryImpl implements QorvaRepositorySpecification<CV>, SimilaritySearchRepository, TextSearchRepository, CVQualityRepository {

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
		// Archived candidates must never surface in job matching — that is the point of archiving.
		matchConditions.add(Criteria.where("archived").ne(true));

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

	// -------------------------------------------------------------------------
	// CVQualityRepository — every query here must be index-backed (see V20260727_01)
	// -------------------------------------------------------------------------

	public static final int FRESHNESS_UP_TO_DATE_MONTHS = 6;
	public static final int FRESHNESS_OUTDATED_MONTHS = 18;

	private Criteria activeTenantCriteria(ObjectId tenantId) {
		return Criteria.where("tenantId").is(tenantId).and("archived").ne(true);
	}

	@Override
	public Page<CV> findQualityIssueCVs(ObjectId tenantId, QualityIssueKeyEnum issueKey, Pageable pageable) {
		Query query = new Query(activeTenantCriteria(tenantId))
			.addCriteria(qualityIssueCriteria(issueKey));

		long total = mongoTemplate.count(Query.of(query), CV.class);

		query.with(pageable);
		query.with(Sort.by(Sort.Direction.DESC, "lastUpdatedAt"));
		// Slim projection — never ship attachment/rawText/embedding in drill-down pages.
		query.fields()
			.include("personalInformation.name")
			.include("personalInformation.role")
			.include("personalInformation.contact.email")
			.include("personalInformation.contact.phone")
			.include("contentDate")
			.include("lastUpdatedAt");

		List<CV> content = mongoTemplate.find(query, CV.class);
		return new PageImpl<>(content, pageable, total);
	}

	/** issueKey → indexed criteria; flag-backed keys hit {tenantId, qualityFlags}, freshness keys hit {tenantId, contentDate}. */
	private Criteria qualityIssueCriteria(QualityIssueKeyEnum issueKey) {
		final Instant outdatedCutoff = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(FRESHNESS_OUTDATED_MONTHS).toInstant();

		return switch (issueKey) {
			case MISSING_CONTACT -> Criteria.where("qualityFlags").is(QualityFlagEnum.MISSING_CONTACT.name());
			case MISSING_EMAIL -> Criteria.where("qualityFlags").is(QualityFlagEnum.MISSING_EMAIL.name());
			case MISSING_PHONE -> Criteria.where("qualityFlags").is(QualityFlagEnum.MISSING_PHONE.name());
			case NO_WORK_EXPERIENCE -> Criteria.where("qualityFlags").is(QualityFlagEnum.NO_WORK_EXPERIENCE.name());
			case NO_SKILLS -> Criteria.where("qualityFlags").is(QualityFlagEnum.NO_SKILLS.name());
			case MISSING_SUMMARY -> Criteria.where("qualityFlags").is(QualityFlagEnum.MISSING_SUMMARY.name());
			case LOW_PARSE_CONFIDENCE -> Criteria.where("qualityFlags").in(
				QualityFlagEnum.NO_AI_ANALYSIS.name(), QualityFlagEnum.LOW_AI_CONFIDENCE.name());
			case OUTDATED -> Criteria.where("contentDate").lt(outdatedCutoff);
			case UNKNOWN_FRESHNESS -> Criteria.where("contentDate").is(null);
			case DUPLICATES -> throw new IllegalArgumentException(
				"DUPLICATES drill-down is served by the dedicated /cvs/duplicates endpoint");
		};
	}

	@Override
	public long countActiveByTenantId(ObjectId tenantId) {
		return mongoTemplate.count(new Query(activeTenantCriteria(tenantId)), CV.class);
	}

	@Override
	public long countQualityIssueCVs(ObjectId tenantId, QualityIssueKeyEnum issueKey, boolean onlyMissingRawText) {
		Query query = new Query(activeTenantCriteria(tenantId)).addCriteria(qualityIssueCriteria(issueKey));
		if (onlyMissingRawText) {
			query.addCriteria(new Criteria().orOperator(
				Criteria.where("rawText").is(null),
				Criteria.where("rawText").is("")));
		}
		return mongoTemplate.count(query, CV.class);
	}

	@Override
	public List<ObjectId> findQualityIssueCvIds(ObjectId tenantId, QualityIssueKeyEnum issueKey) {
		Query query = new Query(activeTenantCriteria(tenantId)).addCriteria(qualityIssueCriteria(issueKey));
		query.fields().include("_id");
		return mongoTemplate.find(query, CV.class).stream()
			.map(cv -> new ObjectId(cv.getId()))
			.toList();
	}

	@Override
	public Map<String, Long> countFreshnessBuckets(ObjectId tenantId) {
		var now = ZonedDateTime.now(ZoneOffset.UTC);
		Instant upToDateCutoff = now.minusMonths(FRESHNESS_UP_TO_DATE_MONTHS).toInstant();
		Instant outdatedCutoff = now.minusMonths(FRESHNESS_OUTDATED_MONTHS).toInstant();

		var buckets = new LinkedHashMap<String, Long>();
		buckets.put("UP_TO_DATE", mongoTemplate.count(
			new Query(activeTenantCriteria(tenantId).and("contentDate").gte(upToDateCutoff)), CV.class));
		buckets.put("REVIEW_SUGGESTED", mongoTemplate.count(
			new Query(activeTenantCriteria(tenantId).and("contentDate").gte(outdatedCutoff).lt(upToDateCutoff)), CV.class));
		buckets.put("OUTDATED", mongoTemplate.count(
			new Query(activeTenantCriteria(tenantId).and("contentDate").lt(outdatedCutoff)), CV.class));
		buckets.put("UNKNOWN", mongoTemplate.count(
			new Query(activeTenantCriteria(tenantId).and("contentDate").is(null)), CV.class));
		return buckets;
	}

	@Override
	public CVDuplicatesData.DuplicateStats duplicateStats(ObjectId tenantId) {
		var pipeline = new ArrayList<Document>();
		pipeline.addAll(duplicateGroupStages(tenantId, "personalInformation.contact.email", false));
		pipeline.add(new Document("$unionWith", new Document("coll", "cvs")
			.append("pipeline", duplicateGroupStages(tenantId, "personalInformation.contact.phone", false))));
		pipeline.add(new Document("$group", new Document("_id", null)
			.append("groupCount", new Document("$sum", 1))
			.append("excessCount", new Document("$sum", new Document("$subtract", List.of("$count", 1))))));

		var result = mongoTemplate.getCollection(mongoTemplate.getCollectionName(CV.class))
			.aggregate(pipeline).first();
		if (result == null) {
			return new CVDuplicatesData.DuplicateStats(0, 0);
		}
		return new CVDuplicatesData.DuplicateStats(
			result.get("groupCount", Number.class).longValue(),
			result.get("excessCount", Number.class).longValue());
	}

	@Override
	public CVDuplicatesData.DuplicatesPage findDuplicateGroups(ObjectId tenantId, int pageNumber, int pageSize) {
		var pipeline = new ArrayList<Document>();
		pipeline.addAll(duplicateGroupStages(tenantId, "personalInformation.contact.email", true));
		pipeline.add(new Document("$addFields", new Document("matchType", "EMAIL")));
		var phoneStages = new ArrayList<>(duplicateGroupStages(tenantId, "personalInformation.contact.phone", true));
		phoneStages.add(new Document("$addFields", new Document("matchType", "PHONE")));
		pipeline.add(new Document("$unionWith", new Document("coll", "cvs").append("pipeline", phoneStages)));
		pipeline.add(new Document("$sort", new Document("count", -1).append("_id", 1)));
		pipeline.add(new Document("$facet", new Document()
			.append("content", List.of(
				new Document("$skip", (long) pageNumber * pageSize),
				new Document("$limit", pageSize)))
			.append("total", List.of(new Document("$count", "n")))));

		var facet = mongoTemplate.getCollection(mongoTemplate.getCollectionName(CV.class))
			.aggregate(pipeline).first();

		long total = 0;
		List<CVDuplicatesData.DuplicateGroup> groups = List.of();
		if (facet != null) {
			var totalDocs = facet.getList("total", Document.class);
			total = totalDocs.isEmpty() ? 0 : totalDocs.getFirst().get("n", Number.class).longValue();
			groups = facet.getList("content", Document.class).stream()
				.map(this::toDuplicateGroup)
				.toList();
		}

		int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
		boolean hasNext = (long) (pageNumber + 1) * pageSize < total;
		return new CVDuplicatesData.DuplicatesPage(groups, pageNumber, pageSize, total, totalPages, hasNext);
	}

	@Override
	public Optional<CV> findContactMatch(ObjectId tenantId, String email, String phone, ObjectId excludeId) {
		var contactMatches = new ArrayList<Criteria>();
		if (email != null && !email.isBlank()) {
			contactMatches.add(Criteria.where("personalInformation.contact.email").is(email));
		}
		if (phone != null && !phone.isBlank()) {
			contactMatches.add(Criteria.where("personalInformation.contact.phone").is(phone));
		}
		if (contactMatches.isEmpty()) {
			return Optional.empty();
		}

		var criteria = activeTenantCriteria(tenantId)
			.orOperator(contactMatches.toArray(new Criteria[0]));
		if (excludeId != null) {
			criteria = criteria.and("_id").ne(excludeId);
		}

		Query query = new Query(criteria).limit(1);
		query.fields()
			.include("personalInformation.name")
			.include("personalInformation.contact.email")
			.include("personalInformation.contact.phone")
			.include("createdAt");
		return Optional.ofNullable(mongoTemplate.findOne(query, CV.class));
	}

	@Override
	public long bulkSetArchived(ObjectId tenantId, QualityIssueKeyEnum issueKey, List<ObjectId> ids, boolean archived) {
		Criteria criteria;
		if (issueKey != null) {
			criteria = activeTenantCriteria(tenantId).andOperator(qualityIssueCriteria(issueKey));
		} else {
			criteria = Criteria.where("tenantId").is(tenantId).and("_id").in(ids);
		}
		var update = new Update()
			.set("archived", archived)
			.set("lastUpdatedAt", Instant.now());
		return mongoTemplate.updateMulti(new Query(criteria), update, CV.class).getModifiedCount();
	}

	@Override
	public long bulkConfirmCurrent(ObjectId tenantId, List<ObjectId> ids) {
		var criteria = Criteria.where("tenantId").is(tenantId).and("_id").in(ids);
		var update = new Update()
			.set("contentDate", Instant.now())
			.set("contentDateSource", ContentDateSourceEnum.VERIFIED.name())
			.set("lastUpdatedAt", Instant.now());
		return mongoTemplate.updateMulti(new Query(criteria), update, CV.class).getModifiedCount();
	}

	/** Shared $match+$group+$match(count>1) stages for one contact field. */
	private List<Document> duplicateGroupStages(ObjectId tenantId, String field, boolean includeCvs) {
		var match = new Document("$match", new Document("tenantId", tenantId)
			.append("archived", new Document("$ne", true))
			.append(field, new Document("$exists", true).append("$nin", Arrays.asList("", null))));

		var group = new Document("_id", "$" + field).append("count", new Document("$sum", 1));
		if (includeCvs) {
			// Cap group content — a pathological group must not produce an unbounded document.
			group.append("cvs", new Document("$topN", new Document("n", 10)
				.append("sortBy", new Document("createdAt", -1))
				.append("output", new Document()
					.append("cvId", new Document("$toString", "$_id"))
					.append("name", "$personalInformation.name")
					.append("email", "$personalInformation.contact.email")
					.append("phone", "$personalInformation.contact.phone")
					.append("createdAt", "$createdAt"))));
		}

		return List.of(
			match,
			new Document("$group", group),
			new Document("$match", new Document("count", new Document("$gt", 1)))
		);
	}

	private CVDuplicatesData.DuplicateGroup toDuplicateGroup(Document doc) {
		var cvs = doc.getList("cvs", Document.class, List.of()).stream()
			.map(cv -> new CVDuplicatesData.CVSummary(
				cv.getString("cvId"),
				cv.getString("name"),
				cv.getString("email"),
				cv.getString("phone"),
				cv.getDate("createdAt") != null ? cv.getDate("createdAt").toInstant() : null))
			.toList();
		return new CVDuplicatesData.DuplicateGroup(
			doc.getString("matchType"),
			doc.get("_id") != null ? doc.get("_id").toString() : null,
			doc.get("count", Number.class).intValue(),
			cvs);
	}

	private Query buildTextQuery(List<String> textTerms, List<String> industryTerms, ObjectId tenantId) {
		if (textTerms == null || textTerms.isEmpty()) {
			return null;
		}
		String searchPhrase = String.join(" ", textTerms);
		Query query = new Query(Criteria.where("tenantId").is(tenantId).and("archived").ne(true));
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

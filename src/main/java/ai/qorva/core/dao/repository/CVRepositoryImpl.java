package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.CVFilterOptionsData;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CVRepositoryImpl implements SimilaritySearchRepository, CVQualityRepository, CVFilterOptionsRepository {

	private final MongoTemplate mongoTemplate;

	@Autowired
	public CVRepositoryImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
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

	// -------------------------------------------------------------------------
	// Filter options (facets for the CV list rail)
	// -------------------------------------------------------------------------

	/** Value facets are capped so a 50k-skill tenant does not ship a megabyte of options. */
	private static final int VALUE_FACET_LIMIT = 300;

	@Override
	public CVFilterOptionsData filterOptions(ObjectId tenantId, boolean archived) {
		var pipeline = List.of(
			new Document("$match", new Document("tenantId", tenantId)
				.append("archived", archived ? Boolean.TRUE : new Document("$ne", true))),
			new Document("$facet", new Document()
				.append("seniority", enumFacet("candidateClustering.seniorityLevel"))
				.append("leadership", enumFacet("candidateClustering.leadershipAndInfluence"))
				.append("availability", enumFacet("personalInformation.availability.status"))
				.append("skillDepth", enumFacet("candidateClustering.skillDepth"))
				.append("industries", valueFacet("searchIndex.industries"))
				.append("locations", valueFacet("searchIndex.locations"))
				.append("skills", valueFacet("searchIndex.skills"))
				.append("tags", tagsFacet())
				.append("sources", sourcesFacet())
				.append("experience", experienceFacet())));

		var facet = mongoTemplate.getCollection(mongoTemplate.getCollectionName(CV.class))
			.aggregate(pipeline).allowDiskUse(true).first();
		if (facet == null) {
			return CVFilterOptionsData.empty();
		}
		return new CVFilterOptionsData(
			options(facet, "seniority", true),
			options(facet, "leadership", true),
			options(facet, "availability", true),
			options(facet, "skillDepth", true),
			options(facet, "industries", false),
			options(facet, "locations", false),
			options(facet, "skills", false),
			options(facet, "tags", false),
			options(facet, "sources", false),
			experienceRange(facet));
	}

	/** One bucket per raw value, null bucket kept so the UI can offer "Not analysed (n)". */
	private static List<Document> enumFacet(String field) {
		return List.of(
			new Document("$group", new Document("_id", "$" + field).append("n", new Document("$sum", 1))),
			new Document("$project", new Document("_id", 0).append("value", "$_id").append("n", 1)),
			new Document("$sort", new Document("n", -1).append("value", 1)));
	}

	/**
	 * Distinct array elements, case-folded. Counted once per CV (a CV listing "Fintech" and
	 * "FinTech" is one row for the case-insensitive filter, so it must be one count here), and
	 * displayed with the most frequent original casing.
	 */
	private static List<Document> valueFacet(String field) {
		return List.of(
			new Document("$project", new Document("vals", new Document("$ifNull", List.of("$" + field, List.of())))),
			new Document("$unwind", "$vals"),
			new Document("$group", new Document("_id", new Document("doc", "$_id").append("lower", new Document("$toLower", "$vals")))
				.append("original", new Document("$first", "$vals"))),
			new Document("$group", new Document("_id", new Document("lower", "$_id.lower").append("original", "$original"))
				.append("n", new Document("$sum", 1))),
			new Document("$sort", new Document("n", -1)),
			new Document("$group", new Document("_id", "$_id.lower")
				.append("value", new Document("$first", "$_id.original"))
				.append("n", new Document("$sum", "$n"))),
			new Document("$sort", new Document("n", -1).append("value", 1)),
			new Document("$limit", VALUE_FACET_LIMIT),
			new Document("$project", new Document("_id", 0).append("value", 1).append("n", 1)));
	}

	/** Tags are user-typed and matched exactly, so no case folding — sorted alphabetically like /cvs/tags. */
	private static List<Document> tagsFacet() {
		return List.of(
			new Document("$project", new Document("vals", new Document("$setUnion",
				List.of(new Document("$ifNull", List.of("$tags", List.of())), List.of())))),
			new Document("$unwind", "$vals"),
			new Document("$group", new Document("_id", "$vals").append("n", new Document("$sum", 1))),
			new Document("$project", new Document("_id", 0).append("value", "$_id").append("n", 1)),
			new Document("$sort", new Document("value", 1)));
	}

	/** "MANUAL" for CVs without an ATS reference, otherwise each distinct provider on the CV. */
	private static List<Document> sourcesFacet() {
		var hasRefs = new Document("$gt", List.of(
			new Document("$size", new Document("$ifNull", List.of("$atsRefs", List.of()))), 0));
		return List.of(
			new Document("$project", new Document("providers", new Document("$cond", List.of(
				hasRefs,
				new Document("$setUnion", List.of("$atsRefs.provider", List.of())),
				List.of("MANUAL"))))),
			new Document("$unwind", "$providers"),
			new Document("$group", new Document("_id", "$providers").append("n", new Document("$sum", 1))),
			new Document("$project", new Document("_id", 0).append("value", "$_id").append("n", 1)),
			new Document("$sort", new Document("n", -1)));
	}

	private static List<Document> experienceFacet() {
		return List.of(
			new Document("$match", new Document("careerStartYear", new Document("$ne", null))),
			new Document("$group", new Document("_id", null)
				.append("minStart", new Document("$min", "$careerStartYear"))
				.append("maxStart", new Document("$max", "$careerStartYear"))));
	}

	private static List<CVFilterOptionsData.Option> options(Document facet, String key, boolean keepNullBucket) {
		return facet.getList(key, Document.class).stream()
			.map(d -> new CVFilterOptionsData.Option(d.getString("value"), d.get("n", Number.class).longValue()))
			.filter(o -> keepNullBucket || (o.value() != null && !o.value().isBlank()))
			.toList();
	}

	private static CVFilterOptionsData.ExperienceRange experienceRange(Document facet) {
		var rows = facet.getList("experience", Document.class);
		if (rows.isEmpty()) {
			return new CVFilterOptionsData.ExperienceRange(null, null);
		}
		int thisYear = Year.now().getValue();
		var row = rows.getFirst();
		Integer minStart = row.get("minStart", Number.class) == null ? null : row.get("minStart", Number.class).intValue();
		Integer maxStart = row.get("maxStart", Number.class) == null ? null : row.get("maxStart", Number.class).intValue();
		return new CVFilterOptionsData.ExperienceRange(
			maxStart == null ? null : Math.max(0, thisYear - maxStart),
			minStart == null ? null : Math.max(0, thisYear - minStart));
	}
}

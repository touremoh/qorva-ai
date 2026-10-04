package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrors;

import ai.qorva.core.utils.Paging;

import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.CascadeResource;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dao.querybuilder.MatchingReportQueryBuilder;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.specifications.MatchingReportSpecifications;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecifications;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.dto.common.ReportStatusChange;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.ReportStatusChannel;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.MatchingReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchingReportService extends AbstractQorvaService<MatchingReportDTO, MatchingReport> {
	protected final UserService userService;
	protected final TenantService tenantService;
	private final CascadeRegistry cascadeRegistry;
	private final MongoTemplate mongoTemplate;

	@Autowired
	public MatchingReportService(MatchingReportRepository repository, MatchingReportMapper mapper, MatchingReportQueryBuilder queryBuilder, UserService userService, TenantService tenantService, CascadeRegistry cascadeRegistry, MongoTemplate mongoTemplate) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
		this.cascadeRegistry = cascadeRegistry;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	protected void preProcessCreateOne(MatchingReportDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		// Check the job post id is present
		if (!StringUtils.hasText(dto.getJobPostId())) {
			log.warn("Job post id is empty while creating Job Application");
			throw new QorvaException(QorvaErrorCodes.REPORT_JOB_ID_REQUIRED);
		}

		// Check candidate info
		if (Objects.isNull(dto.getCandidateInfo())) {
			log.warn("Candidate info is empty while creating Job Application");
			throw new QorvaException(QorvaErrorCodes.REPORT_CANDIDATE_INFO_REQUIRED);
		}
	}

	@Override
	protected void preProcessUpdateOne(String id, MatchingReportDTO requestData) throws QorvaException {
		super.preProcessUpdateOne(id, requestData);
		this.mapper.merge(requestData, getExistingForUpdate());
	}

	/** What a matching run needs to know about a report it may reuse, re-score or retire. */
	public record ReportState(String id, String candidateId, String inputFingerprint, String cvFingerprint,
	                          Double finalScore, boolean outdated) {
	}

	/** The job's reports, keyed by candidate id — one query, fingerprints and score only. */
	public Map<String, ReportState> statesForJob(String tenantId, String jobPostId) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)).and("jobPostId").is(new ObjectId(jobPostId)));
		query.fields().include("_id", "candidateInfo.candidateId", "inputFingerprint", "cvFingerprint",
			"matchingReportDetails.decisionSummary.finalScore", "outdated");
		return mongoTemplate.find(query, MatchingReport.class).stream()
			.filter(r -> r.getCandidateInfo() != null && r.getCandidateInfo().getCandidateId() != null)
			.map(MatchingReportService::state)
			.collect(Collectors.toMap(ReportState::candidateId, Function.identity(), (a, b) -> a));
	}

	/** A candidate's current report on a job, as the staleness sweep sees it. */
	public record CandidateReport(String jobPostId, String candidateId, String cvFingerprint) {
	}

	/** The candidates' current (not outdated) reports on the given jobs, for the staleness sweep. */
	public List<CandidateReport> currentReportsOf(String tenantId, Collection<String> candidateIds, Collection<String> jobPostIds) {
		if (candidateIds.isEmpty() || jobPostIds.isEmpty()) {
			return List.of();
		}
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("candidateInfo.candidateId").in(candidateIds)
			.and("jobPostId").in(jobPostIds.stream().map(ObjectId::new).toList())
			.and("outdated").ne(true));
		query.fields().include("_id", "jobPostId", "candidateInfo.candidateId", "cvFingerprint");
		return mongoTemplate.find(query, MatchingReport.class).stream()
			.filter(r -> r.getCandidateInfo() != null)
			.map(r -> new CandidateReport(r.getJobPostId(), r.getCandidateInfo().getCandidateId(), r.getCvFingerprint()))
			.toList();
	}

	private static ReportState state(MatchingReport r) {
		var details = r.getMatchingReportDetails();
		Double score = details != null && details.getDecisionSummary() != null ? details.getDecisionSummary().getFinalScore() : null;
		return new ReportState(r.getId(), r.getCandidateInfo().getCandidateId(), r.getInputFingerprint(),
			r.getCvFingerprint(), score, Boolean.TRUE.equals(r.getOutdated()));
	}

	/** A reused report is back in the job's latest results. */
	public void markCurrent(String tenantId, String reportId) {
		mongoTemplate.updateFirst(byIdInTenant(tenantId, reportId),
			new Update().set("outdated", false).unset("outdatedReason").unset("outdatedAt"), MatchingReport.class);
	}

	/**
	 * Stores a freshly generated report: re-scores the candidate's existing report (keeping its id, notes and
	 * recruiter status, and the previous score so the list can show what moved) or creates one.
	 */
	public void saveGenerated(JobPostDTO jobPost, MatchingReportDetails details, CVDTO cv, String inputFingerprint,
	                          String cvFingerprint, ReportState existing) throws QorvaException {
		if (existing == null) {
			var report = newReport(jobPost, details, cv);
			report.setInputFingerprint(inputFingerprint);
			report.setCvFingerprint(cvFingerprint);
			report.setOutdated(false);
			this.createOne(report);
			return;
		}
		var current = findOneById(existing.id());
		if (current.getMatchingReportDetails() != null) {
			details.setDetailsID(current.getMatchingReportDetails().getDetailsID());
		}
		var updateDto = new MatchingReportDTO();
		updateDto.setTenantId(jobPost.getTenantId());
		updateDto.setMatchingReportDetails(details);
		this.updateOne(existing.id(), updateDto);

		var update = new Update()
			.set("inputFingerprint", inputFingerprint)
			.set("cvFingerprint", cvFingerprint)
			.set("outdated", false)
			.unset("outdatedReason")
			.unset("outdatedAt")
			.set("rescoredAt", Instant.now());
		if (existing.finalScore() != null) {
			update.set("previousFinalScore", existing.finalScore());
		}
		mongoTemplate.updateFirst(byIdInTenant(jobPost.getTenantId(), existing.id()), update, MatchingReport.class);
	}

	/**
	 * Retires the job's reports whose candidate is not in {@code keptCandidateIds}: they stay, badged, until the
	 * recruiter deletes them. Reports already outdated keep their first reason and date.
	 */
	public long markOutdated(String tenantId, String jobPostId, Set<String> keptCandidateIds,
	                         Function<Collection<String>, Map<String, String>> reasonsByCandidate) {
		var toRetire = statesForJob(tenantId, jobPostId).values().stream()
			.filter(r -> !r.outdated() && !keptCandidateIds.contains(r.candidateId()))
			.toList();
		if (toRetire.isEmpty()) {
			return 0;
		}
		var reasons = reasonsByCandidate.apply(toRetire.stream().map(ReportState::candidateId).toList());
		var now = Instant.now();
		var byReason = toRetire.stream().collect(Collectors.groupingBy(r -> reasons.get(r.candidateId()),
			Collectors.mapping(ReportState::id, Collectors.toList())));
		long count = 0;
		for (var entry : byReason.entrySet()) {
			var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
				.and("_id").in(entry.getValue().stream().map(ObjectId::new).toList())
				.and("outdated").ne(true));
			count += mongoTemplate.updateMulti(query,
				new Update().set("outdated", true).set("outdatedReason", entry.getKey()).set("outdatedAt", now),
				MatchingReport.class).getModifiedCount();
		}
		return count;
	}

	/** Deletes the job's outdated reports, with their notes and chats. */
	public long deleteOutdated(String tenantId, String jobPostId) throws QorvaException {
		if (!StringUtils.hasText(jobPostId) || !ObjectId.isValid(jobPostId)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.REPORT_JOB_ID_REQUIRED);
		}
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("jobPostId").is(new ObjectId(jobPostId))
			.and("outdated").is(true));
		query.fields().include("_id");
		var ids = mongoTemplate.find(query, MatchingReport.class).stream().map(MatchingReport::getId).toList();
		if (ids.isEmpty()) {
			return 0;
		}
		long deleted = mongoTemplate.remove(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("_id").in(ids.stream().map(ObjectId::new).toList())), MatchingReport.class).getDeletedCount();
		this.cascadeRegistry.parentsDeleted(CascadeResource.MATCHING_REPORT, tenantId, ids);
		log.info("Deleted {} outdated reports of job {} (tenant={})", deleted, jobPostId, tenantId);
		return deleted;
	}

	/** Who changed a status: a user (id + name at the time) or a Copilot run ({@code copilot:<runId>}). */
	public record StatusActor(String id, String name) {
		/** The signed-in user, or their email alone when the account can't be read. */
		public static StatusActor of(UserDTO user, String email) {
			if (user == null) {
				return new StatusActor(email, email);
			}
			var name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
				+ (user.getLastName() == null ? "" : user.getLastName())).trim();
			return new StatusActor(user.getId(), name.isEmpty() ? email : name);
		}

		public static StatusActor copilotRun(String runId) {
			return new StatusActor("copilot:" + runId, "Copilot");
		}

		/** True for a change made by a rule run: it must never fire a status rule again (loop guard). */
		public static boolean isCopilot(String by) {
			return by != null && by.startsWith("copilot:");
		}
	}

	/**
	 * Moves the candidate to {@code status} on this job and records the move in the report's history (last
	 * {@value MatchingReport#STATUS_HISTORY_SIZE}). A targeted update: re-scoring and outdated marking never touch
	 * these fields. Setting the current status again changes nothing.
	 */
	public MatchingReportDTO changeStatus(String tenantId, String reportId, String status, StatusActor actor,
	                                      ReportStatusChannel via) throws QorvaException {
		return changeStatus(tenantId, reportId, status, null, actor, via);
	}

	/**
	 * Same, refused with 409 when {@code expectedStatus} is given and the candidate isn't there any more — someone
	 * else moved them since the caller looked (the pipeline board sends where the card was dragged from).
	 */
	public MatchingReportDTO changeStatus(String tenantId, String reportId, String status, String expectedStatus,
	                                      StatusActor actor, ReportStatusChannel via) throws QorvaException {
		var target = ApplicationStatusEnum.parse(status)
			.orElseThrow(() -> QorvaErrors.badRequest(QorvaErrorCodes.REPORT_STATUS_INVALID));
		var expected = expectedStatus == null ? null : ApplicationStatusEnum.parse(expectedStatus)
			.orElseThrow(() -> QorvaErrors.badRequest(QorvaErrorCodes.REPORT_STATUS_INVALID));
		var current = requireInTenant(tenantId, reportId);
		if (expected != null && !expected.getStatus().equals(current.getStatus())) {
			throw QorvaErrors.conflict(QorvaErrorCodes.REPORT_STATUS_CONFLICT);
		}
		if (!target.getStatus().equals(current.getStatus())) {
			boolean moved = applyStatus(tenantId, reportId, current.getStatus(), target, actor, via);
			if (!moved && expected != null) {
				// Moved by someone else between the read and the write.
				throw QorvaErrors.conflict(QorvaErrorCodes.REPORT_STATUS_CONFLICT);
			}
			current = requireInTenant(tenantId, reportId);
		}
		return this.mapper.map(current);
	}

	/**
	 * An email went to the candidate from this report: New becomes Contacted. Any later status is kept (never
	 * downgraded). Returns whether the status changed.
	 */
	public boolean markContacted(String tenantId, String reportId, StatusActor actor) {
		if (!StringUtils.hasText(reportId) || !ObjectId.isValid(reportId)) {
			return false;
		}
		return applyStatus(tenantId, reportId, ApplicationStatusEnum.NEW.getStatus(), ApplicationStatusEnum.CONTACTED,
			actor, ReportStatusChannel.EMAIL);
	}

	/** Conditional on the status read before ({@code from}), so two concurrent moves can't both log the same "from". */
	private boolean applyStatus(String tenantId, String reportId, String from, ApplicationStatusEnum to,
	                            StatusActor actor, ReportStatusChannel via) {
		var now = Instant.now();
		var change = ReportStatusChange.builder()
			.from(from).status(to.getStatus()).by(actor.id()).byName(actor.name()).via(via.name()).at(now)
			.build();
		var query = byIdInTenant(tenantId, reportId).addCriteria(Criteria.where("status").is(from));
		var update = new Update()
			.set("status", to.getStatus())
			.set("statusChangedAt", now)
			.set("statusChangedBy", actor.id());
		update.push("statusHistory").slice(-MatchingReport.STATUS_HISTORY_SIZE).each(change);
		boolean changed = mongoTemplate.updateFirst(query, update, MatchingReport.class).getModifiedCount() > 0;
		if (changed) {
			log.info("Report {} status {} -> {} by {} via {} (tenant={})", reportId, from, to, actor.id(), via, tenantId);
		}
		return changed;
	}

	private MatchingReport requireInTenant(String tenantId, String reportId) throws QorvaException {
		if (!StringUtils.hasText(reportId) || !ObjectId.isValid(reportId)) {
			throw QorvaErrors.notFound(QorvaErrorCodes.REPORT_NOT_FOUND);
		}
		var report = mongoTemplate.findOne(byIdInTenant(tenantId, reportId), MatchingReport.class);
		if (report == null) {
			throw QorvaErrors.notFound(QorvaErrorCodes.REPORT_NOT_FOUND);
		}
		return report;
	}

	private static Query byIdInTenant(String tenantId, String reportId) {
		return Query.query(Criteria.where("_id").is(new ObjectId(reportId)).and("tenantId").is(new ObjectId(tenantId)));
	}

	public MatchingReportDTO createOne(JobPostDTO jobPostDto, MatchingReportDetails reportDetails, CVDTO cvDto) throws QorvaException {
		return this.createOne(newReport(jobPostDto, reportDetails, cvDto));
	}

	private static MatchingReportDTO newReport(JobPostDTO jobPostDto, MatchingReportDetails reportDetails, CVDTO cvDto) {
		// Set Report Details ID
		reportDetails.setDetailsID(UUID.randomUUID().toString());

		// Build Application DTO
		var matchingReportDTO = new MatchingReportDTO();

		matchingReportDTO.setJobPostId(jobPostDto.getId());
		matchingReportDTO.setJobPostTitle(jobPostDto.getTitle());
		matchingReportDTO.setTenantId(jobPostDto.getTenantId());
		matchingReportDTO.setMatchingReportDetails(reportDetails);
		matchingReportDTO.setStatus(ApplicationStatusEnum.NEW.getStatus());

		var candidateInfo = new CandidateInfo();
		candidateInfo.setCandidateName(cvDto.getPersonalInformation().getName());
		candidateInfo.setCandidateId(cvDto.getId());
		candidateInfo.setNbYearsExperience(cvDto.getNbYearsOfExperience());
		candidateInfo.setCandidateProfileSummary(cvDto.getCandidateProfileSummary());
		candidateInfo.setCandidateClustering(cvDto.getCandidateClustering());

		var skills = new ArrayList<String>();

		for (KeySkill keySkill : cvDto.getKeySkills()) {
			skills.addAll(keySkill.getSkills());
		}
		candidateInfo.setSkills(skills);

		matchingReportDTO.setCandidateInfo(candidateInfo);
		return matchingReportDTO;
	}

	@Override
	public MatchingReportDTO findOneByCriteria(MatchingReportDTO searchCriteria) throws QorvaException {
		var response =  ((MatchingReportRepository)this.repository)
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
				new ObjectId(searchCriteria.getTenantId()),
				new ObjectId(searchCriteria.getJobPostId()),
				searchCriteria.getCandidateInfo().getCandidateId()
			);
		if (response.isEmpty()) {
			// Expected state (the resume-chat dialog probes for a report before creating a chat) — a 404, not a 500.
			throw QorvaErrors.notFound(QorvaErrorCodes.REPORT_RESUME_MATCH_NOT_FOUND);
		}
		return this.mapper.map(response.get());
	}

	public Page<MatchingReportDTO> searchAll(Map<String, String> params) throws QorvaException {
		try {

			// Get parameters
			var searchTerms = params.get("searchTerms");
			var tenantId = params.get("tenantId");
			var jobPostId = params.get("jobPostId");
			var pageable = Paging.of(Paging.param(params, "pageNumber", 0), Paging.param(params, "pageSize", 10),
				Sort.by("lastUpdatedAt").descending());

			// Process
			Page<MatchingReport> results = (jobPostId == null || jobPostId.isBlank())
				? ((MatchingReportRepository)repository).searchAll(searchTerms, tenantId, pageable)
				: ((MatchingReportRepository)repository).searchAll(searchTerms, tenantId, jobPostId, pageable);

			// Render results
			return renderFindAll(results);
		} catch (Exception e) {
			throw wrapException(e, "Error finding resources by IDs");
		}
	}

	/** The job's current results — the export is an ATS shortlist, so outdated reports stay out (and out of the rank). */
	public List<MatchingReportDTO> findAllForExport(String tenantId, String jobPostId) {
		var spec = MongoSpecification.where(MongoSpecifications.<MatchingReport>ownedBy(tenantId))
			.and(MatchingReportSpecifications.jobPostIdEquals(jobPostId))
			.and(MatchingReportSpecifications.outdatedEquals("false"));
		return renderFindAll(this.repository.findAll(spec));
	}

	public List<DashboardData.ApplicationPerJobPostReport> getApplicationsPerJobPost(String tenantId) {
		return ((MatchingReportRepository) repository).getApplicationsPerJobPost(new ObjectId(tenantId));
	}

	public DashboardData.TopCandidatesPage getTopCandidatesPerJobPost(String tenantId, int page, int pageSize) {
		var pageable = PageRequest.of(page, pageSize);
		var repo = (MatchingReportRepository) repository;
		var slice = repo.getTopCandidatesPerJobPost(new ObjectId(tenantId), pageable);
		var countResult = repo.countDistinctJobPosts(new ObjectId(tenantId));
		long total = countResult != null ? countResult.total() : 0L;
		int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
		return new DashboardData.TopCandidatesPage(
			slice.getContent(), page, pageSize, total, totalPages, slice.hasNext()
		);
	}

	@Override
	protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
		log.info("Deleted Resume Match with ID: {}", id);
		// Its notes and its chats (with their messages).
		this.cascadeRegistry.parentsDeleted(CascadeResource.MATCHING_REPORT, tenantId, List.of(id));
	}
}

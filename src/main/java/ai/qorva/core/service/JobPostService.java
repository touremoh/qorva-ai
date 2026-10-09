package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.MatchingStaleReasonEnum;
import ai.qorva.core.utils.JobDescriptionHtml;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.CascadeResource;
import ai.qorva.core.dao.querybuilder.JobPostQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    /** How many new candidate ids a job remembers for its badge; past this the badge reads "100+". */
    static final int NEW_CANDIDATES_CAP = 100;

    private final CascadeRegistry cascadeRegistry;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder,
                          CascadeRegistry cascadeRegistry, MongoTemplate mongoTemplate) {
        super(repository, mapper, queryBuilder);
        this.cascadeRegistry = cascadeRegistry;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    protected void preProcessCreateOne(JobPostDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setDescription(JobDescriptionHtml.sanitize(dto.getDescription()));
        dto.setJobReference(UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        dto.setStatus(JobPostStatusEnum.OPEN.getStatus());
        dto.setMatchingReportsNeeded(matchingReportsNeededFor(dto.getStatus()));
        dto.setMatchingStaleReason(MatchingStaleReasonEnum.NEVER_RUN.name());
        dto.setMatchingStaleAt(Instant.now());
        dto.setStatusChangedAt(null);
    }

    @Override
    protected void postProcessCreateOne(JobPost entity) {
        log.debug("JobPost created with ID: {}", entity.getId());
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO newJobPost) throws QorvaException {
        super.preProcessUpdateOne(id, newJobPost);
        var existing = getExistingForUpdate();
        this.mapper.merge(newJobPost, existing);
        // The ATS link belongs to the sync engine. Taking the stored value back rather than
        // trusting the payload keeps an edit from unlinking an imported job — which left the
        // job_reference unique index holding a reference the next sync could no longer match.
        newJobPost.setAtsRef(existing != null ? existing.getAtsRef() : null);
        newJobPost.setDescription(JobDescriptionHtml.sanitize(newJobPost.getDescription()));
        applyMatchingState(existing, newJobPost);
    }

    /**
     * The matching flag after an edit. Only what the reports are computed from — title, description,
     * scoring rules — or reopening the job makes its results stale; a status-only or reference edit
     * leaves them as they were. Closing always clears the flag (only open jobs are matched).
     */
    static void applyMatchingState(JobPostDTO existing, JobPostDTO updated) {
        // When the job was last opened or closed: what a JOB_CLOSED rule watches. Server-written, never the client's.
        boolean statusChanged = existing != null && updated.getStatus() != null && !Objects.equals(existing.getStatus(), updated.getStatus());
        updated.setStatusChangedAt(statusChanged ? Instant.now() : existing != null ? existing.getStatusChangedAt() : null);
        boolean open = matchingReportsNeededFor(updated.getStatus());
        if (!open) {
            updated.setMatchingReportsNeeded(false);
            return;
        }
        // Whatever the client sent, the flag and its reason are the server's: start from what is stored.
        updated.setMatchingReportsNeeded(existing != null ? existing.getMatchingReportsNeeded() : Boolean.TRUE);
        updated.setMatchingStaleReason(existing != null ? existing.getMatchingStaleReason() : MatchingStaleReasonEnum.NEVER_RUN.name());
        boolean reopened = existing != null && !matchingReportsNeededFor(existing.getStatus());
        boolean contentChanged = existing == null
            || !MatchingFingerprint.job(existing).equals(MatchingFingerprint.job(updated));
        if (!reopened && !contentChanged) {
            return;
        }
        var reason = updated.getLastMatchedAt() == null ? MatchingStaleReasonEnum.NEVER_RUN : MatchingStaleReasonEnum.JOB_CHANGED;
        var wasStale = Boolean.TRUE.equals(updated.getMatchingReportsNeeded());
        var previousReason = updated.getMatchingStaleReason();
        var newReason = strongest(previousReason, reason).name();
        updated.setMatchingReportsNeeded(true);
        updated.setMatchingStaleReason(newReason);
        // A new "needs matching" episode — what a JOB_NEEDS_MATCHING rule fires on — starts when the job becomes
        // stale or its reason gets stronger; the same reason again is the same episode.
        if (!wasStale || !newReason.equals(previousReason)) {
            updated.setMatchingStaleAt(Instant.now());
        }
    }

    private static MatchingStaleReasonEnum strongest(String current, MatchingStaleReasonEnum candidate) {
        if (current == null) {
            return candidate;
        }
        try {
            var stored = MatchingStaleReasonEnum.valueOf(current);
            return stored.ordinal() < candidate.ordinal() ? stored : candidate;
        } catch (IllegalArgumentException e) {
            return candidate;
        }
    }

    @Override
    protected void postProcessUpdateOne(JobPost entity) {
        log.info("JobPost updated with ID: {}", entity.getId());
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) {
        log.info("JobPost deleted with ID: {}", id);
        // Its reports (and their notes and chats) and its chats (and their messages).
        this.cascadeRegistry.parentsDeleted(CascadeResource.JOB_POST, tenantId, List.of(id));
    }


    public List<JobPostDTO> findJobPostsNeedingReports(String tenantId) {
        return ((JobPostRepository) this.repository)
            .findAllJobPostNeedingScreeningReports(tenantId, JobPostStatusEnum.OPEN.getStatus(), true)
            .stream().map(mapper::map).toList();
    }

    /**
     * Open jobs that have been matched and have an embedding — the ones a new candidate can be compared with.
     * Never-matched jobs are already flagged {@code NEVER_RUN}, so the staleness sweep has nothing to add there.
     */
    public List<JobPostDTO> findMatchedOpenJobs(String tenantId) {
        var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
            .and("status").is(JobPostStatusEnum.OPEN.getStatus())
            .and("lastMatchedAt").ne(null)
            .and("embedding").exists(true));
        return mongoTemplate.find(query, JobPost.class).stream().map(mapper::map).toList();
    }

    /**
     * Flags a job's results as out of date for {@code reason}, unless a stronger reason is already recorded.
     * A new candidate's id is remembered (capped) so the badge can say how many would enter the top N.
     * Only open jobs are touched; repeated calls are harmless.
     */
    public void flagStale(String tenantId, String jobPostId, MatchingStaleReasonEnum reason, String newCandidateId) {
        var now = Instant.now();
        // Becoming stale starts an episode (stamped); a job already stale keeps its stamp...
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(jobPostId))
                .and("tenantId").is(new ObjectId(tenantId))
                .and("status").is(JobPostStatusEnum.OPEN.getStatus())
                .and("matchingReportsNeeded").ne(true)),
            new Update().set("matchingReportsNeeded", true).set("matchingStaleAt", now), JobPost.class);
        // ...unless the reason gets stronger, which is a new episode too. Never a weaker reason over a stronger one.
        var weaker = new java.util.ArrayList<>(reason.replaces());
        weaker.remove(reason.name());
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(jobPostId))
                .and("tenantId").is(new ObjectId(tenantId))
                .and("status").is(JobPostStatusEnum.OPEN.getStatus())
                .orOperator(Criteria.where("matchingStaleReason").is(null),
                    Criteria.where("matchingStaleReason").in(weaker))),
            new Update().set("matchingStaleReason", reason.name()).set("matchingStaleAt", now), JobPost.class);
        if (newCandidateId != null) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(jobPostId))
                    .and("tenantId").is(new ObjectId(tenantId))
                    .and("newCandidateIds." + (NEW_CANDIDATES_CAP - 1)).exists(false)),
                new Update().addToSet("newCandidateIds", newCandidateId), JobPost.class);
        }
    }

    /**
     * Safety net when candidates cannot be compared one by one (their embedding never arrived): every matched
     * open job is flagged, as every open job used to be on any CV change.
     */
    public void markOpenJobPostsAsNeedingReports(String tenantId) {
        var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
            .and("status").is(JobPostStatusEnum.OPEN.getStatus()));
        query.fields().include("_id");
        var ids = mongoTemplate.find(query, JobPost.class).stream().map(JobPost::getId).toList();
        ids.forEach(id -> flagStale(tenantId, id, MatchingStaleReasonEnum.NEW_CANDIDATES, null));
        log.debug("Marked {} open job posts as needing reports for tenant={}", ids.size(), tenantId);
    }

    /**
     * Records a finished run: when, with which Top N, and the similarity a new candidate must now beat
     * ({@code cutoffScore}). A run with failed candidates leaves the job flagged so it can be re-run.
     */
    public void recordRun(String tenantId, String jobPostId, int topN, double cutoffScore, boolean complete) {
        var update = new Update()
            .set("matchingTopN", topN)
            .set("lastMatchedAt", Instant.now())
            .set("matchingCutoffScore", cutoffScore);
        if (complete) {
            update.set("matchingReportsNeeded", false).unset("matchingStaleReason").unset("matchingStaleAt").unset("newCandidateIds");
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(jobPostId))
            .and("tenantId").is(new ObjectId(tenantId))), update, JobPost.class);
    }

    /**
     * The one rule tying the screening flag to a job's status: only an open job is ever
     * screened. Every writer of {@code status} — the API edit path above and the ATS sync —
     * must derive the flag from here. The screening run only clears the flag on open jobs
     * (see {@code findJobPostsNeedingReports}), so a closed job left flagged is never
     * cleared, and the app counts it as pending on every poll until its timeout.
     */
    public static boolean matchingReportsNeededFor(String status) {
        return JobPostStatusEnum.OPEN.getStatus().equals(status);
    }
}

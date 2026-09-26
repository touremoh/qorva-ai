package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.CascadeResource;
import ai.qorva.core.dao.querybuilder.JobPostQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    private final CascadeRegistry cascadeRegistry;

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder, CascadeRegistry cascadeRegistry) {
        super(repository, mapper, queryBuilder);
        this.cascadeRegistry = cascadeRegistry;
    }

    @Override
    protected void preProcessCreateOne(JobPostDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setJobReference(UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        dto.setStatus(JobPostStatusEnum.OPEN.getStatus());
        dto.setMatchingReportsNeeded(matchingReportsNeededFor(dto.getStatus()));
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
        newJobPost.setMatchingReportsNeeded(matchingReportsNeededFor(newJobPost.getStatus()));
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

    public void markOpenJobPostsAsNeedingReports(String tenantId) {
        var entities = ((JobPostRepository) this.repository)
            .findAllJobPostNeedingScreeningReports(tenantId, JobPostStatusEnum.OPEN.getStatus(), false);

        if (!entities.isEmpty()) {
            entities.forEach(e -> e.setMatchingReportsNeeded(true));
            this.repository.saveAll(entities);
        }
        log.debug("Marked {} open job posts as needing reports for tenant={}", entities.size(), tenantId);
    }

    public void clearMatchingReportsNeeded(String jobPostId) {
        (this.repository).findById(new ObjectId(jobPostId)).ifPresent(e -> {
            e.setMatchingReportsNeeded(false);
            this.repository.save(e);
        });
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

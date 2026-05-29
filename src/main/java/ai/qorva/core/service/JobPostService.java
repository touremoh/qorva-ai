package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.dao.querybuilder.JobPostQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    private final MatchingReportRepository matchingReportRepository;
    private final ChatsRepository chatsRepository;

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder, MatchingReportRepository matchingReportRepository, ChatsRepository chatsRepository) {
        super(repository, mapper, queryBuilder);
        this.matchingReportRepository = matchingReportRepository;
        this.chatsRepository = chatsRepository;
    }

    @Override
    protected void preProcessCreateOne(JobPostDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setStatus(JobPostStatusEnum.OPEN.getStatus());
        dto.setMatchingReportsNeeded(true);
    }

    @Override
    protected void postProcessCreateOne(JobPost entity) {
        log.debug("JobPost created with ID: {}", entity.getId());
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO newJobPost) throws QorvaException {
        super.preProcessUpdateOne(id, newJobPost);
        this.mapper.merge(newJobPost, getExistingForUpdate());
        newJobPost.setMatchingReportsNeeded(isJobPostOpen(newJobPost));
    }

    @Override
    protected void postProcessUpdateOne(JobPost entity) {
        log.info("JobPost updated with ID: {}", entity.getId());
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) {
        log.info("JobPost deleted with ID: {}", id);

        var countDeletedReports = this.matchingReportRepository.deleteByTenantIdAndJobPostId(tenantId, id);
        log.info("Deleted {} CV report for job post {}", countDeletedReports, id);

        var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextJobPostId(tenantId, id);
        log.info("Deleted {} chats for job post {}", countDeletedChats, id);
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

    private boolean isJobPostOpen(JobPostDTO jobPostDTO) {
        return JobPostStatusEnum.OPEN.getStatus().equals(jobPostDTO.getStatus());
    }
}

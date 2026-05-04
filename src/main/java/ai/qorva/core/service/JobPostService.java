package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.ResumeMatchRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.events.NewJobPostEvent;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.dao.querybuilder.JobPostQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    private final ApplicationEventPublisher publisher;
    private final EmbeddingModel embeddingModel;
    private final ResumeMatchRepository resumeMatchRepository;
    private final ChatsRepository chatsRepository;

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder, ApplicationEventPublisher publisher, EmbeddingModel embeddingModel, ResumeMatchRepository resumeMatchRepository, ChatsRepository chatsRepository) {
        super(repository, mapper, queryBuilder);
		this.publisher = publisher;
		this.embeddingModel = embeddingModel;
		this.resumeMatchRepository = resumeMatchRepository;
		this.chatsRepository = chatsRepository;
    }

    @Override
    protected void preProcessCreateOne(JobPostDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);

        // Create a vector embedding for the job post
        dto.setEmbedding(this.embeddingModel.embed(dto.toJobTitleAndDescription()));
        dto.setStatus(JobPostStatusEnum.OPEN.getStatus());
    }

    @Override
    protected void postProcessCreateOne(JobPost entity) {
        log.debug("JobPost created with ID: {}", entity.getId());
        this.publisher.publishEvent(new NewJobPostEvent(this.mapper.map(entity)));
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO newJobPost) throws QorvaException {
        super.preProcessUpdateOne(id, newJobPost);

        // Find user by id
        var existingJobPost = this.findOneById(id);

        // Update newJobPost
        this.mapper.merge(newJobPost, existingJobPost);

        // Create a vector embedding for the job post
        newJobPost.setEmbedding(this.embeddingModel.embed(newJobPost.toJobTitleAndDescription()));
    }

    @Override
    protected void postProcessUpdateOne(JobPost entity) {
        log.info("JobPost updated with ID: {}", entity.getId());
        this.publisher.publishEvent(new NewJobPostEvent(this.mapper.map(entity)));
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) {
        log.info("JobPost deleted with ID: {}", id);

        // Delete all CV report for this job post
        var countDeletedReports = this.resumeMatchRepository.deleteByTenantIdAndJobPostId(tenantId, id);

        // Delete all CV report for this job post
        log.info("Deleted {} CV report for job post {}", countDeletedReports, id);

        // Delete all chat for this job post
        var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextJobPostId(tenantId, id);

        // Delete all chat for this job post
        log.info("Deleted {} chats for job post {}", countDeletedChats, id);
    }
}

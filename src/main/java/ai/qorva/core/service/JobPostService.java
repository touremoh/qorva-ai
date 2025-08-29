package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.events.CVScreeningEvent;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.qbe.JobPostQueryBuilder;
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

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder, ApplicationEventPublisher publisher, EmbeddingModel embeddingModel) {
        super(repository, mapper, queryBuilder);
		this.publisher = publisher;
		this.embeddingModel = embeddingModel;
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
        this.publisher.publishEvent(new CVScreeningEvent(this.mapper.map(entity)));
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO jobPostDTO) throws QorvaException {
        super.preProcessUpdateOne(id, jobPostDTO);

        // Find user by id
        var foundJobPost = this.findOneById(id);

        // Update jobPostDTO
        this.mapper.merge(jobPostDTO, foundJobPost);

        // Create a vector embedding for the job post
        jobPostDTO.setEmbedding(this.embeddingModel.embed(jobPostDTO.toJobTitleAndDescription()));
    }

    @Override
    protected void postProcessUpdateOne(JobPost entity) {
        log.info("JobPost updated with ID: {}", entity.getId());
        this.publisher.publishEvent(new CVScreeningEvent(this.mapper.map(entity)));
    }
}

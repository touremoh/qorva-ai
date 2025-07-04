package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import ai.qorva.core.qbe.JobPostQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, JobPostQueryBuilder queryBuilder) {
        super(repository, mapper, queryBuilder);
	}

    @Override
    protected void preProcessCreateOne(JobPostDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setStatus(JobPostStatusEnum.OPEN.getStatus());
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO jobPostDTO) throws QorvaException {
        super.preProcessUpdateOne(id, jobPostDTO);

        // Find user by id
        var foundJobPost = this.findOneById(id);

        // Update jobPostDTO
        this.mapper.merge(jobPostDTO, foundJobPost);
    }

    @Override
    protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        super.preProcessDeleteOneById(id, tenantId);

        // Find the resource before it get deleted
        var jobPostToDelete = this.findOneById(id);

        // Check if the user is deleting an owned resource
        if (!jobPostToDelete.getTenantId().equals(tenantId)) {
            log.warn("Job post company id {} does not match authenticated company id {}", id, tenantId);
            throw new QorvaException(
                "Impossible to delete this resource",
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus().value(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus()
            );
        }
    }
}

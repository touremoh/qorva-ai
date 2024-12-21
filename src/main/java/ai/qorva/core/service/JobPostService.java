package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobPostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    private final UserService userService;

    @Autowired
    public JobPostService(JobPostRepository repository, JobPostMapper mapper, UserService userService) {
        super(repository, mapper);
		this.userService = userService;
	}

    @Override
    protected void preProcessCreateOne(JobPostDTO jobPost) throws QorvaException {
        super.preProcessCreateOne(jobPost);

        // Get Authenticated User Info
        var userInfo = this.userService.findOneByEmail(this.getAuthenticatedUsername());

        // Set company id if not exists
        jobPost.setCompanyId(userInfo.getCompanyId());
        jobPost.setCreatedBy(userInfo.getId());
        jobPost.setLastUpdatedBy(userInfo.getId());
    }

    @Override
    protected void preProcessUpdateOne(String id, JobPostDTO jobPostDTO) throws QorvaException {
        super.preProcessUpdateOne(id, jobPostDTO);

        // Find user by id
        var foundJobPost = this.findOneById(id);

        // Update jobPostDTO
        this.mapper.merge(jobPostDTO, foundJobPost);

        // Get The Current User
        var userInfo = this.userService.findOneByEmail(this.getAuthenticatedUsername());

        // Update Last Updated By
        jobPostDTO.setLastUpdatedBy(userInfo.getId());
    }

    @Override
    protected void preProcessDeleteOneById(String id) throws QorvaException {
        super.preProcessDeleteOneById(id);

        // Find the resource before it get deleted
        var jobPostToDelete = this.findOneById(id);

        // Get the authenticated company id
        var companyId = this.getAuthenticatedCompanyId();

        // Check if the user is deleting an owned resource
        if (!jobPostToDelete.getCompanyId().equals(companyId)) {
            log.warn("Job post company id {} does not match authenticated company id {}", id, companyId);
            throw new QorvaException(
                "Impossible to delete this resource",
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus().value(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus()
            );
        }
    }
}

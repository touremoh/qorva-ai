package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobPostService extends AbstractQorvaService<JobPostDTO, JobPost> {

    @Autowired
    public JobPostService(QorvaRepository<JobPost> repository, AbstractQorvaMapper<JobPost, JobPostDTO> mapper) {
        super(repository, mapper);
    }

    @Override
    protected void preProcessCreateOne(JobPostDTO input) throws QorvaException {
        super.preProcessCreateOne(input);

        // Make sure the job post has companyId
    }
}

package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobApplication;
import ai.qorva.core.dao.repository.JobApplicationRepository;
import ai.qorva.core.dto.JobApplicationDTO;
import ai.qorva.core.mapper.JobApplicationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CandidateService extends AbstractQorvaService<JobApplicationDTO, JobApplication> {

    @Autowired
    public CandidateService(JobApplicationRepository repository, JobApplicationMapper candidateMapper) {
        super(repository, candidateMapper);
    }
}

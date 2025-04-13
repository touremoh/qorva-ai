package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.JobApplication;
import ai.qorva.core.dto.JobApplicationDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobApplicationMapper extends AbstractQorvaMapper<JobApplication, JobApplicationDTO> {
}

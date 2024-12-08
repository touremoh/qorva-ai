package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dto.JobPostDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobPostMapper extends AbstractQorvaMapper<JobPost, JobPostDTO> {
}

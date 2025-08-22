package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.JobPostDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface JobPostRequestMapper extends QorvaRequestMapper<JobPostDTO> {
	@Override
	@Mapping(target = "embedding", ignore = true)
	JobPostDTO toDto(Map<String, String> params);
}

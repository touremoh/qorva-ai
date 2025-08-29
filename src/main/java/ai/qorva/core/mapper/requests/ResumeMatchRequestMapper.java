package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.ResumeMatchDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface ResumeMatchRequestMapper extends QorvaRequestMapper<ResumeMatchDTO> {
	@Override
	@Mapping(target = "candidateInfo", ignore = true)
	@Mapping(target = "aiAnalysisReportDetails", ignore = true)
	ResumeMatchDTO toDto(Map<String, String> params);
}

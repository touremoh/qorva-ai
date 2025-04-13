package ai.qorva.core.mapper;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.CVScreeningReportOutputDTO;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OpenAIResultMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "tenantId", ignore = true)
	@Mapping(target = "attachment", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "lastUpdatedAt", ignore = true)
	CVDTO map(CVOutputDTO data);


	AIAnalysisReportDetails map(CVScreeningReportOutputDTO data);
}

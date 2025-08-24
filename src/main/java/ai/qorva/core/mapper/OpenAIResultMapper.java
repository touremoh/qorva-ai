package ai.qorva.core.mapper;

import ai.qorva.core.dto.*;
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

	@Mapping(target = "promptTokens", expression = "java(java.lang.Long.valueOf(0))")
	@Mapping(target = "completionTokens", expression = "java(java.lang.Long.valueOf(0))")
	@Mapping(target = "model", expression = "java(org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_O_MINI.getValue())")
	ChatResult map(OpenAIChatResponse data);
}

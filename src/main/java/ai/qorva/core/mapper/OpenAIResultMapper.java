package ai.qorva.core.mapper;

import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OpenAIResultMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "tenantId", ignore = true)
	@Mapping(target = "applicantNumber", ignore = true)
	@Mapping(target = "attachment", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "lastUpdatedAt", ignore = true)
	CVDTO map(CVOutputDTO data);


	MatchingReportDetails map(MatchingReportResponse data);

	MissingSkills.SkillEntry map(MatchingReportResponse.MissingSkills.SkillEntry entry);

	DecisionSummary map(MatchingReportResponse.DecisionSummary decisionSummary);

	Strength map(MatchingReportResponse.Strength strength);

	Weakness map(MatchingReportResponse.Weakness weakness);

	RedFlag map(MatchingReportResponse.RedFlag redFlag);

	@Mapping(target = "promptTokens", expression = "java(java.lang.Long.valueOf(0))")
	@Mapping(target = "completionTokens", expression = "java(java.lang.Long.valueOf(0))")
	@Mapping(target = "model", expression = "java(org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_5_CHAT_LATEST.getValue())")
	ChatResult map(OpenAIChatResponse data);
}

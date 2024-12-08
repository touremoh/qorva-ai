package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.AIPrompt;
import ai.qorva.core.dto.AIPromptDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AIPromptMapper extends AbstractQorvaMapper<AIPrompt, AIPromptDTO> {
}

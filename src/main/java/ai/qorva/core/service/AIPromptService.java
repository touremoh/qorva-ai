package ai.qorva.core.service;

import ai.qorva.core.dao.entity.AIPrompt;
import ai.qorva.core.dao.repository.AIPromptRepository;
import ai.qorva.core.dto.AIPromptDTO;
import ai.qorva.core.mapper.AIPromptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIPromptService extends AbstractQorvaService<AIPromptDTO, AIPrompt> {

    @Autowired
    public AIPromptService(AIPromptRepository repository, AIPromptMapper mapper) {
        super(repository, mapper);
    }
}

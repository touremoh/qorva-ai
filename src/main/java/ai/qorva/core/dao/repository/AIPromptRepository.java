package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AIPrompt;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class AIPromptRepository extends AbstractQorvaRepository<AIPrompt> {
    public AIPromptRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, AIPrompt.class);
    }
}

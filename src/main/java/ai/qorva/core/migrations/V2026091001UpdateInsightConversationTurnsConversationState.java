package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260910_01__UpdateInsightConversationTurnsConversationState", order = "20260910_01", author = "qorva")
public class V2026091001UpdateInsightConversationTurnsConversationState extends AbstractQorvaDbMigration {

    @Execution
    public void execute(MongoDatabase db) {
        updateCollection(
            db,
            "20260910_01__update_insight_conversation_turns_schema.json",
            "V20260910_01 – adding englishQuestion, queryParams and awaitingClarification to insight_conversation_turns schema"
        );
        log.info("V20260910_01 – insight_conversation_turns schema updated");
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        log.warn("V20260910_01 rollback – restoring previous insight_conversation_turns schema");
        updateCollection(
            db,
            "20260617_02__update_insight_conversation_turns_schema.json",
            "V20260910_01 rollback – reverting to the pre-conversation-state schema"
        );
    }
}

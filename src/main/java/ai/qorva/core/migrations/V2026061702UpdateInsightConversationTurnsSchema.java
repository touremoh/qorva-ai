package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260617_02__UpdateInsightConversationTurnsSchema", order = "20260617_02", author = "qorva")
public class V2026061702UpdateInsightConversationTurnsSchema extends AbstractQorvaDbMigration {

    @Execution
    public void execute(MongoDatabase db) {
        updateCollection(
            db,
            "20260617_02__update_insight_conversation_turns_schema.json",
            "V20260617_02 – adding applicantNumber to candidates and rawData to response in insight_conversation_turns schema"
        );
        log.info("V20260617_02 – insight_conversation_turns schema updated");
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        log.warn("V20260617_02 rollback – restoring previous insight_conversation_turns schema");
        updateCollection(
            db,
            "20260530_01__create_insight_conversation_turns_collection.json",
            "V20260617_02 rollback – reverting to original schema"
        );
    }
}

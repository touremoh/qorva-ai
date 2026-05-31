package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
@ChangeUnit(id = "V20260530_01__CreateInsightConversationTurnsCollection", order = "20260530_01", author = "qorva")
public class V2026053001CreateInsightConversationTurnsCollection extends AbstractQorvaDbMigration {

    private static final String COLLECTION = "insight_conversation_turns";
    private static final String DDL_FILE = "20260530_01__create_insight_conversation_turns_collection.json";

    @Execution
    public void execute(MongoDatabase db) {
        log.info("V20260530_01 – ensuring insight_conversation_turns collection exists and applying schema validator");

        boolean exists = db.listCollectionNames()
            .into(new ArrayList<>())
            .contains(COLLECTION);

        if (!exists) {
            db.createCollection(COLLECTION);
            log.info("V20260530_01 – insight_conversation_turns collection created");
        }

        updateCollection(db, DDL_FILE, "V20260530_01 – insight_conversation_turns schema validator applied");

        // Index for single-conversation history lookups
        db.getCollection(COLLECTION).createIndex(
            new Document("conversationId", 1).append("tenantId", 1).append("initiatedBy", 1),
            new IndexOptions().name("conversation_tenant_user_idx")
        );
        // Index for listing all conversations for a user, sorted by time
        db.getCollection(COLLECTION).createIndex(
            new Document("tenantId", 1).append("initiatedBy", 1).append("createdAt", 1),
            new IndexOptions().name("tenant_user_created_idx")
        );
        log.info("V20260530_01 – indexes created");
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        log.warn("V20260530_01 rollback – removing schema validator from insight_conversation_turns collection");
        db.runCommand(new Document("collMod", COLLECTION)
            .append("validator", new Document())
            .append("validationLevel", "off")
        );
    }
}

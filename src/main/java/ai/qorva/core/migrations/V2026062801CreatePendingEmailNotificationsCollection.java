package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
@ChangeUnit(id = "V20260628_01__CreatePendingEmailNotificationsCollection", order = "20260628_01", author = "qorva")
public class V2026062801CreatePendingEmailNotificationsCollection extends AbstractQorvaDbMigration {

    private static final String COLLECTION = "pending_email_notifications";
    private static final String DDL_FILE = "20260628_01__create_pending_email_notifications_collection.json";

    @Execution
    public void execute(MongoDatabase db) {
        log.info("V20260628_01 – ensuring pending_email_notifications collection exists");

        boolean exists = db.listCollectionNames()
            .into(new ArrayList<>())
            .contains(COLLECTION);

        if (!exists) {
            db.createCollection(COLLECTION);
            log.info("V20260628_01 – pending_email_notifications collection created");
        }

        updateCollection(db, DDL_FILE, "V20260628_01 – pending_email_notifications schema validator applied");
        log.info("V20260628_01 – pending_email_notifications setup complete");
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        log.warn("V20260628_01 rollback – removing schema validator from pending_email_notifications");
        db.runCommand(new Document("collMod", COLLECTION)
            .append("validator", new Document())
            .append("validationLevel", "off")
        );
    }
}

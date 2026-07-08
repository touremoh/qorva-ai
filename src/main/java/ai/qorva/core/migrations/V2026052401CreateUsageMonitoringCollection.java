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
@ChangeUnit(id = "V20260524_01__CreateUsageMonitoringCollection", order = "20260524_01", author = "qorva")
public class V2026052401CreateUsageMonitoringCollection extends AbstractQorvaDbMigration {

    private static final String COLLECTION = "usage_monitoring";
    private static final String DDL_FILE = "20260524_01__create_usage_monitoring_collection.json";

    @Execution
    public void execute(MongoDatabase db) {
        log.info("V20260524_01 – ensuring usage_monitoring collection exists");

        boolean exists = db.listCollectionNames()
            .into(new ArrayList<>())
            .contains(COLLECTION);

        if (!exists) {
            db.createCollection(COLLECTION);
            log.info("V20260524_01 – usage_monitoring collection created");
        }

        updateCollection(db, DDL_FILE, "V20260524_01 – usage_monitoring schema validator applied");
        log.info("V20260524_01 – usage_monitoring setup complete");
    }

    @RollbackExecution
    public void rollback(MongoDatabase db) {
        log.warn("V20260524_01 rollback – removing schema validator from usage_monitoring");
        db.runCommand(new Document("collMod", COLLECTION)
            .append("validator", new Document())
            .append("validationLevel", "off")
        );
    }
}

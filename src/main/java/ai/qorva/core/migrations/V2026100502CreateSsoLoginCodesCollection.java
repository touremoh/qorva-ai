package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Microsoft sign-in hand-off codes: one row per code (its hash), exchanged once within a minute; the TTL index
 * removes those never used.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_02__CreateSsoLoginCodesCollection", order = "20261005_02", author = "qorva")
public class V2026100502CreateSsoLoginCodesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "sso_login_codes";
	private static final String DDL_FILE = "20261005_02__create_sso_login_codes_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20261005_02 – creating sso_login_codes collection", DDL_FILE);
		var codes = db.getCollection(COLLECTION);
		codes.createIndex(new Document("codeHash", 1), new IndexOptions().name("sso_login_codes_hash_uniq").unique(true));
		codes.createIndex(new Document("expiresAt", 1), new IndexOptions().name("sso_login_codes_ttl_idx").expireAfter(0L, TimeUnit.SECONDS));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261005_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}

package ai.qorva.core.migrations;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Drops the indexes on the raw contact fields: since V20260928_01 every duplicate check (report,
 * groups list, upload warning) reads the normalised {@code contactKeys}. The only remaining filter
 * on the raw email — the CV list quick search — is an unanchored case-insensitive regex inside an
 * {@code $or} of unindexed fields, which never used these indexes.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260929_01__DropRawContactIndexes", order = "20260929_01", author = "qorva")
public class V2026092901DropRawContactIndexes extends AbstractQorvaDbMigration {

	static final String EMAIL_INDEX = "tenant_contact_email_idx";
	static final String PHONE_INDEX = "tenant_contact_phone_idx";
	/** MongoDB's code for "index not found". */
	private static final int INDEX_NOT_FOUND = 27;

	@Execution
	public void execute(MongoDatabase db) {
		var cvs = db.getCollection("cvs");
		for (var index : new String[]{EMAIL_INDEX, PHONE_INDEX}) {
			try {
				cvs.dropIndex(index);
			} catch (MongoCommandException e) {
				if (e.getErrorCode() != INDEX_NOT_FOUND) throw e;
				log.info("V20260929_01 – index {} already absent", index);
			}
		}
		log.info("V20260929_01 – raw contact indexes dropped");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260929_01 rollback – recreating the raw contact indexes");
		var cvs = db.getCollection("cvs");
		cvs.createIndex(new Document("tenantId", 1).append("personalInformation.contact.email", 1),
			new IndexOptions().name(EMAIL_INDEX));
		cvs.createIndex(new Document("tenantId", 1).append("personalInformation.contact.phone", 1),
			new IndexOptions().name(PHONE_INDEX));
	}
}

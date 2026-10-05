package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Removes what processed emails kept in the queue: invite temporary passwords in clear, set-password and reset
 * links. New code never stores them and clears every payload once an email is sent or finally fails; this clears
 * the rows written before. Pending rows keep theirs (they still have to be sent). Idempotent.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_05__ClearSentEmailPayloads", order = "20261005_05", author = "qorva")
public class V2026100505ClearSentEmailPayloads extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var result = db.getCollection("pending_email_notifications").updateMany(
			new Document("status", new Document("$in", List.of("SENT", "FAILED"))).append("payload", new Document("$exists", true)),
			new Document("$unset", new Document("payload", "")));
		log.info("V20261005_05 – payload removed from {} processed email(s)", result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		// Nothing to restore: the removed values were credentials that should never have been kept.
		log.warn("V20261005_05 rollback – nothing to undo");
	}
}

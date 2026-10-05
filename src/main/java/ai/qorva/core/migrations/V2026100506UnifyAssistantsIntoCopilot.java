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
 * AI Resume Chat and Talent Intelligence become Copilot tools (decision 2026-10-05):
 * <ul>
 *   <li>drops {@code chats}, {@code chat_messages} and {@code insight_conversation_turns}, without migration
 *       (no customers yet; confirmed by the owner);</li>
 *   <li>grants USE_AGENT to demo users (they held VIEW_LIBRARY_INSIGHTS and never USE_AGENT), then removes the chat
 *       and library-insights actions from every user — USE_AGENT covers them now;</li>
 *   <li>lets agent runs hold the conversation focus, a library analysis's blocks and its Talent Intelligence frame.</li>
 * </ul>
 * Values are spelled out rather than read from the enums, which no longer have them.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_06__UnifyAssistantsIntoCopilot", order = "20261005_06", author = "qorva")
public class V2026100506UnifyAssistantsIntoCopilot extends AbstractQorvaDbMigration {

	private static final String USERS = "users";
	private static final String USE_AGENT = "USE_AGENT";
	private static final String LIBRARY_INSIGHTS = "VIEW_LIBRARY_INSIGHTS";
	static final List<String> REMOVED_ACTIONS = List.of(
		"START_CHAT", "VIEW_CHAT", "VIEW_MESSAGE", "REPLY_MESSAGE", "MODIFY_CHAT", "DELETE_CHAT", LIBRARY_INSIGHTS);
	static final List<String> DROPPED_COLLECTIONS = List.of("chats", "chat_messages", "insight_conversation_turns");

	private static final String DDL_FILE = "20261005_06__update_agent_runs_collection_answers.json";
	private static final String PREVIOUS_DDL_FILE = "20260930_02__create_agent_runs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		DROPPED_COLLECTIONS.forEach(collection -> dropCollection(db, collection));

		moveToCopilot(db, USERS);

		updateCollection(db, DDL_FILE, "V20261005_06 – agent_runs schema validator: focus, blocks, insightFrame");
	}

	/** Grants USE_AGENT where VIEW_LIBRARY_INSIGHTS was held without it, then removes the old actions. Idempotent. */
	static void moveToCopilot(MongoDatabase db, String collection) {
		var users = db.getCollection(collection);
		var granted = users.updateMany(
			new Document("$and", List.of(
				new Document("authorities.action", LIBRARY_INSIGHTS),
				new Document("authorities.action", new Document("$ne", USE_AGENT)))),
			new Document("$push", new Document("authorities",
				new Document("role", "ACCOUNT_OWNER").append("action", USE_AGENT).append("permission", "ALLOWED"))));
		var revoked = users.updateMany(new Document(),
			new Document("$pull", new Document("authorities", new Document("action", new Document("$in", REMOVED_ACTIONS)))));
		log.info("V20261005_06 – granted {} to {} users, removed chat and library-insights actions from {} users",
			USE_AGENT, granted.getModifiedCount(), revoked.getModifiedCount());
	}

	/** The validator only: dropped data and removed actions are not restored (the features no longer exist). */
	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261005_06 rollback – restoring the previous agent_runs schema validator");
	}
}

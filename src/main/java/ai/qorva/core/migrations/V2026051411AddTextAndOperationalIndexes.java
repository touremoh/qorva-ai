package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260514_11__AddTextAndOperationalIndexes", order = "20260514_11", author = "qorva")
public class V2026051411AddTextAndOperationalIndexes extends AbstractQorvaDbMigration {

	private static final String TENANT_ID = "tenantId";

	@Execution
	public void execute(MongoDatabase db) {
		createCvsTextIndex(db);
		createJobPostsTextIndex(db);
		createMatchingReportsTextIndex(db);
		createUsersEmailCompanyIndex(db);
		createChatsIndexes(db);
		createChatMessagesIndexes(db);
	}

	private void createCvsTextIndex(MongoDatabase db) {
		db.getCollection("cvs").createIndex(
			Indexes.compoundIndex(
				Indexes.text("personalInformation.name"),
				Indexes.text("personalInformation.contact.email"),
				Indexes.text("personalInformation.role"),
				Indexes.text("tags"),
				Indexes.text("keySkills.category"),
				Indexes.text("keySkills.skills"),
				Indexes.text("skillsAndQualifications.softSkills"),
				Indexes.text("skillsAndQualifications.technicalSkills"),
				Indexes.text("profiles.areasOfExpertise"),
				Indexes.text("applicantNumber")
			),
			new IndexOptions().name("cvs_text_search_idx")
		);
		log.info("V20260514_11 – cvs text search index created");
	}

	private void createJobPostsTextIndex(MongoDatabase db) {
		db.getCollection("job_posts").createIndex(
			Indexes.compoundIndex(
				Indexes.text("title"),
				Indexes.text("description")
			),
			new IndexOptions().name("job_posts_text_search_idx")
		);
		log.info("V20260514_11 – job_posts text search index created");
	}

	private void createMatchingReportsTextIndex(MongoDatabase db) {
		db.getCollection("matching_reports").createIndex(
			Indexes.compoundIndex(
				Indexes.text("candidateInfo.candidateName"),
				Indexes.text("candidateInfo.skills")
			),
			new IndexOptions().name("matching_reports_text_search_idx")
		);
		log.info("V20260514_11 – matching_reports text search index created");
	}

	private void createUsersEmailCompanyIndex(MongoDatabase db) {
		db.getCollection("users").createIndex(
			Indexes.compoundIndex(
				Indexes.ascending("email"),
				Indexes.ascending("companyInfo.tenantId")
			),
			new IndexOptions().unique(true).name("unique_email_company_idx")
		);
		log.info("V20260514_11 – users unique email+company index created");
	}

	private void createChatsIndexes(MongoDatabase db) {
		var chats = db.getCollection("chats");

		chats.createIndex(
			Indexes.compoundIndex(
				Indexes.ascending(TENANT_ID),
				Indexes.ascending("participants.userId"),
				Indexes.ascending("status"),
				Indexes.descending("lastUpdatedAt")
			),
			new IndexOptions().name("chats_tenant_participant_status_idx")
		);

		chats.createIndex(
			Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.ascending("context.cvId")),
			new IndexOptions().name("chats_tenant_cv_idx")
		);

		chats.createIndex(
			Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.ascending("context.jobPostId")),
			new IndexOptions().name("chats_tenant_job_idx")
		);

		chats.createIndex(
			Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.ascending("context.matchingReportId")),
			new IndexOptions().name("chats_tenant_matching_report_idx")
		);

		// Compound text index: tenantId prefix + title full-text + metadata.tags filter
		chats.createIndex(
			new Document(TENANT_ID, 1).append("title", "text").append("metadata.tags", 1),
			new IndexOptions().name("chats_text_search_idx")
		);

		log.info("V20260514_11 – chats indexes created");
	}

	private void createChatMessagesIndexes(MongoDatabase db) {
		var chatMessages = db.getCollection("chat_messages");

		chatMessages.createIndex(
			Indexes.compoundIndex(
				Indexes.ascending(TENANT_ID),
				Indexes.ascending("chatId"),
				Indexes.ascending("createdAt")
			),
			new IndexOptions().name("chat_messages_timeline_idx")
		);

		chatMessages.createIndex(
			Indexes.compoundIndex(
				Indexes.ascending(TENANT_ID),
				Indexes.ascending("chatId"),
				Indexes.ascending("role"),
				Indexes.ascending("createdAt")
			),
			new IndexOptions().name("chat_messages_role_timeline_idx")
		);

		log.info("V20260514_11 – chat_messages indexes created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_11 rollback – dropping text and operational indexes");
		try { db.getCollection("cvs").dropIndex("cvs_text_search_idx"); } catch (Exception ignored) {}
		try { db.getCollection("job_posts").dropIndex("job_posts_text_search_idx"); } catch (Exception ignored) {}
		try { db.getCollection("matching_reports").dropIndex("matching_reports_text_search_idx"); } catch (Exception ignored) {}
		try { db.getCollection("users").dropIndex("unique_email_company_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chats").dropIndex("chats_tenant_participant_status_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chats").dropIndex("chats_tenant_cv_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chats").dropIndex("chats_tenant_job_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chats").dropIndex("chats_tenant_matching_report_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chats").dropIndex("chats_text_search_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chat_messages").dropIndex("chat_messages_timeline_idx"); } catch (Exception ignored) {}
		try { db.getCollection("chat_messages").dropIndex("chat_messages_role_timeline_idx"); } catch (Exception ignored) {}
	}
}

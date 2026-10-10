package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.entity.BackgroundJob;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rest of an account in a {@link PurgeScope#FULL} purge: everything the library and recruitment participants leave
 * (users, connections, settings, queues, conversations), the tenant's background jobs except the purge itself, and
 * finally the tenant document. Kept on purpose: {@code stripe_event_logs} (accounting) and {@code admin_audit_logs}.
 * The tenant id is matched in both storage forms: most collections hold an ObjectId, older chat collections a string.
 */
@Component
public class TenantAccountPurge implements CascadeParticipant {

	/** Every collection holding a {@code tenantId} that no other participant purges. Checked by the purge tests. */
	static final List<String> COLLECTIONS = List.of(
		"users", "ats_connections", "ats_outbound_tasks", "mailbox_connections", "candidate_email_templates",
		"suppressed_emails", "pending_email_notifications", "sso_login_codes", "mfa_challenges", "support_tickets",
		"chats", "chat_messages", "insight_conversation_turns");

	private final MongoTemplate mongoTemplate;

	public TenantAccountPurge(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		var counts = new LinkedHashMap<String, Long>();
		if (!scope.covers(PurgeScope.FULL)) {
			return counts;
		}
		for (var collection : COLLECTIONS) {
			counts.put(collection, mongoTemplate.remove(Query.query(ofTenant(tenantId)), collection).getDeletedCount());
		}
		counts.put("background_jobs", mongoTemplate.remove(Query.query(new Criteria().andOperator(ofTenant(tenantId),
			Criteria.where("type").ne(BackgroundJob.TYPE_TENANT_PURGE))), "background_jobs").getDeletedCount());
		return counts;
	}

	/** Removes the tenant document itself — the very last step, once every participant is done. */
	public long removeTenant(String tenantId) {
		return mongoTemplate.remove(Query.query(Criteria.where("_id").is(new ObjectId(tenantId))), "tenants").getDeletedCount();
	}

	private static Criteria ofTenant(String tenantId) {
		return Criteria.where("tenantId").in(new ObjectId(tenantId), tenantId);
	}
}

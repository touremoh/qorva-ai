package ai.qorva.core.service.agent.rules;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.PendingEmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tells rule owners that runs wait for their approval: at most one email per user per window, listing every
 * rule run still waiting when it is sent. A run counts as news once per pause ({@code notifiedAt}); a pause
 * during the quiet window is picked up by the first check after it.
 */
@Slf4j
@Component
public class AgentApprovalDigest {

	private static final Set<String> LANGUAGES = Set.of("en", "fr", "de", "es", "it", "nl", "pt");
	private static final int SCAN = 500;

	private final MongoTemplate mongoTemplate;
	private final UserRepository userRepository;
	private final PendingEmailNotificationService pendingEmailService;
	private final AgentProperties properties;

	public AgentApprovalDigest(MongoTemplate mongoTemplate, UserRepository userRepository,
	                           PendingEmailNotificationService pendingEmailService, AgentProperties properties) {
		this.mongoTemplate = mongoTemplate;
		this.userRepository = userRepository;
		this.pendingEmailService = pendingEmailService;
		this.properties = properties;
	}

	/** Cross-tenant sweep of rule runs paused since their owner was last told; one email per owner per window. */
	public int queueDue() {
		var query = Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE).and("status").is(AgentRun.STATUS_AWAITING_APPROVAL)
			.and("notifiedAt").is(null)).limit(SCAN);
		query.fields().include("tenantId", "userEmail", "language");
		var byOwner = new LinkedHashMap<String, List<AgentRun>>();
		for (var run : mongoTemplate.find(query, AgentRun.class)) {
			byOwner.computeIfAbsent(run.getTenantId() + "|" + run.getUserEmail(), k -> new ArrayList<>()).add(run);
		}
		int queued = 0;
		var window = Duration.ofMinutes(properties.getRules().getDigestEveryMinutes());
		for (var runs : byOwner.values()) {
			var first = runs.getFirst();
			try {
				if (TenantScope.callAs(first.getTenantId(), () -> queue(first, runs, window))) queued++;
			} catch (RuntimeException e) {
				log.error("agent digest for {} could not be queued", first.getUserEmail(), e);
			}
		}
		return queued;
	}

	private boolean queue(AgentRun first, List<AgentRun> runs, Duration window) {
		var user = userRepository.findByEmail(first.getUserEmail());
		if (user != null && first.getTenantId().equals(user.getTenantId())) {
			if (pendingEmailService.existsRecent(user.getId(), EmailNotificationType.AGENT_APPROVAL_DIGEST, window)) {
				return false;
			}
			pendingEmailService.createPending(first.getTenantId(), user.getId(), EmailNotificationType.AGENT_APPROVAL_DIGEST,
				language(user.getCommunicationLanguage() != null ? user.getCommunicationLanguage() : first.getLanguage()));
		}
		// Gone users are marked too, so they don't come back on every sweep; the scheduler pauses their rules.
		mongoTemplate.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(first.getTenantId()))
				.and("_id").in(runs.stream().map(r -> new ObjectId(r.getId())).toList())),
			new Update().set("notifiedAt", Instant.now()), AgentRun.class);
		return user != null;
	}

	static String language(String code) {
		if (code == null) return "en";
		var primary = code.split("[-_,;]")[0].trim().toLowerCase(Locale.ROOT);
		return LANGUAGES.contains(primary) ? primary : "en";
	}
}

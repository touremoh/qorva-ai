package ai.qorva.core.service;

import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.InsightConversationTurnRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.repository.UsageMonitoringRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Wipes all tenant-scoped recruitment data. Used when a demo tenant upgrades to a paid plan:
 * everything it holds is sample data, so we purge it wholesale to start the real account clean.
 */
@Slf4j
@Service
public class DemoDataPurgeService {

	private final CVRepository cvRepository;
	private final JobPostRepository jobPostRepository;
	private final MatchingReportRepository matchingReportRepository;
	private final ChatsRepository chatsRepository;
	private final ChatMessagesRepository chatMessagesRepository;
	private final InsightConversationTurnRepository insightConversationTurnRepository;
	private final UsageMonitoringRepository usageMonitoringRepository;

	@Autowired
	public DemoDataPurgeService(
		CVRepository cvRepository,
		JobPostRepository jobPostRepository,
		MatchingReportRepository matchingReportRepository,
		ChatsRepository chatsRepository,
		ChatMessagesRepository chatMessagesRepository,
		InsightConversationTurnRepository insightConversationTurnRepository,
		UsageMonitoringRepository usageMonitoringRepository
	) {
		this.cvRepository = cvRepository;
		this.jobPostRepository = jobPostRepository;
		this.matchingReportRepository = matchingReportRepository;
		this.chatsRepository = chatsRepository;
		this.chatMessagesRepository = chatMessagesRepository;
		this.insightConversationTurnRepository = insightConversationTurnRepository;
		this.usageMonitoringRepository = usageMonitoringRepository;
	}

	/** Deletes every recruitment-related document for the tenant. Best-effort per collection. */
	public void purgeAll(String tenantId) {
		log.info("Purging demo data for tenant={}", tenantId);
		long cvs = safeDelete("cvs", () -> cvRepository.deleteByTenantId(tenantId));
		long jobs = safeDelete("job_posts", () -> jobPostRepository.deleteByTenantId(tenantId));
		long reports = safeDelete("matching_reports", () -> matchingReportRepository.deleteByTenantId(tenantId));
		long chats = safeDelete("chats", () -> chatsRepository.deleteByTenantId(tenantId));
		long messages = safeDelete("chat_messages", () -> chatMessagesRepository.deleteByTenantId(tenantId));
		long turns = safeDelete("insight_conversation_turns", () -> insightConversationTurnRepository.deleteByTenantId(tenantId));
		long usage = safeDelete("usage_monitoring", () -> usageMonitoringRepository.deleteByTenantId(tenantId));
		log.info("Demo data purged for tenant={}: cvs={} jobs={} reports={} chats={} messages={} insights={} usage={}",
			tenantId, cvs, jobs, reports, chats, messages, turns, usage);
	}

	private long safeDelete(String collection, java.util.function.LongSupplier delete) {
		try {
			return delete.getAsLong();
		} catch (Exception e) {
			log.warn("Failed to purge {} for tenant during upgrade", collection, e);
			return 0L;
		}
	}
}

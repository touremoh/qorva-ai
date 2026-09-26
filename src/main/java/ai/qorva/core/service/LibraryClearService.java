package ai.qorva.core.service;

import ai.qorva.core.service.cascade.CascadeRegistry;
import ai.qorva.core.service.cascade.PurgeScope;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.LibraryClearData;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tenant-scoped wipe of the resume library and everything derived from it: CVs (and
 * their S3 documents), matching reports, AI chats + messages, talent-intelligence
 * conversations, candidate-update requests (and their staged S3 files), and
 * quality-issue dismissals. Job posts and usage counters deliberately survive — jobs
 * are the recruiter's own work, usage is billing history.
 *
 * The collections come from {@link CascadeRegistry#purgeTenant} with {@link PurgeScope#LIBRARY}, the
 * same participants the demo purge uses with a wider scope.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryClearService {

	private static final List<String> ACTIVE_JOB_STATUSES =
		List.of(BackgroundJob.STATUS_DRAFT, BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);

	private final CVRepository cvRepository;
	private final MatchingReportRepository matchingReportRepository;
	private final ChatsRepository chatsRepository;
	private final BackgroundJobRepository backgroundJobRepository;
	private final CascadeRegistry cascadeRegistry;
	private final S3StorageService s3StorageService;
	private final LibraryQualityCacheEvictor cacheEvictor;

	public LibraryClearData.Preflight preflight(String tenantId) {
		return new LibraryClearData.Preflight(
			cvRepository.countByTenantId(tenantId),
			matchingReportRepository.countByTenantId(tenantId),
			chatsRepository.countByTenantId(tenantId));
	}

	public LibraryClearData.Result clear(String tenantId, String requestedBy) throws QorvaException {
		// A running bulk import or re-analysis would race the wipe and re-insert CVs.
		for (var type : List.of(BackgroundJob.TYPE_BULK_CV_UPLOAD, BackgroundJob.TYPE_REANALYZE,
			BackgroundJob.TYPE_CANDIDATE_UPDATE_CAMPAIGN)) {
			if (backgroundJobRepository.existsByTenantIdAndTypeAndStatusIn(tenantId, type, ACTIVE_JOB_STATUSES)) {
				throw new QorvaException(QorvaErrorCodes.CV_CLEAR_BLOCKED_BY_ACTIVE_JOB,
					HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
			}
		}

		log.warn("Clearing resume library for tenant={} requested by {}", tenantId, requestedBy);

		var deleted = cascadeRegistry.purgeTenant(tenantId, PurgeScope.LIBRARY, false);
		s3StorageService.deleteCvDocumentsForTenant(tenantId);           // best-effort, never throws
		s3StorageService.deleteCandidateSubmissionsForTenant(tenantId);  // best-effort, never throws
		cacheEvictor.evict(tenantId);

		log.info("Library cleared for tenant={}: {}", tenantId, deleted);
		return new LibraryClearData.Result(
			count(deleted, "cvs"), count(deleted, "matching_reports"), count(deleted, "chats"),
			count(deleted, "chat_messages"), count(deleted, "insight_conversation_turns"),
			count(deleted, "candidate_update_requests"), count(deleted, "quality_issue_states"), count(deleted, "notes"));
	}

	private static long count(java.util.Map<String, Long> deleted, String collection) {
		return deleted.getOrDefault(collection, 0L);
	}
}

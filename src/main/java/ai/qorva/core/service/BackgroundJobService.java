package ai.qorva.core.service;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Submission/listing/cancellation of asynchronous bulk operations. Fairness rule:
 * one active job per tenant per type — a 5,000-CV re-analyze must not let a tenant
 * queue five more behind it.
 */
@Slf4j
@Service
public class BackgroundJobService {

	private final BackgroundJobRepository jobRepository;
	private final CVRepository cvRepository;
	private final UsageMonitoringService usageMonitoringService;
	private final CandidateEmailTemplateService candidateEmailTemplateService;

	private static final List<String> ACTIVE_STATUSES =
		List.of(BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);

	@Autowired
	public BackgroundJobService(
		BackgroundJobRepository jobRepository,
		CVRepository cvRepository,
		UsageMonitoringService usageMonitoringService,
		CandidateEmailTemplateService candidateEmailTemplateService
	) {
		this.jobRepository = jobRepository;
		this.cvRepository = cvRepository;
		this.usageMonitoringService = usageMonitoringService;
		this.candidateEmailTemplateService = candidateEmailTemplateService;
	}

	public BackgroundJobData.SubmitResponse submit(String tenantId, String createdBy, BackgroundJobData.SubmitRequest request) throws QorvaException {
		boolean isReanalyze = BackgroundJob.TYPE_REANALYZE.equals(request.type());
		boolean isCampaign = BackgroundJob.TYPE_CANDIDATE_UPDATE_CAMPAIGN.equals(request.type());
		if (!isReanalyze && !isCampaign) {
			throw badRequest("Unsupported job type: " + request.type());
		}
		var issueKey = parseIssueKey(request.issueKey());
		if (issueKey == QualityIssueKeyEnum.DUPLICATES) {
			throw badRequest("Duplicates are resolved individually, not via jobs");
		}
		if (isCampaign && issueKey != QualityIssueKeyEnum.OUTDATED && issueKey != QualityIssueKeyEnum.UNKNOWN_FRESHNESS) {
			throw badRequest("Update campaigns target freshness issues only");
		}

		// Snapshot the invitation template up front (dry runs validate it too): a template
		// edited or deleted after submission must never change a running campaign.
		CandidateEmailTemplate template = null;
		if (StringUtils.hasText(request.templateId())) {
			if (!isCampaign) {
				throw badRequest("Email templates only apply to update campaigns");
			}
			template = candidateEmailTemplateService.findOwned(tenantId, request.templateId());
		}

		var estimate = isReanalyze ? reanalyzeEstimate(tenantId, issueKey) : campaignEstimate(tenantId, issueKey);

		if (request.dryRun()) {
			return new BackgroundJobData.SubmitResponse(estimate, null);
		}

		if (jobRepository.existsByTenantIdAndTypeAndStatusIn(tenantId, request.type(), ACTIVE_STATUSES)) {
			throw new QorvaException("A job of this type is already running for your workspace",
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		if (isReanalyze) {
			if (estimate.remainingQuota() != null && estimate.estimatedActions() > estimate.remainingQuota()) {
				throw badRequest("This job would exceed your remaining screening-action quota ("
					+ estimate.estimatedActions() + " needed, " + estimate.remainingQuota() + " left)");
			}
			if (estimate.estimatedActions() == 0) {
				throw badRequest("No re-analyzable resumes match this issue");
			}
		} else if (estimate.affectedCount() == 0) {
			throw badRequest("No resumes match this issue");
		}

		var job = jobRepository.save(BackgroundJob.builder()
			.tenantId(tenantId)
			.type(request.type())
			.issueKey(issueKey.name())
			.language(request.language())
			.emailSubject(template != null ? template.getSubject() : null)
			.emailBody(template != null ? template.getBodyText() : null)
			.status(BackgroundJob.STATUS_PENDING)
			.total(estimate.affectedCount())
			.errorSamples(List.of())
			.createdBy(createdBy)
			.createdAt(Instant.now())
			.build());

		log.info("Background job {} submitted: type={} issueKey={} tenant={} total={}",
			job.getId(), job.getType(), job.getIssueKey(), tenantId, job.getTotal());
		return new BackgroundJobData.SubmitResponse(estimate, BackgroundJobData.JobView.from(job));
	}

	/** Campaign sends carry no LLM cost; the worker skips no-email/suppressed/already-invited CVs. */
	private BackgroundJobData.Estimate campaignEstimate(String tenantId, QualityIssueKeyEnum issueKey) {
		long affected = cvRepository.countQualityIssueCVs(new ObjectId(tenantId), issueKey, false);
		return new BackgroundJobData.Estimate(affected, 0, 0, null);
	}

	private BackgroundJobData.Estimate reanalyzeEstimate(String tenantId, QualityIssueKeyEnum issueKey) {
		var tenantObjectId = new ObjectId(tenantId);
		long affected = cvRepository.countQualityIssueCVs(tenantObjectId, issueKey, false);
		long noRawText = cvRepository.countQualityIssueCVs(tenantObjectId, issueKey, true);

		Integer remaining = usageMonitoringService.findCurrentPeriodByTenantId(tenantId)
			.map(usage -> {
				var metrics = usage.getFeatures() != null ? usage.getFeatures().getScreeningActions() : null;
				if (metrics == null || metrics.getLimit() == null) return null;
				return Math.max(0, metrics.getLimit() - (metrics.getConsumed() != null ? metrics.getConsumed() : 0));
			})
			.orElse(null);

		return new BackgroundJobData.Estimate(affected, noRawText, affected - noRawText, remaining);
	}

	public BackgroundJobData.JobList list(String tenantId) {
		var jobs = jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 10)).stream()
			.map(BackgroundJobData.JobView::from)
			.toList();
		return new BackgroundJobData.JobList(jobs);
	}

	public BackgroundJobData.JobView get(String tenantId, String jobId) throws QorvaException {
		return jobRepository.findByIdAndTenantId(jobId, tenantId)
			.map(BackgroundJobData.JobView::from)
			.orElseThrow(() -> new QorvaException("Job not found", HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
	}

	public BackgroundJobData.JobView cancel(String tenantId, String jobId) throws QorvaException {
		var job = jobRepository.findByIdAndTenantId(jobId, tenantId)
			.orElseThrow(() -> new QorvaException("Job not found", HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
		if (ACTIVE_STATUSES.contains(job.getStatus())) {
			job.setStatus(BackgroundJob.STATUS_CANCELLED);
			job.setFinishedAt(Instant.now());
			jobRepository.save(job);
			log.info("Background job {} cancelled by tenant {}", jobId, tenantId);
		}
		return BackgroundJobData.JobView.from(job);
	}

	private QualityIssueKeyEnum parseIssueKey(String issueKey) throws QorvaException {
		try {
			return QualityIssueKeyEnum.valueOf(issueKey);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw badRequest("Unknown issue key: " + issueKey);
		}
	}

	private QorvaException badRequest(String message) {
		return new QorvaException(message, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
	}
}

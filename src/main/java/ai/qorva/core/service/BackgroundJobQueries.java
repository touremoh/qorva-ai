package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrors;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * The tenant's background jobs as the UI sees them — list, get, cancel — shared by the library-quality
 * jobs and the bulk CV uploads, which used to carry a copy each. Every lookup includes the tenant.
 */
@Slf4j
@Service
public class BackgroundJobQueries {

	/** How many recent jobs a job list shows. */
	static final int RECENT_JOBS = 10;

	private final BackgroundJobRepository repository;

	public BackgroundJobQueries(BackgroundJobRepository repository) {
		this.repository = repository;
	}

	/** The most recent jobs of the tenant, of every type. */
	public BackgroundJobData.JobList recent(String tenantId) {
		return toList(repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, RECENT_JOBS)));
	}

	/** The most recent jobs of one type — filtered in the query, so older jobs of other types never crowd them out. */
	public BackgroundJobData.JobList recent(String tenantId, String type) {
		return toList(repository.findByTenantIdAndTypeOrderByCreatedAtDesc(tenantId, type, PageRequest.of(0, RECENT_JOBS)));
	}

	public BackgroundJob require(String tenantId, String jobId, String notFoundMessage) throws QorvaException {
		return repository.findByIdInTenant(jobId, tenantId)
			.orElseThrow(() -> QorvaErrors.notFound(notFoundMessage));
	}

	public BackgroundJobData.JobView get(String tenantId, String jobId, String notFoundMessage) throws QorvaException {
		return BackgroundJobData.JobView.from(require(tenantId, jobId, notFoundMessage));
	}

	/**
	 * Cancels the job when its status is one of {@code cancellable} (otherwise it is returned
	 * unchanged); {@code onCancelled} releases whatever the job type holds (staged files, …).
	 */
	public BackgroundJobData.JobView cancel(String tenantId, String jobId, String notFoundMessage,
	                                         List<String> cancellable, Consumer<BackgroundJob> onCancelled) throws QorvaException {
		var job = require(tenantId, jobId, notFoundMessage);
		if (cancellable.contains(job.getStatus())) {
			job.setStatus(BackgroundJob.STATUS_CANCELLED);
			job.setFinishedAt(Instant.now());
			repository.save(job);
			onCancelled.accept(job);
			log.info("Background job {} ({}) cancelled by tenant {}", jobId, job.getType(), tenantId);
		}
		return BackgroundJobData.JobView.from(job);
	}

	private static BackgroundJobData.JobList toList(List<BackgroundJob> jobs) {
		return new BackgroundJobData.JobList(jobs.stream().map(BackgroundJobData.JobView::from).toList());
	}
}

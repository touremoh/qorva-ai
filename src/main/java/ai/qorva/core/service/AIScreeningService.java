package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.ResumeMatchDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.events.CVScreeningEvent;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class AIScreeningService {

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final ResumeMatchService resumeMatchService;
	private final EmbeddingModel embeddingModel;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, ResumeMatchService resumeMatchService, EmbeddingModel embeddingModel) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.resumeMatchService = resumeMatchService;
		this.embeddingModel = embeddingModel;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void startScreeningProcess(CVScreeningEvent event) throws QorvaException {
		log.info("CV Screening event received for job post {}", event.jobPost().getId());

		// Get the job post
		var jobPost = event.jobPost();

		// Check if the job post has an embedding vector
		if (Objects.isNull(jobPost.getEmbedding()) || jobPost.getEmbedding().length == 0) {
			jobPost.setEmbedding(this.embeddingModel.embed(jobPost.toJobTitleAndDescription()));
		}

		// Get the tenant
		String tenantId = jobPost.getTenantId();

		// Check if the monthly usage limit was not reached before the screening process starts
		if (this.resumeMatchService.hasReachedMonthlyUsageLimit(tenantId)) {
			log.warn("User {} has reached monthly limit.", tenantId);
			throw new QorvaException("User " + tenantId + " has reached monthly limit.", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		// Perform similar search and extract results (List of CV IDs) => must be filter out by tenantId
		var results = this.cvService.findCVsMatchingJobDescription(jobPost);

		// Filter out the CVs that are not relevant for the screening process
		var filteredCVs = results.stream()
			.filter(cvdto -> {
				try {
					return isCVRelevantToScreening(cvdto, jobPost);
				} catch (QorvaException e) {
					log.error("An error occurred while check candidate relevancy for job application", e.getMessage());
					return false;
				}
			})
			.toList();

		// Start the screening process
		var resumeMatches = filteredCVs
			.parallelStream()
			.map(cvdto -> {
				try {
					var analysisDetails = this.openAIService.generateReport(QorvaUtils.toJSON(cvdto), jobPost.toJobTitleAndDescription());
					return this.resumeMatchService.createOne(jobPost, analysisDetails, cvdto);
				} catch (QorvaException e) {
					throw new RuntimeException(e);
				}
			})
			.toList();


		// Check if something was found before saving
		if (!resumeMatches.isEmpty()) {
			// Save all
			var savedResumeMatches = this.resumeMatchService.saveAll(resumeMatches);
			// log new application saved
			log.debug("{} new applications for job post {}", savedResumeMatches.size(), jobPost.getId());
		}
	}

	protected boolean isCVRelevantToScreening(CVDTO cvdto, JobPostDTO jobPostDTO) throws QorvaException {
		// Build criteria
		var searchData = new ResumeMatchDTO();
		searchData.setTenantId(jobPostDTO.getTenantId());
		searchData.setJobPostId(jobPostDTO.getId());

		var candidateInfo = new CandidateInfo();
		candidateInfo.setCandidateId(cvdto.getId());
		searchData.setCandidateInfo(candidateInfo);

		try {
			// Find CV in Resume Matches
			var resumeMatchDTO = this.resumeMatchService.findOneByData(searchData);

			// Check the case where a candidate not relevant
			if (Objects.nonNull(resumeMatchDTO)) {
				if (jobPostDTO.getLastUpdatedAt().isAfter(resumeMatchDTO.getLastUpdatedAt())
					|| cvdto.getLastUpdatedAt().isAfter(resumeMatchDTO.getLastUpdatedAt())) {

					// Remove that job application to a new one
					this.resumeMatchService.deleteOneById(resumeMatchDTO.getId(), jobPostDTO.getTenantId());

					// Return true for the system to take into account the CV
					return true;
				}
				return false;
			}
		} catch (QorvaException e) {
			log.warn(e.getMessage());
			return true;
		}
		return true;
	}
}

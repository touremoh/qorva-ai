package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.events.NewJobPostEvent;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class AIScreeningService {

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final MatchingReportService matchingReportService;
	private final EmbeddingModel embeddingModel;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, MatchingReportService matchingReportService, EmbeddingModel embeddingModel) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.matchingReportService = matchingReportService;
		this.embeddingModel = embeddingModel;
	}

	@Async
	@EventListener
	public void startScreeningProcess(NewJobPostEvent event) throws QorvaException {
		log.info("CV Screening event received for job post {}", event.jobPost().getId());

		// Get the job post
		var jobPost = event.jobPost();

		// Get the language code for the report
		var reportLanguage = jobPost.getLanguageCode();

		// Check if the job post has an embedding vector
		if (Objects.isNull(jobPost.getEmbedding()) || jobPost.getEmbedding().length == 0) {
			jobPost.setEmbedding(this.embeddingModel.embed(jobPost.toJobTitleAndDescription()));
		}

		// Get the tenant
		String tenantId = jobPost.getTenantId();

		// Check if the monthly usage limit was not reached before the screening process starts
		if (this.matchingReportService.hasReachedMonthlyUsageLimit(tenantId)) {
			log.warn("User {} has reached monthly limit.", tenantId);
			throw new QorvaException("User " + tenantId + " has reached monthly limit.", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		// Perform similar search and extract results (List of CV IDs) => must be filter out by tenantId
		var matchingCVs = this.cvService.findCVsMatchingJobDescription(jobPost);

		// Filter out the CVs that are not relevant for the screening process
		var filteredCVs = matchingCVs.stream()
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
		var matchingReports = filteredCVs
			.parallelStream()
			.map(cvdto -> {
				try {
					var analysisDetails = this.openAIService.generateReport(QorvaUtils.toJSON(cvdto), jobPost.toJobTitleAndDescription(), reportLanguage, jobPost.getScoringRules());
					return this.matchingReportService.createOne(jobPost, analysisDetails, cvdto);
				} catch (QorvaException e) {
					throw new RuntimeException(e);
				}
			})
			.toList();


		// Check if something was found before saving
		if (!matchingReports.isEmpty()) {
			// Save all
			var persistedMatchingReports = this.matchingReportService.saveAll(matchingReports);
			// log new application saved
			log.debug("{} new applications for job post {}", persistedMatchingReports.size(), jobPost.getId());
		}
	}

	protected boolean isCVRelevantToScreening(CVDTO cvdto, JobPostDTO jobPostDTO) throws QorvaException {
		// Build criteria
		var searchData = new MatchingReportDTO();
		searchData.setTenantId(jobPostDTO.getTenantId());
		searchData.setJobPostId(jobPostDTO.getId());

		var candidateInfo = new CandidateInfo();
		candidateInfo.setCandidateId(cvdto.getId());
		searchData.setCandidateInfo(candidateInfo);

		try {
			// Find CV in Resume Matches
			var matchingReport = this.matchingReportService.findOneByCriteria(searchData);

			// Check the case where a candidate not relevant
			if (Objects.nonNull(matchingReport)) {
				if (matchingReportIsOlderThanJobPostOrCV(cvdto, jobPostDTO, matchingReport)) {
					// Remove that job application to a new one
					this.matchingReportService.deleteOneById(matchingReport.getId(), jobPostDTO.getTenantId());

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


	protected boolean matchingReportIsOlderThanJobPostOrCV(CVDTO cv, JobPostDTO jobPost, MatchingReportDTO matchingReportDTO) {
		return matchingReportDTO.getLastUpdatedAt().isBefore(jobPost.getLastUpdatedAt()) || matchingReportDTO.getLastUpdatedAt().isBefore(cv.getLastUpdatedAt());
	}
}

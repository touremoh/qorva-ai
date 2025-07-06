package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.ResumeMatchDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.chrono.ChronoLocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class AIScreeningService {

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final JobPostService jobPostService;
	private final ResumeMatchService resumeMatchService;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, JobPostService jobPostService, ResumeMatchService resumeMatchService) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.jobPostService = jobPostService;
		this.resumeMatchService = resumeMatchService;
	}

	public Page<ResumeMatchDTO> startScreeningProcess(String jobPostId, List<String> tags) throws QorvaException {
		// Get the job post
		var jobPost = this.jobPostService.findOneById(jobPostId);

		// Check if the job post was found
		if (Objects.isNull(jobPost)) {
			throw new QorvaException("Invalid jobPostId: " + jobPostId);
		}

		// Get the tenant
		String tenantId = jobPost.getTenantId();

		// Check if the monthly usage limit was not reached before the screening process starts
		if (this.resumeMatchService.hasReachedMonthlyUsageLimit(tenantId)) {
			log.warn("User {} has reached monthly limit.", tenantId);
			throw new QorvaException("User " + tenantId + " has reached monthly limit.", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		// Perform similar search and extract results (List of CV IDs) => must be filter out by tenantId
		var results = this.cvService.findCVsMatchingJobDescription(jobPost, tags);

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
			log.debug("{} new applications for job post {}", savedResumeMatches.size(), jobPostId);
		}

		// Build search request
		var searchCriteria = new ResumeMatchDTO();
		searchCriteria.setTenantId(jobPost.getTenantId());
		searchCriteria.setJobPostId(jobPostId);

		// Return all job applications for company id and job post id sorted by AI Score Desc
		return this.resumeMatchService.findAll(searchCriteria, 0, 25);
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

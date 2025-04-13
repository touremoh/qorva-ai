package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobApplicationDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.request.FindManyRequestCriteria;
import ai.qorva.core.enums.MontlyUsageLimitCodeEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Slf4j
@Service
public class AIScreeningService {
	private final CVService cvService;
	private final OpenAIService openAIService;
	private final JobPostService jobPostService;
	private final JobsApplicationService jobsApplicationService;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, JobPostService jobPostService, JobsApplicationService jobsApplicationService) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.jobPostService = jobPostService;
		this.jobsApplicationService = jobsApplicationService;
	}

	public Page<JobApplicationDTO> startScreeningProcess(String jobPostId, String userId) throws QorvaException {
		// Check if monthly usage limit was not reached before screening process start
		var usageLimitStatus = this.jobsApplicationService.checkCVAnalysisMonthlyUsageLimit(userId);

		if (MontlyUsageLimitCodeEnum.REACHED.getValue().equals(usageLimitStatus)) {
			log.warn("User {} has reached monthly limit.", userId);
			throw new QorvaException("User " + userId + " has reached monthly limit.", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		// Get the job post
		var jobPost = this.jobPostService.findOneById(jobPostId);

		// Perform similar search and extract results (List of CV IDs) => must be filter out by tenantId
		var results = this.cvService.findCVsMatchingJobDescription(jobPost.toJobTitleAndDescription(), jobPost.getTenantId());

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

		// Finally, start the screening process
		var newJobApplications = filteredCVs.parallelStream()
			.map(cvdto -> {
				try {
					var analysisDetails = this.openAIService.generateReport(QorvaUtils.toJSON(cvdto), jobPost.toJobTitleAndDescription());
					return this.jobsApplicationService.createOne(jobPost, analysisDetails, cvdto);
				} catch (QorvaException e) {
					throw new RuntimeException(e);
				}
			}).toList();

		// Save all
		var savedApplications = this.jobsApplicationService.saveAll(newJobApplications);

		// log new application saved
		log.debug("{} new applications for job post {}", savedApplications.size(), jobPostId);

		var criteria = new FindManyRequestCriteria();
		criteria.setTenantId(jobPost.getTenantId());
		criteria.setPageNumber(0);
		criteria.setPageSize(25);

		// Return all job applications for company id and job post id sorted by AI Score Desc
		return this.jobsApplicationService.findMany(criteria);
	}

	protected boolean isCVRelevantToScreening(CVDTO cvdto, JobPostDTO jobPostDTO) throws QorvaException {
		// Build criteria
		var searchData = new JobApplicationDTO();
		searchData.setTenantId(jobPostDTO.getTenantId());
		searchData.setJobPostId(jobPostDTO.getId());

		var candidateInfo = new CandidateInfo();
		candidateInfo.setCandidateId(cvdto.getId());
		searchData.setCandidateInfo(candidateInfo);

		// Find CV in Job Applications Pipeline
		var jobApplication = this.jobsApplicationService.findOneByData(searchData);

		// Check case where candidate not relevant
		if (Objects.nonNull(jobApplication)) {
			if (jobPostDTO.getLastUpdatedAt().isAfter(jobApplication.getLastUpdatedAt())
				|| cvdto.getLastUpdatedAt().isAfter(jobApplication.getLastUpdatedAt())) {

				// Remove that job application to a new one
				this.jobsApplicationService.deleteOneById(jobApplication.getId());

				// Return true for the system to take into account the CV
				return true;
			}
			return false;
		}
		return true;
	}
}

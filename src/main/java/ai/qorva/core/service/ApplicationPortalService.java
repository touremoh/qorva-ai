package ai.qorva.core.service;

import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import io.jsonwebtoken.lang.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Slf4j
@Service
public class ApplicationPortalService {
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final OpenAIService openAIService;
	protected final ResumeMatchService jobsApplicationService;

	@Autowired
	public ApplicationPortalService(CVService cvService, JobPostService jobPostService, OpenAIService openAIService, ResumeMatchService jobsApplicationService) {
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.openAIService = openAIService;
		this.jobsApplicationService = jobsApplicationService;
	}

	public JobPostDTO getJobPostDetails(String jobPostId) throws QorvaException {
		return this.jobPostService.findOneById(jobPostId);
	}

	public Boolean apply(String jobPostId, MultipartFile application) throws QorvaException {
		// Find the job post the candidate is applying for
		var jobPostDto = this.jobPostService.findOneById(jobPostId);

		// Extract candidate data and save it to the db
		var cvDto = this.cvService.processFile(application, jobPostDto.getTenantId());

		// Generate JobApplication vs Job Post AI-matching score
		var reportDetails = this.openAIService.generateReport(QorvaUtils.toJSON(cvDto), jobPostDto.toJobTitleAndDescription());

		// Save the newly generated report in the db
		var response =this.jobsApplicationService.createOne(jobPostDto, reportDetails, cvDto);

		// Let the applicant know that his application was successfully sent
		return Objects.nonNull(response) && Strings.hasText(response.getId());
	}
}

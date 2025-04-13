package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobApplication;
import ai.qorva.core.dao.repository.JobApplicationRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobApplicationDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.MontlyUsageLimitCodeEnum;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JobApplicationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class JobsApplicationService extends AbstractQorvaService<JobApplicationDTO, JobApplication> {
	protected final UserService userService;

	@Autowired
	public JobsApplicationService(JobApplicationRepository repository, JobApplicationMapper mapper, UserService userService) {
		super(repository, mapper);
		this.userService = userService;
	}

	@Override
	protected void preProcessCreateOne(JobApplicationDTO requestData) throws QorvaException {
		super.preProcessCreateOne(requestData);

		// Check the job post id is present
		if (!StringUtils.hasText(requestData.getJobPostId())) {
			log.warn("Job post id is empty while creating Job Application");
			throw new QorvaException("Job post id cannot be empty in Job Application");
		}

		// Check candidate info
		if (Objects.isNull(requestData.getCandidateInfo())) {
			log.warn("Candidate info is empty while creating Job Application");
			throw new QorvaException("Candidate info cannot be empty in job application");
		}
	}

	@Override
	protected void preProcessUpdateOne(String id, JobApplicationDTO requestData) throws QorvaException {
		super.preProcessUpdateOne(id, requestData);

		// Check if user exists before creating one
		var application = this.findOneById(id);

		// Make sure we only update existing user
		if (Objects.isNull(application)) {
			log.warn("Job Application not found while trying to update");
			throw new QorvaException("Job Application not found while trying to update");
		}

		// Merge objects
		if (Objects.nonNull(requestData.getCandidateInfo()) || Objects.nonNull(requestData.getReportDetails())) {
			log.warn("Updating Candidate Info and Report Details is not allowed");
			throw new QorvaException("Updating Candidate Info and Report Details is not allowed");
		}
	}

	public   JobApplicationDTO createOne(JobPostDTO jobPostDto, AIAnalysisReportDetails reportDetails, CVDTO cvDto) throws QorvaException {
		// Set Report  Details ID
		reportDetails.setDetailsID(UUID.randomUUID().toString());

		// Build Application DTO
		var jobApplicationDTO = new JobApplicationDTO();

		jobApplicationDTO.setJobPostId(jobPostDto.getId());
		jobApplicationDTO.setTenantId(jobPostDto.getTenantId());
		jobApplicationDTO.setReportDetails(reportDetails);
		jobApplicationDTO.setStatus(ApplicationStatusEnum.NEW.getStatus());
		jobApplicationDTO.setCreatedBy("system");
		jobApplicationDTO.setLastUpdatedBy("system");

		var candidateInfo = new CandidateInfo();
		candidateInfo.setCandidateName(cvDto.getPersonalInformation().getName());
		candidateInfo.setCandidateId(cvDto.getId());
		candidateInfo.setNbYearsExperience(cvDto.getNbYearsOfExperience());
		candidateInfo.setCandidateProfileSummary(cvDto.getCandidateProfileSummary());

		var skills = new ArrayList<String>();

		for (KeySkill keySkill : cvDto.getKeySkills()) {
			skills.addAll(keySkill.getSkills());
		}
		candidateInfo.setSkills(skills);

		jobApplicationDTO.setCandidateInfo(candidateInfo);
		return this.createOne(jobApplicationDTO);
	}

	public String checkCVAnalysisMonthlyUsageLimit(String userId) throws QorvaException {
		// Get the user by its ID and extract the company id and the subscription plan
		var userDTO = this.userService.findOneById(userId);

		if (Objects.isNull(userDTO)) {
			log.warn("Trying to update an unknown user => wrong user id [{}]", userId);
			throw new QorvaException("Trying to update an unknown user => wrong user id");
		}

		// Get the info necessary subscription information
		var companyId = userDTO.getCompanyInfo().tenantId();
		var planName = userDTO.getSubscriptionInfo().getSubscriptionPlan();

		// Get the first day of the month
		var startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());

		// Get the last day of the month
		var endOfMonth = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

		// Count all CV analysis of the month
		var nbCvAnalyzedInTheMonth = ((JobApplicationRepository)repository).countCVAnalyzedInMonth(companyId, startOfMonth, endOfMonth);

		// Check if user has reached CV analysis monthly limit
		return nbCvAnalyzedInTheMonth >= SubscriptionPlanEnum.valueOf(planName).getLimit()
			? MontlyUsageLimitCodeEnum.REACHED.getValue()
			: MontlyUsageLimitCodeEnum.NOT_REACHED.getValue();
	}

	public List<JobApplicationDTO> saveAll(List<JobApplicationDTO> jobApplicationDTOs) throws QorvaException {
		if (Objects.isNull(jobApplicationDTOs) || jobApplicationDTOs.isEmpty()) {
			log.error("SaveAll - Job Application DTOs is empty");
			throw new QorvaException("SaveAll - Job Application DTOs cannot be empty");
		}
		// Convert to entities list
		var jobApplicationEntities = jobApplicationDTOs.stream().map(this.mapper::map).toList();

		// Save all
		var persistedJobApplications = ((JobApplicationRepository)this.repository).savaAll(jobApplicationEntities);

		// Convert results to list of DTOs and return
		return persistedJobApplications.stream().map(this.mapper::map).toList();
	}
}

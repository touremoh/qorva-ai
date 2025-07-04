package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ResumeMatch;
import ai.qorva.core.dao.repository.ResumeMatchRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.ResumeMatchDTO;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.MontlyUsageLimitCodeEnum;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ResumeMatchMapper;
import ai.qorva.core.qbe.ResumeMatchQueryBuilder;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class ResumeMatchService extends AbstractQorvaService<ResumeMatchDTO, ResumeMatch> {
	protected final UserService userService;
	protected final TenantService tenantService;

	@Autowired
	public ResumeMatchService(ResumeMatchRepository repository, ResumeMatchMapper mapper, ResumeMatchQueryBuilder queryBuilder, UserService userService, TenantService tenantService) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
	}

	@Override
	protected void preProcessCreateOne(ResumeMatchDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		// Check the job post id is present
		if (!StringUtils.hasText(dto.getJobPostId())) {
			log.warn("Job post id is empty while creating Job Application");
			throw new QorvaException("Job post id cannot be empty in Job Application");
		}

		// Check candidate info
		if (Objects.isNull(dto.getCandidateInfo())) {
			log.warn("Candidate info is empty while creating Job Application");
			throw new QorvaException("Candidate info cannot be empty in job application");
		}
	}

	@Override
	protected void preProcessUpdateOne(String id, ResumeMatchDTO requestData) throws QorvaException {
		super.preProcessUpdateOne(id, requestData);

		// Check if the user exists before creating one
		var application = this.findOneById(id);

		// Make sure we only update existing user
		if (Objects.isNull(application)) {
			log.warn("Job Application not found while trying to update");
			throw new QorvaException("Job Application not found while trying to update");
		}

		// Merge objects
		this.mapper.merge(requestData, application);
	}

	public ResumeMatchDTO createOne(JobPostDTO jobPostDto, AIAnalysisReportDetails reportDetails, CVDTO cvDto) throws QorvaException {
		// Set Report Details ID
		reportDetails.setDetailsID(UUID.randomUUID().toString());

		// Build Application DTO
		var resumeMatchDTO = new ResumeMatchDTO();

		resumeMatchDTO.setJobPostId(jobPostDto.getId());
		resumeMatchDTO.setTenantId(jobPostDto.getTenantId());
		resumeMatchDTO.setAiAnalysisReportDetails(reportDetails);
		resumeMatchDTO.setStatus(ApplicationStatusEnum.NEW.getStatus());

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

		resumeMatchDTO.setCandidateInfo(candidateInfo);
		return this.createOne(resumeMatchDTO);
	}

	public boolean hasReachedMonthlyUsageLimit(String tenantId) throws QorvaException {
		return MontlyUsageLimitCodeEnum.REACHED.getValue().equals(this.checkCVAnalysisMonthlyUsageLimit(tenantId));
	}

	public String checkCVAnalysisMonthlyUsageLimit(String tenantId) throws QorvaException {

		var tenantDTO = this.tenantService.findOneById(tenantId);

		// Get the info necessary subscription information
		var planName = tenantDTO.getSubscriptionInfo().getSubscriptionPlan();

		// Get the first day of the month
		var startOfMonth = QorvaUtils.getFirstDayOfMonth();

		// Get the last day of the month
		var endOfMonth = QorvaUtils.getLastDayOfMonth();

		// Count all CV analysis of the month
		var nbCvAnalyzedInTheMonth = ((ResumeMatchRepository)repository).countByTenantIdAndCreatedAtBetween(tenantId, startOfMonth, endOfMonth);

		// Check if user has reached CV analysis monthly limit
		return nbCvAnalyzedInTheMonth >= SubscriptionPlanEnum.valueOf(planName).getLimit()
			? MontlyUsageLimitCodeEnum.REACHED.getValue()
			: MontlyUsageLimitCodeEnum.NOT_REACHED.getValue();
	}

	@Override
	public ResumeMatchDTO findOneByData(ResumeMatchDTO requestData) throws QorvaException {
		var response =  ((ResumeMatchRepository)this.repository)
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
				requestData.getTenantId(),
				requestData.getJobPostId(),
				requestData.getCandidateInfo().getCandidateId()
			);
		if (response.isEmpty()) {
			throw new QorvaException("Could not find resume match for request data");
		}
		return this.mapper.map(response.get());
	}
}

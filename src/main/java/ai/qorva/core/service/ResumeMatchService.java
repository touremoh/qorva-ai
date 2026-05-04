package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ResumeMatch;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.ResumeMatchRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.DashboardData;
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
import ai.qorva.core.dao.querybuilder.ResumeMatchQueryBuilder;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
public class ResumeMatchService extends AbstractQorvaService<ResumeMatchDTO, ResumeMatch> {
	protected final UserService userService;
	protected final TenantService tenantService;
	protected final ChatsRepository chatsRepository;

	@Autowired
	public ResumeMatchService(ResumeMatchRepository repository, ResumeMatchMapper mapper, ResumeMatchQueryBuilder queryBuilder, UserService userService, TenantService tenantService, ChatsRepository chatsRepository) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
		this.chatsRepository = chatsRepository;
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
		resumeMatchDTO.setJobPostTitle(jobPostDto.getTitle());
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

		// Count all CV analysis of the month
		var nbCvAnalyzedInTheMonth = this.countResumeMatchesInCurrentMonth(tenantId);

		// Check if the user has reached CV analysis monthly limit
		return nbCvAnalyzedInTheMonth >= SubscriptionPlanEnum.valueOf(planName.toUpperCase()).getLimit()
			? MontlyUsageLimitCodeEnum.REACHED.getValue()
			: MontlyUsageLimitCodeEnum.NOT_REACHED.getValue();
	}

	@Override
	public ResumeMatchDTO findOneByCriteria(ResumeMatchDTO searchCriteria) throws QorvaException {
		var response =  ((ResumeMatchRepository)this.repository)
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
				new ObjectId(searchCriteria.getTenantId()),
				new ObjectId(searchCriteria.getJobPostId()),
				searchCriteria.getCandidateInfo().getCandidateId()
			);
		if (response.isEmpty()) {
			throw new QorvaException("Could not find resume match for request data");
		}
		return this.mapper.map(response.get());
	}

	public long countResumeMatchesInCurrentMonth(String tenantId) {
		// Get the first day of the month
		var startOfMonth = QorvaUtils.getFirstDayOfMonth();

		// Get the last day of the month
		var endOfMonth = QorvaUtils.getLastDayOfMonth();

		// Count all CV analysis of the month
		return  ((ResumeMatchRepository)repository).countByTenantIdAndCreatedAtBetween(tenantId, startOfMonth, endOfMonth);
	}

	public Page<ResumeMatchDTO> searchAll(Map<String, String> params) throws QorvaException {
		try {

			// Get parameters
			var searchTerms = params.get("searchTerms");
			var tenantId = params.get("tenantId");
			var jobPostId = params.get("jobPostId");
			int pageSize = Integer.parseInt(params.get("pageSize"));
			int pageNumber = Integer.parseInt(params.get("pageNumber"));
			var pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastUpdatedAt").descending());

			// Process
			Page<ResumeMatch> results = (jobPostId == null || jobPostId.isBlank())
				? ((ResumeMatchRepository)repository).searchAll(searchTerms, tenantId, pageable)
				: ((ResumeMatchRepository)repository).searchAll(searchTerms, tenantId, jobPostId, pageable);

			// Render results
			return renderFindAll(results);
		} catch (Exception e) {
			throw wrapException(e, "Error finding resources by IDs");
		}
	}

	public List<DashboardData.ApplicationPerJobPostReport> getApplicationsPerJobPost(String tenantId) {
		return ((ResumeMatchRepository) repository).getApplicationsPerJobPost(new ObjectId(tenantId));
	}

	public List<DashboardData.TopCandidatesPerJobReport> getTopCandidatesPerJobPost(String tenantId) {
		return ((ResumeMatchRepository) repository).getTopCandidatesPerJobPost(new ObjectId(tenantId));
	}

	@Override
	protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
		log.info("Deleted Resume Match with ID: {}", id);

		var existing = this.findOneById(id);

		if (existing == null) {
			throw new QorvaException("Resume Match with ID " + id + " not found");
		}

		// Delete chat associated with this Report
		var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextResumeMatchId(tenantId, id);

		log.info("Deleted {} chats associated with Resume Match ID: {}", countDeletedChats, id);
	}
}

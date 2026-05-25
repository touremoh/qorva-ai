package ai.qorva.core.service;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dao.querybuilder.MatchingReportQueryBuilder;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.KeySkill;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.MatchingReportMapper;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
public class MatchingReportService extends AbstractQorvaService<MatchingReportDTO, MatchingReport> {
	protected final UserService userService;
	protected final TenantService tenantService;
	protected final ChatsRepository chatsRepository;

	@Autowired
	public MatchingReportService(MatchingReportRepository repository, MatchingReportMapper mapper, MatchingReportQueryBuilder queryBuilder, UserService userService, TenantService tenantService, ChatsRepository chatsRepository) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
		this.chatsRepository = chatsRepository;
	}

	@Override
	protected void preProcessCreateOne(MatchingReportDTO dto) throws QorvaException {
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
	protected void preProcessUpdateOne(String id, MatchingReportDTO requestData) throws QorvaException {
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

	public void upsertReport(JobPostDTO jobPost, MatchingReportDetails details, CVDTO cv) throws QorvaException {
		var existing = ((MatchingReportRepository) this.repository)
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
				new ObjectId(jobPost.getTenantId()),
				new ObjectId(jobPost.getId()),
				cv.getId()
			);

		if (existing.isPresent()) {
			var existingDTO = this.mapper.map(existing.get());
			if (existingDTO.getMatchingReportDetails() != null) {
				details.setDetailsID(existingDTO.getMatchingReportDetails().getDetailsID());
			}
			var updateDto = new MatchingReportDTO();
			updateDto.setTenantId(jobPost.getTenantId());
			updateDto.setMatchingReportDetails(details);
			this.updateOne(existingDTO.getId(), updateDto);
		} else {
			this.createOne(jobPost, details, cv);
		}
	}

	public MatchingReportDTO createOne(JobPostDTO jobPostDto, MatchingReportDetails reportDetails, CVDTO cvDto) throws QorvaException {
		// Set Report Details ID
		reportDetails.setDetailsID(UUID.randomUUID().toString());

		// Build Application DTO
		var matchingReportDTO = new MatchingReportDTO();

		matchingReportDTO.setJobPostId(jobPostDto.getId());
		matchingReportDTO.setJobPostTitle(jobPostDto.getTitle());
		matchingReportDTO.setTenantId(jobPostDto.getTenantId());
		matchingReportDTO.setMatchingReportDetails(reportDetails);
		matchingReportDTO.setStatus(ApplicationStatusEnum.NEW.getStatus());

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

		matchingReportDTO.setCandidateInfo(candidateInfo);
		return this.createOne(matchingReportDTO);
	}

	@Override
	public MatchingReportDTO findOneByCriteria(MatchingReportDTO searchCriteria) throws QorvaException {
		var response =  ((MatchingReportRepository)this.repository)
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

	public long countMatchingReportInCurrentMonth(String tenantId) {
		// Get the first day of the month
		var startOfMonth = QorvaUtils.getFirstDayOfMonth();

		// Get the last day of the month
		var endOfMonth = QorvaUtils.getLastDayOfMonth();

		// Count all CV analysis of the month
		return  ((MatchingReportRepository)repository).countByTenantIdAndCreatedAtBetween(tenantId, startOfMonth, endOfMonth);
	}

	public Page<MatchingReportDTO> searchAll(Map<String, String> params) throws QorvaException {
		try {

			// Get parameters
			var searchTerms = params.get("searchTerms");
			var tenantId = params.get("tenantId");
			var jobPostId = params.get("jobPostId");
			int pageSize = Integer.parseInt(params.get("pageSize"));
			int pageNumber = Integer.parseInt(params.get("pageNumber"));
			var pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastUpdatedAt").descending());

			// Process
			Page<MatchingReport> results = (jobPostId == null || jobPostId.isBlank())
				? ((MatchingReportRepository)repository).searchAll(searchTerms, tenantId, pageable)
				: ((MatchingReportRepository)repository).searchAll(searchTerms, tenantId, jobPostId, pageable);

			// Render results
			return renderFindAll(results);
		} catch (Exception e) {
			throw wrapException(e, "Error finding resources by IDs");
		}
	}

	public List<DashboardData.ApplicationPerJobPostReport> getApplicationsPerJobPost(String tenantId) {
		return ((MatchingReportRepository) repository).getApplicationsPerJobPost(new ObjectId(tenantId));
	}

	public List<DashboardData.TopCandidatesPerJobReport> getTopCandidatesPerJobPost(String tenantId) {
		return ((MatchingReportRepository) repository).getTopCandidatesPerJobPost(new ObjectId(tenantId));
	}

	@Override
	protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
		log.info("Deleted Resume Match with ID: {}", id);

		var existing = this.findOneById(id);

		if (existing == null) {
			throw new QorvaException("Resume Match with ID " + id + " not found");
		}

		// Delete chat associated with this Report
		var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextMatchingReportId(tenantId, id);

		log.info("Deleted {} chats associated with Resume Match ID: {}", countDeletedChats, id);
	}
}

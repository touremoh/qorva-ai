package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ScreeningReport;
import ai.qorva.core.dao.repository.ScreeningReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.ScreeningReportDTO;
import ai.qorva.core.dto.common.ReportDetails;
import ai.qorva.core.enums.ReportStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ScreeningReportMapper;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class ScreeningReportService extends AbstractQorvaService<ScreeningReportDTO, ScreeningReport> {

    private final OpenAIService openAIService;
    private final JobPostService jobPostService;
    private final CVService cvService;

    @Autowired
    public ScreeningReportService(
		ScreeningReportRepository repository,
		ScreeningReportMapper mapper,
		OpenAIService openAIService,
		JobPostService jobPostService,
		CVService cvService) {
        super(repository, mapper);
		this.openAIService = openAIService;
		this.jobPostService = jobPostService;
		this.cvService = cvService;
	}

    @Override
    protected void preProcessCreateOne(ScreeningReportDTO input) throws QorvaException {
        super.preProcessCreateOne(input);

		if (!StringUtils.hasText(input.getCompanyId())) {
			input.setCompanyId(this.getAuthenticatedCompanyId());
		}
    }

	@Override
	protected void preProcessUpdateOne(String id, ScreeningReportDTO newReport) throws QorvaException {
		super.preProcessUpdateOne(id, newReport);

		// Set the company ID if not exists
		if (!StringUtils.hasText(newReport.getCompanyId())) {
			newReport.setCompanyId(this.getAuthenticatedCompanyId());
		}

		// Set the status to permanent
		newReport.setStatus(ReportStatusEnum.PERMANENT.getStatus());

		// Find report by ID
		var oldReport = this.findOneById(id);

		// Merge with existing report
		this.mapper.merge(newReport, oldReport);
	}

	public ScreeningReportDTO generateReport(String jobPostId, List<String> cvIds, String languageCode) throws QorvaException {
        // Find job post and ToString it
        var jobPost = this.jobPostService.findOneById(jobPostId).toJobTitleAndDescription();

        // Find all CV by IDs
		List<CVDTO> cvdtos = this.cvService.findManyByIds(cvIds).getContent();

		// Get authenticated companyId
		var companyId = this.getAuthenticatedCompanyId();

        // Build a report details list
		var reportDetails = cvdtos.parallelStream().map(cvdto -> getReport(jobPost, QorvaUtils.toJSON(cvdto), languageCode, companyId)).toList();

		// Build the final report and persist it
		var reportToSave = ScreeningReportDTO.builder()
			.reportName("Report_"+ Instant.now().toString())
			.status(ReportStatusEnum.TEMPORARY.getStatus())
			.reportDetails(reportDetails)
			.companyId(companyId)
			.build();

		// persist and return
		return this.createOne(reportToSave);
    }

	public ReportDetails getReport(String jobPost, String cvData, String languageCode, String companyId) {
		log.info("CV Data: {}", cvData);
		return this.openAIService.generateReport(cvData, jobPost, languageCode);
	}
}

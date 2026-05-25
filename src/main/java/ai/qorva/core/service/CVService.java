package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.querybuilder.CVQueryBuilder;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.Availability;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {
    private final CVMapper cvMapper;

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    private final JobPostService jobPostService;
    private final PdfGenerationService pdfGenerator;
    private final MatchingReportRepository matchingReportRepository;
    private final ChatsRepository chatsRepository;
    private final UsageMonitoringService usageMonitoringService;

    @Autowired
    public CVService(
        CVRepository repository,
        CVMapper cvMapper,
        CVQueryBuilder queryBuilder,
        OpenAIService openAIService,
        OpenAIResultMapper openAIResultMapper,
        JobPostService jobPostService,
        PdfGenerationService pdfGenerator,
        CVMapper cVMapper,
        MatchingReportRepository matchingReportRepository,
        ChatsRepository chatsRepository,
        UsageMonitoringService usageMonitoringService) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
        this.jobPostService = jobPostService;
        this.pdfGenerator = pdfGenerator;
        this.cvMapper = cVMapper;
        this.matchingReportRepository = matchingReportRepository;
        this.chatsRepository = chatsRepository;
        this.usageMonitoringService = usageMonitoringService;
    }

    @Override
    protected void preProcessCreateOne(CVDTO dto) throws QorvaException {
        super.preProcessCreateOne(dto);
        dto.setApplicantNumber(UUID.randomUUID().toString().toUpperCase(Locale.ROOT));

        // Apply default availability settings
        if (dto.getPersonalInformation() == null) {
            dto.setPersonalInformation(new PersonalInformation());
        }
        var info = dto.getPersonalInformation();
        if (info.getAvailability() == null) {
            info.setAvailability(new Availability());
        }
        var availability = info.getAvailability();
        if (availability.getOpenToWork() == null) {
            availability.setOpenToWork(true);
        }
        if (!StringUtils.hasText(availability.getStatus())) {
            availability.setStatus("openButNotSearching");
        }
        if (availability.getRemoteOnly() == null) {
            availability.setRemoteOnly(false);
        }
    }

    @Override
    protected void preProcessUpdateOne(String id, CVDTO newCV) throws QorvaException {
        super.preProcessUpdateOne(id, newCV);

        var existingCV = Optional
            .ofNullable(this.findOneById(id))
            .orElseThrow(() -> {
                log.warn("Unable to update CV. Resource {} not found", id);
                return new QorvaException("Unable to update CV. CV not found");
            });

        this.mapper.merge(newCV, existingCV);
    }

    public List<CVDTO> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing for {} files", files.size());

        if (files.size() > 100) {
            log.error("CV Service - Exceeded the maximum of 100 files");
            throw new QorvaException("Only up to 100 files are allowed");
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = files.stream()
                .<CompletableFuture<CVDTO>>map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return processFile(file, tenantId);
                    } catch (RuntimeException | QorvaException e) {
                        log.error("CV Service - Error processing file: {}", file.getOriginalFilename(), e);
                        return null;
                    }
                }, executor))
                .toList();

            var processedFiles = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

            if (processedFiles.isEmpty()) {
                throw new QorvaException(
                    "CV Service - No files processed",
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR
                );
            }

            log.debug("CV Service - {} CVs saved - Marking open job posts as needing reports", processedFiles.size());
            jobPostService.markOpenJobPostsAsNeedingReports(tenantId);

            log.debug("CV Service - File upload completed");
            return processedFiles;
        }
    }

    public CVDTO processFile(MultipartFile file, String tenantId) throws RuntimeException, QorvaException {
        var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
        String fileContent = fileReaderContext.readFile(file);

        log.debug("Processing file: {}", file.getOriginalFilename());
        return extractCVData(fileContent, tenantId);
    }

    private CVDTO extractCVData(String cvContent, String tenantId) throws QorvaException {
        if (!StringUtils.hasText(cvContent)) {
            log.warn("CV Service - CV Content is empty");
            throw new QorvaException("CV Service - CV Content is empty");
        }
        var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
        var content = this.openAIService.streamCVExtraction(cvContent);

        if (!StringUtils.hasText(content)) {
            log.warn("CV content extraction failed");
            throw new QorvaException("CV content extraction failed");
        }

        incrementUsageSilently(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);

        var outputDTO = outputConverter.convert(content);
        var cvDtoToPersist = this.openAIResultMapper.map(outputDTO);
        cvDtoToPersist.setTenantId(tenantId);

        return createOne(cvDtoToPersist);
    }

    public List<CVDTO> match(JobPostDTO jobPostDTO) throws QorvaException {
        var rules = jobPostDTO.getScoringRules();
        Boolean filterOpenToWork = rules != null ? rules.getFilterOpenToWork() : null;
        var includedStatuses = rules != null ? rules.getAvailabilityStatuses() : null;

        var matchingCVs = ((CVRepository) this.repository).similaritySearch(
            jobPostDTO.getEmbedding(),
            new ObjectId(jobPostDTO.getTenantId()),
            filterOpenToWork,
            includedStatuses
        );

        if (Objects.isNull(matchingCVs) || matchingCVs.isEmpty()) {
            log.warn("CV Service - CVs matching job description not found");
            return List.of();
        }

        return matchingCVs.stream().map(cvMapper::map).toList();
    }

    public Page<CVDTO> searchAll(String tenantId, String searchTerms, int pageSize, int pageNumber) throws QorvaException {
        try {
            preProcessSearchAll(tenantId, searchTerms, pageSize, pageNumber);
            Page<CV> entities = ((CVRepository) this.repository).searchAll(searchTerms, tenantId, Pageable.ofSize(pageSize).withPage(pageNumber));
            postProcessSearchAll(entities);
            return renderFindAll(entities);
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources by IDs");
        }
    }

    protected void preProcessSearchAll(String tenantId, String searchTerms, int pageSize, int pageNumber) throws QorvaException {
        Assert.notNull(searchTerms, "Search terms must not be null");
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");

        if (Objects.isNull(tenantId) || tenantId.isEmpty()) {
            throw new QorvaException("Tenant ID must not be null or  empty");
        }
    }

    protected void postProcessSearchAll(Page<CV> entities) {
        log.debug("postProcessSearchAll: {} CV found", entities.getContent().size());
    }

    public List<String> findAllTagsByTenantId(String tenantId) {
        return ((CVRepository) this.repository).findAllTagsByTenantId(new ObjectId(tenantId));
    }

    public List<DashboardData.SkillReport> getSkillReportByTenantId(String tenantId) {
        return ((CVRepository) this.repository).getSkillReportByTenantId(new ObjectId(tenantId));
    }

    public byte[] generateCVInPdfFormat(String cvId, String languageCode) throws QorvaException {
        var cvData = this.findOneById(cvId);
        return this.pdfGenerator.generateCV(cvData, languageCode);
    }

    private void incrementUsageSilently(String tenantId, UsageMonitoringService.FeatureKey key) {
        try {
            usageMonitoringService.incrementUsage(tenantId, key, 1);
        } catch (Exception e) {
            log.warn("Failed to increment {} usage for tenant={}", key, tenantId, e);
        }
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        log.info("Deleted CV with ID: {}", id);

        var countDeletedReports = this.matchingReportRepository.deleteByTenantIdAndCandidateInfoCandidateId(tenantId, id);
        log.info("Deleted {} reports associated with CV ID: {}", countDeletedReports, id);

        var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextCvId(tenantId, id);
        log.info("Deleted {} chats associated with CV ID: {}", countDeletedChats, id);
    }
}

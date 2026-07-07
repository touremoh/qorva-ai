package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.querybuilder.CVQueryBuilder;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.Availability;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.exception.QorvaErrorCodes;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {
    private final CVMapper cvMapper;

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    private final JobPostService jobPostService;
    private final MatchingReportRepository matchingReportRepository;
    private final ChatsRepository chatsRepository;
    private final UsageMonitoringService usageMonitoringService;

    private static final int DEFAULT_MATCH_LIMIT = 10;

    @Autowired
    public CVService(
        CVRepository repository,
        CVMapper cvMapper,
        CVQueryBuilder queryBuilder,
        OpenAIService openAIService,
        OpenAIResultMapper openAIResultMapper,
        JobPostService jobPostService,
        CVMapper cVMapper,
        MatchingReportRepository matchingReportRepository,
        ChatsRepository chatsRepository,
        UsageMonitoringService usageMonitoringService) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
        this.jobPostService = jobPostService;
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
        this.mapper.merge(newCV, getExistingForUpdate());
    }

    @Override
    protected void postProcessUpdateOne(CV entity) {
        // Since entity was update we've needed to relaunch the marching report again
        this.jobPostService.markOpenJobPostsAsNeedingReports(entity.getTenantId());
    }

    public List<CVDTO> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing for {} files", files.size());

        if (files.size() > 100) {
            log.error("CV Service - Exceeded the maximum of 100 files");
            throw new QorvaException(QorvaErrorCodes.CV_MAX_FILES_EXCEEDED);
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
                throw new QorvaException(QorvaErrorCodes.CV_NO_FILES_PROCESSED, HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
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
            throw new QorvaException(QorvaErrorCodes.CV_CONTENT_EMPTY);
        }
        var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
        var content = this.openAIService.streamCVExtraction(cvContent);

        if (!StringUtils.hasText(content)) {
            log.warn("CV content extraction failed");
            throw new QorvaException(QorvaErrorCodes.CV_EXTRACTION_FAILED);
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
            includedStatuses,
            DEFAULT_MATCH_LIMIT
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

    public List<DashboardData.ClusteringCategoryReport> getSkillDepthReportByTenantId(String tenantId) {
        return withPercentages(((CVRepository) this.repository).getSkillDepthReportByTenantId(new ObjectId(tenantId)));
    }

    public List<DashboardData.ClusteringCategoryReport> getSeniorityLevelReportByTenantId(String tenantId) {
        return withPercentages(((CVRepository) this.repository).getSeniorityLevelReportByTenantId(new ObjectId(tenantId)));
    }

    public List<DashboardData.ClusteringCategoryReport> getLeadershipReportByTenantId(String tenantId) {
        return withPercentages(((CVRepository) this.repository).getLeadershipReportByTenantId(new ObjectId(tenantId)));
    }

    public List<DashboardData.ClusteringCategoryReport> getLearningVelocityReportByTenantId(String tenantId) {
        return withPercentages(((CVRepository) this.repository).getLearningVelocityReportByTenantId(new ObjectId(tenantId)));
    }

    private List<DashboardData.ClusteringCategoryReport> withPercentages(List<DashboardData.ClusteringCategoryReport> raw) {
        int total = raw.stream().mapToInt(DashboardData.ClusteringCategoryReport::count).sum();
        if (total == 0) return raw;
        return raw.stream()
            .map(r -> new DashboardData.ClusteringCategoryReport(r.name(), r.count(), Math.round((double) r.count() / total * 1000.0) / 10.0))
            .toList();
    }

    private void incrementUsageSilently(String tenantId, UsageMonitoringService.FeatureKey key) {
        try {
            usageMonitoringService.incrementUsage(tenantId, key, 1);
        } catch (Exception e) {
            log.warn("Failed to increment {} usage for tenant={}", key, tenantId, e);
        }
    }

    public CVDuplicatesData.DuplicatesPage findDuplicates(String tenantId, int page, int pageSize) {
        var repo = (CVRepository) repository;
        var emailGroups = repo.findEmailDuplicates(new ObjectId(tenantId)).stream()
            .map(r -> new CVDuplicatesData.DuplicateGroup("EMAIL", r.matchValue(), r.count(), r.cvs()))
            .toList();
        var phoneGroups = repo.findPhoneDuplicates(new ObjectId(tenantId)).stream()
            .map(r -> new CVDuplicatesData.DuplicateGroup("PHONE", r.matchValue(), r.count(), r.cvs()))
            .toList();

        var all = new ArrayList<CVDuplicatesData.DuplicateGroup>(emailGroups.size() + phoneGroups.size());
        all.addAll(emailGroups);
        all.addAll(phoneGroups);

        long total = all.size();
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        int fromIdx = page * pageSize;
        int toIdx = (int) Math.min(fromIdx + pageSize, total);
        var content = fromIdx >= total ? List.<CVDuplicatesData.DuplicateGroup>of() : all.subList(fromIdx, toIdx);

        return new CVDuplicatesData.DuplicatesPage(content, page, pageSize, total, totalPages, toIdx < total);
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

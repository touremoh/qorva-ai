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
import ai.qorva.core.dto.UploadResult;
import ai.qorva.core.enums.QualityFlagEnum;
import ai.qorva.core.dto.common.Availability;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.enums.ContentDateSourceEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.utils.CVContentDateResolver;
import ai.qorva.core.utils.CVPageImageRenderer;
import ai.qorva.core.utils.CVQualityFlagResolver;
import ai.qorva.core.utils.VisionEscalationPolicy;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
    private final S3StorageService s3StorageService;
    private final LibraryQualityCacheEvictor libraryQualityCacheEvictor;

    private static final int DEFAULT_MATCH_LIMIT = 10;

    /**
     * Cap for the synchronous upload path. Kept at or below Tomcat's max-part-count
     * (see application.yml) so the request is never rejected at the connector; larger
     * batches go through the asynchronous bulk-upload job.
     */
    public static final int SYNC_UPLOAD_MAX_FILES = 50;

    /** Stashes the attachment S3 key between pre- and post-delete hooks (same pattern as existingDTOForUpdate). */
    private final ThreadLocal<String> attachmentKeyForDelete = new ThreadLocal<>();

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
        UsageMonitoringService usageMonitoringService,
        S3StorageService s3StorageService,
        LibraryQualityCacheEvictor libraryQualityCacheEvictor) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
        this.jobPostService = jobPostService;
        this.cvMapper = cVMapper;
        this.matchingReportRepository = matchingReportRepository;
        this.chatsRepository = chatsRepository;
        this.usageMonitoringService = usageMonitoringService;
        this.s3StorageService = s3StorageService;
        this.libraryQualityCacheEvictor = libraryQualityCacheEvictor;
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

        // Content-based freshness evidence — applies to every creation path (upload, seeding, API).
        CVContentDateResolver.resolve(dto);
        CVQualityFlagResolver.resolve(dto);
    }

    @Override
    protected void preProcessUpdateOne(String id, CVDTO newCV) throws QorvaException {
        super.preProcessUpdateOne(id, newCV);
        this.mapper.merge(newCV, getExistingForUpdate());
        // Flags must never drift from the data — recompute after every merge.
        CVContentDateResolver.resolve(newCV);
        CVQualityFlagResolver.resolve(newCV);
    }

    @Override
    protected void postProcessCreateOne(CV entity) {
        this.libraryQualityCacheEvictor.evict(entity.getTenantId());
    }

    @Override
    protected void postProcessUpdateOne(CV entity) {
        // Since entity was update we've needed to relaunch the marching report again
        this.jobPostService.markOpenJobPostsAsNeedingReports(entity.getTenantId());
        this.libraryQualityCacheEvictor.evict(entity.getTenantId());
    }

    /** Quality flags surfaced as per-file warnings in the upload response. */
    private static final Set<String> UPLOAD_WARNING_FLAGS = Set.of(
        QualityFlagEnum.MISSING_PHONE.name(),
        QualityFlagEnum.MISSING_EMAIL.name(),
        QualityFlagEnum.MISSING_CONTACT.name(),
        QualityFlagEnum.NO_WORK_EXPERIENCE.name(),
        QualityFlagEnum.LOW_AI_CONFIDENCE.name(),
        QualityFlagEnum.NO_AI_ANALYSIS.name()
    );

    public List<UploadResult> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing for {} files", files.size());

        if (files.size() > SYNC_UPLOAD_MAX_FILES) {
            log.error("CV Service - Exceeded the maximum of {} files", SYNC_UPLOAD_MAX_FILES);
            throw new QorvaException(QorvaErrorCodes.CV_MAX_FILES_EXCEEDED);
        }

        // Whole-batch capacity check: the gate used to run once per request against the
        // *current* consumption, so a batch could overshoot the plan limit by its own size.
        if (!usageMonitoringService.hasCapacityFor(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, files.size())) {
            log.warn("CV Service - Tenant {} lacks screening-action capacity for {} files", tenantId, files.size());
            throw new QorvaException(QorvaErrorCodes.USAGE_SCREENING_LIMIT_EXCEEDED, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = files.stream()
                .<CompletableFuture<UploadResult>>map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return toUploadResult(file, processFile(file, tenantId), tenantId);
                    } catch (QorvaException e) {
                        log.error("CV Service - Error processing file: {}", file.getOriginalFilename(), e);
                        return new UploadResult(file.getOriginalFilename(), UploadResult.STATUS_FAILED,
                            null, null, List.of(), e.getMessage());
                    } catch (RuntimeException e) {
                        log.error("CV Service - Error processing file: {}", file.getOriginalFilename(), e);
                        return new UploadResult(file.getOriginalFilename(), UploadResult.STATUS_FAILED,
                            null, null, List.of(), QorvaErrorCodes.CV_EXTRACTION_FAILED);
                    }
                }, executor))
                .toList();

            var results = futures.stream().map(CompletableFuture::join).toList();

            boolean anyCreated = results.stream()
                .anyMatch(r -> !UploadResult.STATUS_FAILED.equals(r.status()));
            if (!anyCreated) {
                throw new QorvaException(QorvaErrorCodes.CV_NO_FILES_PROCESSED, HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
            }

            log.debug("CV Service - {} files processed - Marking open job posts as needing reports", results.size());
            jobPostService.markOpenJobPostsAsNeedingReports(tenantId);

            log.debug("CV Service - File upload completed");
            return results;
        }
    }

    /** Ingest gate: annotate the freshly created CV with duplicate collision + parse warnings. */
    private UploadResult toUploadResult(MultipartFile file, CVDTO created, String tenantId) {
        var warnings = created.getQualityFlags() == null ? List.<String>of()
            : created.getQualityFlags().stream().filter(UPLOAD_WARNING_FLAGS::contains).toList();

        var contact = created.getPersonalInformation() != null
            ? created.getPersonalInformation().getContact() : null;
        var email = contact != null ? contact.getEmail() : null;
        var phone = contact != null ? contact.getPhone() : null;

        var duplicate = ((CVRepository) this.repository).findContactMatch(
                new ObjectId(tenantId), email, phone, new ObjectId(created.getId()))
            .map(existing -> {
                var existingContact = existing.getPersonalInformation() != null
                    ? existing.getPersonalInformation().getContact() : null;
                var matchType = existingContact != null && email != null
                    && email.equals(existingContact.getEmail()) ? "EMAIL" : "PHONE";
                return new UploadResult.DuplicateMatch(
                    existing.getId(),
                    existing.getPersonalInformation() != null ? existing.getPersonalInformation().getName() : null,
                    matchType,
                    existing.getCreatedAt());
            })
            .orElse(null);

        return new UploadResult(
            file.getOriginalFilename(),
            duplicate != null ? UploadResult.STATUS_DUPLICATE_DETECTED : UploadResult.STATUS_CREATED,
            created,
            duplicate,
            warnings,
            null);
    }

    /**
     * Resolves an upload-time duplicate by keeping the new CV and removing the old copy.
     * Recruiter knowledge survives: tags from the old copy are merged into the new one.
     */
    public CVDTO replaceDuplicate(String newCvId, String oldCvId, String tenantId) throws QorvaException {
        if (newCvId.equals(oldCvId)) {
            throw new QorvaException("Cannot replace a CV with itself",
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
        }
        var newCv = this.findOneById(newCvId);   // tenant ownership asserted inside
        var oldCv = this.findOneById(oldCvId);

        var mergedTags = new LinkedHashSet<String>();
        if (newCv.getTags() != null) mergedTags.addAll(newCv.getTags());
        if (oldCv.getTags() != null) mergedTags.addAll(oldCv.getTags());
        newCv.setTags(new ArrayList<>(mergedTags));

        var updated = this.updateOne(newCvId, newCv);
        this.deleteOneById(oldCvId, tenantId);   // cascades reports/chats/S3 + evicts cache
        return updated;
    }

    /** Ingest from staged bytes (async candidate submissions) — same pipeline as live uploads. */
    public CVDTO processFile(byte[] bytes, String originalFilename, String contentType, String tenantId)
        throws RuntimeException, QorvaException {
        return processFile(new ai.qorva.core.utils.ByteArrayMultipartFile(bytes, originalFilename, contentType), tenantId);
    }

    public CVDTO processFile(MultipartFile file, String tenantId) throws RuntimeException, QorvaException {
        var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
        String fileContent = fileReaderContext.readFile(file);

        log.debug("Processing file: {}", file.getOriginalFilename());

        // Text-only parsing is blind to pixels. A near-empty text layer means a scanned
        // or fully designed CV — go straight to vision instead of failing on empty text.
        CVDTO cvDtoToPersist;
        boolean visionFirst = false;
        if (VisionEscalationPolicy.isTextTooThin(fileContent)) {
            var pages = renderForVision(file);
            if (!pages.isEmpty()) {
                cvDtoToPersist = extractCVDataFromImages(fileContent, pages, tenantId);
                visionFirst = true;
            } else {
                cvDtoToPersist = extractCVData(fileContent, tenantId);
            }
        } else {
            cvDtoToPersist = extractCVData(fileContent, tenantId);
        }
        cvDtoToPersist.setRawText(fileContent);

        // Document metadata date is freshness evidence; work-history dates may override it in preProcessCreateOne.
        var documentDate = fileReaderContext.readDocumentDate(file);
        if (documentDate != null) {
            cvDtoToPersist.setContentDate(documentDate);
            cvDtoToPersist.setContentDateSource(ContentDateSourceEnum.DOC_METADATA.name());
        }

        // Best-effort: an S3 outage must not cost the recruiter the parsed CV.
        try {
            cvDtoToPersist.setAttachment(this.s3StorageService.uploadCvDocument(tenantId, file));
        } catch (QorvaException e) {
            log.warn("CV Service - Could not store original file {} for tenant {} — CV will be saved without attachment",
                file.getOriginalFilename(), tenantId);
        }

        CVDTO created;
        try {
            created = createOne(cvDtoToPersist);
        } catch (QorvaException | RuntimeException e) {
            // Persisting failed — remove the uploaded object so S3 never holds orphans.
            if (cvDtoToPersist.getAttachment() != null) {
                this.s3StorageService.deleteObject(cvDtoToPersist.getAttachment().getS3Key());
            }
            throw e;
        }

        // Text pass done but symptoms of pixel-hidden content (missing contact, low
        // confidence) — refine with a vision pass. Best-effort: the text result stands
        // if the refinement fails, and the CV is only billed once.
        if (!visionFirst && VisionEscalationPolicy.shouldEscalate(fileContent, created.getQualityFlags())) {
            created = refineWithVision(created, fileContent, file, tenantId);
        }
        return created;
    }

    private List<CVPageImageRenderer.PageImage> renderForVision(MultipartFile file) {
        try {
            return CVPageImageRenderer.render(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (Exception e) {
            log.warn("CV Service - Could not read {} for vision rendering: {}", file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    private CVDTO refineWithVision(CVDTO created, String fileContent, MultipartFile file, String tenantId) {
        try {
            var pages = renderForVision(file);
            if (pages.isEmpty()) {
                return created;
            }
            log.debug("CV Service - Vision refinement for {} ({} page images)", file.getOriginalFilename(), pages.size());
            var refined = parseExtractionResult(this.openAIService.streamCVVisionExtraction(fileContent, pages), tenantId);
            // updateOne merges the existing document into null fields and recomputes quality flags.
            return updateOne(created.getId(), refined);
        } catch (Exception e) {
            log.warn("CV Service - Vision refinement failed for {} — keeping the text-pass result: {}",
                file.getOriginalFilename(), e.getMessage());
            return created;
        }
    }

    /** Vision-first extraction for CVs with no usable text layer. Bills one screening action, like the text pass. */
    private CVDTO extractCVDataFromImages(String partialText, List<CVPageImageRenderer.PageImage> pages, String tenantId)
        throws QorvaException {
        var content = this.openAIService.streamCVVisionExtraction(partialText, pages);
        if (!StringUtils.hasText(content)) {
            log.warn("CV vision extraction failed");
            throw new QorvaException(QorvaErrorCodes.CV_EXTRACTION_FAILED);
        }
        incrementUsageSilently(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);
        return parseExtractionResult(content, tenantId);
    }

    /**
     * Re-analysis fallback for CVs with no usable raw text (scanned/designed documents):
     * fetches the original from S3 and runs vision extraction over its rendered pages.
     * Returns false when there is no attachment or nothing renderable — the caller skips.
     */
    public boolean reanalyzeFromOriginal(CVDTO existing, String tenantId) {
        try {
            var attachment = existing.getAttachment();
            if (attachment == null || !StringUtils.hasText(attachment.getS3Key())) {
                return false;
            }
            var bytes = this.s3StorageService.fetchObjectBytes(attachment.getS3Key());
            var pages = CVPageImageRenderer.render(attachment.getFileName(), attachment.getContentType(), bytes);
            if (pages.isEmpty()) {
                return false;
            }
            var refined = parseExtractionResult(
                this.openAIService.streamCVVisionExtraction(existing.getRawText(), pages), tenantId);
            updateOne(existing.getId(), refined);
            incrementUsageSilently(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);
            return true;
        } catch (Exception e) {
            log.warn("CV Service - Vision re-analysis from original failed for CV {}: {}", existing.getId(), e.getMessage());
            return false;
        }
    }

    private CVDTO parseExtractionResult(String content, String tenantId) throws QorvaException {
        if (!StringUtils.hasText(content)) {
            throw new QorvaException(QorvaErrorCodes.CV_EXTRACTION_FAILED);
        }
        var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
        var dto = this.openAIResultMapper.map(outputConverter.convert(content));
        dto.setTenantId(tenantId);
        return dto;
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

        return cvDtoToPersist;
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
        // Paged server-side — never load all groups into memory (libraries can hold tens of thousands of CVs).
        return ((CVRepository) this.repository).findDuplicateGroups(new ObjectId(tenantId), page, pageSize);
    }

    @Override
    protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        super.preProcessDeleteOneById(id, tenantId);

        // Capture the S3 key now — after the DB delete the reference is gone.
        var dto = this.findOneById(id);
        this.attachmentKeyForDelete.set(dto.getAttachment() != null ? dto.getAttachment().getS3Key() : null);
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        log.info("Deleted CV with ID: {}", id);

        var countDeletedReports = this.matchingReportRepository.deleteByTenantIdAndCandidateInfoCandidateId(tenantId, id);
        log.info("Deleted {} reports associated with CV ID: {}", countDeletedReports, id);

        var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextCvId(tenantId, id);
        log.info("Deleted {} chats associated with CV ID: {}", countDeletedChats, id);

        try {
            this.s3StorageService.deleteObject(this.attachmentKeyForDelete.get());
        } finally {
            this.attachmentKeyForDelete.remove();
        }

        this.libraryQualityCacheEvictor.evict(tenantId);
    }
}

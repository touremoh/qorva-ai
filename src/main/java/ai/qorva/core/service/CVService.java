package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.events.NewJobPostEvent;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.dao.querybuilder.CVQueryBuilder;
import ai.qorva.core.security.LanguageContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {
    private final CVMapper cvMapper;

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    private final ApplicationEventPublisher publisher;
    private final JobPostService jobPostService;
    private final EmbeddingModel embeddingModel;
    private final PdfGenerationService pdfGenerator;
    private final MatchingReportRepository matchingReportRepository;
    private final ChatsRepository chatsRepository;

    @Autowired
    public CVService(
        CVRepository repository,
        CVMapper cvMapper,
        CVQueryBuilder queryBuilder,
        OpenAIService openAIService,
        OpenAIResultMapper openAIResultMapper,
        ApplicationEventPublisher publisher,
        JobPostService jobPostService,
        EmbeddingModel embeddingModel,
        PdfGenerationService pdfGenerator,
        CVMapper cVMapper, MatchingReportRepository matchingReportRepository, ChatsRepository chatsRepository) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
		this.publisher = publisher;
		this.jobPostService = jobPostService;
		this.embeddingModel = embeddingModel;
		this.pdfGenerator = pdfGenerator;
        this.cvMapper = cVMapper;
		this.matchingReportRepository = matchingReportRepository;
		this.chatsRepository = chatsRepository;
    }

    @Override
    protected void preProcessUpdateOne(String id, CVDTO newCV) throws QorvaException {
        super.preProcessUpdateOne(id, newCV);

        // Check if CV exists (find by ID)
        var existingCV = Optional
            .ofNullable(this.findOneById(id))
            .orElseThrow(() -> {
                log.warn("Unable to update CV. Resource {} not found", id);
				return new QorvaException("Unable to update CV. CV not found");
            });

        // If cv was found, then merge the source with the target
        this.mapper.merge(newCV, existingCV);
    }

    @Transactional
    public List<CVDTO> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing");

        if (files.size() > 100) {
            log.error("CV Service - Exceeded the maximum of 100 files");
            throw new QorvaException("Only up to 100 files are allowed");
        }

        var processFiles = files
                .parallelStream()
                .map(file -> {
                    try {
                        return processFile(file, tenantId);
                    } catch (RuntimeException | QorvaException e) {
                        log.error("CV Service - Error processing file: {}", file.getOriginalFilename(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (processFiles.isEmpty()) {
            throw new QorvaException(
                "CV Service - No files processed",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        log.debug("CV Service - CV saved in database - Triggering CV screening for all job post");

        publishCVUpsertEvents(tenantId);

        log.debug("CV Service - File upload completed");

        return processFiles;
    }

    public CVDTO processFile(MultipartFile file, String tenantId) throws RuntimeException, QorvaException {
        var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
        String fileContent = fileReaderContext.readFile(file);

        log.debug("Processing file: {}", file.getOriginalFilename());
        return extractCVData(fileContent, tenantId);
    }


    private CVDTO extractCVData(String cvContent, String tenantId) throws QorvaException {
        // Check CV Content exists
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

        var outputDTO = outputConverter.convert(content);
        var cvDtoToPersist = this.openAIResultMapper.map(outputDTO);
        cvDtoToPersist.setTenantId(tenantId);

        // Create a vector embedding for the CV
        cvDtoToPersist.setEmbedding(this.embeddingModel.embed(cvContent));

        // Persist and return
        return  createOne(cvDtoToPersist);
    }

    public List<CVDTO> findCVsMatchingJobDescription(JobPostDTO jobPostDTO) throws QorvaException {
        // Perform similarity search
        var matchingCVs = ((CVRepository) this.repository).similaritySearch(
            jobPostDTO.getEmbedding(),
            new ObjectId(jobPostDTO.getTenantId())
        );

        // Get the list of documents ids
        if (Objects.isNull(matchingCVs) || matchingCVs.isEmpty()) {
            log.warn("CV Service - CVs matching job description not found");
            return List.of();
        }

        // Get the corresponding DTOs
		return matchingCVs.stream().map(cvMapper::map).toList();
    }

    public Page<CVDTO> searchAll(String tenantId, String searchTerms, int pageSize, int pageNumber) throws QorvaException {
        try {
            // Pre Process
            preProcessSearchAll(tenantId, searchTerms, pageSize, pageNumber);

            // Process
            Page<CV> entities = ((CVRepository)this.repository).searchAll(searchTerms, tenantId, Pageable.ofSize(pageSize).withPage(pageNumber));

            // Post Process
            postProcessSearchAll(entities);

            // Render results
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

    protected void postProcessSearchAll(Page<CV> entities) throws QorvaException {
        log.debug("postProcessSearchAll: {} CV found", entities.getContent().size());
    }

    public List<String> findAllTagsByTenantId(String tenantId) {
        return ((CVRepository)this.repository).findAllTagsByTenantId(new ObjectId(tenantId));
    }

    public List<DashboardData.SkillReport> getSkillReportByTenantId(String tenantId) {
        return ((CVRepository)this.repository).getSkillReportByTenantId(new ObjectId(tenantId));
    }

    public void publishCVUpsertEvents(String tenantId) throws QorvaException {
        int pageSize = 25;
        int pageNumber = 0;
        long totalCount = this.jobPostService.countAll(tenantId);

        if (totalCount > 0) {
            int totalPages = totalCount % pageSize == 0 ? (int) (totalCount / pageSize) : (int) (totalCount / pageSize) + 1;
            do {
                var params = Map.of(
                    "tenantId", tenantId,
                    "status", JobPostStatusEnum.OPEN.getStatus(),
                    "pageNumber", String.valueOf(pageNumber),
                    "pageSize", String.valueOf(pageSize)
                );
                var jobPosts = this.jobPostService.findAll(params);
                for (JobPostDTO jobPost : jobPosts.getContent()) {
                    jobPost.setLanguageCode(LanguageContextHolder.getLanguage());
                    this.publisher.publishEvent(new NewJobPostEvent(jobPost));
                }
            } while (++pageNumber < totalPages);
        }
    }

    public byte[] generateCVInPdfFormat(String cvId, String languageCode) throws QorvaException {
        // Find CV by ID
        var cvData = this.findOneById(cvId);

        // Generate PDF
        return this.pdfGenerator.generateCV(cvData, languageCode);
    }

    @Override
    protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        log.info("Deleted CV with ID: {}", id);

        // Delete Reports associated with this CV
        var countDeletedReports = this.matchingReportRepository.deleteByTenantIdAndCandidateInfoCandidateId(tenantId, id);

        log.info("Deleted {} reports associated with CV ID: {}", countDeletedReports, id);

        // Delete chat associated with this CV
        var countDeletedChats = this.chatsRepository.deleteByTenantIdAndContextCvId(tenantId, id);

        log.info("Deleted {} chats associated with CV ID: {}", countDeletedChats, id);
    }
}

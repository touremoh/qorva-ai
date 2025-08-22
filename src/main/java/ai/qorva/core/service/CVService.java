package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.events.CVScreeningEvent;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.qbe.CVQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    private final ApplicationEventPublisher publisher;
    private final JobPostService jobPostService;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public CVService(
		CVRepository repository,
		CVMapper cvMapper,
		CVQueryBuilder queryBuilder,
		OpenAIService openAIService,
		OpenAIResultMapper openAIResultMapper,
		ApplicationEventPublisher publisher,
        JobPostService jobPostService,
        EmbeddingModel embeddingModel) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
		this.publisher = publisher;
		this.jobPostService = jobPostService;
		this.embeddingModel = embeddingModel;
	}

    @Override
    protected void preProcessUpdateOne(String id, CVDTO cvdto) throws QorvaException {
        super.preProcessUpdateOne(id, cvdto);

        // Check if CV exists (find by ID)
        var cvFound = Optional
            .ofNullable(this.findOneById(id))
            .orElseThrow(() -> {
                log.warn("Unable to update CV. Resource {} not found", id);
				return new QorvaException("Unable to update CV. CV not found");
            });

        // If cv was found, then merge the source with the target
        this.mapper.merge(cvdto, cvFound);
    }

    @Transactional
    public List<CVDTO> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing");

        if (files.size() > 10) {
            log.error("CV Service - Exceeded the maximum of 10 files");
            throw new QorvaException("Only up to 10 files are allowed");
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
        String content = this.openAIService.streamCVExtraction(cvContent)
            .reduce(String::concat)
            .block();

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
        var results = ((CVRepository) this.repository).similaritySearch(
            jobPostDTO.getEmbedding(),
            new ObjectId(jobPostDTO.getTenantId())
        );

        // Get the list of documents ids
        if (Objects.isNull(results) || results.isEmpty()) {
            log.warn("CV Service - CVs matching job description not found");
            return List.of();
        }

        // Get documents IDs
		var ids = results.stream().map(CV::getId).toList();

        // Find the CVs for those ids
        return this.findAllByIds(ids);
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
        var searchCriteria = JobPostDTO.builder().tenantId(tenantId).status(JobPostStatusEnum.OPEN.getStatus()).build();
        long totalCount = this.jobPostService.countAll(tenantId);

        if (totalCount > 0) {
            int totalPages = totalCount % pageSize == 0 ? (int) (totalCount / pageSize) : (int) (totalCount / pageSize) + 1;
            do {
                var jobPosts = this.jobPostService.findAll(searchCriteria, pageNumber, pageSize);
                for (JobPostDTO jobPost : jobPosts.getContent()) {
                    this.publisher.publishEvent(new CVScreeningEvent(jobPost));
                }
            } while (++pageNumber < totalPages);
        }
    }
}

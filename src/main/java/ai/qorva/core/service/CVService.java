package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.qbe.CVQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    protected final EmbeddingModel embeddingModel;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    public CVService(
		CVRepository repository,
		CVMapper cvMapper,
		CVQueryBuilder queryBuilder,
		OpenAIService openAIService,
		OpenAIResultMapper openAIResultMapper,
        EmbeddingModel embeddingModel) {
        super(repository, cvMapper, queryBuilder);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
		this.embeddingModel = embeddingModel;
	}

    public List<CVDTO> upload(List<MultipartFile> files, String tenantId) throws QorvaException {
        log.debug("CV Service - Starting file processing");

        if (files.size() > 10) {
            log.error("CV Service - Exceeded the maximum of 10 files");
            throw new QorvaException("Only up to 10 files are allowed");
        }

        return files
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

        // Persist and return
        return  this.createOne(cvDtoToPersist);
    }

    public List<CVDTO> findCVsMatchingJobDescription(float[] vectorQuery, String tenantId) throws QorvaException {
        // Perform similarity search
        var results = ((CVRepository) this.repository).similaritySearch(vectorQuery, new ObjectId(tenantId));

        // Get the list of documents ids
        if (Objects.isNull(results) || results.isEmpty()) {
            log.warn("CV Service - CVs matching job description not found");
            return List.of();
        }

        // Get documents IDs
		var ids = results.stream().map(cv -> {
            log.debug("CV Score: {}", cv.getScore());
            return cv.getId();
        }).toList();

        // Find the CVs for those ids
        return this.findAllByIds(ids);
    }

    public List<CVDTO> searchAll(String tenantId, String searchTerms, int pageSize, int pageNumber) throws QorvaException {
        try {
            // Pre Process
            preProcessSearchAll(tenantId, searchTerms, pageSize, pageNumber);

            // Process
            List<CV> entities = ((CVRepository)this.repository).searchAll(searchTerms);

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
    protected void postProcessSearchAll(List<CV> entities) throws QorvaException {
        log.debug("postProcessSearchAll: {} CV found", entities.size());
    }
}

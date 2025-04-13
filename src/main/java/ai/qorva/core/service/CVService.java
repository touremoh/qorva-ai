package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    private final OpenAIService openAIService;
    private final OpenAIResultMapper openAIResultMapper;
    private final VectorStore vectorStore;
    private static final String COMPANY_ID = "tenantId";

    @Autowired
    public CVService(CVRepository repository, CVMapper cvMapper, OpenAIService openAIService, OpenAIResultMapper openAIResultMapper, VectorStore vectorStore) {
        super(repository, cvMapper);
        this.openAIService = openAIService;
        this.openAIResultMapper = openAIResultMapper;
		this.vectorStore = vectorStore;
	}

    public List<CVDTO> upload(List<MultipartFile> files) throws QorvaException {
        log.debug("CV Service - Starting file processing");

        if (files.size() > 10) {
            log.error("CV Service - Exceeded the maximum of 10 files");
            throw new QorvaException("Only up to 10 files are allowed");
        }

        var companyId = this.getAuthenticatedCompanyId();
        log.debug("CV Service - Company ID: {}", companyId);

        return files.parallelStream().map(file -> processFile(file, companyId)).toList();
    }

    public CVDTO processFile(MultipartFile file, String companyId) {
        try {
            var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
            String fileContent = fileReaderContext.readFile(file);

            log.debug("Processing file: {}", file.getOriginalFilename());
            return extractCVData(fileContent, companyId);

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Error processing file: " + file.getOriginalFilename(), e);
        }
    }

    private CVDTO extractCVData(String cvContent, String companyId) {
        try {
            // Check CV Content exists
            if (!StringUtils.hasText(cvContent)) {
                log.warn("CV Service - CV Content is empty");
                throw new QorvaException("CV Service - CV Content is empty");
            }
            var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
            String content = this.openAIService.streamCVExtraction(cvContent)
                .reduce(String::concat)
                .block();

            var outputDTO = outputConverter.convert(content);
            var cvDtoToPersist = this.openAIResultMapper.map(outputDTO);
            cvDtoToPersist.setTenantId(companyId);

            // Get persisted CV
            var persistedCV = this.createOne(cvDtoToPersist);

            // Build document meta data id for vector store
            Map<String, Object> documentMetadata = Map.of(COMPANY_ID, companyId);

            // Add the document to the store
            this.vectorStore.add(List.of(new Document(persistedCV.getId(), cvContent, documentMetadata)));

            // return persisted CV
            return persistedCV;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process CV data", e);
        }
    }

    public List<CVDTO> findCVsMatchingJobDescription(String jobDescription, String companyId) throws QorvaException {
        // Init filter expression
        var fe = new FilterExpressionBuilder();

        // Perform similarity search
        List<Document> results = this.vectorStore.similaritySearch(
            SearchRequest.defaults()
                .withQuery(jobDescription)
                .withTopK(100)
                .withSimilarityThreshold(0.7)
                .withFilterExpression(fe.eq(COMPANY_ID, companyId).build())
        );

        // Get the list of documents ids
        var ids = results.stream().map(Document::getId).toList();

        // Find the CVs for those ids
        var cvList = this.findManyByIds(ids);

        // return final results
        return cvList.getContent();
    }
}

package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.mapper.OpenAIResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ai.qorva.core.enums.QorvaErrorsEnum.BAD_REQUEST;
import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    private final OpenAIService openAIService;
	private final OpenAIResultMapper openAIResultMapper;

	@Autowired
    public CVService(CVRepository repository, CVMapper cvMapper, OpenAIService openAIService, OpenAIResultMapper openAIResultMapper) {
        super(repository, cvMapper);
		this.openAIService = openAIService;
		this.openAIResultMapper = openAIResultMapper;
	}

    @Override
    protected void preProcessCreateOne(CVDTO cvdto) throws QorvaException {
        super.preProcessCreateOne(cvdto);
    }

    @Override
    protected void postProcessFindManyByIds(Page<CV> entities) throws QorvaException {
        super.postProcessFindManyByIds(entities);

        // Extract content
        List<CV> results = entities.getContent();

        // Get companyId
        var companyId = results.getFirst().getCompanyId();

        // Make sure all the returned data belongs to the same company
        var countInconsistentCV = results.stream().filter(cv -> !cv.getCompanyId().equals(companyId)).count();

        // Check results
        if (countInconsistentCV > 0) {
            log.warn("Inconsistent CV found");
            throw new QorvaException(
                BAD_REQUEST.getMessage(),
                BAD_REQUEST.getHttpStatus().value(),
                BAD_REQUEST.getHttpStatus()
            );
        }
    }

    public Flux<CVDTO> upload(List<MultipartFile> files) throws QorvaException {
        log.debug("CV Service - Starting files processing");

        // Make sure we don't exceed the maximum number of files to process
        if (files.size() > 10) {
            log.error("CV Service - Exceeded the maximum of 10 files");
            throw new QorvaException("Only a many of 10 files are allowed");
        }

        // Get the  companyId
        var companyId = this.getAuthenticatedCompanyId();

        // Start processing files one by one
        return Flux
            .fromIterable(files)
            .flatMap(file -> Flux.defer(() -> {
                try {
                    // Extract content from each file
                    var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
                    String fileContent = fileReaderContext.readFile(file);

                    log.info("Processing file: {}", file.getOriginalFilename());
                    log.debug("File content: {}", fileContent);

                    // Call OpenAI API reactively and return results as a stream
                    return this.extractCVData(fileContent, companyId);
                } catch (QorvaException e) {
                    log.error("Error processing file: {}", file.getOriginalFilename(), e);
                    return Flux.error(new QorvaException("Unable to process file: " + file.getOriginalFilename(), e));
                }
            }));

    }

    public Flux<CVDTO> extractCVData(String cvContent, String companyId) {
        // Instantiate Output Converter
        var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);

        // Stream the OpenAI API response reactively
        return this.openAIService.streamCVExtraction(cvContent)
            .reduce(String::concat) // Combine response fragments reactively
            .flatMapMany(content -> {
                try {
                    // Convert the combined content into DTOs
                    CVOutputDTO outputDTO = outputConverter.convert(content);

                    // Map Output DTO to CV DTO
                    var cvDtoToPersist = this.openAIResultMapper.map(outputDTO);

                    // set company id before persist
                    cvDtoToPersist.setCompanyId(companyId);

                    // persist CV to db
                    var persistedCV = this.createOne(cvDtoToPersist);

                    // Map to CVDTO and return as a Flux
                    return Flux.just(persistedCV);
                } catch (Exception e) {
                    return Flux.error(new QorvaException("Failed to map OpenAI response", e));
                }
            });
    }

}

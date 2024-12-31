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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

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

    public List<CVDTO> upload(List<MultipartFile> files) throws QorvaException {
        log.info("CV Service - Starting file processing");

        if (files.size() > 10) {
            log.error("CV Service - Exceeded the maximum of 10 files");
            throw new QorvaException("Only up to 10 files are allowed");
        }

        var companyId = this.getAuthenticatedCompanyId();
        log.info("CV Service - Company ID: {}", companyId);

        return files.parallelStream().map(file -> processFile(file, companyId)).toList();
    }

    private CVDTO processFile(MultipartFile file, String companyId) {
        try {
            var fileReaderContext = new QorvaFileReaderContext(QorvaFileReaderFactory.getFileReader(file));
            String fileContent = fileReaderContext.readFile(file);

            log.info("Processing file: {}", file.getOriginalFilename());
            return extractCVData(fileContent, companyId);

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Error processing file: " + file.getOriginalFilename(), e);
        }
    }

    private CVDTO extractCVData(String cvContent, String companyId) {
        try {
            var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
            String content = this.openAIService.streamCVExtraction(cvContent)
                .reduce(String::concat)
                .block();

            var outputDTO = outputConverter.convert(content);
            var cvDtoToPersist = this.openAIResultMapper.map(outputDTO);
            cvDtoToPersist.setCompanyId(companyId);

            return this.createOne(cvDtoToPersist);
        } catch (Exception e) {
            throw new RuntimeException("Failed to map OpenAI response", e);
        }
    }
}

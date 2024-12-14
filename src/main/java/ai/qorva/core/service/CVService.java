package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static ai.qorva.core.enums.QorvaErrorsEnum.BAD_REQUEST;
import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    @Autowired
    public CVService(QorvaRepository<CV> repository, AbstractQorvaMapper<CV, CVDTO> mapper) {
        super(repository, mapper);
    }

    @Override
    protected void preProcessCreateOne(CVDTO input) throws QorvaException {
        super.preProcessCreateOne(input);

        // Make sure the cv has companyId
        if (!StringUtils.hasText(input.getCompanyId())) {
            log.warn("CompanyId is empty");
            throw new QorvaException(
                BAD_REQUEST.getMessage(),
                BAD_REQUEST.getHttpStatus().value(),
                BAD_REQUEST.getHttpStatus()
            );
        }
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
}

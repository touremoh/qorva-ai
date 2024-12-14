package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Company;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CompanyDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CompanyService extends AbstractQorvaService<CompanyDTO, Company> {

    @Autowired
    public CompanyService(QorvaRepository<Company> repository, AbstractQorvaMapper<Company, CompanyDTO> mapper) {
        super(repository, mapper);
    }

    @Override
    protected void preProcessCreateOne(CompanyDTO input) throws QorvaException {
        super.preProcessCreateOne(input);

        // Check if user does not exist
        if (this.existsByData("NA", CompanyDTO.builder().name(input.getName()).build())) {
            log.error("Trying to create an existing company {}", input);
            throw new QorvaException(
                "Unable to create an existing company " + input.getName(),
                HttpStatus.NOT_ACCEPTABLE.value(),
                HttpStatus.NOT_ACCEPTABLE
            );
        }
    }
}

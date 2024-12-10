package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Company;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CompanyDTO;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CompanyService extends AbstractQorvaService<CompanyDTO, Company> {

    @Autowired
    public CompanyService(QorvaRepository<Company> repository, AbstractQorvaMapper<Company, CompanyDTO> mapper) {
        super(repository, mapper);
    }
}

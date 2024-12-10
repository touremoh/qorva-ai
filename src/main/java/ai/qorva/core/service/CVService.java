package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CVService extends AbstractQorvaService<CVDTO, CV> {

    @Autowired
    public CVService(QorvaRepository<CV> repository, AbstractQorvaMapper<CV, CVDTO> mapper) {
        super(repository, mapper);
    }
}

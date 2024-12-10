package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CVScreeningReport;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.CVScreeningReportDTO;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CVScreeningReportService extends AbstractQorvaService<CVScreeningReportDTO, CVScreeningReport> {

    @Autowired
    public CVScreeningReportService(QorvaRepository<CVScreeningReport> repository, AbstractQorvaMapper<CVScreeningReport, CVScreeningReportDTO> mapper) {
        super(repository, mapper);
    }
}

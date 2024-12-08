package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.CVScreeningReport;
import ai.qorva.core.dto.CVScreeningReportDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CVScreeningReportMapper extends AbstractQorvaMapper<CVScreeningReport, CVScreeningReportDTO> {
}

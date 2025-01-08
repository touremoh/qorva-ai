package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.ScreeningReport;
import ai.qorva.core.dto.ScreeningReportDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScreeningReportMapper extends AbstractQorvaMapper<ScreeningReport, ScreeningReportDTO> {
}

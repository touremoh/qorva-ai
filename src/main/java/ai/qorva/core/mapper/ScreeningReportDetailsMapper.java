package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.ScreeningReportDetails;
import ai.qorva.core.dto.ScreeningReportDetailsDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScreeningReportDetailsMapper extends AbstractQorvaMapper<ScreeningReportDetails, ScreeningReportDetailsDTO> {
}

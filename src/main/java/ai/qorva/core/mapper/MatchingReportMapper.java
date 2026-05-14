package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.MatchingReportDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MatchingReportMapper extends AbstractQorvaMapper<MatchingReport, MatchingReportDTO> {
}

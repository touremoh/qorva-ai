package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.ClientReport;
import ai.qorva.core.dto.ClientReportDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientReportMapper extends AbstractQorvaMapper<ClientReport, ClientReportDTO> {
}

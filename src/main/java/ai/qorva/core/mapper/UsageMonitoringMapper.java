package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dto.UsageMonitoringDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UsageMonitoringMapper extends AbstractQorvaMapper<UsageMonitoring, UsageMonitoringDTO> {
}

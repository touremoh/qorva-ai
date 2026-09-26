package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dto.StripeEventLogDTO;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface StripeEventMapper extends AbstractQorvaMapper<StripeEventLog, StripeEventLogDTO> {
}

package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.StripeEventLogDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StripeEventRequestMapper extends QorvaRequestMapper<StripeEventLogDTO> {
}

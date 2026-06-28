package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.dto.PendingEmailNotificationDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PendingEmailNotificationMapper extends AbstractQorvaMapper<PendingEmailNotification, PendingEmailNotificationDTO> {
}

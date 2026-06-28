package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingEmailNotificationRepository extends QorvaRepository<PendingEmailNotification> {
}

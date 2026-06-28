package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.PendingEmailNotificationSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PendingEmailNotificationQueryBuilder implements QorvaQueryBuilder<PendingEmailNotification> {

    @Override
    public MongoSpecification<PendingEmailNotification> buildQuery(Map<String, String> params) {
        return MongoSpecification
            .where(PendingEmailNotificationSpecifications.tenantIdEquals(params.get("tenantId")))
            .and(PendingEmailNotificationSpecifications.statusEquals(params.get("status")))
            .and(PendingEmailNotificationSpecifications.notificationTypeEquals(params.get("notificationType")))
            .and(PendingEmailNotificationSpecifications.userIdEquals(params.get("userId")));
    }
}

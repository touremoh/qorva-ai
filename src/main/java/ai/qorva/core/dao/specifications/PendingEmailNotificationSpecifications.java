package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import org.springframework.data.mongodb.core.query.Criteria;

public final class PendingEmailNotificationSpecifications {

    private PendingEmailNotificationSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static MongoSpecification<PendingEmailNotification> tenantIdEquals(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
        return () -> Criteria.where("tenantId").is(tenantId);
    }

    public static MongoSpecification<PendingEmailNotification> statusEquals(String status) {
        if (status == null || status.isBlank()) return MongoSpecifications.empty();
        return () -> Criteria.where("status").is(status);
    }

    public static MongoSpecification<PendingEmailNotification> notificationTypeEquals(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) return MongoSpecifications.empty();
        return () -> Criteria.where("notificationType").is(notificationType);
    }

    public static MongoSpecification<PendingEmailNotification> userIdEquals(String userId) {
        if (userId == null || userId.isBlank()) return MongoSpecifications.empty();
        return () -> Criteria.where("userId").is(userId);
    }

    public static MongoSpecification<PendingEmailNotification> attemptsLessThan(int maxAttempts) {
        return () -> Criteria.where("attempts").lt(maxAttempts);
    }
}

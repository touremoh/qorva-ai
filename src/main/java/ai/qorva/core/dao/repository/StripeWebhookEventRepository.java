package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.StripeWebhookEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StripeWebhookEventRepository extends MongoRepository<StripeWebhookEvent, String> {
}
